package com.github.razorplay01.mixin;

import com.github.darkpred.morehitboxes.internal.HitboxDataLoader;
import com.github.razorplay01.entity.custom.util.SelfHealingHitboxes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Repara las entidades multipart en el momento exacto en que la data de morehitboxes
 * aterriza en el cliente, en vez de sondear cada tick.
 * <p>
 * {@code replaceData} es el {@code client.execute(...)} que corre al recibir el paquete
 * {@code morehitboxes:sync_hitbox_data}. Como {@code client.execute} es una cola FIFO en
 * el render thread, toda entidad encolada ANTES de este task se construyó vacía (la data
 * aún no estaba) y la reparamos aquí; toda entidad encolada DESPUÉS ya lee la data buena
 * en su constructor. No queda hueco. También cubre {@code /reload} (re-sync idempotente).
 */
@Mixin(HitboxDataLoader.class)
public class HitboxDataLoaderMixin {

    @Inject(method = "replaceData", at = @At("TAIL"), remap = false)
    private void gww$healLoadedEntities(Map<?, ?> data, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof SelfHealingHitboxes healing) {
                healing.rebuildHitboxesIfMissing();
            }
        }
    }
}
