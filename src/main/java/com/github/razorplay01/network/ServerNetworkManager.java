package com.github.razorplay01.network;

import com.github.razorplay.packet_handler.network.IPacket;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.network.packet.MinigameStatePacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import static com.github.razorplay01.GWW.LOGGER;

public class ServerNetworkManager {
    private ServerNetworkManager() {
        // []
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(FabricCustomPayload.CUSTOM_PAYLOAD_ID, (payload, context) -> {
            IPacket packet = payload.packet();

            switch (packet) {
                //case SaveEventPacket pkt -> checkSaveEventPacket(context, pkt);

                default -> LOGGER.info("Unknown client packet: {}", packet.getPacketId());
            }
        });
    }

    public static void sendMinigameStatePacketToPlayer(ServerPlayer player, MinigameStatePacket packet) {
        ServerPlayNetworking.send(player, new FabricCustomPayload(packet));
        GwwDebug.count(GwwDebug.PACKETS_MINIGAME);
    }
}
