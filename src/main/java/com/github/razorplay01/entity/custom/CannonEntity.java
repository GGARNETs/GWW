package com.github.razorplay01.entity.custom;

import com.github.razorplay01.sound.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CannonEntity extends PathfinderMob implements GeoEntity {
    protected static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("shoot");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenPlay("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public CannonEntity(EntityType<? extends CannonEntity> type, Level level) {
        super(type, level);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    /**
     * Los cañones solo existen mientras su partida los mueve: no se guardan en
     * el disco. Sin esto, un cierre en seco a mitad de partida los dejaba
     * spawneados para siempre al reiniciar.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shoot_controller", state -> PlayState.STOP)
                .triggerableAnim("shoot", SHOOT));
        controllers.add(new AnimationController<>(this, "idle_controller", 0, this::animController));
    }

    protected <E extends CannonEntity> PlayState animController(final AnimationState<E> state) {
        return state.setAndContinue(IDLE_ANIM);
    }

    public void triggerShootAnim(Level level) {
        if (level instanceof ServerLevel) {
            triggerAnim("shoot_controller", "shoot");
            ModSounds.playAt(this, ModSounds.CANNON_FIRE, 1.0F,
                    0.95F + this.random.nextFloat() * 0.1F);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
