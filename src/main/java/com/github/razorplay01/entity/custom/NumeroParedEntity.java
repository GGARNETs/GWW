package com.github.razorplay01.entity.custom;

import com.github.razorplay01.sound.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;

/**
 * Cartel de pared con un número del 0 al 99. Es el primo numérico de
 * FigurasParedEntity (que queda tal cual): misma entidad siempre, y el número
 * elige qué textura se dibuja.
 * <p>
 * Se ajusta en creativo: clic = +1, agachado + clic = +10. Así cualquier número
 * queda a un puñado de clics y no hace falta ningún comando.
 */
public class NumeroParedEntity extends BaseEntity {
    public static final int MAX_NUMBER = 99;

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(NumeroParedEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Integer> NUMBER = SynchedEntityData.defineId(
            NumeroParedEntity.class, EntityDataSerializers.INT);

    public NumeroParedEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(NUMBER, 0);
        builder.define(DATA_FACING, Direction.NORTH);
    }

    public int getNumber() {
        return this.entityData.get(NUMBER);
    }

    public void setNumber(int number) {
        if (number < 0 || number > MAX_NUMBER) {
            number = 0;
        }
        this.entityData.set(NUMBER, number);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Number", getNumber());
        tag.putString("Facing", getFacing().getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setNumber(tag.getInt("Number"));
        if (tag.contains("Facing")) {
            Direction dir = Direction.byName(tag.getString("Facing"));
            if (dir != null) {
                setFacing(dir);
            }
        }
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (!player.level().isClientSide && player.isCreative()) {
            int step = player.isShiftKeyDown() ? 10 : 1;
            setNumber((getNumber() + step) % (MAX_NUMBER + 1));
            ModSounds.playAt(this, ModSounds.BUTTON_PRESS, 0.5F, step == 10 ? 0.9F : 1.1F);
        }
    }

    @Override
    protected @NotNull AABB makeBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        double height = 2;
        double width = 2;
        double depth = 0.2;

        double hw = width / 2.0;
        double hd = depth / 2.0;

        if (getFacing().getAxis() == Direction.Axis.Z) {
            return new AABB(x - hw, y, z - hd, x + hw, y + height, z + hd);
        } else {
            return new AABB(x - hd, y, z - hw, x + hd, y + height, z + hw);
        }
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
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_FACING.equals(key)) {
            this.refreshDimensions();
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
}
