package com.github.razorplay01.mixin;

import com.github.razorplay01.arena.ArenaManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hace indestructibles los marcos (item frames) que estén dentro de una arena: no se
 * pueden romper ni eliminar, solo se les puede quitar el item que sostienen.
 */
@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void gww$protectInArena(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemFrame self = (ItemFrame) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        if (ArenaManager.getArenaAt(self.position()) == null) {
            return;
        }

        // El daño solo se deja pasar cuando quitaría el item (marco con item y sin
        // explosión): esa es la rama de vanilla que suelta el item y conserva el marco.
        // Cualquier otro caso destruiría el marco, así que se cancela.
        boolean wouldDestroyFrame = source.is(DamageTypeTags.IS_EXPLOSION) || self.getItem().isEmpty();
        if (wouldDestroyFrame) {
            cir.setReturnValue(false);
        }
    }
}
