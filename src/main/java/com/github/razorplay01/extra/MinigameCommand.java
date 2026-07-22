package com.github.razorplay01.extra;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /cc — minijuego de cañones. Las arenas (centro y radio) se guardan en
 * cannon.yml; el tiempo, la dificultad y la velocidad de bala se eligen al
 * arrancar, para poder repetir la misma arena con distintos ajustes.
 */
public class MinigameCommand {
    private MinigameCommand() {
        /* This utility class should not be instantiated */
    }

    private static final String ALL = "all";

    private static final SuggestionProvider<CommandSourceStack> ARENA_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(CannonArenaManager.getIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> ARENA_IDS_OR_ALL =
            (ctx, builder) -> {
                List<String> options = new ArrayList<>();
                options.add(ALL);
                options.addAll(CannonArenaManager.getIds());
                return SharedSuggestionProvider.suggest(options, builder);
            };

    private static final SuggestionProvider<CommandSourceStack> RUNNING_IDS_OR_ALL =
            (ctx, builder) -> {
                List<String> options = new ArrayList<>();
                options.add(ALL);
                options.addAll(MinigameManager.getRunningIds());
                return SharedSuggestionProvider.suggest(options, builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cc")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("arena")
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("centro", Vec3Argument.vec3())
                                                .then(Commands.argument("radio", DoubleArgumentType.doubleArg(
                                                                CannonArenaManager.MIN_RADIUS, CannonArenaManager.MAX_RADIUS))
                                                        .executes(MinigameCommand::createArena)
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(ARENA_IDS)
                                        .executes(MinigameCommand::removeArena)
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(MinigameCommand::listArenas)
                        )
                        .then(Commands.literal("reload")
                                .executes(MinigameCommand::reload)
                        )
                )

                .then(Commands.literal("start")
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(ARENA_IDS_OR_ALL)
                                .then(Commands.argument("tiempo_segundos", IntegerArgumentType.integer(1, 3600))
                                        .then(Commands.argument("dificultad", IntegerArgumentType.integer(1, 1000))
                                                .then(Commands.argument("bulletSpeed", DoubleArgumentType.doubleArg(0.05, 5.0))
                                                        .executes(MinigameCommand::start)
                                                )
                                        )
                                )
                        )
                )

                .then(Commands.literal("stop")
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(RUNNING_IDS_OR_ALL)
                                .executes(MinigameCommand::stop)
                        )
                )
        );
    }

    // ==================== ARENAS ====================

    private static int createArena(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        Vec3 center = Vec3Argument.getVec3(context, "centro");
        double radius = DoubleArgumentType.getDouble(context, "radio");

        boolean replaced = CannonArenaManager.get(id) != null;
        if (replaced && MinigameManager.isRunning(id)) {
            MinigameManager.stop(id);
        }

        try {
            CannonArenaManager.addOrUpdate(new CannonArena(id, center, radius));
        } catch (IOException e) {
            source.sendFailure(Component.literal("❌ No se pudo guardar cannon.yml: " + e.getMessage()));
            return 0;
        }

        String verb = replaced ? "actualizada" : "creada";
        source.sendSuccess(() -> Component.literal(String.format(
                "✅ Arena '%s' %s en (%.1f, %.1f, %.1f) con radio %.1f",
                id, verb, center.x, center.y, center.z, radius)), false);
        return 1;
    }

    private static int removeArena(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        MinigameManager.stop(id);
        try {
            if (!CannonArenaManager.remove(id)) {
                source.sendFailure(Component.literal("❌ No existe la arena '" + id + "'"));
                return 0;
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("❌ No se pudo guardar cannon.yml: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("🗑️ Arena '" + id + "' eliminada"), false);
        return 1;
    }

    private static int listArenas(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<CannonArena> arenas = CannonArenaManager.getAll();

        if (arenas.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No hay arenas de cañones. Créalas con /cc arena create"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6Arenas de cañones (" + arenas.size() + "):"), false);
        for (CannonArena arena : arenas) {
            Vec3 center = arena.getCenter();
            String estado = MinigameManager.isRunning(arena.getId()) ? "§aEN MARCHA" : "§7parada";
            source.sendSuccess(() -> Component.literal(String.format(
                    "  §f%s §7- centro (%.1f, %.1f, %.1f), radio %.1f - %s",
                    arena.getId(), center.x, center.y, center.z, arena.getRadius(), estado)), false);
        }
        return arenas.size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        // Las arenas cargadas se sustituyen por objetos nuevos: las partidas en
        // marcha quedarían apuntando a la arena vieja, así que se paran.
        int stopped = MinigameManager.stopAll();
        List<String> errors = CannonArenaManager.load();

        if (stopped > 0) {
            source.sendSuccess(() -> Component.literal("🛑 " + stopped + " partida(s) detenida(s) por la recarga"), false);
        }
        for (String error : errors) {
            source.sendFailure(Component.literal("§c" + error));
        }
        source.sendSuccess(() -> Component.literal(
                "🔄 Recargadas " + CannonArenaManager.getAll().size() + " arenas de cañones"), false);
        return errors.isEmpty() ? 1 : 0;
    }

    // ==================== PARTIDAS ====================

    private static int start(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "arena");
        int seconds = IntegerArgumentType.getInteger(context, "tiempo_segundos");
        int shots = IntegerArgumentType.getInteger(context, "dificultad");
        double bulletSpeed = DoubleArgumentType.getDouble(context, "bulletSpeed");

        List<CannonArena> targets = resolve(source, target);
        if (targets.isEmpty()) return 0;

        for (CannonArena arena : targets) {
            MinigameManager.start(source.getLevel(), arena, seconds, shots, bulletSpeed);
        }

        int count = targets.size();
        source.sendSuccess(() -> Component.literal(String.format(
                "✅ %d partida(s) iniciada(s): %d segundos, %d disparos, velocidad %.2f",
                count, seconds, shots, bulletSpeed)), true);
        return count;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "arena");

        if (ALL.equalsIgnoreCase(target)) {
            int stopped = MinigameManager.stopAll();
            if (stopped == 0) {
                source.sendFailure(Component.literal("❌ No hay ninguna partida en marcha"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("🛑 " + stopped + " partida(s) detenida(s)"), true);
            return stopped;
        }

        if (!MinigameManager.stop(target)) {
            source.sendFailure(Component.literal("❌ La arena '" + target + "' no tiene ninguna partida en marcha"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("🛑 Partida de '" + target + "' detenida"), true);
        return 1;
    }

    /**
     * Traduce el argumento a la lista de arenas afectadas: 'all' son todas las
     * cargadas, cualquier otra cosa es un id concreto.
     */
    private static List<CannonArena> resolve(CommandSourceStack source, String target) {
        if (ALL.equalsIgnoreCase(target)) {
            List<CannonArena> all = new ArrayList<>(CannonArenaManager.getAll());
            if (all.isEmpty()) {
                source.sendFailure(Component.literal("❌ No hay arenas creadas. Usa /cc arena create"));
            }
            return all;
        }

        CannonArena arena = CannonArenaManager.get(target);
        if (arena == null) {
            source.sendFailure(Component.literal("❌ No existe la arena '" + target + "'"));
            return List.of();
        }
        return List.of(arena);
    }
}
