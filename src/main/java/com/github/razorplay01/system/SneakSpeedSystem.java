package com.github.razorplay01.system;

import com.github.razorplay01.GWW;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;
import java.util.UUID;

/**
 * Con la partida en marcha se pasa mucho tiempo agachado por el sistema de ruido,
 * así que a los jugadores dentro de una arena en marcha se les duplica la
 * velocidad al andar agachados. El modificador es transitorio: no se guarda en el
 * NBT del jugador, y si sale de la arena (o se para la partida) el siguiente
 * barrido se lo quita. Al ser un atributo vanilla, el cliente lo recibe solo y el
 * movimiento se predice sin tirones.
 */
public final class SneakSpeedSystem {
    private SneakSpeedSystem() {
        /* This utility class should not be instantiated */
    }

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "escape_room_sneak_boost");
    // Base vanilla 0.30 (30% de la velocidad al caminar) -> 0.30 * 2.00 = 0.60, o sea
    // un 60% de la velocidad al caminar: rápido pero sin llegar a andar normal.
    private static final double SNEAK_SPEED_BOOST = 1.00;

    /**
     * Deja el modificador puesto exactamente en los jugadores del set y en nadie más.
     * Se llama cada medio segundo desde el controller; con nadie jugando solo son
     * comprobaciones de atributo por jugador, sin coste real.
     */
    public static void sync(MinecraftServer server, Set<UUID> playersInGame) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AttributeInstance sneakSpeed = player.getAttribute(Attributes.SNEAKING_SPEED);
            if (sneakSpeed == null) {
                continue;
            }
            boolean hasBoost = sneakSpeed.getModifier(MODIFIER_ID) != null;
            boolean shouldHave = playersInGame.contains(player.getUUID());
            if (shouldHave && !hasBoost) {
                sneakSpeed.addTransientModifier(new AttributeModifier(
                        MODIFIER_ID, SNEAK_SPEED_BOOST, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!shouldHave && hasBoost) {
                sneakSpeed.removeModifier(MODIFIER_ID);
            }
        }
    }
}
