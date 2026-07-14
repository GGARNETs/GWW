package com.github.razorplay01.entity.custom;

import com.github.razorplay01.entity.BaseInteractiveEntity;
import com.github.razorplay01.entity.custom.util.ValvulaType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ManivelaEntity extends BaseInteractiveEntity {

    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(ManivelaEntity.class, EntityDataSerializers.INT);

    public ManivelaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, ValvulaType.NARANJA.getId());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Type", getValType().getId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setType(ValvulaType.byId(tag.getInt("Type")));
    }

    public ValvulaType getValType() {
        return ValvulaType.byId(this.entityData.get(DATA_TYPE));
    }

    public void setType(ValvulaType type) {
        this.entityData.set(DATA_TYPE, type.getId());
    }

    @Override
    public void handleNormalInteract(Player player) {
        // Las manivelas no se pueden interactuar directamente
    }

    @Override
    protected boolean canBound() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            this.level().getEntitiesOfClass(ValvulaEntity.class, this.getBoundingBox().inflate(0.5D))
                    .forEach(valvula -> {
                        if (valvula.getValType() == this.getValType() && !valvula.hasManivela()) {
                            valvula.attachManivela(this.getValType());
                            if (this.getCachedBoundPlayer() != null) {
                                onUnbound(this.getCachedBoundPlayer());
                            }
                            this.discard();
                        }
                    });
        }
        if (!this.level().isClientSide && !isBound()) {
            handleGravityAndMovement();
        }
    }

    @Override
    protected void onBound(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 999999, 2, false, false));
    }

    @Override
    protected void onUnbound(Player player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private void handleGravityAndMovement() {
        this.setNoGravity(false);

        Vec3 motion = this.getDeltaMovement();

        if (!this.onGround()) {
            motion = motion.add(0, -0.08, 0);
            motion = motion.multiply(0.98, 0.98, 0.98);
        } else {
            motion = new Vec3(0, motion.y, 0);
        }

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}