package com.github.razorplay01.entity.custom;

import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.system.NoiseDetectionSystem;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;

public class PanelEnergiaEntity extends BaseEntity {

    private static final EntityDataAccessor<Boolean> IS_OPEN = SynchedEntityData.defineId(
            PanelEnergiaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_ACTIVE = SynchedEntityData.defineId(
            PanelEnergiaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(PanelEnergiaEntity.class, EntityDataSerializers.DIRECTION);

    private static final RawAnimation ANIMATION_IDLE = RawAnimation.begin().thenLoop("close");
    private static final RawAnimation ANIMATION_OPEN = RawAnimation.begin().thenPlayAndHold("open");

    public PanelEnergiaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_OPEN, false);
        builder.define(IS_ACTIVE, false);
        builder.define(DATA_FACING, Direction.NORTH);
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

    public boolean isOpen() {
        return this.entityData.get(IS_OPEN);
    }

    public boolean isActive() {
        return this.entityData.get(IS_ACTIVE);
    }

    public void setOpen(boolean open) {
        this.entityData.set(IS_OPEN, open);
    }

    public void setActive(boolean active) {
        boolean wasActive = isActive();
        this.entityData.set(IS_ACTIVE, active);

        if (!wasActive && active) {
            notifyLinkedRejas();
        }
    }

    private void notifyLinkedRejas() {
        if (this.level().isClientSide) return;

        List<RejaDuctoEntity> rejas = this.level().getEntitiesOfClass(RejaDuctoEntity.class,
                AABB.ofSize(this.position(), 10, 10, 10), r -> true);

        for (RejaDuctoEntity reja : rejas) {
            if (reja.isPowerPanelActive()) {
                reja.tryOpenAutomatically();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsOpen", isOpen());
        tag.putBoolean("IsActive", isActive());
        tag.putString("Facing", getFacing().getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOpen(tag.getBoolean("IsOpen"));
        setActive(tag.getBoolean("IsActive"));
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
        controllers.add(new AnimationController<>(
                this,
                "puerta_controller",
                0,
                state -> isOpen()
                        ? state.setAndContinue(ANIMATION_OPEN)
                        : state.setAndContinue(ANIMATION_IDLE)
        ));
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (!player.level().isClientSide) {
            if (!isOpen()) {
                if (hasRequiredItem(player, new ItemStack(ModItems.GANZUA))) {
                    consumeRequiredItem(player, new ItemStack(ModItems.GANZUA));
                    setOpen(true);
                    player.sendSystemMessage(Component.literal("§a¡Has abierto el panel eléctrico!"));
                } else {
                    player.sendSystemMessage(Component.literal("§cNecesitas un §bobjeto §cpara abrir el panel eléctrico"));
                }
            } else {
                if (!isActive()) {
                    if (hasRequiredItem(player, new ItemStack(ModItems.ALICATE_CORTACABLES))) {
                        consumeRequiredItem(player, new ItemStack(ModItems.ALICATE_CORTACABLES));
                        setActive(true);
                        NoiseDetectionSystem.addNoise(player, 0.5f);
                        player.sendSystemMessage(Component.literal("§a¡Has cortado los cables!"));
                    } else {
                        player.sendSystemMessage(Component.literal("§cNecesitas un §bobjeto §cpara interactuar con el panel eléctrico"));
                    }
                }
            }
        }
    }

    private boolean hasRequiredItem(Player player, ItemStack itemStack) {
        return player.getInventory().contains(itemStack);
    }

    private void consumeRequiredItem(Player player, ItemStack itemStack) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(itemStack.getItem())) {
                stack.shrink(1);
                return;
            }
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isOpen();           // Permite pasar cuando está abierta
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return !isOpen() && super.canCollideWith(entity);
    }

    @Override
    public void push(Entity entity) {
        if (!isOpen()) {
            super.push(entity);
        }
    }

    @Override
    protected void pushEntities() {
        if (!isOpen()) {
            super.pushEntities();
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (IS_OPEN.equals(key) || DATA_FACING.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected @NotNull AABB makeBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        double height = 1.2;
        double width = 1.0;
        double depth = 0.4;

        double hw = width / 2.0;
        double hd = depth / 2.0;

        if (getFacing().getAxis() == Direction.Axis.Z) {
            return new AABB(x - hw, y, z - hd, x + hw, y + height, z + hd);
        } else {
            return new AABB(x - hd, y, z - hw, x + hd, y + height, z + hw);
        }
    }
}