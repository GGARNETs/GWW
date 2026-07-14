package com.github.razorplay01;

import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.cam.starup.AnnotationFinder;
import com.github.razorplay01.client.ClientNoiseState;
import com.github.razorplay01.client.render.NoiseHudRenderer;
import com.github.razorplay01.command.EscapeRoomConfigCommand;
import com.github.razorplay01.command.NoiseCommand;
import com.github.razorplay01.config.GwwSettings;
import com.github.razorplay01.entity.ModEntities;
import com.github.razorplay01.entity.attribute.ModAttributes;
import com.github.razorplay01.entity.client.*;
import com.github.razorplay01.entity.custom.*;
import com.github.razorplay01.entity.custom.util.PuzzleEntityChecker;
import com.github.razorplay01.event.NoiseEventHandler;
import com.github.razorplay01.extra.MinigameCommand;
import com.github.razorplay01.extra.MinigameState;
import com.github.razorplay01.instance.InstanceManager;
import com.github.razorplay01.item.ModComponents;
import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.network.ClientNetworkManager;
import com.github.razorplay01.network.FabricCustomPayload;
import com.github.razorplay01.network.ServerNetworkManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GWW implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "gww";
    public static final String PACKET_BASE_CHANNEL = MOD_ID + ":packets_channel";
    public static final int ALLOWED_SLOT = 4;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static MinigameState currentGame = null;
    public static MinecraftServer server;

    @Override
    public void onInitialize() {
        FabricCustomPayload.register();
        ServerNetworkManager.register();
        GwwSettings.load();
        InstanceManager.loadAll();
        ArenaManager.load();
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            ArenaManager.tickGroups(server);
            if (currentGame != null && currentGame.isActive()) {
                boolean sigue = currentGame.tick(server);
                if (!sigue) {
                    currentGame = null;
                }
            }
        });
        NoiseEventHandler.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MinigameCommand.register(dispatcher);
            NoiseCommand.register(dispatcher);
            EscapeRoomCommands.register(dispatcher);
            EscapeRoomConfigCommand.register(dispatcher);
        });
        ModComponents.register();
        ModItems.registerModItems();
        ModAttributes.register();
        PuzzleEntityChecker.registerDefaultCheckers();
        ModEntities.registerModEntities();
        FabricDefaultAttributeRegistry.register(ModEntities.CANNON, CannonEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CANNON_BULLET, CannonBulletEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.UBLABLA, UblablaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAJA, CajaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CUADRO1, Cuadro1Entity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CUADRO2, Cuadro2Entity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CUADRO3, Cuadro3Entity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.REJA_DUCTO, RejaDuctoEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PUERTA_ATICO, PuertaAticoEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.LUZ_TORTUGA, LuzTortugaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.INTERRUPTOR_INDUSTRIAL, InterruptorIndustrialEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAJA_HERRAMIENTAS, CajaHerramientasEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PANEL_FUSIBLES, PanelFusiblesEntity.setAttributes());
        //FabricDefaultAttributeRegistry.register(ModEntities.MYSTIC_ORB, MysticOrbEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PUERTA_METALICA, PuertaMetalicaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PUERTA_METALICA_UBLABLA, PuertaMetalicaUblablaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.FIGURAS_PARED, FigurasParedEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MANIVELA, ManivelaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.VALVULA, ValvulaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PALANCA, PalancaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.ESCALERA, EscaleraEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CABLE, CableEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PUERTA_JAULA, PuertaJaulaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PANEL_ENERGIA, PanelEnergiaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PANEL_CODIGO, PanelCodigoEntity.setAttributes());
        /*ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (SingleSlotState.isEnabled(player.getUUID())) {
                    cleanLockedSlots(player);
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (SingleSlotState.isEnabled(player.getUUID())) {
                player.getInventory().selected = ALLOWED_SLOT;
                cleanLockedSlots(player);
            }
        });*/
        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> server = minecraftServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(minecraftServer -> server = null);
        LOGGER.info("Hello Fabric world!");
    }

    @Override
    public void onInitializeClient() {
        AnnotationFinder.clientLoading();
        ClientNetworkManager.register();
        EntityRendererRegistry.register(ModEntities.CANNON, CannonEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CANNON_BULLET, CannonBulletEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.UBLABLA, UblablaEntityRenderer::new);
        //EntityRendererRegistry.register(ModEntities.MYSTIC_ORB, BaseInteractiveRenderer::new);
        EntityRendererRegistry.register(ModEntities.CAJA, CajaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CUADRO1, Cuadro1EntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CUADRO2, Cuadro2EntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CUADRO3, Cuadro3EntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.REJA_DUCTO, RejaDuctoEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PUERTA_ATICO, PuertaAticoEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.LUZ_TORTUGA, LuzTortugaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.INTERRUPTOR_INDUSTRIAL, InterruptorIndustrialEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CAJA_HERRAMIENTAS, CajaHerramientasEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PANEL_FUSIBLES, PanelFusiblesEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PUERTA_METALICA, PuertaMetalicaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PUERTA_METALICA_UBLABLA, PuertaMetalicaUblablaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.FIGURAS_PARED, FigurasParedEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MANIVELA, ManivelaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.VALVULA, ValvulaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PALANCA, PalancaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.ESCALERA, EscaleraEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CABLE, CableEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PUERTA_JAULA, PuertaJaulaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PANEL_ENERGIA, PanelEnergiaEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PANEL_CODIGO, PanelCodigoEntityRenderer::new);
        NoiseHudRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                ClientNoiseState.get().tick();
            }
        });
        /*ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (SingleSlotState.isClientEnabled()) {
                    if (client.player.getInventory().selected != ALLOWED_SLOT) {
                        client.player.getInventory().selected = ALLOWED_SLOT;
                    }
                }
            }
        });*/
    }

    public static void cleanLockedSlots(ServerPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (i == ALLOWED_SLOT) continue;

            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                boolean moved = false;
                for (int j = 9; j < 36; j++) {
                    if (player.getInventory().getItem(j).isEmpty()) {
                        player.getInventory().setItem(j, stack.copy());
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                        moved = true;
                        break;
                    }
                }
                if (!moved) {
                    player.drop(stack, false);
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    public static boolean isSlotLocked(int hotbarSlot) {
        return hotbarSlot >= 0 && hotbarSlot < 9 && hotbarSlot != ALLOWED_SLOT;
    }
}