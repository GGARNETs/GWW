package com.github.razorplay01.instance;

import com.github.razorplay01.GWW;
import com.github.razorplay01.config.GwwPuzzles;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.custom.*;
import com.github.razorplay01.entity.custom.util.EscapeRoomPersistable;
import com.github.razorplay01.instance.Instance.EntitySnapshot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Gestiona las instances guardadas en config/GWW/instances/&lt;nombre&gt;.dat:
 * capturarlas del mundo, guardarlas, pegarlas en cualquier coordenada y borrarlas.
 */
public class InstanceManager {
    private InstanceManager() {
    }

    private static final String EXTENSION = ".dat";
    /** Margen extra que se limpia alrededor del radio capturado antes de pegar. */
    private static final int CLEAR_MARGIN = 5;
    /** Alcance de la búsqueda de entidades al reconstruir los enlaces de los puzzles. */
    private static final int RELINK_RANGE = 150;

    private static final Map<String, Instance> INSTANCES = new LinkedHashMap<>();

    public static Path getDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("GWW").resolve("instances");
    }

    // ==================== ARCHIVOS ====================

    /**
     * Carga (o recarga) todas las instances del disco a memoria.
     * Devuelve los errores encontrados para poder mostrarlos en /escaperoom reload.
     */
    public static synchronized List<String> loadAll() {
        List<String> errors = new ArrayList<>();
        INSTANCES.clear();
        Path dir = getDirectory();

        try {
            Files.createDirectories(dir);
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(EXTENSION)).toList()) {
                    String fileName = file.getFileName().toString();
                    String name = fileName.substring(0, fileName.length() - EXTENSION.length());
                    try {
                        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                        INSTANCES.put(name, Instance.deserialize(tag));
                    } catch (Exception e) {
                        errors.add("No se pudo leer la instance '" + name + "': " + e.getMessage());
                    }
                }
            }
            GWW.LOGGER.info("[GWW] {} instances cargadas desde {}", INSTANCES.size(), dir);
        } catch (IOException e) {
            errors.add("Error leyendo la carpeta de instances: " + e.getMessage());
            GWW.LOGGER.error("[GWW] Error leyendo la carpeta de instances", e);
        }
        return errors;
    }

    public static synchronized void save(String name, Instance instance) throws IOException {
        Path dir = getDirectory();
        Files.createDirectories(dir);
        NbtIo.writeCompressed(instance.serialize(), dir.resolve(name + EXTENSION));
        INSTANCES.put(name, instance);
    }

    /** Devuelve false si la instance no existía. */
    public static synchronized boolean delete(String name) throws IOException {
        if (INSTANCES.remove(name) == null) {
            return false;
        }
        Files.deleteIfExists(getDirectory().resolve(name + EXTENSION));
        return true;
    }

    public static Instance get(String name) {
        return INSTANCES.get(name);
    }

    public static Collection<String> getNames() {
        return INSTANCES.keySet();
    }

    /** El nombre acaba siendo un nombre de archivo, así que no vale cualquier cosa. */
    public static boolean isValidName(String name) {
        return name.matches("[A-Za-z0-9_-]+");
    }

    // ==================== MUNDO ====================

    /**
     * Captura las entidades de la sala en un cubo de lado 2*radius centrado en
     * origin, guardándolas relativas a ese punto.
     */
    public static Instance capture(ServerLevel level, BlockPos origin, int radius) {
        Instance instance = new Instance(origin, radius);
        Vec3 center = Vec3.atCenterOf(origin);

        for (Entity entity : level.getEntities((Entity) null, new AABB(origin).inflate(radius),
                InstanceManager::isRoomEntity)) {
            instance.add(new EntitySnapshot(entity, center));
        }

        return instance;
    }

    /** Radio total que toca un paste: el capturado más el margen que se limpia alrededor. */
    public static int pasteRadius(Instance instance) {
        return instance.getRadius() + CLEAR_MARGIN;
    }

    /** Borra las entidades de la sala alrededor de un punto. */
    public static void clear(ServerLevel level, BlockPos center, int radius) {
        level.getEntities((Entity) null, new AABB(center).inflate(radius), InstanceManager::isRoomEntity)
                .forEach(Entity::discard);
    }

    private static boolean isRoomEntity(Entity entity) {
        return entity instanceof BaseEntity
                || entity instanceof UblablaEntity
                || entity instanceof ItemFrame;
    }

    /**
     * Pega la instance con su origen en target, limpiando antes lo que hubiera ahí.
     */
    public static void paste(ServerLevel level, Instance instance, BlockPos target) {
        Vec3 newCenter = Vec3.atCenterOf(target);
        BlockPos tileDelta = target.subtract(instance.getOrigin());

        clear(level, target, instance.getRadius() + CLEAR_MARGIN);

        for (EntitySnapshot snapshot : instance.getEntities()) {
            Vec3 absolutePos = newCenter.add(snapshot.getRelativePos());
            CompoundTag entityTag = snapshot.getEntityData().copy();

            ListTag posList = new ListTag();
            posList.add(DoubleTag.valueOf(absolutePos.x));
            posList.add(DoubleTag.valueOf(absolutePos.y));
            posList.add(DoubleTag.valueOf(absolutePos.z));
            entityTag.put("Pos", posList);

            ListTag rotList = new ListTag();
            rotList.add(FloatTag.valueOf(snapshot.getYaw()));
            rotList.add(FloatTag.valueOf(snapshot.getPitch()));
            entityTag.put("Rotation", rotList);

            entityTag.putUUID("UUID", UUID.randomUUID());

            // Las entidades colgantes (item frames) guardan su bloque en TileX/Y/Z,
            // que manda sobre "Pos" al cargarse: hay que desplazarlo también.
            if (entityTag.contains("TileX")) {
                entityTag.putInt("TileX", entityTag.getInt("TileX") + tileDelta.getX());
                entityTag.putInt("TileY", entityTag.getInt("TileY") + tileDelta.getY());
                entityTag.putInt("TileZ", entityTag.getInt("TileZ") + tileDelta.getZ());
            }

            try {
                Entity entity = EntityType.loadEntityRecursive(entityTag, level, e -> e);
                if (entity == null) continue;

                level.addFreshEntity(entity);

                if (entity instanceof EscapeRoomPersistable persistable) {
                    persistable.restoreEscapeRoomData(snapshot.getEntityData(), target);
                    persistable.resetPuzzleState();
                }
                if (entity instanceof UblablaEntity ublabla) {
                    ublabla.resetToPatrol();
                }
                if (entity instanceof InterruptorIndustrialEntity interruptor
                        && interruptor.isOn() && !interruptor.areAllCablesReady()) {
                    interruptor.setState(0);
                }
            } catch (Exception e) {
                GWW.LOGGER.error("[GWW] Error al cargar la entidad {} de la instance", snapshot.getEntityType(), e);
            }
        }

        reLinkPanels(level, target);
        reLinkInterruptors(level, target);
        reLinkRejas(level, target);
        reLinkDoors(level, target);
        reLinkCodigoPanels(level, target);
        applyPuzzlesConfig(level, instance, target);
    }

    // ==================== SOLUCIONES DESDE puzzles.yml ====================
    // Cables, válvulas y cajas toman su configuración del yml en el momento de
    // montar la sala: editar el yml + /escaperoom reload deja las soluciones
    // nuevas listas para el próximo reset, sin re-guardar instances.

    /**
     * Orden estable de las entidades de una sala, el que usa puzzles.yml: primero
     * por altura (de abajo a arriba) y a igual altura de oeste a este y de norte a
     * sur. Todas las salas son clones, así que el orden es idéntico en todas.
     */
    public static Comparator<Entity> roomOrder() {
        return Comparator.<Entity>comparingDouble(Entity::getY)
                .thenComparingDouble(Entity::getX)
                .thenComparingDouble(Entity::getZ);
    }

    private static void applyPuzzlesConfig(ServerLevel level, Instance instance, BlockPos center) {
        AABB room = new AABB(center).inflate(instance.getRadius() + CLEAR_MARGIN);

        int[] rotaciones = GwwPuzzles.cablesRotaciones();
        int[] tipos = GwwPuzzles.cablesTipos();
        if (rotaciones != null || tipos != null) {
            List<CableEntity> cables = level.getEntitiesOfClass(CableEntity.class, room, c -> true);
            cables.sort(roomOrder());
            if (rotaciones != null && !cables.isEmpty() && rotaciones.length != cables.size()) {
                GWW.LOGGER.warn("[GWW] puzzles.yml trae {} rotaciones de cable pero la sala en {} tiene {} cables.",
                        rotaciones.length, center.toShortString(), cables.size());
            }
            for (int i = 0; i < cables.size(); i++) {
                if (rotaciones != null && i < rotaciones.length) {
                    cables.get(i).setCorrectState(rotaciones[i]);
                }
                if (tipos != null && i < tipos.length) {
                    cables.get(i).setCableType(tipos[i]);
                }
            }
            GwwDebug.log(GwwDebug.Category.PUZZLE, "Sala {}: {} cables configurados desde puzzles.yml",
                    center.toShortString(), cables.size());
        }

        int[] estados = GwwPuzzles.valvulasEstados();
        if (estados != null) {
            List<ValvulaEntity> valvulas = level.getEntitiesOfClass(ValvulaEntity.class, room, v -> true);
            valvulas.sort(roomOrder());
            if (!valvulas.isEmpty() && estados.length != valvulas.size()) {
                GWW.LOGGER.warn("[GWW] puzzles.yml trae {} estados de valvula pero la sala en {} tiene {} valvulas.",
                        estados.length, center.toShortString(), valvulas.size());
            }
            for (int i = 0; i < valvulas.size() && i < estados.length; i++) {
                valvulas.get(i).setCorrectState(estados[i]);
            }
            GwwDebug.log(GwwDebug.Category.PUZZLE, "Sala {}: {} valvulas configuradas desde puzzles.yml",
                    center.toShortString(), valvulas.size());
        }

        int cajasConfiguradas = 0;
        for (CajaEntity caja : level.getEntitiesOfClass(CajaEntity.class, room, c -> true)) {
            if (applyCajaContents(caja.getCustomName(), caja::setBoxContents)) {
                cajasConfiguradas++;
            }
        }
        for (CajaHerramientasEntity caja : level.getEntitiesOfClass(CajaHerramientasEntity.class, room, c -> true)) {
            if (applyCajaContents(caja.getCustomName(), caja::setBoxContents)) {
                cajasConfiguradas++;
            }
        }
        if (cajasConfiguradas > 0) {
            GwwDebug.log(GwwDebug.Category.PUZZLE, "Sala {}: {} cajas llenadas desde puzzles.yml",
                    center.toShortString(), cajasConfiguradas);
        }
    }

    private static boolean applyCajaContents(net.minecraft.network.chat.Component customName,
                                             java.util.function.Consumer<List<net.minecraft.world.item.ItemStack>> setter) {
        if (customName == null) {
            return false;
        }
        String name = customName.getString();
        if (!GwwPuzzles.hasCajaContenido(name)) {
            return false;
        }
        setter.accept(GwwPuzzles.cajaContenido(name));
        return true;
    }

    // ==================== RE-ENLAZADO DE PUZZLES ====================
    // Los enlaces se guardan como desplazamientos entre entidades, así que tras
    // pegar hay que volver a emparejarlas por cercanía a la posición esperada.

    private static AABB relinkArea(BlockPos center) {
        return new AABB(center).inflate(RELINK_RANGE);
    }

    private static void reLinkPanels(ServerLevel level, BlockPos center) {
        level.getEntitiesOfClass(PanelFusiblesEntity.class, relinkArea(center), p -> true)
                .forEach(panel -> {
                    panel.updateLinkedTurtles(1, puzzleState(panel, 1));
                    panel.updateLinkedTurtles(2, puzzleState(panel, 2));
                });
    }

    private static int puzzleState(PanelFusiblesEntity panel, int puzzleId) {
        boolean solved = (puzzleId == 1) ? panel.isPuzzle1Solved() : panel.isPuzzle2Solved();
        if (solved) {
            return 2;
        }
        return panel.areAllSlotsFilled(puzzleId) ? 1 : 0;
    }

    private static void reLinkInterruptors(ServerLevel level, BlockPos center) {
        AABB area = relinkArea(center);
        List<InterruptorIndustrialEntity> interruptors =
                level.getEntitiesOfClass(InterruptorIndustrialEntity.class, area, i -> true);
        List<CableEntity> allCables = level.getEntitiesOfClass(CableEntity.class, area, c -> true);
        List<UblablaEntity> allUblablas = level.getEntitiesOfClass(UblablaEntity.class, area, u -> true);
        List<PanelCodigoEntity> allPanels = level.getEntitiesOfClass(PanelCodigoEntity.class, area, p -> true);
        List<PanelTecladoEntity> allTeclados = level.getEntitiesOfClass(PanelTecladoEntity.class, area, p -> true);

        for (InterruptorIndustrialEntity interruptor : interruptors) {
            List<Vec3> savedCables = new ArrayList<>(interruptor.getLinkedCables());
            interruptor.unlinkAllCables();
            for (Vec3 relPos : savedCables) {
                CableEntity closest = findClosest(interruptor.position().add(relPos), allCables, 4.0);
                if (closest != null) {
                    interruptor.linkCable(closest);
                }
            }

            reLinkSingle(interruptor.getLinkedUblablas(), interruptor.position(), allUblablas);
            reLinkSingle(interruptor.getLinkedPanels(), interruptor.position(), allPanels);

            // Los teclados nacen encendidos: con interruptor vinculado, este manda en
            // ambos sentidos (apagado incluido). linkTeclado ya les aplica el estado.
            List<Vec3> savedTeclados = new ArrayList<>(interruptor.getLinkedTeclados());
            interruptor.getLinkedTeclados().clear();
            for (Vec3 relPos : savedTeclados) {
                PanelTecladoEntity closestTeclado =
                        findClosest(interruptor.position().add(relPos), allTeclados, 4.0);
                if (closestTeclado != null) {
                    interruptor.linkTeclado(closestTeclado);
                }
            }

            if (interruptor.isOn()) {
                PanelCodigoEntity panel = interruptor.getLinkedPanel();
                if (panel != null) {
                    panel.setPowered(true);
                }
            }
        }
    }

    /**
     * Reengancha un enlace único (guardado como desplazamiento) a la entidad real
     * más cercana, o lo descarta si ya no existe.
     */
    private static <T extends Entity> void reLinkSingle(List<Vec3> links, Vec3 sourcePos, List<T> candidates) {
        if (links.isEmpty()) {
            return;
        }
        T closest = findClosest(sourcePos.add(links.get(0)), candidates, 4.0);
        links.clear();
        if (closest != null) {
            links.add(closest.position().subtract(sourcePos));
        }
    }

    private static void reLinkRejas(ServerLevel level, BlockPos center) {
        AABB area = relinkArea(center);
        List<PanelEnergiaEntity> allPanels =
                level.getEntitiesOfClass(PanelEnergiaEntity.class, area, p -> true);

        for (RejaDuctoEntity reja : level.getEntitiesOfClass(RejaDuctoEntity.class, area, r -> true)) {
            List<Vec3> saved = new ArrayList<>(reja.getLinkedPowerPanels());
            reja.unlinkAllPowerPanels();

            for (Vec3 relPos : saved) {
                PanelEnergiaEntity closest = findClosest(reja.position().add(relPos), allPanels, 5.0);
                if (closest != null) {
                    reja.linkPowerPanel(closest, reja.position());
                }
            }

            if (reja.isPowerPanelActive()) {
                reja.tryOpenAutomatically();
            }
        }
    }

    private static void reLinkDoors(ServerLevel level, BlockPos center) {
        level.getEntitiesOfClass(PanelFusiblesEntity.class, relinkArea(center), p -> true)
                .forEach(PanelFusiblesEntity::updateAllLinkedDoors);
    }

    private static void reLinkCodigoPanels(ServerLevel level, BlockPos center) {
        level.getEntitiesOfClass(PanelCodigoEntity.class, relinkArea(center), p -> true)
                .stream()
                .filter(PanelCodigoEntity::isSolved)
                .forEach(PanelCodigoEntity::updateAllLinkedDoors);
    }

    private static <T extends Entity> T findClosest(Vec3 pos, List<T> entities, double maxDistSq) {
        T closest = null;
        double closestDist = maxDistSq;
        for (T entity : entities) {
            double dist = entity.position().distanceToSqr(pos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }
        return closest;
    }
}
