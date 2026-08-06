package com.github.razorplay01.mixin;

import com.github.darkpred.morehitboxes.api.MultiPart;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * morehitboxes 1.9.4-alpha redirige la escritura del campo {@code camera} dentro de
 * {@code setCamera} con una condición rota: solo asigna si la cámara vieja era una
 * sub-hitbox o si el campo era null, así que salir de espectador (shift o cambio de
 * gamemode) nunca escribe el campo y el jugador queda atascado siguiendo a la entidad.
 * <p>
 * Cancelamos en HEAD y reproducimos el cuerpo vanilla tal cual (el redirect del mod
 * queda muerto porque el cuerpo original nunca corre), conservando la intención del
 * mod: espectar una sub-hitbox pasa a espectar al mob padre.
 */
@Mixin(ServerPlayer.class)
public class SpectatorCameraFixMixin {

    @Shadow
    private Entity camera;

    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void gww$fixSpectatorCamera(Entity entityToSpectate, CallbackInfo ci) {
        ci.cancel();
        ServerPlayer self = (ServerPlayer) (Object) this;
        Entity oldCamera = self.getCamera();
        Entity newCamera = entityToSpectate == null ? self : entityToSpectate;
        if (newCamera instanceof MultiPart<?> part) {
            newCamera = part.getParent();
        }
        this.camera = newCamera;
        if (oldCamera != newCamera) {
            if (newCamera.level() instanceof ServerLevel serverLevel) {
                self.teleportTo(serverLevel, newCamera.getX(), newCamera.getY(), newCamera.getZ(), Set.of(), self.getYRot(), self.getXRot());
            }
            if (entityToSpectate != null) {
                self.serverLevel().getChunkSource().move(self);
            }
            self.connection.send(new ClientboundSetCameraPacket(newCamera));
            self.connection.resetPosition();
        }
    }
}
