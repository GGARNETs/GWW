package com.github.razorplay01.entity.custom;

import com.github.darkpred.morehitboxes.api.EntityHitboxData;
import com.github.darkpred.morehitboxes.api.EntityHitboxDataFactory;
import com.github.darkpred.morehitboxes.api.GeckoLibMultiPartEntity;
import com.github.darkpred.morehitboxes.api.MultiPart;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CannonBulletEntity extends PathfinderMob implements GeoEntity, GeckoLibMultiPartEntity<CannonBulletEntity> {
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenPlay("idle");
    private final EntityHitboxData<CannonBulletEntity> hitboxData = EntityHitboxDataFactory.create(this);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public CannonBulletEntity(EntityType<? extends CannonBulletEntity> type, Level level) {
        super(type, level);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    public EntityHitboxData<CannonBulletEntity> getEntityHitboxData() {
        return hitboxData;
    }

    @Override
    public boolean partHurt(MultiPart<CannonBulletEntity> multiPart, @NotNull DamageSource damageSource, float v) {
        return false;
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 0, this::animController));
    }

    protected <E extends CannonBulletEntity> PlayState animController(final AnimationState<E> state) {
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
