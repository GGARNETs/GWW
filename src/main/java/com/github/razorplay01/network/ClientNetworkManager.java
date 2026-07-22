package com.github.razorplay01.network;

import com.github.razorplay.packet_handler.network.IPacket;
import com.github.razorplay01.SingleSlotState;
import com.github.razorplay01.client.ClientNoiseState;
import com.github.razorplay01.extra.ClientMinigameState;
import com.github.razorplay01.network.packet.MinigameStatePacket;
import com.github.razorplay01.network.packet.NoisePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.phys.Vec3;

import static com.github.razorplay01.GWW.LOGGER;

public class ClientNetworkManager {
    private ClientNetworkManager() {
        // []
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FabricCustomPayload.CUSTOM_PAYLOAD_ID, (payload, context) ->
                context.client().execute(() -> {
                    IPacket packet = payload.packet();

                    switch (packet) {
                        case MinigameStatePacket pkt -> checkMinigameStatePacket(context, pkt);
                        case NoisePacket pkt -> handleNoisePacket(context, pkt);

                        default -> LOGGER.info("Unknown client packet: {}", packet.getPacketId());
                    }
                }));
    }

    private static void checkMinigameStatePacket(ClientPlayNetworking.Context context, MinigameStatePacket pkt) {
        ClientMinigameState.get().update(
                pkt.isActive(),
                new Vec3(pkt.getX(), pkt.getY(), pkt.getZ()),
                pkt.getRadius()
        );
    }

    private static void handleNoisePacket(ClientPlayNetworking.Context context, NoisePacket pkt) {
        ClientNoiseState.get().update(
                pkt.getNoiseLevel(),
                pkt.getDecayRate(),
                pkt.isEnabled()
        );
    }

	/*private static void checkToastPacket(ClientPlayNetworking.Context context, ToastPacket pkt) {
		context.client().execute(() -> sendToast(Component.literal(pkt.getTitle()), Component.literal(pkt.getDescription())));
	}
	ClientPlayNetworking.send(new FabricCustomPayload(
				new SaveEventPacket(pkt.getEventId(), json)));
	*/
}
