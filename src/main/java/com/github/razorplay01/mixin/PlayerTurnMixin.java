package com.github.razorplay01.mixin;

import com.github.razorplay01.extra.ClientMinigameState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anula el giro con el ratón mientras se juega al minijuego de cañones: con la
 * cámara cenital fija, el jugador se mueve solo con WASD estilo juego 2D y el
 * ratón no debe cambiar hacia dónde mira. La rotación fija la pone el tick de
 * cliente en {@link com.github.razorplay01.GWW}.
 */
@Mixin(Entity.class)
public class PlayerTurnMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void gww$lockTurnDuringMinigame(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && ClientMinigameState.get().isActive()) {
            ci.cancel();
        }
    }
}
