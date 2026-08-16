package com.github.razorplay01.event;

import com.github.razorplay01.system.NoiseDetectionSystem;

/**
 * Enganches del sistema de ruido a los eventos del juego.
 * <p>
 * El tick ya NO se registra aquí: antes esta clase enganchaba su propio
 * END_SERVER_TICK y recorría la lista entera de jugadores, sumando un segundo
 * barrido al que el mod ya hacía en su tick principal. Ahora el tick del ruido lo
 * llama {@code GWW} desde el único bucle que hay, vía
 * {@link NoiseDetectionSystem#tickAll}.
 */
public class NoiseEventHandler {

    public static void register() {
        // Romper bloques
        /*PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                NoiseDetectionSystem.onBlockBreak(serverPlayer, state);
            }
        });*/

        // Colocar bloques
        /*UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && !world.isClientSide()) {
                var blockState = world.getBlockState(hitResult.getBlockPos());
                NoiseDetectionSystem.onBlockPlace(serverPlayer, blockState);
            }
            return InteractionResult.PASS;
        });*/

        // Atacar entidades
        /*AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                NoiseDetectionSystem.onAttack(serverPlayer);
            }
            return InteractionResult.PASS;
        });*/

        // Usar items
        /*UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                NoiseDetectionSystem.onItemUse(serverPlayer, player.getItemInHand(hand));
            }
            return InteractionResult.PASS;
        });*/
    }
}
