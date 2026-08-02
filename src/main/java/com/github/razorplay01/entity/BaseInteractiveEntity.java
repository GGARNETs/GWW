package com.github.razorplay01.entity;

import com.github.razorplay01.entity.custom.BaseEntity;
import com.github.razorplay01.sound.ModSounds;
import com.github.razorplay01.system.NoiseDetectionSystem;
import lombok.Getter;
import net.minecraft.sounds.SoundEvent;
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
    private static final double MAX_MOVE_PER_TICK = 1.5;
    /** Por debajo de esto el objeto solo se está asentando: no suena. */
    private static final double MIN_LANDING_SPEED = 0.12;

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
                    boolean moved = updatePositionNearPlayer(player);
                    if (moved && (this.horizontalCollision || this.verticalCollision)) {
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

    private boolean updatePositionNearPlayer(Player player) {
        Vec3 look = player.getLookAngle().normalize();

        // Posición objetivo ideal
        double targetX = player.getX() + look.x * DISTANCE_FROM_PLAYER;
        double targetY = player.getY() + player.getEyeHeight() * 0.6 + look.y * DISTANCE_FROM_PLAYER * 0.75;
        double targetZ = player.getZ() + look.z * DISTANCE_FROM_PLAYER;

        Vec3 targetPos = new Vec3(targetX, targetY, targetZ);
        Vec3 currentPos = this.position();

        // Vector de movimiento hacia la posición objetivo
        Vec3 movement = targetPos.subtract(currentPos);

        // Con el jugador quieto la entidad ya está en su sitio: sin mover no hay
        // colisiones que calcular ni paquetes de posición que mandar
        if (movement.lengthSqr() < 1.0E-4) {
            updateRotationTowardsPlayer(player);
            return false;
        }

        // Acotado por tick: un objetivo lejano (jugador teletransportado o entidad
        // atascada en una pared) barría colisiones sobre todo el trayecto de golpe
        if (movement.lengthSqr() > MAX_MOVE_PER_TICK * MAX_MOVE_PER_TICK) {
            movement = movement.normalize().scale(MAX_MOVE_PER_TICK);
        }

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
        return true;
    }

    /**
     * Gravedad manual para las entidades que caen al soltarse (palanca, manivela,
     * escalera). NO_GRAVITY es un dato sincronizado y debe quedarse en true: alternarlo
     * cada tick manda un paquete de metadatos por entidad y por tick a cada jugador
     * cercano. En reposo sobre el suelo solo sondea el move cada 10 ticks para detectar
     * si le quitan el bloque de debajo.
     */
    protected void handleGravityAndMovement() {
        Vec3 motion = this.getDeltaMovement();
        boolean groundedBefore = this.onGround();

        if (!this.onGround()) {
            motion = motion.add(0, -0.08, 0);
            motion = motion.multiply(0.98, 0.98, 0.98);
        } else {
            motion = new Vec3(0, motion.y, 0);
        }

        this.setDeltaMovement(motion);

        if (this.onGround() && motion.lengthSqr() < 1.0E-6) {
            // En reposo no se llama a move(): incluso como sondeo marcó 1,5 ms/tick en
            // el spark. Para detectar que le quitaron el suelo alcanza mirar el bloque
            // de abajo cada segundo; si falta, se reactiva la física de verdad.
            if (this.tickCount % 20 == 0
                    && this.level().getBlockState(this.blockPosition().below()).isAir()) {
                this.setOnGround(false);
            }
            return;
        }
        this.move(MoverType.SELF, this.getDeltaMovement());

        // El objeto acaba de tocar el suelo: suena una sola vez y con el peso de la
        // caída, así soltar la palanca a los pies no suena igual que tirarla desde
        // lo alto de la escalera.
        if (!groundedBefore && this.onGround()) {
            double fallSpeed = Math.abs(motion.y);
            if (fallSpeed > MIN_LANDING_SPEED) {
                float volume = (float) Math.min(1.0, 0.35 + fallSpeed * 1.2);
                ModSounds.playAt(this, getLandSound(), volume, randomPitch());
            }
        }
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
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
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
                ModSounds.playAt(this, getReleaseSound(), 0.7F, randomPitch());
                NoiseDetectionSystem.addNoise(player, 0.3f);
            } else {
                ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.5F, 1.0F);
                player.sendSystemMessage(Component.literal("§c¡Esta entidad está vinculada a otro jugador!"));
            }
        } else {
            bind(player);
            onBound(player);
            ModSounds.playAt(this, getGrabSound(), 0.7F, randomPitch());
            NoiseDetectionSystem.addNoise(player, 0.3f);
        }
    }

    /**
     * Sonidos de manipulación. Por defecto suenan a metal, que es lo que son la
     * palanca, la manivela y la escalera; los cuadros los sobrescriben con madera.
     */
    protected SoundEvent getGrabSound() {
        return ModSounds.GRAB_METAL;
    }

    protected SoundEvent getReleaseSound() {
        return ModSounds.GRAB_METAL;
    }

    /** Golpe contra el suelo al soltar el objeto en el aire. */
    protected SoundEvent getLandSound() {
        return ModSounds.DROP_METAL;
    }

    /** Variación leve para que agarrar lo mismo diez veces no suene idéntico. */
    protected float randomPitch() {
        return 0.94F + this.random.nextFloat() * 0.12F;
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