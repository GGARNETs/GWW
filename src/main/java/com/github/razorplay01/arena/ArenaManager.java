package com.github.razorplay01.arena;

import com.github.razorplay01.GWW;
import com.github.razorplay01.system.NoiseDetectionSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Carga las arenas desde config/GWW/config.yml y gestiona el agrupado
 * automático de jugadores por zona (barra de ruido compartida).
 */
public class ArenaManager {
    private ArenaManager() {
    }

    private static final Map<String, Arena> ARENAS = new LinkedHashMap<>();
    // Jugadores actualmente asignados a una arena (uuid -> arenaId)
    private static final Map<UUID, String> PLAYER_ARENAS = new HashMap<>();
    /**
     * En qué arena está cada jugador a efectos de la luz (uuid -> arenaId). Va aparte
     * de PLAYER_ARENAS porque la luz no depende ni del gamemode ni de que la partida
     * esté en marcha: solo de estar dentro de la zona.
     */
    private static final Map<UUID, String> PLAYER_LIGHT_ARENAS = new HashMap<>();
    /**
     * Arenas con la partida en marcha (/escaperoom start). Mientras una arena no
     * esté aquí, sus jugadores no tienen barra de ruido y sus Ublablas están
     * congelados. Es estado de partida, no de config: al reiniciar todo empieza parado.
     */
    private static final Set<String> RUNNING = new HashSet<>();
    private static final int GROUP_CHECK_INTERVAL = 20;
    private static int tickCounter = 0;

    private static final String FILE_HEADER = """
            # ============================================================
            #  GWW - Configuración de arenas del Escape Room
            # ============================================================
            # Cada arena define:
            #   instance : nombre de la instance a pegar, guardada con
            #              /escaperoom save <nombre> en config/GWW/instances/<nombre>.dat
            #   origin   : coordenada donde se pega la instance. La instance se guarda
            #              relativa a donde se capturó, así que puede ir donde quieras.
            #   zone     : cubo [min, max] de la zona de juego. Los jugadores en
            #              survival o aventura dentro de la zona comparten la barra
            #              de ruido automáticamente, sin límite de jugadores.
            #   jail     : (opcional) cubo [min, max] de la jaula. Se aplica a los
            #              Ublablas de la zona al hacer /escaperoom reset <id>.
            #
            # Comandos:
            #   /escaperoom capture <radio>    -> captura la sala alrededor del jugador
            #   /escaperoom save <nombre>      -> guarda lo capturado como instance
            #   /escaperoom arena create <id> <instance> <origin> <zoneMin> <zoneMax>
            #                                  -> crea una arena y la guarda aquí
            #   /escaperoom arena setjail <id> <min> <max> -> define la jaula
            #   /escaperoom arena remove <id>  -> elimina la arena
            #   /escaperoom arena list         -> lista las arenas cargadas
            #   /escaperoom reset <id>         -> pega la instance de la arena en su origin
            #   /escaperoom reload             -> recarga instances y arenas sin reiniciar
            #
            # OJO: los comandos de arena reescriben este archivo; los comentarios
            # que añadas fuera de esta cabecera se perderán.
            #
            # Formato de cada arena:
            #  arena_1:
            #    instance: "sala_base"
            #    origin: [100, 64, 200]
            #    zone:
            #      min: [80, 50, 180]
            #      max: [130, 90, 230]
            #    jail:
            #      min: [90, 64, 190]
            #      max: [95, 68, 195]
            """;

    private static final String DEFAULT_CONFIG = FILE_HEADER + "arenas:\n";

    public static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("GWW").resolve("config.yml");
    }

    /**
     * Carga (o recarga) el config. Devuelve la lista de errores encontrados
     * para poder mostrarlos al ejecutor de /escaperoom arena reload.
     */
    public static synchronized List<String> load() {
        List<String> errors = new ArrayList<>();
        ARENAS.clear();
        Path file = getConfigFile();

        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_CONFIG);
                GWW.LOGGER.info("[GWW] Config de arenas creado en {}", file);
                return errors;
            }

            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                root = new Yaml().load(in);
            }

            Object arenasObj = root != null ? root.get("arenas") : null;
            if (arenasObj == null) {
                GWW.LOGGER.info("[GWW] config.yml sin arenas definidas");
                return errors;
            }
            if (!(arenasObj instanceof Map<?, ?> arenas)) {
                errors.add("La sección 'arenas' de config.yml tiene un formato inválido.");
                return errors;
            }

            for (Map.Entry<?, ?> entry : arenas.entrySet()) {
                String id = String.valueOf(entry.getKey());
                try {
                    if (!(entry.getValue() instanceof Map<?, ?> def)) {
                        throw new IllegalArgumentException("formato inválido (debe ser una sección)");
                    }
                    ARENAS.put(id, parseArena(id, def));
                } catch (Exception e) {
                    errors.add("Arena '" + id + "': " + e.getMessage());
                }
            }
            // Una arena que ya no existe no puede seguir "en marcha"
            RUNNING.retainAll(ARENAS.keySet());
            GWW.LOGGER.info("[GWW] {} arenas cargadas desde config.yml", ARENAS.size());
        } catch (Exception e) {
            errors.add("Error leyendo config.yml: " + e.getMessage());
            GWW.LOGGER.error("[GWW] Error leyendo config.yml", e);
        } finally {
            rebuildSpatialIndex();
        }
        return errors;
    }

    private static Arena parseArena(String id, Map<?, ?> def) {
        Object instanceObj = def.get("instance");
        if (instanceObj == null) {
            throw new IllegalArgumentException("falta el campo 'instance'");
        }
        String instance = String.valueOf(instanceObj);

        BlockPos origin = parsePos(def.get("origin"), "origin");

        if (!(def.get("zone") instanceof Map<?, ?> zone)) {
            throw new IllegalArgumentException("falta la sección 'zone' (min/max)");
        }
        BlockPos zoneMin = parsePos(zone.get("min"), "zone.min");
        BlockPos zoneMax = parsePos(zone.get("max"), "zone.max");

        BlockPos jailMin = null;
        BlockPos jailMax = null;
        if (def.get("jail") instanceof Map<?, ?> jail) {
            jailMin = parsePos(jail.get("min"), "jail.min");
            jailMax = parsePos(jail.get("max"), "jail.max");
        }

        return new Arena(id, instance, origin, zoneMin, zoneMax, jailMin, jailMax);
    }

    private static BlockPos parsePos(Object value, String field) {
        if (value instanceof List<?> list && list.size() == 3
                && list.get(0) instanceof Number x
                && list.get(1) instanceof Number y
                && list.get(2) instanceof Number z) {
            return new BlockPos(x.intValue(), y.intValue(), z.intValue());
        }
        if (value instanceof String s) {
            String[] parts = s.trim().split("\\s+");
            if (parts.length == 3) {
                try {
                    return new BlockPos(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                    // cae al error genérico de abajo
                }
            }
        }
        throw new IllegalArgumentException("'" + field + "' debe ser [x, y, z]");
    }

    // ==================== ESCRITURA ====================

    /**
     * Añade (o reemplaza) una arena y reescribe config.yml.
     */
    public static synchronized void addOrUpdate(Arena arena) throws IOException {
        ARENAS.put(arena.getId(), arena);
        rebuildSpatialIndex();
        save();
    }

    /**
     * Elimina una arena y reescribe config.yml. Devuelve false si no existía.
     */
    public static synchronized boolean remove(String id) throws IOException {
        if (ARENAS.remove(id) == null) {
            return false;
        }
        rebuildSpatialIndex();
        save();
        return true;
    }

    private static void save() throws IOException {
        StringBuilder sb = new StringBuilder(FILE_HEADER);
        sb.append("arenas:\n");
        for (Arena arena : ARENAS.values()) {
            sb.append("  ").append(arena.getId()).append(":\n");
            sb.append("    instance: \"").append(arena.getInstanceName()).append("\"\n");
            sb.append("    origin: ").append(fmtPos(arena.getOrigin())).append("\n");
            sb.append("    zone:\n");
            sb.append("      min: ").append(fmtPos(arena.getZoneMin())).append("\n");
            sb.append("      max: ").append(fmtPos(arena.getZoneMax())).append("\n");
            if (arena.hasJail()) {
                sb.append("    jail:\n");
                sb.append("      min: ").append(fmtPos(arena.getJailMin())).append("\n");
                sb.append("      max: ").append(fmtPos(arena.getJailMax())).append("\n");
            }
        }

        Path file = getConfigFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString());
    }

    private static String fmtPos(BlockPos pos) {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }

    // ==================== CONSULTAS ====================

    public static Arena get(String id) {
        return ARENAS.get(id);
    }

    public static Collection<Arena> getAll() {
        return ARENAS.values();
    }

    public static Collection<String> getIds() {
        return ARENAS.keySet();
    }

    /**
     * Celdas de 256 bloques. Las salas del mapa están separadas ~230 bloques, así que
     * cada arena cae en una o dos celdas y la búsqueda queda en un par de comparaciones.
     */
    private static final int CELL_SHIFT = 8;
    private static final Map<Long, List<Arena>> SPATIAL_INDEX = new HashMap<>();

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    /**
     * Reconstruye el índice espacial. Hay que llamarlo tras cualquier cambio en ARENAS.
     */
    private static void rebuildSpatialIndex() {
        SPATIAL_INDEX.clear();
        for (Arena arena : ARENAS.values()) {
            AABB box = arena.getZoneAABB();
            int minX = Mth.floor(box.minX) >> CELL_SHIFT;
            int maxX = Mth.floor(box.maxX) >> CELL_SHIFT;
            int minZ = Mth.floor(box.minZ) >> CELL_SHIFT;
            int maxZ = Mth.floor(box.maxZ) >> CELL_SHIFT;
            for (int cellX = minX; cellX <= maxX; cellX++) {
                for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
                    SPATIAL_INDEX.computeIfAbsent(cellKey(cellX, cellZ), k -> new ArrayList<>()).add(arena);
                }
            }
        }
    }

    /**
     * Arena que contiene esa posición, o null.
     * <p>
     * Va por índice espacial y no recorriendo el mapa entero porque los Ublablas
     * preguntan esto en CADA tick: con un mapa de cientos de salas y un Ublabla por
     * sala, el barrido lineal era cuadrático y se comía el tick él solo.
     */
    public static Arena getArenaAt(Vec3 pos) {
        List<Arena> candidates = SPATIAL_INDEX.get(
                cellKey(Mth.floor(pos.x) >> CELL_SHIFT, Mth.floor(pos.z) >> CELL_SHIFT));
        if (candidates == null) {
            return null;
        }
        for (Arena arena : candidates) {
            if (arena.contains(pos)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Área en la que una entidad debe buscar a sus compañeras de puzzle: la zona de su
     * arena si está dentro de una, o el margen indicado si no (entidades sueltas en un
     * mundo de pruebas, sin arena configurada).
     * <p>
     * Importa que sea la zona y no un radio fijo: con salas montadas cerca unas de
     * otras, un radio grande alcanza salas vecinas y una partida acaba afectando a la
     * de al lado, además de escanear muchos más chunks de los necesarios.
     */
    public static AABB searchAreaAround(Vec3 pos, AABB fallback) {
        Arena arena = getArenaAt(pos);
        return arena != null ? arena.getZoneAABB() : fallback;
    }

    // ==================== ESTADO DE PARTIDA ====================

    public static boolean isRunning(String id) {
        return RUNNING.contains(id);
    }

    /**
     * Arranca o para la partida de una arena. Al parar, los jugadores salen del
     * grupo de ruido (se les oculta la barra) en la siguiente comprobación, que
     * se fuerza aquí para que el cambio se note al instante.
     */
    public static void setRunning(String id, boolean running) {
        if (running) {
            RUNNING.add(id);
            // Arrancar es empezar una partida nueva: se rearma la victoria y el aviso.
            EscapeRoomController.onArenaStarted(id);
        } else {
            RUNNING.remove(id);
        }
        tickCounter = GROUP_CHECK_INTERVAL;
    }

    /**
     * True si la posición cae dentro de una arena que está parada: es lo que
     * congela a los Ublablas. Fuera de toda arena no se congela nada, para no
     * romper las salas montadas sin arena configurada.
     */
    public static boolean isInsideStoppedArena(Vec3 pos) {
        Arena arena = getArenaAt(pos);
        return arena != null && !isRunning(arena.getId());
    }

    /**
     * ID de la arena a la que está asignado el jugador ahora mismo (o null).
     */
    public static String getArenaIdOf(UUID playerId) {
        return PLAYER_ARENAS.get(playerId);
    }

    // ==================== AGRUPADO AUTOMÁTICO ====================

    /**
     * Cada segundo comprueba en qué arena está cada jugador y reacciona solo a los
     * cambios: entrar o salir de una zona. Es el único sitio donde se detectan esas
     * transiciones, y de ahí cuelgan tanto el grupo de ruido como la luz.
     */
    public static void tickGroups(MinecraftServer server) {
        if (++tickCounter < GROUP_CHECK_INTERVAL) return;
        tickCounter = 0;
        if (ARENAS.isEmpty() && PLAYER_ARENAS.isEmpty() && PLAYER_LIGHT_ARENAS.isEmpty()) return;

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());

            Arena zone = getArenaAt(player.position());
            updateNoiseGroup(player, zone);
            updateLight(player, zone);
        }

        // Jugadores desconectados: sacarlos del grupo para que no arrastren
        // el estado de la arena al reconectarse en otro sitio.
        PLAYER_ARENAS.entrySet().removeIf(entry -> {
            if (!online.contains(entry.getKey())) {
                NoiseDetectionSystem.removeFromArenaGroup(entry.getKey());
                return true;
            }
            return false;
        });
        PLAYER_LIGHT_ARENAS.keySet().removeIf(id -> !online.contains(id));
    }

    private static void updateNoiseGroup(ServerPlayer player, Arena zone) {
        // Creativo y espectador quedan fuera del sistema: ni grupo, ni barra, ni ruido.
        // Y sin partida en marcha tampoco hay barra.
        Arena arena = zone;
        if (arena != null && (!NoiseDetectionSystem.isEligible(player) || !isRunning(arena.getId()))) {
            arena = null;
        }

        String currentId = PLAYER_ARENAS.get(player.getUUID());
        String newId = arena != null ? arena.getId() : null;
        if (Objects.equals(currentId, newId)) return;

        if (currentId != null) {
            NoiseDetectionSystem.leaveArenaGroup(player);
            PLAYER_ARENAS.remove(player.getUUID());
        }
        if (arena != null) {
            NoiseDetectionSystem.joinArenaGroup(player, arena.getNoiseGroupId());
            PLAYER_ARENAS.put(player.getUUID(), newId);
        }
    }

    /**
     * La luz solo se toca al cruzar el borde de la arena. Mientras el jugador siga
     * dentro no se comprueba nada: el efecto es infinito y quien lo enciende y lo
     * apaga es el interruptor.
     */
    private static void updateLight(ServerPlayer player, Arena zone) {
        String currentId = PLAYER_LIGHT_ARENAS.get(player.getUUID());
        String newId = zone != null ? zone.getId() : null;
        if (Objects.equals(currentId, newId)) return;

        if (currentId != null) {
            ArenaLight.remove(player);
            PLAYER_LIGHT_ARENAS.remove(player.getUUID());
        }
        if (zone != null) {
            PLAYER_LIGHT_ARENAS.put(player.getUUID(), newId);
            if (ArenaLight.isOn(player.serverLevel(), zone)) {
                ArenaLight.apply(player);
            }
        }
    }

}
