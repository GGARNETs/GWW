package com.github.razorplay01.extra;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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

    /** Daño por balazo si no se indica en el comando: 2 corazones. */
    private static final float DEFAULT_BULLET_DAMAGE = 4.0f;

    private static final SuggestionProvider<CommandSourceStack> ARENA_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(CannonArenaManager.getIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> ARENA_IDS_OR_ALL =
            (ctx, builder) -> {
                List<String> options = new ArrayList<>();
                options.add(ALL);
                options.addAll(CannonArenaManager.getIds());
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
                                                        .executes(ctx -> start(ctx, DEFAULT_BULLET_DAMAGE))
                                                        .then(Commands.argument("danio", FloatArgumentType.floatArg(0.0f, 100.0f))
                                                                .executes(ctx -> start(ctx, FloatArgumentType.getFloat(ctx, "danio")))
                                                        )
                                                )
                                        )
                                )
                        )
                )

                .then(Commands.literal("stop")
                        .then(Commands.argument("arena", StringArgumentType.word())
                                .suggests(ARENA_IDS_OR_ALL)
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

    private static int start(CommandContext<CommandSourceStack> context, float bulletDamage) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "arena");
        int seconds = IntegerArgumentType.getInteger(context, "tiempo_segundos");
        int shots = IntegerArgumentType.getInteger(context, "dificultad");
        double bulletSpeed = DoubleArgumentType.getDouble(context, "bulletSpeed");

        List<CannonArena> targets = resolve(source, target);
        if (targets.isEmpty()) return 0;

        for (CannonArena arena : targets) {
            MinigameManager.start(source.getLevel(), arena, seconds, shots, bulletSpeed, bulletDamage);
        }

        int count = targets.size();
        source.sendSuccess(() -> Component.literal(String.format(
                "✅ %d partida(s) iniciada(s): %d segundos, %d disparos, velocidad %.2f, daño %.1f",
                count, seconds, shots, bulletSpeed, bulletDamage)), true);
        return count;
    }

    /**
     * Para la(s) partida(s) y además barre cañones y balas huérfanos de la zona,
     * haya partida o no: tras un cierre en seco no queda ninguna activa pero los
     * restos siguen ahí, y antes este comando no los tocaba.
     */
    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "arena");

        List<CannonArena> targets = resolve(source, target);
        if (targets.isEmpty()) return 0;

        int stopped = 0;
        int orphans = 0;
        for (CannonArena arena : targets) {
            if (MinigameManager.stop(arena.getId())) stopped++;
            orphans += MinigameManager.clearOrphans(source.getLevel(), arena);
        }

        if (stopped == 0 && orphans == 0) {
            source.sendSuccess(() -> Component.literal(
                    "No había partidas en marcha ni restos que limpiar."), false);
            return 0;
        }

        int stoppedFinal = stopped;
        int orphansFinal = orphans;
        source.sendSuccess(() -> Component.literal(String.format(
                "🛑 %d partida(s) detenida(s), %d cañón(es)/bala(s) huérfanos limpiados",
                stoppedFinal, orphansFinal)), true);
        return stopped + orphans;
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
