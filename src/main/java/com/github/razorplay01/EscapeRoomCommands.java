package com.github.razorplay01;

import com.github.razorplay01.arena.Arena;
import com.github.razorplay01.arena.ArenaLight;
import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.arena.EscapeRoomController;
import com.github.razorplay01.config.GwwPuzzles;
import com.github.razorplay01.config.GwwSettings;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.custom.CableEntity;
import com.github.razorplay01.entity.custom.CajaEntity;
import com.github.razorplay01.entity.custom.CajaHerramientasEntity;
import com.github.razorplay01.entity.custom.UblablaEntity;
import com.github.razorplay01.entity.custom.ValvulaEntity;
import com.github.razorplay01.instance.ChunkPreloader;
import com.github.razorplay01.instance.Instance;
import com.github.razorplay01.instance.InstanceManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comandos del sistema de instances: capturar una sala, guardarla como si fuera
 * una schematic y pegarla donde haga falta (suelta o a través de una arena).
 */
public class EscapeRoomCommands {
    private EscapeRoomCommands() {
        /* This utility class should not be instantiated */
    }

    /** Última captura hecha con /escaperoom capture, a la espera de un /escaperoom save. */
    private static Instance clipboard;

    private static final SuggestionProvider<CommandSourceStack> INSTANCE_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(InstanceManager.getNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> ARENA_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(ArenaManager.getIds(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("escaperoom")
                .requires(source -> source.hasPermission(2))

                // Capturar la sala alrededor del jugador (queda en el portapapeles)
                .then(Commands.literal("capture")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                .executes(EscapeRoomCommands::capture)
                        )
                )

                // Guardar el portapapeles como instance
                .then(Commands.literal("save")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .suggests(INSTANCE_NAMES)
                                .executes(EscapeRoomCommands::save)
                        )
                )

                // Borrar una instance del disco
                .then(Commands.literal("delete")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .suggests(INSTANCE_NAMES)
                                .executes(EscapeRoomCommands::delete)
                        )
                )

                // Pegar en la posición actual del jugador
                .then(Commands.literal("paste")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .suggests(INSTANCE_NAMES)
                                .executes(EscapeRoomCommands::paste)
                        )
                )

                // Pegar en coordenadas específicas
                .then(Commands.literal("pasteat")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .suggests(INSTANCE_NAMES)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(EscapeRoomCommands::pasteAt)
                                )
                        )
                )

                // Listar instances guardadas
                .then(Commands.literal("list")
                        .executes(EscapeRoomCommands::list)
                )

                // Información detallada de una instance
                .then(Commands.literal("info")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .suggests(INSTANCE_NAMES)
                                .executes(EscapeRoomCommands::info)
                        )
                )

                // Definir la zona de meta de una instance (coords donde está capturada)
                .then(Commands.literal("setmeta")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .suggests(INSTANCE_NAMES)
                                .then(Commands.argument("min", BlockPosArgument.blockPos())
                                        .then(Commands.argument("max", BlockPosArgument.blockPos())
                                                .executes(EscapeRoomCommands::setMeta)
                                        )
                                )
                        )
                )

                // Quitar la meta de una instance
                .then(Commands.literal("clearmeta")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .suggests(INSTANCE_NAMES)
                                .executes(EscapeRoomCommands::clearMeta)
                        )
                )

                // Ver la zona de meta ya colocada en una arena (contorno de partículas)
                .then(Commands.literal("showmeta")
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(ARENA_IDS)
                                .executes(EscapeRoomCommands::showMeta)
                        )
                )

                // Limpiar entidades alrededor del jugador
                .then(Commands.literal("clear")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                .executes(EscapeRoomCommands::clear)
                        )
                )

                // Limpiar entidades en coordenadas específicas
                .then(Commands.literal("clearat")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .executes(EscapeRoomCommands::clearAt)
                                )
                        )
                )

                // Arrancar la partida de una arena (barra de ruido + Ublablas), o de todas
                .then(Commands.literal("start")
                        .then(Commands.literal("all")
                                .executes(context -> setRunningAll(context, true))
                        )
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(ARENA_IDS)
                                .executes(context -> setRunning(context, true))
                        )
                )

                // Parar la partida: la arena vuelve a estar inerte
                .then(Commands.literal("stop")
                        .then(Commands.literal("all")
                                .executes(context -> setRunningAll(context, false))
                        )
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(ARENA_IDS)
                                .executes(context -> setRunning(context, false))
                        )
                )

                // Resetear una arena (o todas): repega su instance en el origen configurado
                .then(Commands.literal("reset")
                        .then(Commands.literal("all")
                                .executes(EscapeRoomCommands::resetAll)
                        )
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(ARENA_IDS)
                                .executes(EscapeRoomCommands::reset)
                        )
                )

                // Recargar instances y arenas desde el disco
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx, false))
                        .then(Commands.literal("all")
                                .executes(ctx -> reload(ctx, true))
                        )
                )

                // Gestión de arenas (config/GWW/config.yml)
                .then(Commands.literal("arena")
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("instance", StringArgumentType.word())
                                                .suggests(INSTANCE_NAMES)
                                                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("zonemin", BlockPosArgument.blockPos())
                                                                .then(Commands.argument("zonemax", BlockPosArgument.blockPos())
                                                                        .executes(EscapeRoomCommands::arenaCreate)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("setjail")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(ARENA_IDS)
                                        .then(Commands.argument("jailmin", BlockPosArgument.blockPos())
                                                .then(Commands.argument("jailmax", BlockPosArgument.blockPos())
                                                        .executes(EscapeRoomCommands::arenaSetJail)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(ARENA_IDS)
                                        .executes(EscapeRoomCommands::arenaRemove)
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(EscapeRoomCommands::arenaList)
                        )
                )
        );
    }

    // ==================== AUXILIARES ====================

    /**
     * Devuelve la instance guardada con ese nombre, o null (avisando al ejecutor)
     * si no existe.
     */
    private static Instance requireInstance(String name, CommandSourceStack source) {
        Instance instance = InstanceManager.get(name);
        if (instance == null) {
            source.sendFailure(Component.literal(
                    "§cNo existe la instance '" + name + "'. Guardadas: "
                            + String.join(", ", InstanceManager.getNames())));
        }
        return instance;
    }

    // ==================== INSTANCES ====================

    private static int capture(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        CommandSourceStack source = context.getSource();
        BlockPos origin = BlockPos.containing(source.getPosition());

        // Con poca distancia de visión, el borde de un radio grande puede estar
        // descargado y la captura saldría incompleta sin avisar.
        ChunkPreloader.runWhenLoaded(source.getLevel(), origin, radius, () -> {
            clipboard = InstanceManager.capture(source.getLevel(), origin, radius);
            source.sendSuccess(() -> Component.literal(
                    "§aCapturadas §f" + clipboard.getEntities().size() + "§a entidades alrededor de "
                            + origin.toShortString() + " §7(radio " + radius + ")\n"
                            + "§7Guárdala con /escaperoom save <nombre>"
            ), true);
        }, msg -> source.sendFailure(Component.literal("§c" + msg)));
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        if (clipboard == null) {
            source.sendFailure(Component.literal(
                    "§cNo hay nada capturado. Usa primero /escaperoom capture <radio>."));
            return 0;
        }
        if (!InstanceManager.isValidName(name)) {
            source.sendFailure(Component.literal(
                    "§cNombre inválido. Usa solo letras, números, '_' o '-'."));
            return 0;
        }

        Instance previous = InstanceManager.get(name);
        boolean overwrite = previous != null;
        Instance saved = clipboard;

        // La meta es una propiedad de la instance (la zona de win viaja con ella a cada
        // arena), no de una captura concreta. Si la sala ya tenía meta y esta captura no
        // trae una propia, se conserva para no perderla al re-guardar.
        boolean keptMeta = false;
        if (previous != null && previous.hasMeta() && !saved.hasMeta()) {
            saved.copyMetaFrom(previous);
            keptMeta = true;
        }

        try {
            InstanceManager.save(name, saved);
        } catch (IOException e) {
            source.sendFailure(Component.literal("§cError al guardar la instance: " + e.getMessage()));
            GWW.LOGGER.error("[GWW] Error al guardar la instance '{}'", name, e);
            return 0;
        }

        boolean metaNote = keptMeta;
        source.sendSuccess(() -> Component.literal(
                "§aInstance '" + name + "' " + (overwrite ? "sobrescrita" : "guardada")
                        + " §7(" + saved.getEntities().size() + " entidades) en "
                        + InstanceManager.getDirectory().resolve(name + ".dat")
                        + (metaNote ? "\n§aMeta conservada de la versión anterior." : "")
        ), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        List<String> usedBy = ArenaManager.getAll().stream()
                .filter(arena -> arena.getInstanceName().equals(name))
                .map(Arena::getId)
                .toList();
        if (!usedBy.isEmpty()) {
            source.sendFailure(Component.literal(
                    "§cNo se puede borrar: la usan las arenas " + String.join(", ", usedBy) + "."));
            return 0;
        }

        try {
            if (!InstanceManager.delete(name)) {
                source.sendFailure(Component.literal("§cNo existe la instance '" + name + "'."));
                return 0;
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("§cError al borrar la instance: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§cInstance '" + name + "' borrada."), true);
        return 1;
    }

    private static int paste(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        Instance instance = requireInstance(name, source);
        if (instance == null) {
            return 0;
        }

        BlockPos target = BlockPos.containing(source.getPosition());
        pasteWhenLoaded(source, instance, name, target);
        return 1;
    }

    private static int pasteAt(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        Instance instance = requireInstance(name, source);
        if (instance == null) {
            return 0;
        }

        BlockPos target = BlockPosArgument.getBlockPos(context, "pos");
        pasteWhenLoaded(source, instance, name, target);
        return 1;
    }

    /** Pega la instance cuando sus chunks estén cargados, para no dejar duplicados sin borrar. */
    private static void pasteWhenLoaded(CommandSourceStack source, Instance instance, String name, BlockPos target) {
        ChunkPreloader.runWhenLoaded(source.getLevel(), target, InstanceManager.pasteRadius(instance), () -> {
            InstanceManager.paste(source.getLevel(), instance, target);
            source.sendSuccess(() -> Component.literal(
                    "§aInstance '" + name + "' pegada en " + target.toShortString()), true);
        }, msg -> source.sendFailure(Component.literal("§c" + msg)));
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        CommandSourceStack source = context.getSource();
        BlockPos center = BlockPos.containing(source.getPosition());

        clearWhenLoaded(source, center, radius);
        return 1;
    }

    private static int clearAt(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        CommandSourceStack source = context.getSource();
        BlockPos center = BlockPosArgument.getBlockPos(context, "pos");

        clearWhenLoaded(source, center, radius);
        return 1;
    }

    /** Limpia el área cuando sus chunks estén cargados: si no, las entidades descargadas sobreviven. */
    private static void clearWhenLoaded(CommandSourceStack source, BlockPos center, int radius) {
        ChunkPreloader.runWhenLoaded(source.getLevel(), center, radius, () -> {
            InstanceManager.clear(source.getLevel(), center, radius);
            source.sendSuccess(() -> Component.literal(
                    "§aÁrea limpiada en " + center.toShortString() + " §7(radio " + radius + ")"), true);
        }, msg -> source.sendFailure(Component.literal("§c" + msg)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (clipboard != null) {
            source.sendSuccess(() -> Component.literal(
                    "§7Portapapeles: " + clipboard.getEntities().size()
                            + " entidades sin guardar (/escaperoom save <nombre>)"), false);
        }

        if (InstanceManager.getNames().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eNo hay instances guardadas."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6=== Instances guardadas ==="), false);
        for (String name : InstanceManager.getNames()) {
            Instance instance = InstanceManager.get(name);
            source.sendSuccess(() -> Component.literal(
                    "§e- " + name + " §7(" + instance.getEntities().size()
                            + " entidades, radio " + instance.getRadius() + ")"), false);
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        Instance instance = requireInstance(name, source);
        if (instance == null) {
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6=== Instance: " + name + " ==="), false);
        source.sendSuccess(() -> Component.literal(
                "§eCapturada en: §f" + instance.getOrigin().toShortString()
                        + " §7(radio " + instance.getRadius() + ")"), false);
        source.sendSuccess(() -> Component.literal(
                "§eEntidades: §f" + instance.getEntities().size()), false);
        source.sendSuccess(() -> Component.literal(
                "§eMeta: §f" + (instance.hasMeta()
                        ? instance.getMetaMin().toShortString() + " -> " + instance.getMetaMax().toShortString() + " §7(relativa)"
                        : "§7sin definir")), false);

        Map<String, Integer> byType = new HashMap<>();
        instance.getEntities().forEach(snapshot ->
                byType.merge(snapshot.getEntityType(), 1, Integer::sum));
        byType.forEach((type, count) -> source.sendSuccess(() -> Component.literal(
                "  §7- " + type + ": §f" + count), false));

        return 1;
    }

    private static int setMeta(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        Instance instance = requireInstance(name, source);
        if (instance == null) {
            return 0;
        }

        BlockPos min = BlockPosArgument.getBlockPos(context, "min");
        BlockPos max = BlockPosArgument.getBlockPos(context, "max");

        // La meta se guarda relativa al origen de la ARENA donde caen esas coordenadas,
        // no al de captura: así la caja se reconstruye justo donde la pusiste. Si las
        // coordenadas no caen en ninguna arena que use esta instance, se usa el origen
        // de captura como referencia.
        BlockPos reference = instance.getOrigin();
        for (Arena arena : ArenaManager.getAll()) {
            if (arena.getInstanceName().equals(name)
                    && arena.getZoneAABB().contains(net.minecraft.world.phys.Vec3.atCenterOf(min))) {
                reference = arena.getOrigin();
                break;
            }
        }
        instance.setMeta(reference, min, max);

        try {
            InstanceManager.save(name, instance);
        } catch (IOException e) {
            source.sendFailure(Component.literal("§cError al guardar la instance: " + e.getMessage()));
            return 0;
        }

        BlockPos ref = reference;
        source.sendSuccess(() -> Component.literal(
                "§aMeta de '" + name + "' definida: " + min.toShortString() + " -> " + max.toShortString()
                        + "\n§7Relativa al origen " + ref.toShortString()
                        + ". Compruébala con /escaperoom showmeta <arena>."), true);
        return 1;
    }

    private static int clearMeta(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        Instance instance = requireInstance(name, source);
        if (instance == null) {
            return 0;
        }
        if (!instance.hasMeta()) {
            source.sendFailure(Component.literal("§cLa instance '" + name + "' no tiene meta."));
            return 0;
        }

        instance.clearMeta();
        try {
            InstanceManager.save(name, instance);
        } catch (IOException e) {
            source.sendFailure(Component.literal("§cError al guardar la instance: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§cMeta de '" + name + "' eliminada."), true);
        return 1;
    }

    private static int showMeta(CommandContext<CommandSourceStack> context) {
        String arenaId = StringArgumentType.getString(context, "arena");
        CommandSourceStack source = context.getSource();

        Arena arena = requireArena(arenaId, source);
        if (arena == null) {
            return 0;
        }
        Instance instance = InstanceManager.get(arena.getInstanceName());
        if (instance == null || !instance.hasMeta()) {
            source.sendFailure(Component.literal(
                    "§cLa instance '" + arena.getInstanceName() + "' no tiene meta. "
                            + "Defínela con /escaperoom setmeta " + arena.getInstanceName() + " <min> <max>."));
            return 0;
        }

        net.minecraft.world.phys.AABB box = instance.resolveMetaBox(arena.getOrigin());
        EscapeRoomController.showMetaPreview(source.getLevel(), arenaId, box, 100); // 5 s

        source.sendSuccess(() -> Component.literal(
                "§aMostrando la meta de '" + arenaId + "' durante 5 s: "
                        + String.format("[%.0f, %.0f, %.0f] -> [%.0f, %.0f, %.0f]",
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)), true);
        return 1;
    }

    /** Radio alrededor de quien recarga en el que se reinician los puzzles para reprobarlos. */
    private static final double RELOAD_RESET_RADIUS = 64.0;

    private static int reload(CommandContext<CommandSourceStack> context, boolean allCajas) {
        CommandSourceStack source = context.getSource();

        List<String> errors = InstanceManager.loadAll();
        errors.addAll(ArenaManager.load());
        errors.addAll(GwwSettings.load());
        errors.addAll(GwwPuzzles.load());
        errors.forEach(error -> source.sendFailure(Component.literal("§c" + error)));

        source.sendSuccess(() -> Component.literal(
                "§aRecargado: " + InstanceManager.getNames().size() + " instances, "
                        + ArenaManager.getAll().size() + " arenas, settings.yml y puzzles.yml."), true);

        // Ver los nombres que quedaron cargados evita la duda de si el yml se leyó
        // bien: una caja que no aparece aquí es un error de escritura, no del mod.
        List<String> cajas = GwwPuzzles.cajaNames();
        source.sendSuccess(() -> Component.literal(cajas.isEmpty()
                ? "§7Sin cajas configuradas en puzzles.yml."
                : "§7Cajas en puzzles.yml (" + cajas.size() + "): §f" + String.join("§7, §f", cajas)), false);

        resetPuzzleEntities(source, allCajas);
        return errors.isEmpty() ? 1 : 0;
    }

    /**
     * Deja los puzzles manipulables como recién montados para poder reprobarlos tras
     * editar el yml: cajas cerradas, cables desenchufados y válvulas sin manivela en
     * el estado 0. Por defecto solo los de alrededor: hacerlo en todo el mundo con
     * salas en partida les desharía el avance a los equipos y les dejaría reabrir sus
     * cajas para duplicar items.
     */
    private static void resetPuzzleEntities(CommandSourceStack source, boolean all) {
        ServerLevel level = source.getLevel();
        List<CajaEntity> cajas;
        List<CajaHerramientasEntity> herramientas;
        List<CableEntity> cables;
        List<ValvulaEntity> valvulas;

        if (all) {
            source.sendSuccess(() -> Component.literal(
                    "§eReiniciando los puzzles de TODO el mundo: las salas en partida pierden"
                            + " su avance y pueden reabrir cajas para duplicar items."), true);
            cajas = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(CajaEntity.class), c -> true));
            herramientas = new ArrayList<>(
                    level.getEntities(EntityTypeTest.forClass(CajaHerramientasEntity.class), c -> true));
            cables = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(CableEntity.class), c -> true));
            valvulas = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(ValvulaEntity.class), v -> true));
        } else {
            Entity executor = source.getEntity();
            if (executor == null) {
                source.sendSuccess(() -> Component.literal(
                        "§7Puzzles sin tocar: desde consola no hay posición. Usa §f/escaperoom reload all§7."), false);
                return;
            }
            AABB area = executor.getBoundingBox().inflate(RELOAD_RESET_RADIUS);
            cajas = level.getEntitiesOfClass(CajaEntity.class, area, c -> true);
            herramientas = level.getEntitiesOfClass(CajaHerramientasEntity.class, area, c -> true);
            cables = level.getEntitiesOfClass(CableEntity.class, area, c -> true);
            valvulas = level.getEntitiesOfClass(ValvulaEntity.class, area, v -> true);
        }

        cajas.forEach(CajaEntity::reset);
        herramientas.forEach(CajaHerramientasEntity::reset);
        cables.forEach(cable -> {
            cable.setActive(false);
            cable.setState(0);
        });
        valvulas.forEach(valvula -> {
            valvula.setHasManivela(false);
            valvula.setState(0);
        });

        int totalCajas = cajas.size() + herramientas.size();
        source.sendSuccess(() -> Component.literal("§7Reiniciados: §f" + totalCajas + "§7 caja(s), §f"
                + cables.size() + "§7 cable(s), §f" + valvulas.size() + "§7 valvula(s)"
                + (all ? " en todo el mundo." : " en " + (int) RELOAD_RESET_RADIUS + " bloques.")), false);
        GwwDebug.log(GwwDebug.Category.PUZZLE, "Reload: {} cajas, {} cables y {} valvulas reiniciados ({})",
                totalCajas, cables.size(), valvulas.size(), all ? "todo el mundo" : "radio " + RELOAD_RESET_RADIUS);
    }

    // ==================== ARENAS (config/GWW/config.yml) ====================

    private static Arena requireArena(String id, CommandSourceStack source) {
        Arena arena = ArenaManager.get(id);
        if (arena == null) {
            source.sendFailure(Component.literal(
                    "§cNo existe la arena '" + id + "'. Disponibles: "
                            + String.join(", ", ArenaManager.getIds())));
        }
        return arena;
    }

    /**
     * Arranca o para la partida de una arena. Los Ublablas se congelan y descongelan
     * solos en su propio tick al ver el cambio de estado, así que aquí basta con
     * cambiarlo.
     */
    private static int setRunning(CommandContext<CommandSourceStack> context, boolean running) {
        String arenaId = StringArgumentType.getString(context, "arena");
        CommandSourceStack source = context.getSource();

        Arena arena = requireArena(arenaId, source);
        if (arena == null) {
            return 0;
        }

        if (ArenaManager.isRunning(arenaId) == running) {
            source.sendFailure(Component.literal("§cLa arena '" + arenaId + "' ya está "
                    + (running ? "en marcha" : "parada") + "."));
            return 0;
        }

        ArenaManager.setRunning(arenaId, running);
        if (!running) {
            // Parar la partida apaga la luz: nadie se queda con la visión nocturna puesta.
            ArenaLight.refresh(source.getLevel(), arena, false);
        }

        source.sendSuccess(() -> Component.literal(running
                ? "§a▶ Arena '" + arenaId + "' EN MARCHA. §7Barra de ruido activa y Ublablas sueltos."
                : "§e■ Arena '" + arenaId + "' PARADA. §7Sin barra de ruido, sin luz y Ublablas en su puesto."
        ), true);
        return 1;
    }

    /** Igual que setRunning, pero de golpe para todas las arenas configuradas. */
    private static int setRunningAll(CommandContext<CommandSourceStack> context, boolean running) {
        CommandSourceStack source = context.getSource();

        if (ArenaManager.getAll().isEmpty()) {
            source.sendFailure(Component.literal(
                    "§cNo hay arenas configuradas en config/GWW/config.yml."));
            return 0;
        }

        int changed = 0;
        for (Arena arena : List.copyOf(ArenaManager.getAll())) {
            if (ArenaManager.isRunning(arena.getId()) == running) {
                continue;
            }
            ArenaManager.setRunning(arena.getId(), running);
            if (!running) {
                ArenaLight.refresh(source.getLevel(), arena, false);
            }
            changed++;
        }

        if (changed == 0) {
            source.sendFailure(Component.literal("§cTodas las arenas ya están "
                    + (running ? "en marcha" : "paradas") + "."));
            return 0;
        }

        int total = changed;
        source.sendSuccess(() -> Component.literal(running
                ? "§a▶ " + total + " arena(s) EN MARCHA. §7Barra de ruido activa y Ublablas sueltos."
                : "§e■ " + total + " arena(s) PARADA(s). §7Sin barra de ruido y Ublablas en su puesto."
        ), true);
        return total;
    }

    /**
     * Repega la instance de la arena en su origen. Devuelve false (avisando al
     * ejecutor) si la arena no tiene una instance guardada con la que montarse.
     * <p>
     * Con la sala descargada, el borrado previo del paste no veía las entidades
     * viejas y cada reset dejaba una copia más: por eso todo el trabajo corre
     * cuando el preloader confirma que los chunks están cargados con sus entidades.
     */
    private static boolean resetArena(Arena arena, CommandSourceStack source) {
        Instance instance = requireInstance(arena.getInstanceName(), source);
        if (instance == null) {
            return false;
        }

        ServerLevel level = source.getLevel();
        ChunkPreloader.runWhenLoaded(level, arena.getOrigin(), InstanceManager.pasteRadius(instance), () -> {
            InstanceManager.paste(level, instance, arena.getOrigin());

            // La jaula es propia de la arena, no de la instance: se aplica al pegar.
            if (arena.hasJail()) {
                level.getEntitiesOfClass(UblablaEntity.class, arena.getZoneAABB(), u -> true)
                        .forEach(u -> u.setJailArea(arena.getJailMin(), arena.getJailMax()));
            }

            // El interruptor que se acaba de pegar trae el estado con el que se capturó
            // (normalmente apagado), así que la luz se recalcula desde cero: si no, los
            // jugadores que estuvieran dentro se quedarían con la visión nocturna puesta.
            ArenaLight.refresh(level, arena, ArenaLight.isOn(level, arena));

            source.sendSuccess(() -> Component.literal(
                    "§aArena '" + arena.getId() + "' reseteada: " + instance.getEntities().size()
                            + " entidades restauradas en " + arena.getOrigin().toShortString()), true);
        }, msg -> source.sendFailure(Component.literal("§cArena '" + arena.getId() + "': " + msg)));
        return true;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        String arenaId = StringArgumentType.getString(context, "arena");
        CommandSourceStack source = context.getSource();

        Arena arena = requireArena(arenaId, source);
        if (arena == null) {
            return 0;
        }
        return resetArena(arena, source) ? 1 : 0;
    }

    /**
     * Resetea todas las arenas. Una arena sin instance guardada no aborta el resto:
     * se avisa y se sigue con las demás.
     */
    private static int resetAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (ArenaManager.getAll().isEmpty()) {
            source.sendFailure(Component.literal(
                    "§cNo hay arenas configuradas en config/GWW/config.yml."));
            return 0;
        }

        int done = 0;
        for (Arena arena : List.copyOf(ArenaManager.getAll())) {
            if (resetArena(arena, source)) {
                done++;
            }
        }

        if (done == 0) {
            source.sendFailure(Component.literal("§cNo se pudo resetear ninguna arena."));
            return 0;
        }

        int total = done;
        source.sendSuccess(() -> Component.literal(
                "§a" + total + " arena(s) reseteada(s)."), true);
        return total;
    }

    private static int arenaCreate(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        String instanceName = StringArgumentType.getString(context, "instance");
        CommandSourceStack source = context.getSource();

        if (!id.matches("[A-Za-z0-9_-]+")) {
            source.sendFailure(Component.literal(
                    "§cID inválido. Usa solo letras, números, '_' o '-'."));
            return 0;
        }

        // Refrescar desde disco para no pisar ediciones manuales del yml
        ArenaManager.load();

        if (ArenaManager.get(id) != null) {
            source.sendFailure(Component.literal(
                    "§cLa arena '" + id + "' ya existe. Elimínala primero con /escaperoom arena remove " + id));
            return 0;
        }

        BlockPos origin = BlockPosArgument.getBlockPos(context, "origin");
        BlockPos zoneMin = BlockPosArgument.getBlockPos(context, "zonemin");
        BlockPos zoneMax = BlockPosArgument.getBlockPos(context, "zonemax");

        try {
            ArenaManager.addOrUpdate(new Arena(id, instanceName, origin, zoneMin, zoneMax, null, null));
        } catch (IOException e) {
            source.sendFailure(Component.literal("§cError guardando config.yml: " + e.getMessage()));
            return 0;
        }

        if (InstanceManager.get(instanceName) == null) {
            source.sendSuccess(() -> Component.literal(
                    "§eAviso: la instance '" + instanceName + "' aún no existe. "
                            + "Captúrala y guárdala con /escaperoom save " + instanceName), false);
        }

        source.sendSuccess(() -> Component.literal(
                "§aArena '" + id + "' creada y guardada en config/GWW/config.yml.\n"
                        + "§7Jaula (opcional): /escaperoom arena setjail " + id + " <min> <max>\n"
                        + "§7Montarla: /escaperoom reset " + id
        ), true);
        return 1;
    }

    private static int arenaSetJail(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        CommandSourceStack source = context.getSource();

        ArenaManager.load();
        Arena existing = ArenaManager.get(id);
        if (existing == null) {
            source.sendFailure(Component.literal(
                    "§cNo existe la arena '" + id + "'. Disponibles: "
                            + String.join(", ", ArenaManager.getIds())));
            return 0;
        }

        BlockPos jailMin = BlockPosArgument.getBlockPos(context, "jailmin");
        BlockPos jailMax = BlockPosArgument.getBlockPos(context, "jailmax");

        try {
            ArenaManager.addOrUpdate(new Arena(existing.getId(), existing.getInstanceName(),
                    existing.getOrigin(), existing.getZoneMin(), existing.getZoneMax(),
                    jailMin, jailMax));
        } catch (IOException e) {
            source.sendFailure(Component.literal("§cError guardando config.yml: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "§aJaula de la arena '" + id + "' definida: " + jailMin.toShortString()
                        + " -> " + jailMax.toShortString()
                        + ". Se aplicará a los Ublablas en el próximo /escaperoom reset " + id
        ), true);
        return 1;
    }

    private static int arenaRemove(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        CommandSourceStack source = context.getSource();

        ArenaManager.load();
        try {
            if (!ArenaManager.remove(id)) {
                source.sendFailure(Component.literal("§cNo existe la arena '" + id + "'."));
                return 0;
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("§cError guardando config.yml: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "§cArena '" + id + "' eliminada de config/GWW/config.yml."), true);
        return 1;
    }

    private static int arenaList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (ArenaManager.getAll().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§eNo hay arenas configuradas en config/GWW/config.yml."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6=== Arenas configuradas ==="), false);
        for (Arena arena : ArenaManager.getAll()) {
            String status = ArenaManager.isRunning(arena.getId()) ? "§a▶ " : "§8■ ";
            source.sendSuccess(() -> Component.literal(
                    status + "§e" + arena.getId()
                            + " §7(instance: " + arena.getInstanceName()
                            + ", origen: " + arena.getOrigin().toShortString()
                            + ", zona: " + arena.getZoneMin().toShortString()
                            + " -> " + arena.getZoneMax().toShortString()
                            + (arena.hasJail() ? ", con jaula" : "") + ")"
            ), false);
        }
        return 1;
    }
}
