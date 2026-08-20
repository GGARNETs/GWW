package com.github.razorplay01.command;

import com.github.razorplay01.config.GwwPuzzles;
import com.github.razorplay01.entity.custom.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class EscapeRoomConfigCommand {

    private static final String NOT_PANEL_FUSIBLES = "§cLa entidad seleccionada no es un Panel de Fusibles.";
    private static final String NOT_PANEL_CODIGO = "§cLa entidad seleccionada no es un Panel de Código.";
    private static final String NOT_TECLADO = "§cLa entidad seleccionada no es un Teclado Numérico.";
    private static final String NOT_PUERTA_METALICA = "§cLa segunda entidad debe ser una Puerta Metálica.";
    private static final String NOT_TORTUGA = "§cLa segunda entidad debe ser una Luz Tortuga.";
    private static final String NOT_INTERRUPTOR = "§cLa entidad seleccionada no es un Interruptor Industrial.";
    private static final String NOT_CABLE = "§cLa segunda entidad debe ser un Cable.";
    private static final String NOT_REJA = "§cLa entidad seleccionada no es una Reja de Ducto.";
    private static final String NOT_PANEL_ENERGIA = "§cLa segunda entidad debe ser un Panel de Energía.";
    private static final String NOT_VALVULA = "§cLa entidad no es una válvula.";
    private static final String NOT_CUADRO = "§cLa entidad no es un cuadro.";
    private static final String NOT_PALANCA = "§cLa entidad no es una palanca.";
    private static final String NOT_UBLABLA = "§cLa entidad debe ser un Ublabla.";
    private static final String SUCCESS_LINK = "§a✓ Vinculado correctamente.";
    private static final String SUCCESS_UNLINK_ALL = "§aTodas las vinculaciones han sido eliminadas.";

    private static final SuggestionProvider<CommandSourceStack> FUSE_COLORS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"rojo", "verde", "azul", "amarillo", "violeta"}, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("escaperoom")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("config")
                        // ========== PANEL_FUSIBLES ==========
                        .then(Commands.literal("panel_fusible")
                                .then(Commands.argument("panel", EntityArgument.entity())
                                        .then(Commands.literal("link")
                                                .then(Commands.argument("puerta", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkDoorToPanel)
                                                )
                                                .then(Commands.argument("turtle", EntityArgument.entity())
                                                        .then(Commands.argument("puzzleId", IntegerArgumentType.integer(1, 2))
                                                                .executes(EscapeRoomConfigCommand::linkTurtleToPanel)
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("unlink")
                                                .executes(EscapeRoomConfigCommand::unlinkAllFromPanel) // unifica puertas y tortugas
                                        )
                                        .then(Commands.literal("list")
                                                .executes(EscapeRoomConfigCommand::listPanelLinks)
                                        )
                                        .then(Commands.literal("setsolution")
                                                .then(Commands.argument("puzzleId", IntegerArgumentType.integer(1, 2))
                                                        .then(Commands.argument("color1", StringArgumentType.word()).suggests(FUSE_COLORS)
                                                                .then(Commands.argument("color2", StringArgumentType.word()).suggests(FUSE_COLORS)
                                                                        .then(Commands.argument("color3", StringArgumentType.word()).suggests(FUSE_COLORS)
                                                                                .then(Commands.argument("color4", StringArgumentType.word()).suggests(FUSE_COLORS)
                                                                                        .executes(EscapeRoomConfigCommand::setPanelSolution)
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        // ========== CUADRO ==========
                        .then(Commands.literal("cuadro")
                                .then(Commands.literal("setinitial")
                                        .then(Commands.argument("cuadro", EntityArgument.entity())
                                                .executes(EscapeRoomConfigCommand::setInitialFromCurrent)
                                        )
                                )
                                .then(Commands.literal("setinitialpos")
                                        .then(Commands.argument("cuadro", EntityArgument.entity())
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(EscapeRoomConfigCommand::setInitialPosition)
                                                )
                                        )
                                )
                                .then(Commands.literal("setinitialrot")
                                        .then(Commands.argument("cuadro", EntityArgument.entity())
                                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                                                                .executes(EscapeRoomConfigCommand::setInitialRotation)
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("cuadro", EntityArgument.entity())
                                                .executes(EscapeRoomConfigCommand::resetPuzzle)
                                        )
                                )
                                .then(Commands.literal("teleportinitial")
                                        .then(Commands.argument("cuadro", EntityArgument.entity())
                                                .executes(EscapeRoomConfigCommand::teleportToInitial)
                                        )
                                )
                                .then(Commands.literal("check")
                                        .then(Commands.argument("cuadro", EntityArgument.entity())
                                                .executes(EscapeRoomConfigCommand::checkPlacement)
                                        )
                                )
                        )
                        // ========== INTERRUPTOR ==========
                        .then(Commands.literal("interruptor")
                                .then(Commands.argument("interruptor", EntityArgument.entity())
                                        .then(Commands.literal("link")
                                                .then(Commands.argument("cable", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkCableToInterruptor)
                                                )
                                        )
                                        .then(Commands.literal("unlink")
                                                .executes(EscapeRoomConfigCommand::unlinkAllFromInterruptor)
                                        )
                                        .then(Commands.literal("link_ublabla")
                                                .then(Commands.argument("ublabla", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkUblablaToInterruptor)
                                                )
                                        )
                                        .then(Commands.literal("unlink_ublabla")
                                                .executes(EscapeRoomConfigCommand::unlinkUblablaFromInterruptor)
                                        )
                                        .then(Commands.literal("link_panel")
                                                .then(Commands.argument("panel", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkPanelToInterruptor)
                                                )
                                        )
                                        .then(Commands.literal("unlink_panel")
                                                .executes(EscapeRoomConfigCommand::unlinkPanelFromInterruptor)
                                        )
                                        .then(Commands.literal("link_teclado")
                                                .then(Commands.argument("teclado", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkTecladoToInterruptor)
                                                )
                                        )
                                        .then(Commands.literal("unlink_teclado")
                                                .executes(EscapeRoomConfigCommand::unlinkTecladoFromInterruptor)
                                        )
                                        .then(Commands.literal("list")
                                                .executes(EscapeRoomConfigCommand::listInterruptorLinks)
                                        )
                                )
                        )

                        // ========== REJA ==========
                        .then(Commands.literal("reja")
                                .then(Commands.argument("reja", EntityArgument.entity())
                                        .then(Commands.literal("link")
                                                .then(Commands.argument("panel", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkPanelToReja)
                                                )
                                        )
                                        .then(Commands.literal("unlink")
                                                .executes(EscapeRoomConfigCommand::unlinkAllFromReja)
                                        )
                                        .then(Commands.literal("list")
                                                .executes(EscapeRoomConfigCommand::listRejaLinks)
                                        )
                                )
                        )
                        // ========== VALVULA ==========
                        .then(Commands.literal("valvula")
                                .then(Commands.argument("valvula", EntityArgument.entity())
                                        .then(Commands.literal("addparticle")
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("dirX", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("dirY", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("dirZ", DoubleArgumentType.doubleArg())
                                                                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0))
                                                                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1))
                                                                                                .then(Commands.argument("pushForce", DoubleArgumentType.doubleArg(0))
                                                                                                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 100))
                                                                                                                .executes(EscapeRoomConfigCommand::addParticleEmitter)
                                                                                                        )
                                                                                                        .executes(ctx -> addParticleEmitterWithDefaults(ctx, 5))
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("listparticles")
                                                .executes(EscapeRoomConfigCommand::listParticleEmitters)
                                        )
                                        .then(Commands.literal("removeparticle")
                                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                        .executes(EscapeRoomConfigCommand::removeParticleEmitter)
                                                )
                                        )
                                        .then(Commands.literal("clearparticles")
                                                .executes(EscapeRoomConfigCommand::clearParticleEmitters)
                                        )
                                        // Estado en el que la presión se estabiliza (0 = cerrada, 4 = abierta)
                                        .then(Commands.literal("setcorrect")
                                                .then(Commands.argument("estado", IntegerArgumentType.integer(
                                                                ValvulaEntity.MIN_STATE, ValvulaEntity.MAX_STATE))
                                                        .executes(EscapeRoomConfigCommand::setValvulaCorrectState)
                                                )
                                        )
                                        .then(Commands.literal("info")
                                                .executes(EscapeRoomConfigCommand::valvulaInfo)
                                        )
                                )
                        )
                        // ========== PANEL_CODIGO ==========
                        .then(Commands.literal("panel_codigo")
                                .then(Commands.argument("panel", EntityArgument.entity())
                                        .then(Commands.literal("link")
                                                .then(Commands.argument("puerta", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkDoorToCodigoPanel)
                                                )
                                        )
                                        .then(Commands.literal("unlink")
                                                .executes(EscapeRoomConfigCommand::unlinkAllDoorsFromCodigoPanel)
                                        )
                                        .then(Commands.literal("list")
                                                .executes(EscapeRoomConfigCommand::listCodigoPanelDoors)
                                        )
                                )
                        )
                        // ========== CABLES / VALVULAS (orden del yml) ==========
                        .then(Commands.literal("cables")
                                .then(Commands.literal("list")
                                        .executes(ctx -> listRoomCables(ctx, 30))
                                        .then(Commands.argument("radio", IntegerArgumentType.integer(5, 100))
                                                .executes(ctx -> listRoomCables(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "radio")))
                                        )
                                )
                        )
                        .then(Commands.literal("valvulas")
                                .then(Commands.literal("list")
                                        .executes(ctx -> listRoomValvulas(ctx, 30))
                                        .then(Commands.argument("radio", IntegerArgumentType.integer(5, 100))
                                                .executes(ctx -> listRoomValvulas(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "radio")))
                                        )
                                )
                        )
                        // ========== CAJA ==========
                        .then(Commands.literal("caja")
                                .then(Commands.argument("caja", EntityArgument.entity())
                                        .then(Commands.literal("setname")
                                                .then(Commands.argument("nombre", StringArgumentType.word())
                                                        .executes(EscapeRoomConfigCommand::setCajaName)
                                                )
                                        )
                                )
                        )
                        // ========== TECLADO ==========
                        .then(Commands.literal("teclado")
                                .then(Commands.argument("teclado", EntityArgument.entity())
                                        .then(Commands.literal("setname")
                                                .then(Commands.argument("nombre", StringArgumentType.word())
                                                        .executes(EscapeRoomConfigCommand::setTecladoName)
                                                )
                                        )
                                        .then(Commands.literal("link")
                                                .then(Commands.argument("puerta", EntityArgument.entity())
                                                        .executes(EscapeRoomConfigCommand::linkDoorToTeclado)
                                                )
                                        )
                                        .then(Commands.literal("unlink")
                                                .executes(EscapeRoomConfigCommand::unlinkAllDoorsFromTeclado)
                                        )
                                        .then(Commands.literal("info")
                                                .executes(EscapeRoomConfigCommand::tecladoInfo)
                                        )
                                        .then(Commands.literal("reset")
                                                .executes(EscapeRoomConfigCommand::resetTeclado)
                                        )
                                )
                        )
                        // ========== PALANCA ==========
                        .then(Commands.literal("palanca")
                                .then(Commands.argument("palanca", EntityArgument.entity())
                                        .then(Commands.literal("setinitial")
                                                .executes(EscapeRoomConfigCommand::setInitialPalancaFromCurrent)
                                        )
                                        .then(Commands.literal("setinitialpos")
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(EscapeRoomConfigCommand::setInitialPalancaPosition)
                                                )
                                        )
                                        .then(Commands.literal("reset")
                                                .executes(EscapeRoomConfigCommand::resetPalancaPuzzle)
                                        )
                                        .then(Commands.literal("teleportinitial")
                                                .executes(EscapeRoomConfigCommand::teleportPalancaToInitial)
                                        )
                                        .then(Commands.literal("check")
                                                .executes(EscapeRoomConfigCommand::checkPalancaPlacement)
                                        )
                                )
                        )
                        // ========== UBLABLA ==========
                        .then(Commands.literal("ublabla")
                                .then(Commands.argument("entity", EntityArgument.entity())
                                        .then(Commands.literal("link")
                                                .then(Commands.literal("door")
                                                        .then(Commands.argument("door", EntityArgument.entity())
                                                                .executes(EscapeRoomConfigCommand::linkDoorToUblabla)
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("unlink")
                                                .then(Commands.literal("door")
                                                        .then(Commands.argument("door", EntityArgument.entity())
                                                                .executes(EscapeRoomConfigCommand::unlinkDoorFromUblabla)
                                                        )
                                                )
                                                .then(Commands.literal("doors")
                                                        .executes(EscapeRoomConfigCommand::unlinkAllDoorsFromUblabla)
                                                )
                                        )
                                        .then(Commands.literal("list")
                                                .then(Commands.literal("doors")
                                                        .executes(EscapeRoomConfigCommand::listUblablaDoors)
                                                )
                                        )
                                        .then(Commands.literal("setinvestigation")
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(EscapeRoomConfigCommand::setInvestigationPos)
                                                )
                                        )
                                        .then(Commands.literal("setjail")
                                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                                .executes(EscapeRoomConfigCommand::setJailArea)
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("clearjail")
                                                .executes(EscapeRoomConfigCommand::clearJailArea)
                                        )
                                        .then(Commands.literal("getinfo")
                                                .executes(EscapeRoomConfigCommand::getUblablaInfo)
                                        )
                                        .then(Commands.literal("setpatrolcenter")
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(EscapeRoomConfigCommand::setPatrolCenter)
                                                )
                                        )
                                        .then(Commands.literal("setpatrolradius")
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(8.0))
                                                        .executes(EscapeRoomConfigCommand::setPatrolRadius)
                                                )
                                        )
                                        .then(Commands.literal("setspawn")
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(EscapeRoomConfigCommand::setSpawnPos)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    // ==================== HELPERS ====================

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T getEntityOfType(CommandContext<CommandSourceStack> context,
                                                        String argName, Class<T> clazz, String errorMsg) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(context, argName);
        if (!clazz.isInstance(e)) {
            context.getSource().sendFailure(Component.literal(errorMsg));
            return null;
        }
        return (T) e;
    }

    private static void sendSuccess(CommandContext<CommandSourceStack> context, String msg) {
        context.getSource().sendSuccess(() -> Component.literal(msg), true);
    }

    private static void sendInfo(CommandContext<CommandSourceStack> context, String msg) {
        context.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static void sendFailure(CommandContext<CommandSourceStack> context, String msg) {
        context.getSource().sendFailure(Component.literal(msg));
    }

    // ==================== PANEL_FUSIBLES ====================

    private static int linkTurtleToPanel(CommandContext<CommandSourceStack> context) {
        try {
            PanelFusiblesEntity panel = getEntityOfType(context, "panel", PanelFusiblesEntity.class, NOT_PANEL_FUSIBLES);
            if (panel == null) return 0;
            LuzTortugaEntity turtle = getEntityOfType(context, "turtle", LuzTortugaEntity.class, NOT_TORTUGA);
            if (turtle == null) return 0;
            int puzzleId = IntegerArgumentType.getInteger(context, "puzzleId");
            panel.linkTurtle(puzzleId, turtle, panel.position());
            sendSuccess(context, "§a✓ Tortuga vinculada correctamente al Puzzle " + puzzleId + " del panel.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkAllFromPanel(CommandContext<CommandSourceStack> context) {
        try {
            PanelFusiblesEntity panel = getEntityOfType(context, "panel", PanelFusiblesEntity.class, NOT_PANEL_FUSIBLES);
            if (panel == null) return 0;
            panel.unlinkAllTurtles();
            panel.unlinkAllDoors();
            sendSuccess(context, SUCCESS_UNLINK_ALL);
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int listPanelLinks(CommandContext<CommandSourceStack> context) {
        try {
            PanelFusiblesEntity panel = getEntityOfType(context, "panel", PanelFusiblesEntity.class, NOT_PANEL_FUSIBLES);
            if (panel == null) return 0;
            sendInfo(context, "§6=== Vinculaciones del Panel ===");
            sendInfo(context, "§ePuertas: §f" + panel.getLinkedDoorsCount());
            sendInfo(context, "§ePuzzle 1 → " + panel.getLinkedCount(1) + " tortugas");
            sendInfo(context, "§ePuzzle 2 → " + panel.getLinkedCount(2) + " tortugas");
            sendInfo(context, "§eSolución Puzzle 1: §f" + formatSolution(panel.getSolution(1)));
            sendInfo(context, "§eSolución Puzzle 2: §f" + formatSolution(panel.getSolution(2)));
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int linkDoorToPanel(CommandContext<CommandSourceStack> context) {
        try {
            PanelFusiblesEntity panel = getEntityOfType(context, "panel", PanelFusiblesEntity.class, NOT_PANEL_FUSIBLES);
            if (panel == null) return 0;
            PuertaMetalicaEntity door = getEntityOfType(context, "puerta", PuertaMetalicaEntity.class, NOT_PUERTA_METALICA);
            if (door == null) return 0;
            panel.linkDoor(door, panel.position());
            sendSuccess(context, "§a✓ Puerta metálica vinculada correctamente.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int setPanelSolution(CommandContext<CommandSourceStack> context) {
        try {
            PanelFusiblesEntity panel = getEntityOfType(context, "panel", PanelFusiblesEntity.class, NOT_PANEL_FUSIBLES);
            if (panel == null) return 0;
            int puzzleId = IntegerArgumentType.getInteger(context, "puzzleId");

            int[] solution = new int[4];
            String[] argNames = {"color1", "color2", "color3", "color4"};
            for (int i = 0; i < 4; i++) {
                String color = StringArgumentType.getString(context, argNames[i]);
                int fuse = parseFuseColor(color);
                if (fuse == PanelFusiblesEntity.FUSE_NONE) {
                    sendFailure(context, "§cColor inválido: '" + color
                            + "'. Usa rojo, verde, azul, amarillo o violeta.");
                    return 0;
                }
                solution[i] = fuse;
            }

            panel.setSolution(puzzleId, solution);
            sendSuccess(context, "§a✓ Solución de respaldo del circuito " + puzzleId + ": "
                    + formatSolution(solution));
            if (com.github.razorplay01.config.GwwPuzzles.fusiblesCircuito(puzzleId) != null) {
                sendInfo(context, "§eOjo: puzzles.yml define este circuito, así que el yml manda. "
                        + "Este valor solo se usa si borras esa sección.");
            }
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int parseFuseColor(String color) {
        return switch (color.toLowerCase(java.util.Locale.ROOT)) {
            case "rojo" -> PanelFusiblesEntity.FUSE_ROJO;
            case "verde" -> PanelFusiblesEntity.FUSE_VERDE;
            case "azul" -> PanelFusiblesEntity.FUSE_AZUL;
            case "amarillo" -> PanelFusiblesEntity.FUSE_AMARILLO;
            case "violeta" -> PanelFusiblesEntity.FUSE_VIOLETA;
            default -> PanelFusiblesEntity.FUSE_NONE;
        };
    }

    private static String formatSolution(int[] solution) {
        StringBuilder sb = new StringBuilder();
        for (int fuse : solution) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(PanelFusiblesEntity.getColorName(fuse));
        }
        return sb.toString();
    }

    // ==================== CUADRO ====================

    private static int setInitialFromCurrent(CommandContext<CommandSourceStack> context) {
        try {
            BaseCuadroEntity cuadro = getEntityOfType(context, "cuadro", BaseCuadroEntity.class, NOT_CUADRO);
            if (cuadro == null) return 0;
            cuadro.setInitialPosition((float) cuadro.getX(), (float) cuadro.getY(), (float) cuadro.getZ());
            cuadro.setInitialRotation(cuadro.getYRot(), cuadro.getXRot());
            sendSuccess(context, String.format("§aPosición inicial: §f%.2f, %.2f, %.2f", cuadro.getX(), cuadro.getY(), cuadro.getZ()));
            sendSuccess(context, String.format("§aRotación inicial: §fYaw: %.1f°, Pitch: %.1f°", cuadro.getYRot(), cuadro.getXRot()));
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int setInitialPosition(CommandContext<CommandSourceStack> context) {
        try {
            BaseCuadroEntity cuadro = getEntityOfType(context, "cuadro", BaseCuadroEntity.class, NOT_CUADRO);
            if (cuadro == null) return 0;
            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
            cuadro.setInitialPosition(pos.getX(), pos.getY(), pos.getZ());
            sendSuccess(context, String.format("§aPosición inicial establecida en: §f%d, %d, %d", pos.getX(), pos.getY(), pos.getZ()));
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int setInitialRotation(CommandContext<CommandSourceStack> context) {
        try {
            BaseCuadroEntity cuadro = getEntityOfType(context, "cuadro", BaseCuadroEntity.class, NOT_CUADRO);
            if (cuadro == null) return 0;
            float yaw = FloatArgumentType.getFloat(context, "yaw");
            float pitch = FloatArgumentType.getFloat(context, "pitch");
            cuadro.setInitialRotation(yaw, pitch);
            sendSuccess(context, String.format("§aRotación inicial: §fYaw: %.1f°, Pitch: %.1f°", yaw, pitch));
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int resetPuzzle(CommandContext<CommandSourceStack> context) {
        try {
            BaseCuadroEntity cuadro = getEntityOfType(context, "cuadro", BaseCuadroEntity.class, NOT_CUADRO);
            if (cuadro == null) return 0;
            cuadro.resetPuzzleState();
            sendSuccess(context, "§aEstado del puzzle reseteado.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int teleportToInitial(CommandContext<CommandSourceStack> context) {
        try {
            BaseCuadroEntity cuadro = getEntityOfType(context, "cuadro", BaseCuadroEntity.class, NOT_CUADRO);
            if (cuadro == null) return 0;
            cuadro.setPos(cuadro.getInitialPosition());
            cuadro.setYRot(cuadro.getInitialYaw());
            cuadro.setXRot(cuadro.getInitialPitch());
            sendSuccess(context, "§aCuadro teletransportado a su posición inicial.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int checkPlacement(CommandContext<CommandSourceStack> context) {
        try {
            BaseCuadroEntity cuadro = getEntityOfType(context, "cuadro", BaseCuadroEntity.class, NOT_CUADRO);
            if (cuadro == null) return 0;
            boolean isCorrect = cuadro.checkIfCorrectlyPlaced();
            double distance = cuadro.getDistanceToInitialPosition();
            float yawDiff = Math.abs(cuadro.getYawDifferenceFromInitial());
            if (isCorrect) {
                sendInfo(context, "§a✓ El cuadro está correctamente colocado.");
            } else {
                sendFailure(context, "§c✗ El cuadro NO está correctamente colocado.");
                sendFailure(context, String.format("§eDistancia: §f%.2f §ebloques (máx: 0.5)", distance));
                sendFailure(context, String.format("§eDiferencia de rotación: §f%.1f° §e(máx: 15°)", yawDiff));
            }
            return isCorrect ? 1 : 0;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // ==================== INTERRUPTOR ====================

    private static int linkUblablaToInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            UblablaEntity ublabla = getEntityOfType(context, "ublabla", UblablaEntity.class, NOT_UBLABLA);
            if (ublabla == null) return 0;
            interruptor.linkUblabla(ublabla);
            sendSuccess(context, "§a✓ Ublabla vinculado correctamente.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkUblablaFromInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            interruptor.unlinkUblabla();
            sendSuccess(context, "§aEnlace con Ublabla eliminado.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int linkPanelToInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            PanelCodigoEntity panel = getEntityOfType(context, "panel", PanelCodigoEntity.class, NOT_PANEL_CODIGO);
            if (panel == null) return 0;
            interruptor.linkPanel(panel);
            sendSuccess(context, "§a✓ Panel de Código vinculado correctamente.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkPanelFromInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            interruptor.unlinkPanel();
            sendSuccess(context, "§aEnlace con Panel de Código eliminado.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // Modificar listInterruptorLinks para mostrar también los nuevos enlaces:
    private static int listInterruptorLinks(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            sendInfo(context, "§6=== Vinculaciones del Interruptor ===");
            sendInfo(context, "§eCables: §f" + interruptor.getLinkedCables().size());
            sendInfo(context, "§eUblabla: §f" + (interruptor.getLinkedUblablas().isEmpty() ? "§cNo vinculado" : "§aVinculado"));
            sendInfo(context, "§ePanel de Código: §f" + (interruptor.getLinkedPanels().isEmpty() ? "§cNo vinculado" : "§aVinculado"));
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int linkCableToInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            CableEntity cable = getEntityOfType(context, "cable", CableEntity.class, NOT_CABLE);
            if (cable == null) return 0;
            interruptor.linkCable(cable);
            sendSuccess(context, "§a✓ Cable vinculado correctamente.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkAllFromInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            interruptor.unlinkAllCables();
            sendSuccess(context, SUCCESS_UNLINK_ALL);
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // ==================== REJA ====================

    private static int linkPanelToReja(CommandContext<CommandSourceStack> context) {
        try {
            RejaDuctoEntity reja = getEntityOfType(context, "reja", RejaDuctoEntity.class, NOT_REJA);
            if (reja == null) return 0;
            PanelEnergiaEntity panel = getEntityOfType(context, "panel", PanelEnergiaEntity.class, NOT_PANEL_ENERGIA);
            if (panel == null) return 0;
            reja.linkPowerPanel(panel, reja.position());
            sendSuccess(context, "§a✓ Panel de Energía vinculado correctamente.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkAllFromReja(CommandContext<CommandSourceStack> context) {
        try {
            RejaDuctoEntity reja = getEntityOfType(context, "reja", RejaDuctoEntity.class, NOT_REJA);
            if (reja == null) return 0;
            reja.unlinkAllPowerPanels();
            sendSuccess(context, SUCCESS_UNLINK_ALL);
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int listRejaLinks(CommandContext<CommandSourceStack> context) {
        try {
            RejaDuctoEntity reja = getEntityOfType(context, "reja", RejaDuctoEntity.class, NOT_REJA);
            if (reja == null) return 0;
            int count = reja.getLinkedPowerPanels().size();
            sendInfo(context, "§6=== Vinculaciones de la Reja ===");
            sendInfo(context, "§ePaneles de Energía: §f" + count);
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // ==================== VALVULA ====================

    private static int addParticleEmitter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ValvulaEntity valvula = getEntityOfType(ctx, "valvula", ValvulaEntity.class, NOT_VALVULA);
        if (valvula == null) return 0;
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        double worldX = pos.getX();
        double worldY = pos.getY();
        double worldZ = pos.getZ();
        double dirX = DoubleArgumentType.getDouble(ctx, "dirX");
        double dirY = DoubleArgumentType.getDouble(ctx, "dirY");
        double dirZ = DoubleArgumentType.getDouble(ctx, "dirZ");
        double speed = DoubleArgumentType.getDouble(ctx, "speed");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        double pushForce = DoubleArgumentType.getDouble(ctx, "pushForce");
        int count = IntegerArgumentType.getInteger(ctx, "count");

        ValvulaEntity.ParticleEmitter emitter = ValvulaEntity.ParticleEmitter.fromWorldCoordinates(
                valvula, worldX, worldY, worldZ, dirX, dirY, dirZ, speed, radius, pushForce, count
        );
        valvula.addParticleEmitter(emitter);
        int index = valvula.getParticleEmitters().size() - 1;
        sendInfo(ctx, String.format(
                "§a✔ Emisor añadido [%d]\n§7  Pos: §f%d %d %d\n§7  Dir: §f%.2f %.2f %.2f\n§7  Speed: §f%.3f | Count: §f%d\n§7  Radius: §f%.2f | PushForce: §f%.2f",
                index, pos.getX(), pos.getY(), pos.getZ(), dirX, dirY, dirZ, speed, count, radius, pushForce
        ));
        return 1;
    }

    private static int addParticleEmitterWithDefaults(CommandContext<CommandSourceStack> ctx, int defaultCount) throws CommandSyntaxException {
        ValvulaEntity valvula = getEntityOfType(ctx, "valvula", ValvulaEntity.class, NOT_VALVULA);
        if (valvula == null) return 0;
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        double worldX = pos.getX();
        double worldY = pos.getY();
        double worldZ = pos.getZ();
        double dirX = DoubleArgumentType.getDouble(ctx, "dirX");
        double dirY = DoubleArgumentType.getDouble(ctx, "dirY");
        double dirZ = DoubleArgumentType.getDouble(ctx, "dirZ");
        double speed = DoubleArgumentType.getDouble(ctx, "speed");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        double pushForce = DoubleArgumentType.getDouble(ctx, "pushForce");

        ValvulaEntity.ParticleEmitter emitter = ValvulaEntity.ParticleEmitter.fromWorldCoordinates(
                valvula, worldX, worldY, worldZ, dirX, dirY, dirZ, speed, radius, pushForce, defaultCount
        );
        valvula.addParticleEmitter(emitter);
        int index = valvula.getParticleEmitters().size() - 1;
        sendSuccess(ctx, "§a✔ Emisor añadido [" + index + "] con count: " + defaultCount);
        return 1;
    }

    private static int listParticleEmitters(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ValvulaEntity valvula = getEntityOfType(ctx, "valvula", ValvulaEntity.class, NOT_VALVULA);
        if (valvula == null) return 0;
        List<ValvulaEntity.ParticleEmitter> emitters = valvula.getParticleEmitters();
        if (emitters.isEmpty()) {
            sendInfo(ctx, "§eNo hay emisores configurados.");
            return 1;
        }
        StringBuilder sb = new StringBuilder("§6=== Emisores de Partículas ===\n");
        sb.append("§7Estado: ").append(valvula.getState()).append("/").append(ValvulaEntity.MAX_STATE);
        sb.append(" (correcto: ").append(valvula.getCorrectState()).append(")");
        sb.append(" | Activas: ").append(valvula.areParticlesActive() ? "§aSí" : "§cNo").append("\n\n");
        for (int i = 0; i < emitters.size(); i++) {
            ValvulaEntity.ParticleEmitter e = emitters.get(i);
            Vec3 worldPos = e.getWorldPosition(valvula);
            Vec3 worldDir = e.getWorldDirection(valvula);
            sb.append(String.format("§e[%d]\n", i));
            sb.append(String.format("  §7Offset local: §f%.2f, %.2f, %.2f\n", e.offsetX, e.offsetY, e.offsetZ));
            sb.append(String.format("  §7Pos mundo:    §f%.2f, %.2f, %.2f\n", worldPos.x, worldPos.y, worldPos.z));
            sb.append(String.format("  §7Dir local:    §f%.2f, %.2f, %.2f\n", e.dirX, e.dirY, e.dirZ));
            sb.append(String.format("  §7Dir mundo:    §f%.2f, %.2f, %.2f\n", worldDir.x, worldDir.y, worldDir.z));
            sb.append(String.format("  §7Speed: §f%.3f | Count: §f%d\n", e.speed, e.count));
            sb.append(String.format("  §7Radius: §f%.2f | PushForce: §f%.2f\n\n", e.radius, e.pushForce));
        }
        sendInfo(ctx, sb.toString());
        return 1;
    }

    private static int removeParticleEmitter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ValvulaEntity valvula = getEntityOfType(ctx, "valvula", ValvulaEntity.class, NOT_VALVULA);
        if (valvula == null) return 0;
        int index = IntegerArgumentType.getInteger(ctx, "index");
        if (index < 0 || index >= valvula.getParticleEmitters().size()) {
            sendFailure(ctx, "§cÍndice inválido. Rango: 0-" + (valvula.getParticleEmitters().size() - 1));
            return 0;
        }
        valvula.removeParticleEmitter(index);
        sendSuccess(ctx, "§a✔ Emisor [" + index + "] eliminado.");
        return 1;
    }

    private static int clearParticleEmitters(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ValvulaEntity valvula = getEntityOfType(ctx, "valvula", ValvulaEntity.class, NOT_VALVULA);
        if (valvula == null) return 0;
        int count = valvula.getParticleEmitters().size();
        valvula.clearParticleEmitters();
        sendSuccess(ctx, "§a✔ " + count + " emisores eliminados.");
        return 1;
    }

    private static int setValvulaCorrectState(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ValvulaEntity valvula = getEntityOfType(ctx, "valvula", ValvulaEntity.class, NOT_VALVULA);
        if (valvula == null) return 0;

        int estado = IntegerArgumentType.getInteger(ctx, "estado");
        valvula.setCorrectState(estado);

        sendSuccess(ctx, "§a✔ Válvula §6" + valvula.getValType().name()
                + "§a: estado correcto = §f" + estado + "§7/" + ValvulaEntity.MAX_STATE
                + "\n§7La presión se cortará solo cuando esté justo en ese estado.");
        return 1;
    }

    private static int valvulaInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ValvulaEntity valvula = getEntityOfType(ctx, "valvula", ValvulaEntity.class, NOT_VALVULA);
        if (valvula == null) return 0;

        sendInfo(ctx, "§6=== Válvula " + valvula.getValType().name() + " ===");
        sendInfo(ctx, "§eManivela: " + (valvula.hasManivela() ? "§apuesta" : "§cno tiene (no gira)"));
        sendInfo(ctx, "§eEstado actual: §f" + valvula.getState() + "§7/" + ValvulaEntity.MAX_STATE);
        sendInfo(ctx, "§eEstado correcto: §f" + valvula.getCorrectState() + "§7/" + ValvulaEntity.MAX_STATE);
        sendInfo(ctx, "§ePresión: " + (valvula.areParticlesActive() ? "§cescapando" : "§aestabilizada"));
        sendInfo(ctx, "§eEmisores: §f" + valvula.getParticleEmitters().size());
        return 1;
    }

    // ==================== PANEL_CODIGO ====================

    private static int linkDoorToCodigoPanel(CommandContext<CommandSourceStack> context) {
        try {
            PanelCodigoEntity panel = getEntityOfType(context, "panel", PanelCodigoEntity.class, NOT_PANEL_CODIGO);
            if (panel == null) return 0;
            PuertaMetalicaEntity door = getEntityOfType(context, "puerta", PuertaMetalicaEntity.class, NOT_PUERTA_METALICA);
            if (door == null) return 0;
            panel.linkDoor(door, panel.position());
            sendSuccess(context, "§a✓ Puerta vinculada al Panel de Código.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkAllDoorsFromCodigoPanel(CommandContext<CommandSourceStack> context) {
        try {
            PanelCodigoEntity panel = getEntityOfType(context, "panel", PanelCodigoEntity.class, NOT_PANEL_CODIGO);
            if (panel == null) return 0;
            panel.unlinkAllDoors();
            sendSuccess(context, SUCCESS_UNLINK_ALL);
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int listCodigoPanelDoors(CommandContext<CommandSourceStack> context) {
        try {
            PanelCodigoEntity panel = getEntityOfType(context, "panel", PanelCodigoEntity.class, NOT_PANEL_CODIGO);
            if (panel == null) return 0;
            sendInfo(context, "§6=== Puertas Vinculadas (Panel Código) ===");
            sendInfo(context, "§ePuertas: §f" + panel.getLinkedDoorsCount());
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // ==================== CABLES / VALVULAS / CAJAS ====================

    /**
     * Lista los cables alrededor del que ejecuta, en el MISMO orden que usa
     * puzzles.yml, para poder escribir la lista de rotaciones sin adivinar.
     */
    private static int listRoomCables(CommandContext<CommandSourceStack> context, int radio) {
        var source = context.getSource();
        var area = new net.minecraft.world.phys.AABB(BlockPos.containing(source.getPosition())).inflate(radio);
        List<CableEntity> cables = source.getLevel().getEntitiesOfClass(CableEntity.class, area, c -> true);
        cables.sort(com.github.razorplay01.instance.InstanceManager.roomOrder());

        if (cables.isEmpty()) {
            sendFailure(context, "§cNo hay cables en un radio de " + radio + " bloques.");
            return 0;
        }
        sendInfo(context, "§6=== Cables en orden de puzzles.yml (radio " + radio + ") ===");
        for (int i = 0; i < cables.size(); i++) {
            CableEntity cable = cables.get(i);
            sendInfo(context, String.format("§e#%d §7(%.0f, %.0f, %.0f) §ftipo %s, rotación %d, correcta %d",
                    i + 1, cable.getX(), cable.getY(), cable.getZ(),
                    cable.getCableType() == 0 ? "recto" : "curvo",
                    cable.getState(), cable.getCorrectState()));
        }
        return cables.size();
    }

    private static int listRoomValvulas(CommandContext<CommandSourceStack> context, int radio) {
        var source = context.getSource();
        var area = new net.minecraft.world.phys.AABB(BlockPos.containing(source.getPosition())).inflate(radio);
        List<ValvulaEntity> valvulas = source.getLevel().getEntitiesOfClass(ValvulaEntity.class, area, v -> true);
        valvulas.sort(com.github.razorplay01.instance.InstanceManager.roomOrder());

        if (valvulas.isEmpty()) {
            sendFailure(context, "§cNo hay válvulas en un radio de " + radio + " bloques.");
            return 0;
        }
        sendInfo(context, "§6=== Válvulas en orden de puzzles.yml (radio " + radio + ") ===");
        for (int i = 0; i < valvulas.size(); i++) {
            ValvulaEntity valvula = valvulas.get(i);
            sendInfo(context, String.format("§e#%d §7(%.0f, %.0f, %.0f) §festado %d, correcto %d",
                    i + 1, valvula.getX(), valvula.getY(), valvula.getZ(),
                    valvula.getState(), valvula.getCorrectState()));
        }
        return valvulas.size();
    }

    private static int setCajaName(CommandContext<CommandSourceStack> context) {
        try {
            Entity entity = EntityArgument.getEntity(context, "caja");
            if (!(entity instanceof CajaEntity) && !(entity instanceof CajaHerramientasEntity)) {
                sendFailure(context, "§cLa entidad debe ser una Caja o una Caja de Herramientas.");
                return 0;
            }
            String nombre = StringArgumentType.getString(context, "nombre");
            entity.setCustomName(Component.literal(nombre));
            entity.setCustomNameVisible(false);

            if (GwwPuzzles.hasCajaContenido(nombre)) {
                sendSuccess(context, "§a✓ Caja renombrada a '" + nombre
                        + "'. §7puzzles.yml le pondrá su contenido en el próximo reset de la sala.");
            } else {
                sendSuccess(context, "§a✓ Caja renombrada a '" + nombre + "'.\n"
                        + "§eAviso: puzzles.yml no tiene una entrada 'cajas." + nombre
                        + "'; hasta que la agregues conserva su contenido actual.");
            }
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // ==================== TECLADO ====================

    private static int setTecladoName(CommandContext<CommandSourceStack> context) {
        try {
            PanelTecladoEntity teclado = getEntityOfType(context, "teclado", PanelTecladoEntity.class, NOT_TECLADO);
            if (teclado == null) return 0;
            String nombre = StringArgumentType.getString(context, "nombre");
            teclado.setCustomName(Component.literal(nombre));
            teclado.setCustomNameVisible(false);

            String code = GwwPuzzles.tecladoCode(nombre);
            if (code == null) {
                sendSuccess(context, "§a✓ Teclado renombrado a '" + nombre + "'.\n"
                        + "§eAviso: puzzles.yml no tiene un codigo para ese nombre; usara 1234 hasta que lo agregues.");
            } else {
                sendSuccess(context, "§a✓ Teclado renombrado a '" + nombre
                        + "' §7(codigo en puzzles.yml: " + code + ")");
            }
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int linkDoorToTeclado(CommandContext<CommandSourceStack> context) {
        try {
            PanelTecladoEntity teclado = getEntityOfType(context, "teclado", PanelTecladoEntity.class, NOT_TECLADO);
            if (teclado == null) return 0;
            Entity door = EntityArgument.getEntity(context, "puerta");
            if (!(door instanceof BaseEntity baseDoor) || !teclado.linkDoor(baseDoor)) {
                sendFailure(context, "§cLa segunda entidad debe ser una Puerta Metálica o una Puerta del Ático.");
                return 0;
            }
            if (door instanceof PuertaAticoEntity) {
                sendSuccess(context, "§a✓ Puerta del ático vinculada: ya no acepta la llave, la abre este teclado.");
            } else {
                sendSuccess(context, "§a✓ Puerta metálica vinculada al teclado.");
            }
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkAllDoorsFromTeclado(CommandContext<CommandSourceStack> context) {
        try {
            PanelTecladoEntity teclado = getEntityOfType(context, "teclado", PanelTecladoEntity.class, NOT_TECLADO);
            if (teclado == null) return 0;
            teclado.unlinkAllDoors();
            sendSuccess(context, SUCCESS_UNLINK_ALL + " §7(las puertas de ático vuelven a aceptar la llave)");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int tecladoInfo(CommandContext<CommandSourceStack> context) {
        try {
            PanelTecladoEntity teclado = getEntityOfType(context, "teclado", PanelTecladoEntity.class, NOT_TECLADO);
            if (teclado == null) return 0;
            String nombre = teclado.getTecladoName();
            String code = GwwPuzzles.tecladoCode(nombre);
            sendInfo(context, "§6=== Teclado Numérico ===");
            sendInfo(context, "§eNombre: §f" + (nombre.isEmpty() ? "§c(sin nombre)" : nombre));
            sendInfo(context, "§eCodigo en puzzles.yml: §f" + (code == null ? "§c(no configurado, usa 1234)" : code));
            sendInfo(context, "§eResuelto: §f" + (teclado.isSolved() ? "sí" : "no")
                    + " §7| §eEncendido: §f" + (teclado.isPowered() ? "sí" : "no"));
            sendInfo(context, "§ePuertas vinculadas: §f" + teclado.getLinkedDoorsCount());
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int resetTeclado(CommandContext<CommandSourceStack> context) {
        try {
            PanelTecladoEntity teclado = getEntityOfType(context, "teclado", PanelTecladoEntity.class, NOT_TECLADO);
            if (teclado == null) return 0;
            teclado.resetPuzzle();
            sendSuccess(context, "§aTeclado reseteado: el próximo intento usa el codigo actual de puzzles.yml.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int linkTecladoToInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            PanelTecladoEntity teclado = getEntityOfType(context, "teclado", PanelTecladoEntity.class, NOT_TECLADO);
            if (teclado == null) return 0;
            interruptor.linkTeclado(teclado);
            sendSuccess(context, "§a✓ Teclado vinculado: ahora depende del interruptor.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int unlinkTecladoFromInterruptor(CommandContext<CommandSourceStack> context) {
        try {
            InterruptorIndustrialEntity interruptor = getEntityOfType(context, "interruptor", InterruptorIndustrialEntity.class, NOT_INTERRUPTOR);
            if (interruptor == null) return 0;
            interruptor.unlinkTeclado();
            sendSuccess(context, "§aEnlace con el teclado eliminado: vuelve a funcionar por su cuenta.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // ==================== PALANCA ====================

    private static int setInitialPalancaFromCurrent(CommandContext<CommandSourceStack> context) {
        try {
            PalancaEntity palanca = getEntityOfType(context, "palanca", PalancaEntity.class, NOT_PALANCA);
            if (palanca == null) return 0;
            palanca.setInitialPosition((float) palanca.getX(), (float) palanca.getY(), (float) palanca.getZ());
            sendSuccess(context, String.format("§aPosición inicial: §f%.2f, %.2f, %.2f", palanca.getX(), palanca.getY(), palanca.getZ()));
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int setInitialPalancaPosition(CommandContext<CommandSourceStack> context) {
        try {
            PalancaEntity palanca = getEntityOfType(context, "palanca", PalancaEntity.class, NOT_PALANCA);
            if (palanca == null) return 0;
            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
            palanca.setInitialPosition(pos.getX(), pos.getY(), pos.getZ());
            sendSuccess(context, String.format("§aPosición inicial establecida en: §f%d, %d, %d", pos.getX(), pos.getY(), pos.getZ()));
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int resetPalancaPuzzle(CommandContext<CommandSourceStack> context) {
        try {
            PalancaEntity palanca = getEntityOfType(context, "palanca", PalancaEntity.class, NOT_PALANCA);
            if (palanca == null) return 0;
            palanca.resetPuzzleState();
            sendSuccess(context, "§aEstado del puzzle de la palanca reseteado.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int teleportPalancaToInitial(CommandContext<CommandSourceStack> context) {
        try {
            PalancaEntity palanca = getEntityOfType(context, "palanca", PalancaEntity.class, NOT_PALANCA);
            if (palanca == null) return 0;
            palanca.setPos(palanca.getInitialPosition());
            sendSuccess(context, "§aPalanca teletransportada a su posición inicial.");
            return 1;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    private static int checkPalancaPlacement(CommandContext<CommandSourceStack> context) {
        try {
            PalancaEntity palanca = getEntityOfType(context, "palanca", PalancaEntity.class, NOT_PALANCA);
            if (palanca == null) return 0;
            boolean isCorrect = palanca.checkIfCorrectlyPlaced();
            double distance = palanca.getDistanceToInitialPosition();
            if (isCorrect) {
                sendInfo(context, "§a✓ La palanca está correctamente colocada.");
            } else {
                sendFailure(context, "§c✗ La palanca NO está correctamente colocada.");
                sendFailure(context, String.format("§eDistancia: §f%.2f §ebloques (máx: 0.5)", distance));
            }
            return isCorrect ? 1 : 0;
        } catch (Exception e) {
            sendFailure(context, "§cError: " + e.getMessage());
            return 0;
        }
    }

    // ==================== UBLABLA ====================
    private static int linkDoorToUblabla(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        PuertaMetalicaUblablaEntity door = getEntityOfType(context, "door", PuertaMetalicaUblablaEntity.class, "§cLa entidad no es una Puerta Metálica.");
        if (door == null) return 0;
        ublabla.linkDoor(door);
        sendSuccess(context, "§a✓ Puerta vinculada al Ublabla.");
        return 1;
    }

    private static int unlinkDoorFromUblabla(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        PuertaMetalicaUblablaEntity door = getEntityOfType(context, "door", PuertaMetalicaUblablaEntity.class, "§cLa entidad no es una Puerta Metálica.");
        if (door == null) return 0;
        ublabla.unlinkDoor(door);
        sendSuccess(context, "§a✓ Enlace con puerta eliminado.");
        return 1;
    }

    private static int unlinkAllDoorsFromUblabla(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        ublabla.unlinkAllDoors();
        sendSuccess(context, "§aTodas las puertas han sido desvinculadas.");
        return 1;
    }

    private static int listUblablaDoors(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        List<Vec3> doors = ublabla.getLinkedDoors();
        if (doors.isEmpty()) {
            sendInfo(context, "§eEste Ublabla no tiene puertas vinculadas.");
        } else {
            sendInfo(context, "§6=== Puertas vinculadas al Ublabla ===");
            for (int i = 0; i < doors.size(); i++) {
                Vec3 rel = doors.get(i);
                sendInfo(context, String.format("§e[%d] §f(%.2f, %.2f, %.2f)", i, rel.x, rel.y, rel.z));
            }
        }
        return 1;
    }

    private static int setInvestigationPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        ublabla.setInvestigationTarget(pos);
        sendSuccess(context, String.format("§aPunto de investigación establecido en §f(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }

    private static int setJailArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        ublabla.setJailArea(from, to);
        sendSuccess(context, String.format("§aRegión de cárcel definida: §f(%d,%d,%d) → (%d,%d,%d)",
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()));
        return 1;
    }

    private static int clearJailArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        ublabla.setJailArea(null, null);
        sendSuccess(context, "§aRegión de cárcel eliminada.");
        return 1;
    }

    private static int getUblablaInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        BlockPos spawn = ublabla.getSpawnPos();
        BlockPos inv = ublabla.getInvestigationTarget();
        BlockPos jailMin = ublabla.getJailMin();
        BlockPos jailMax = ublabla.getJailMax();
        Vec3 patrolCenter = ublabla.getPatrolCenter();
        double patrolRadius = ublabla.getPatrolRadius();
        int state = ublabla.getState();

        String msg = String.format("§6=== Ublabla Info ===\n" +
                        "§eSpawn: §f(%d, %d, %d)\n" +
                        "§eCentro patrulla: §f(%.2f, %.2f, %.2f)\n" +
                        "§eRadio patrulla: §f%.2f\n" +
                        "§eInvestigación: §f%s\n" +
                        "§eCárcel: §f%s\n" +
                        "§eEstado actual: §f%d",
                spawn.getX(), spawn.getY(), spawn.getZ(),
                patrolCenter.x, patrolCenter.y, patrolCenter.z,
                patrolRadius,
                inv != null ? String.format("(%d,%d,%d)", inv.getX(), inv.getY(), inv.getZ()) : "§cNo definido",
                (jailMin != null && jailMax != null) ?
                        String.format("(%d,%d,%d) → (%d,%d,%d)", jailMin.getX(), jailMin.getY(), jailMin.getZ(),
                                jailMax.getX(), jailMax.getY(), jailMax.getZ()) :
                        "§cNo definida",
                state);
        sendInfo(context, msg);
        return 1;
    }

    private static int setPatrolCenter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        Vec3 center = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        ublabla.setPatrolCenter(center);
        sendSuccess(context, String.format("§aCentro de patrulla establecido en §f(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }

    private static int setPatrolRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        double radius = DoubleArgumentType.getDouble(context, "radius");
        ublabla.setPatrolRadius(radius);
        sendSuccess(context, String.format("§aRadio de patrulla establecido en §f%.2f", radius));
        return 1;
    }

    private static int setSpawnPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UblablaEntity ublabla = getEntityOfType(context, "entity", UblablaEntity.class, NOT_UBLABLA);
        if (ublabla == null) return 0;
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        ublabla.setSpawnPos(pos);
        sendSuccess(context, String.format("§aPunto de spawn establecido en §f(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }
}