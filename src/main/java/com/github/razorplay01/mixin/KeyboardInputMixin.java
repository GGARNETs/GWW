package com.github.razorplay01.mixin;

import com.github.razorplay01.extra.ClientMinigameState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Movimiento estilo juego 2D durante el minijuego de cañones. En vanilla,
 * caminar de lado o hacia atrás es más lento que hacia adelante y no deja
 * esprintar, así que con el personaje mirando fijo la velocidad cambiaba según
 * la tecla. Aquí se traduce el WASD a una dirección de pantalla (W arriba = sur,
 * A izquierda = este, con la cámara cenital a yaw 0), se gira al personaje hacia
 * allí y se le manda siempre "adelante": misma velocidad en las 8 direcciones
 * y se puede correr en cualquiera.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void gww$topDownMovement(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo ci) {
        if (!ClientMinigameState.get().isActive()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        int east = (this.left ? 1 : 0) - (this.right ? 1 : 0);
        int south = (this.up ? 1 : 0) - (this.down ? 1 : 0);

        this.leftImpulse = 0.0f;
        if (east == 0 && south == 0) {
            // Sin teclas (o teclas opuestas): quieto, mirando hacia el último rumbo.
            this.forwardImpulse = 0.0f;
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-east, south));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);

        this.forwardImpulse = isSneaking ? sneakingSpeedMultiplier : 1.0f;
    }
}
