package com.github.razorplay01.entity.custom;

import com.github.razorplay01.entity.BaseInteractiveEntity;
import com.github.razorplay01.entity.custom.util.EscapeRoomPersistable;
import com.github.razorplay01.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public abstract class BaseCuadroEntity extends BaseInteractiveEntity implements EscapeRoomPersistable {

    private static final EntityDataAccessor<Float> INITIAL_X =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INITIAL_Y =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INITIAL_Z =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INITIAL_YAW =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INITIAL_PITCH =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAS_BEEN_MOVED =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PUZZLE_SOLVED =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(BaseCuadroEntity.class, EntityDataSerializers.DIRECTION);

    private static final double POSITION_TOLERANCE = 0.5;
    private static final float ROTATION_TOLERANCE = 15.0F;

    protected boolean itemSpawned = false;

    private int checkCooldown = 0;
    private static final int CHECK_INTERVAL = 10;

    public BaseCuadroEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(INITIAL_X, 0.0F);
        builder.define(INITIAL_Y, 0.0F);
        builder.define(INITIAL_Z, 0.0F);
        builder.define(INITIAL_YAW, 0.0F);
        builder.define(INITIAL_PITCH, 0.0F);
        builder.define(HAS_BEEN_MOVED, false);
        builder.define(PUZZLE_SOLVED, false);
        builder.define(DATA_FACING, Direction.NORTH);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (isBound() && !hasBeenMoved() && !itemSpawned) {
                setHasBeenMoved(true);
                spawnRewardItem();
                itemSpawned = true;
            }

            if (!isBound() && hasBeenMoved()) {
                checkCooldown--;
                if (checkCooldown <= 0) {
                    checkCooldown = CHECK_INTERVAL;

                    boolean isCorrect = checkIfCorrectlyPlaced();
                    boolean wasSolved = isPuzzleSolved();

                    if (isCorrect != wasSolved) {
                        setPuzzleSolved(isCorrect);

                        if (isCorrect) {
                            onPuzzleSolved();
                        } else {
                            onPuzzleUnsolved();
                        }
                    }
                }
            }
        }
    }

    public void setInitialPosition(Vec3 position) {
        this.entityData.set(INITIAL_X, (float) position.x);
        this.entityData.set(INITIAL_Y, (float) position.y);
        this.entityData.set(INITIAL_Z, (float) position.z);
    }

    public void setInitialPosition(double x, double y, double z) {
        this.entityData.set(INITIAL_X, (float) x);
        this.entityData.set(INITIAL_Y, (float) y);
        this.entityData.set(INITIAL_Z, (float) z);
    }

    public void setInitialRotation(float yaw, float pitch) {
        this.entityData.set(INITIAL_YAW, yaw);
        this.entityData.set(INITIAL_PITCH, pitch);
    }

    public Vec3 getInitialPosition() {
        return new Vec3(
                this.entityData.get(INITIAL_X),
                this.entityData.get(INITIAL_Y),
                this.entityData.get(INITIAL_Z)
        );
    }

    public float getInitialYaw() {
        return this.entityData.get(INITIAL_YAW);
    }

    public float getInitialPitch() {
        return this.entityData.get(INITIAL_PITCH);
    }

    public boolean hasBeenMoved() {
        return this.entityData.get(HAS_BEEN_MOVED);
    }

    protected void setHasBeenMoved(boolean moved) {
        this.entityData.set(HAS_BEEN_MOVED, moved);
    }

    public boolean isPuzzleSolved() {
        return this.entityData.get(PUZZLE_SOLVED);
    }

    protected void setPuzzleSolved(boolean solved) {
        this.entityData.set(PUZZLE_SOLVED, solved);
    }

    public boolean checkIfCorrectlyPlaced() {
        Vec3 initialPos = getInitialPosition();
        Vec3 currentPos = this.position();

        double distance = currentPos.distanceTo(initialPos);
        if (distance > POSITION_TOLERANCE) {
            return false;
        }

        float yawDifference = Math.abs(normalizeAngle(this.getYRot() - getInitialYaw()));
        if (yawDifference > ROTATION_TOLERANCE) {
            return false;
        }

        float pitchDifference = Math.abs(normalizeAngle(this.getXRot() - getInitialPitch()));
        if (pitchDifference > ROTATION_TOLERANCE) {
            return false;
        }

        return true;
    }

    protected float normalizeAngle(float angle) {
        while (angle > 180.0F) angle -= 360.0F;
        while (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    public double getDistanceToInitialPosition() {
        return this.position().distanceTo(getInitialPosition());
    }

    public float getYawDifferenceFromInitial() {
        return normalizeAngle(this.getYRot() - getInitialYaw());
    }

    protected void spawnRewardItem() {
        if (!this.level().isClientSide) {
            ItemStack reward = new ItemStack(ModItems.COLGANTE_CUADROS, 1);
            if (reward != null && !reward.isEmpty()) {
                Vec3 spawnPos = this.position().add(0, 3, 0);
                ItemEntity itemEntity = new ItemEntity(
                        this.level(),
                        spawnPos.x,
                        spawnPos.y,
                        spawnPos.z,
                        reward
                );
                itemEntity.setDefaultPickUpDelay();
                this.level().addFreshEntity(itemEntity);
            }
        }
    }

    protected void onPuzzleSolved() {
    }

    protected void onPuzzleUnsolved() {
    }

    public void resetPuzzleState() {
        this.entityData.set(HAS_BEEN_MOVED, false);
        this.entityData.set(PUZZLE_SOLVED, false);
        this.itemSpawned = false;
    }

    /**
     * Devuelve el cuadro a su posición y rotación iniciales sin tocar el progreso: si
     * ya soltó su colgante, sigue soltado. Es lo que hace el Ublabla al ordenar la sala.
     */
    public void snapToInitial() {
        if (this.level().isClientSide) {
            return;
        }
        if (isBound()) {
            unbind();
        }
        Vec3 initial = getInitialPosition();
        this.moveTo(initial.x, initial.y, initial.z, getInitialYaw(), getInitialPitch());
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("InitialX", this.entityData.get(INITIAL_X));
        tag.putFloat("InitialY", this.entityData.get(INITIAL_Y));
        tag.putFloat("InitialZ", this.entityData.get(INITIAL_Z));
        tag.putFloat("InitialYaw", this.entityData.get(INITIAL_YAW));
        tag.putFloat("InitialPitch", this.entityData.get(INITIAL_PITCH));
        tag.putBoolean("HasBeenMoved", this.entityData.get(HAS_BEEN_MOVED));
        tag.putBoolean("PuzzleSolved", this.entityData.get(PUZZLE_SOLVED));
        tag.putBoolean("ItemSpawned", this.itemSpawned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(INITIAL_X, tag.getFloat("InitialX"));
        this.entityData.set(INITIAL_Y, tag.getFloat("InitialY"));
        this.entityData.set(INITIAL_Z, tag.getFloat("InitialZ"));
        this.entityData.set(INITIAL_YAW, tag.getFloat("InitialYaw"));
        this.entityData.set(INITIAL_PITCH, tag.getFloat("InitialPitch"));
        this.entityData.set(HAS_BEEN_MOVED, tag.getBoolean("HasBeenMoved"));
        this.entityData.set(PUZZLE_SOLVED, tag.getBoolean("PuzzleSolved"));
        this.itemSpawned = tag.getBoolean("ItemSpawned");
    }

    @Override
    public void handleNormalInteract(Player player) {
        super.handleNormalInteract(player);

        if (!this.level().isClientSide) {
            double distance = getDistanceToInitialPosition();
            float yawDiff = Math.abs(getYawDifferenceFromInitial());

            player.sendSystemMessage(Component.literal(
                    String.format("§eDistancia: §f%.2f §e/ §f%.2f", distance, POSITION_TOLERANCE)
            ));
            player.sendSystemMessage(Component.literal(
                    String.format("§eRotación: §f%.1f° §e/ §f%.1f°", yawDiff, ROTATION_TOLERANCE)
            ));

            if (isPuzzleSolved()) {
                player.sendSystemMessage(Component.literal("§a✓ Cuadro en su lugar"));
            } else if (hasBeenMoved()) {
                player.sendSystemMessage(Component.literal("§cCuadro desordenado"));
            } else {
                player.sendSystemMessage(Component.literal("§7Mueve el cuadro para activar el puzzle"));
            }
        }
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

    public Direction getFacing() {
        return this.entityData.get(DATA_FACING);
    }

    public void setFacing(Direction direction) {
        if (this.entityData.get(DATA_FACING) != direction) {
            this.entityData.set(DATA_FACING, direction);
            this.refreshDimensions();
        }
    }

    @Override
    public void setYRot(float yaw) {
        super.setYRot(yaw);
        setFacing(Direction.fromYRot(yaw));
    }

    @Override
    protected @NotNull AABB makeBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        double height = 2.0;
        double width = 2.0;
        double depth = 0.1;

        double halfWidth = width / 2.0;
        double halfDepth = depth / 2.0;

        if (getFacing().getAxis() == Direction.Axis.Z) {
            return new AABB(x - halfWidth, y, z - halfDepth,
                    x + halfWidth, y + height, z + halfDepth);
        } else {
            return new AABB(x - halfDepth, y, z - halfWidth,
                    x + halfDepth, y + height, z + halfWidth);
        }
    }

    @Override
    public void saveEscapeRoomData(CompoundTag tag, Vec3 centerPos) {
        Vec3 initialPos = getInitialPosition();
        Vec3 relInitialPos = initialPos.subtract(centerPos);
        tag.putDouble("RelInitialX", relInitialPos.x);
        tag.putDouble("RelInitialY", relInitialPos.y);
        tag.putDouble("RelInitialZ", relInitialPos.z);
        tag.putFloat("InitialYaw", getInitialYaw());
        tag.putFloat("InitialPitch", getInitialPitch());
    }

    @Override
    public void restoreEscapeRoomData(CompoundTag tag, BlockPos newCenterPos) {
        Vec3 newCenter = Vec3.atCenterOf(newCenterPos);
        if (tag.contains("RelInitialX")) {
            Vec3 relInitialPos = new Vec3(
                    tag.getDouble("RelInitialX"),
                    tag.getDouble("RelInitialY"),
                    tag.getDouble("RelInitialZ")
            );
            Vec3 absInitialPos = newCenter.add(relInitialPos);
            setInitialPosition(absInitialPos);
        }
        if (tag.contains("InitialYaw")) {
            setInitialRotation(tag.getFloat("InitialYaw"), tag.getFloat("InitialPitch"));
        }
    }
}
