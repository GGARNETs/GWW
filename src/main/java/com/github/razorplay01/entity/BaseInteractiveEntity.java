package com.github.razorplay01.entity;

import com.github.razorplay01.entity.custom.BaseEntity;
import com.github.razorplay01.system.NoiseDetectionSystem;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.UUID;

public class BaseInteractiveEntity extends BaseEntity {

    private static final EntityDataAccessor<Boolean> BOUND =
            SynchedEntityData.defineId(BaseInteractiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> LOCKED_YAW =
            SynchedEntityData.defineId(BaseInteractiveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LOCKED_PITCH =
            SynchedEntityData.defineId(BaseInteractiveEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private UUID boundPlayerUUID;
    @Getter
    @Nullable
    private Player cachedBoundPlayer;

    private static final float DISTANCE_FROM_PLAYER = 1.9F;

    public BaseInteractiveEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOUND, false);
        builder.define(LOCKED_YAW, 0.0F);
        builder.define(LOCKED_PITCH, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (isBound()) {
                Player player = getBoundPlayer();
                if (player != null && player.isAlive()) {
                    updatePositionNearPlayer(player);
                    if (this.horizontalCollision || this.verticalCollision) {
                        this.setDeltaMovement(0, 0.1, 0);
                        this.move(MoverType.SELF, this.getDeltaMovement());
                    }
                } else {
                    unbind();
                }
            } else {
                this.setDeltaMovement(0, 0, 0);
                this.setNoGravity(true);

                float lockedYaw = this.entityData.get(LOCKED_YAW);
                float lockedPitch = this.entityData.get(LOCKED_PITCH);

                this.setYRot(lockedYaw);
                this.setXRot(lockedPitch);
                this.yRotO = lockedYaw;
                this.xRotO = lockedPitch;
                this.yHeadRot = lockedYaw;
                this.yHeadRotO = lockedYaw;
            }
        } else {
            if (!isBound()) {
                float lockedYaw = this.entityData.get(LOCKED_YAW);
                float lockedPitch = this.entityData.get(LOCKED_PITCH);

                this.setYRot(lockedYaw);
                this.setXRot(lockedPitch);
                this.yRotO = lockedYaw;
                this.xRotO = lockedPitch;
                this.yHeadRot = lockedYaw;
                this.yHeadRotO = lockedYaw;
            }
        }
    }

    private void updatePositionNearPlayer(Player player) {
        Vec3 look = player.getLookAngle().normalize();

        // Posición objetivo ideal
        double targetX = player.getX() + look.x * DISTANCE_FROM_PLAYER;
        double targetY = player.getY() + player.getEyeHeight() * 0.6 + look.y * DISTANCE_FROM_PLAYER * 0.75;
        double targetZ = player.getZ() + look.z * DISTANCE_FROM_PLAYER;

        Vec3 targetPos = new Vec3(targetX, targetY, targetZ);
        Vec3 currentPos = this.position();

        // Vector de movimiento hacia la posición objetivo
        Vec3 movement = targetPos.subtract(currentPos);

        // === MOVIMIENTO CON COLISIONES ===
        this.move(MoverType.SELF, movement);

        // Si después del movimiento sigue habiendo colisión con el jugador o bloques, ajustamos
        if (this.position().distanceTo(player.position()) < 0.8) {
            // Demasiado cerca → empujamos un poco hacia afuera
            Vec3 push = this.position().subtract(player.position()).normalize().scale(0.9);
            this.move(MoverType.SELF, push);
        }

        // Actualizamos rotación para que mire al jugador
        updateRotationTowardsPlayer(player);
    }

    private void updateRotationTowardsPlayer(Player player) {
        Vec3 toPlayer = player.position().subtract(this.position()).normalize();

        if (toPlayer.lengthSqr() > 0.001) {
            float yaw = (float) (Math.atan2(toPlayer.z, toPlayer.x) * (180.0 / Math.PI)) - 90.0F;
            float pitch = (float) (-Math.asin(toPlayer.y / toPlayer.length()) * (180.0 / Math.PI));

            this.setYRot(yaw);
            this.setXRot(pitch);
            this.yRotO = yaw;
            this.xRotO = pitch;
            this.yHeadRot = yaw;
            this.yHeadRotO = yaw;
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            if (player.isShiftKeyDown()) {
                if (canBound()) {
                    handleShiftInteract(player);
                }
            } else {
                handleNormalInteract(player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    public void handleNormalInteract(Player player) {
        player.sendSystemMessage(Component.literal("§e¡Has interactuado con la entidad!"));
        player.sendSystemMessage(Component.literal("§7Shift + Click derecho para vincular/desvincular"));
    }

    protected void handleShiftInteract(Player player) {
        if (isBound()) {
            if (isPlayerBound(player)) {
                unbind();
                onUnbound(player);
                NoiseDetectionSystem.addNoise(player, 0.3f);
            } else {
                player.sendSystemMessage(Component.literal("§c¡Esta entidad está vinculada a otro jugador!"));
            }
        } else {
            bind(player);
            onBound(player);
            NoiseDetectionSystem.addNoise(player, 0.3f);
        }
    }

    public void bind(Player player) {
        this.boundPlayerUUID = player.getUUID();
        this.cachedBoundPlayer = player;
        this.entityData.set(BOUND, true);
        this.setNoGravity(true);

        this.setInvulnerable(true);
        this.noPhysics = false;
    }

    public void unbind() {
        float currentYaw = this.getYRot();
        float currentPitch = this.getXRot();

        this.boundPlayerUUID = null;
        this.cachedBoundPlayer = null;
        this.entityData.set(BOUND, false);
        this.setNoGravity(true);
        this.setDeltaMovement(0, 0, 0);

        this.entityData.set(LOCKED_YAW, currentYaw);
        this.entityData.set(LOCKED_PITCH, currentPitch);

        this.setYRot(currentYaw);
        this.setXRot(currentPitch);
        this.yRotO = currentYaw;
        this.xRotO = currentPitch;
        this.yHeadRot = currentYaw;
        this.yHeadRotO = currentYaw;
    }

    public boolean isBound() {
        return this.entityData.get(BOUND);
    }

    public boolean isPlayerBound(Player player) {
        return this.boundPlayerUUID != null && this.boundPlayerUUID.equals(player.getUUID());
    }

    @Nullable
    public Player getBoundPlayer() {
        if (this.boundPlayerUUID == null) return null;
        if (this.cachedBoundPlayer != null && this.cachedBoundPlayer.getUUID().equals(this.boundPlayerUUID)) {
            return this.cachedBoundPlayer;
        }
        this.cachedBoundPlayer = this.level().getPlayerByUUID(this.boundPlayerUUID);
        return this.cachedBoundPlayer;
    }

    protected boolean canBound() {
        return false;
    }

    protected void onBound(Player player) {
    }

    protected void onUnbound(Player player) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.boundPlayerUUID != null) {
            tag.putUUID("BoundPlayer", this.boundPlayerUUID);
        }
        tag.putFloat("LockedYaw", this.entityData.get(LOCKED_YAW));
        tag.putFloat("LockedPitch", this.entityData.get(LOCKED_PITCH));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("BoundPlayer")) {
            this.boundPlayerUUID = tag.getUUID("BoundPlayer");
            this.entityData.set(BOUND, true);
        } else {
            this.boundPlayerUUID = null;
            this.entityData.set(BOUND, false);
        }

        if (tag.contains("LockedYaw")) {
            this.entityData.set(LOCKED_YAW, tag.getFloat("LockedYaw"));
        }
        if (tag.contains("LockedPitch")) {
            this.entityData.set(LOCKED_PITCH, tag.getFloat("LockedPitch"));
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

    }
}