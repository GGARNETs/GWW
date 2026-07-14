package com.github.razorplay01.entity.custom;

import com.github.razorplay01.entity.BaseInteractiveEntity;
import com.github.razorplay01.entity.custom.util.EscapeRoomPersistable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PalancaEntity extends BaseInteractiveEntity implements EscapeRoomPersistable {

    private static final EntityDataAccessor<Float> INITIAL_X =
            SynchedEntityData.defineId(PalancaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INITIAL_Y =
            SynchedEntityData.defineId(PalancaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> INITIAL_Z =
            SynchedEntityData.defineId(PalancaEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Boolean> HAS_BEEN_MOVED =
            SynchedEntityData.defineId(PalancaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PUZZLE_SOLVED =
            SynchedEntityData.defineId(PalancaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double POSITION_TOLERANCE = 0.5;

    private int checkCooldown = 0;
    private static final int CHECK_INTERVAL = 10;

    public PalancaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
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
        builder.define(INITIAL_X, 0.0F);
        builder.define(INITIAL_Y, 0.0F);
        builder.define(INITIAL_Z, 0.0F);
        builder.define(HAS_BEEN_MOVED, false);
        builder.define(PUZZLE_SOLVED, false);
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

    public Vec3 getInitialPosition() {
        return new Vec3(
                this.entityData.get(INITIAL_X),
                this.entityData.get(INITIAL_Y),
                this.entityData.get(INITIAL_Z)
        );
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
        return distance <= POSITION_TOLERANCE;
    }

    public double getDistanceToInitialPosition() {
        return this.position().distanceTo(getInitialPosition());
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (isBound() && !hasBeenMoved()) {
                setHasBeenMoved(true);
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

            if (!isBound()) {
                handleGravityAndMovement();
            }
        }
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
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
    }

    protected void onPuzzleSolved() {
    }

    protected void onPuzzleUnsolved() {
    }

    public void resetPuzzleState() {
        this.entityData.set(HAS_BEEN_MOVED, false);
        this.entityData.set(PUZZLE_SOLVED, false);
    }

    @Override
    public void handleNormalInteract(Player player) {
        super.handleNormalInteract(player);

        if (!this.level().isClientSide) {
            double distance = getDistanceToInitialPosition();

            player.sendSystemMessage(Component.literal(
                    String.format("§eDistancia: §f%.2f §e/ §f%.2f", distance, POSITION_TOLERANCE)
            ));

            if (isPuzzleSolved()) {
                player.sendSystemMessage(Component.literal("§a✓ Palanca correctamente colocada"));
            } else if (hasBeenMoved()) {
                player.sendSystemMessage(Component.literal("§cPalanca sin resolver"));
            } else {
                player.sendSystemMessage(Component.literal("§7Mueve la palanca para activar el puzzle"));
            }
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

    @Override
    protected boolean canBound() {
        return true;
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("InitialX", this.entityData.get(INITIAL_X));
        tag.putFloat("InitialY", this.entityData.get(INITIAL_Y));
        tag.putFloat("InitialZ", this.entityData.get(INITIAL_Z));
        tag.putBoolean("HasBeenMoved", this.entityData.get(HAS_BEEN_MOVED));
        tag.putBoolean("PuzzleSolved", this.entityData.get(PUZZLE_SOLVED));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(INITIAL_X, tag.getFloat("InitialX"));
        this.entityData.set(INITIAL_Y, tag.getFloat("InitialY"));
        this.entityData.set(INITIAL_Z, tag.getFloat("InitialZ"));
        this.entityData.set(HAS_BEEN_MOVED, tag.getBoolean("HasBeenMoved"));
        this.entityData.set(PUZZLE_SOLVED, tag.getBoolean("PuzzleSolved"));
    }

    @Override
    public void saveEscapeRoomData(CompoundTag tag, Vec3 centerPos) {
        Vec3 initialPos = getInitialPosition();
        Vec3 relInitialPos = initialPos.subtract(centerPos);
        tag.putDouble("RelInitialX", relInitialPos.x);
        tag.putDouble("RelInitialY", relInitialPos.y);
        tag.putDouble("RelInitialZ", relInitialPos.z);
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
    }
}
