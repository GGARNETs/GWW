package com.github.razorplay01;

import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.arena.EscapeRoomController;
import com.github.razorplay01.cam.starup.CameraPluginLoader;
import com.github.razorplay01.client.ClientNoiseState;
import com.github.razorplay01.client.render.NoiseHudRenderer;
import com.github.razorplay01.command.EscapeRoomConfigCommand;
import com.github.razorplay01.command.GwwDebugCommand;
import com.github.razorplay01.command.NoiseCommand;
import com.github.razorplay01.config.GwwSettings;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.ModEntities;
import com.github.razorplay01.entity.attribute.ModAttributes;
import com.github.razorplay01.entity.client.*;
import com.github.razorplay01.entity.custom.*;
import com.github.razorplay01.entity.custom.util.PuzzleEntityChecker;
import com.github.razorplay01.event.NoiseEventHandler;
import com.github.razorplay01.client.render.MinigameBorderRenderer;
import com.github.razorplay01.extra.CannonArenaManager;
import com.github.razorplay01.extra.ClientMinigameState;
import com.github.razorplay01.extra.MinigameCommand;
import com.github.razorplay01.extra.MinigameManager;
import com.github.razorplay01.instance.ChunkPreloader;
import com.github.razorplay01.instance.InstanceManager;
import com.github.razorplay01.item.ModComponents;
import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.network.ClientNetworkManager;
import com.github.razorplay01.network.FabricCustomPayload;
import com.github.razorplay01.network.ServerNetworkManager;
import com.github.razorplay01.sound.ModSounds;
import com.github.razorplay01.system.NoiseDetectionSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.client.player.LocalPlayer;
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
    public static MinecraftServer server;

    @Override
    public void onInitialize() {
        FabricCustomPayload.register();
        ServerNetworkManager.register();
        // Un único bucle por tick para todo el mod. El sistema de ruido tenía el suyo
        // aparte, así que la lista de jugadores se recorría dos veces por tick.
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            ArenaManager.tickGroups(server);
            EscapeRoomController.tick(server);
            NoiseDetectionSystem.tickAll(server);
            MinigameManager.tick();
            ChunkPreloader.tick();
            GwwDebug.tick();
        });
        // Morir jugando a los cañones no es una muerte de verdad: la partida
        // repone la vida y deja al jugador de espectador viendo a los demás.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) ->
                !(entity instanceof ServerPlayer player && MinigameManager.onPlayerDeath(player)));
        NoiseEventHandler.register();
        // Al desconectarse hay que soltar su estado de ruido en el momento: si se
        // espera al barrido periódico, los mapas del sistema van creciendo con
        // jugadores que ya no están.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) ->
                NoiseDetectionSystem.removePlayer(handler.getPlayer().getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MinigameCommand.register(dispatcher);
            NoiseCommand.register(dispatcher);
            EscapeRoomCommands.register(dispatcher);
            EscapeRoomConfigCommand.register(dispatcher);
            GwwDebugCommand.register(dispatcher);
        });
        ModComponents.register();
        ModItems.registerModItems();
        ModSounds.registerModSounds();
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
        // Los archivos de config se cargan (y se crean si faltan) al arrancar el servidor,
        // no al cargar el mod: este es un mod de minijuegos de servidor y un cliente
        // conectándose no tiene nada que hacer con config/GWW. Si se hiciera en
        // onInitialize, que Fabric ejecuta también en el cliente, cada jugador acabaría
        // con una carpeta config/GWW inútil en su .minecraft.
        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> {
            server = minecraftServer;
            GwwSettings.load();
            InstanceManager.loadAll();
            ArenaManager.load();
            CannonArenaManager.load();
        });
        // Al apagar, cerrar las partidas: si no, los cañones y balas quedan guardados
        // en el mundo y reaparecen al arrancar de nuevo, ya sin nadie que los mueva.
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> MinigameManager.stopAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(minecraftServer -> {
            server = null;
            ChunkPreloader.reset();
            NoiseDetectionSystem.clearAll();
        });
        LOGGER.info("Hello Fabric world!");
    }

    @Override
    public void onInitializeClient() {
        CameraPluginLoader.clientLoading();
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
        MinigameBorderRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                ClientNoiseState.get().tick();
                if (ClientMinigameState.get().isActive()) {
                    lockTopDownFacing(client.player);
                }
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

    /**
     * Durante el minijuego de cañones el ratón queda anulado (PlayerTurnMixin) y
     * el rumbo del personaje lo decide el WASD (KeyboardInputMixin). Aquí solo se
     * mantiene la mirada horizontal, por si algo (el teleport del muro, un
     * balazo) tocó el pitch.
     */
    private static void lockTopDownFacing(LocalPlayer player) {
        player.setXRot(0.0f);
        player.xRotO = 0.0f;
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