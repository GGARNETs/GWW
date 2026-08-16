package com.github.razorplay01.command;

import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.extra.MinigameManager;
import com.github.razorplay01.instance.InstanceManager;
import com.github.razorplay01.system.NoiseDetectionSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * {@code /escaperoom debug}: enciende y apaga los logs del mod y los contadores.
 * <p>
 * Existe para poder responder en caliente a "el server va lento / está spameando,
 * ¿qué lo está causando?" sin reiniciar ni recompilar. Todo arranca apagado.
 */
public final class GwwDebugCommand {
    private GwwDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var debug = Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .executes(GwwDebugCommand::showStatus);

        // Una rama por categoría: /escaperoom debug noise on
        for (GwwDebug.Category category : GwwDebug.Category.values()) {
            debug.then(Commands.literal(category.name().toLowerCase(Locale.ROOT))
                    .then(Commands.literal("on").executes(ctx -> toggle(ctx, category, true)))
                    .then(Commands.literal("off").executes(ctx -> toggle(ctx, category, false))));
        }

        debug.then(Commands.literal("all")
                .then(Commands.literal("on").executes(ctx -> toggleAll(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> toggleAll(ctx, false))));

        debug.then(Commands.literal("stats")
                .executes(GwwDebugCommand::showStats)
                .then(Commands.literal("on").executes(ctx -> setStats(ctx, true, false)))
                .then(Commands.literal("console").executes(ctx -> setStats(ctx, true, true)))
                .then(Commands.literal("off").executes(ctx -> setStats(ctx, false, false))));

        debug.then(Commands.literal("info").executes(GwwDebugCommand::showInfo));

        dispatcher.register(Commands.literal("escaperoom")
                .requires(source -> source.hasPermission(2))
                .then(debug));
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§6=== Debug de GWW ==="), false);

        for (GwwDebug.Category category : GwwDebug.Category.values()) {
            boolean on = GwwDebug.isOn(category);
            source.sendSuccess(() -> Component.literal(
                    "  §f" + category.name().toLowerCase(Locale.ROOT) + ": "
                            + (on ? "§aencendido" : "§7apagado")), false);
        }
        source.sendSuccess(() -> Component.literal(
                "  §fcontadores: " + (GwwDebug.isStatsEnabled()
                        ? (GwwDebug.isStatsToConsole() ? "§aencendidos §7(volcando a consola)" : "§aencendidos")
                        : "§7apagados")), false);
        source.sendSuccess(() -> Component.literal(
                "§7Uso: /escaperoom debug <categoria|all> <on|off> §8| §7stats §8| §7info"), false);
        return 1;
    }

    /** Qué hay vivo ahora mismo: lo primero que se quiere ver cuando el server va raro. */
    private static int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int players = source.getServer().getPlayerCount();
        int arenas = ArenaManager.getAll().size();
        int running = 0;
        for (String id : ArenaManager.getIds()) {
            if (ArenaManager.isRunning(id)) {
                running++;
            }
        }
        int runningFinal = running;

        source.sendSuccess(() -> Component.literal("§6=== Estado de GWW ==="), false);
        source.sendSuccess(() -> Component.literal("  §fJugadores conectados: §b" + players), false);
        source.sendSuccess(() -> Component.literal(
                "  §fArenas cargadas: §b" + arenas + " §7(en marcha: " + runningFinal + ")"), false);
        source.sendSuccess(() -> Component.literal(
                "  §fGrupos de ruido activos: §b" + NoiseDetectionSystem.getGroupCount()), false);
        source.sendSuccess(() -> Component.literal(
                "  §fPartidas de cañones: §b" + MinigameManager.getRunningIds().size()), false);
        source.sendSuccess(() -> Component.literal(
                "  §fInstances en memoria: §b" + InstanceManager.getNames().size()), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> context, GwwDebug.Category category, boolean on) {
        GwwDebug.set(category, on);
        context.getSource().sendSuccess(() -> Component.literal(
                "§aDebug de §f" + category.name().toLowerCase(Locale.ROOT) + "§a: "
                        + (on ? "§2encendido" : "§capagado")), true);
        return 1;
    }

    private static int toggleAll(CommandContext<CommandSourceStack> context, boolean on) {
        GwwDebug.setAll(on);
        context.getSource().sendSuccess(() -> Component.literal(
                "§aDebug completo " + (on ? "§2encendido" : "§capagado")), true);
        return 1;
    }

    private static int setStats(CommandContext<CommandSourceStack> context, boolean on, boolean console) {
        GwwDebug.setStats(on, console);
        context.getSource().sendSuccess(() -> Component.literal(on
                ? (console
                ? "§aContadores encendidos, volcando a consola cada segundo."
                : "§aContadores encendidos. Míralos con §f/escaperoom debug stats§a.")
                : "§cContadores apagados."), true);
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!GwwDebug.isStatsEnabled()) {
            source.sendSuccess(() -> Component.literal(
                    "§cLos contadores están apagados. Enciéndelos con §f/escaperoom debug stats on"), false);
            return 0;
        }

        List<String> lines = GwwDebug.statLines();
        source.sendSuccess(() -> Component.literal("§6=== Último segundo ==="), false);
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  §7sin actividad"), false);
            return 1;
        }
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal("  §f" + line), false);
        }
        return 1;
    }
}
