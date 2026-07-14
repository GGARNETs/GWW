package com.github.razorplay01.entity.custom;

import com.github.razorplay01.entity.custom.util.Util;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;

public class RejaDuctoEntity extends BaseEntity {

    private static final EntityDataAccessor<Boolean> IS_OPEN = SynchedEntityData.defineId(
            RejaDuctoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(RejaDuctoEntity.class, EntityDataSerializers.DIRECTION);

    private static final RawAnimation ANIMATION_IDLE = RawAnimation.begin().thenLoop("animation.idle");
    private static final RawAnimation ANIMATION_OPEN = RawAnimation.begin().thenPlayAndHold("animation.open");

    private final List<Vec3> linkedPowerPanels = new ArrayList<>();

    public RejaDuctoEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_OPEN, false);
        builder.define(DATA_FACING, Direction.NORTH);
    }

    public boolean isOpen() {
        return this.entityData.get(IS_OPEN);
    }

    public void setOpen(boolean open) {
        this.entityData.set(IS_OPEN, open);
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


    public void linkPowerPanel(PanelEnergiaEntity panel, Vec3 roomCenter) {
        if (panel == null || roomCenter == null) return;
        Vec3 relativePos = panel.position().subtract(roomCenter);
        if (!linkedPowerPanels.contains(relativePos)) {
            linkedPowerPanels.add(relativePos);
        }
    }

    public void unlinkAllPowerPanels() {
        linkedPowerPanels.clear();
    }

    /** Margen al resolver un enlace guardado a la entidad real que hay en esa posición. */
    private static final double LINK_TOLERANCE_SQ = 2.5;

    public boolean isPowerPanelActive() {
        if (linkedPowerPanels.isEmpty()) return true;

        Vec3 rejaPos = this.position();

        for (Vec3 relPos : linkedPowerPanels) {
            Vec3 absolutePos = rejaPos.add(relPos);
            List<PanelEnergiaEntity> panels = this.level().getEntitiesOfClass(PanelEnergiaEntity.class,
                    AABB.ofSize(absolutePos, 5, 5, 5),
                    p -> p.position().distanceToSqr(absolutePos) < LINK_TOLERANCE_SQ);

            if (!panels.isEmpty() && panels.get(0).isActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * True si esta reja está enlazada a ese panel concreto. El enlace lo guarda la
     * reja (como desplazamiento hasta el panel), así que es ella quien puede
     * responder a la pregunta; el panel no tiene referencia de vuelta.
     */
    public boolean isLinkedTo(PanelEnergiaEntity panel) {
        Vec3 rejaPos = this.position();
        for (Vec3 relPos : linkedPowerPanels) {
            if (rejaPos.add(relPos).distanceToSqr(panel.position()) < LINK_TOLERANCE_SQ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Método llamado automáticamente cuando el panel se activa
     */
    public void tryOpenAutomatically() {
        if (!isOpen() && isPowerPanelActive()) {
            setOpen(true);
        }
    }

    @Override
    public void handleNormalInteract(Player player) {
      // []
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsOpen", isOpen());
        tag.putString("Facing", getFacing().getSerializedName());
        Util.saveLinkedList(tag, "LinkedPowerPanels", linkedPowerPanels);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOpen(tag.getBoolean("IsOpen"));
        if (tag.contains("Facing")) {
            Direction dir = Direction.byName(tag.getString("Facing"));
            if (dir != null) setFacing(dir);
        }
        linkedPowerPanels.clear();
        linkedPowerPanels.addAll(Util.loadLinkedList(tag, "LinkedPowerPanels"));
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "reja_controller",
                0,
                state -> isOpen() ? state.setAndContinue(ANIMATION_OPEN) : state.setAndContinue(ANIMATION_IDLE)
        ));
    }

    public List<Vec3> getLinkedPowerPanels() {
        return linkedPowerPanels;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isOpen();
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return !isOpen() && super.canCollideWith(entity);
    }

    @Override
    public void push(Entity entity) {
        if (!isOpen()) super.push(entity);
    }

    @Override
    protected void pushEntities() {
        if (!isOpen()) super.pushEntities();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (IS_OPEN.equals(key) || DATA_FACING.equals(key)) {
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
        double depth = 0.5;
        double hw = width / 2.0;
        double hd = depth / 2.0;

        if (getFacing().getAxis() == Direction.Axis.Z) {
            return new AABB(x - hw, y, z - hd, x + hw, y + height, z + hd);
        } else {
            return new AABB(x - hd, y, z - hw, x + hd, y + height, z + hw);
        }
    }
}