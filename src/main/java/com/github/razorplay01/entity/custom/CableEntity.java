package com.github.razorplay01.entity.custom;

import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.sound.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;

public class CableEntity extends BaseEntity {
    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(CableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(CableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CORRECT_STATE =
            SynchedEntityData.defineId(CableEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_ACTIVE =
            SynchedEntityData.defineId(CableEntity.class, EntityDataSerializers.BOOLEAN);

    public CableEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (isActive()) {
            int nextState = (this.getState() + 1) % 4;
            this.setState(nextState);
            ModSounds.playAt(this, ModSounds.CABLE_ROTATE, 0.7F, 1.0F);
        } else {
            if (getCableType() == 0 && player.getInventory().contains(new ItemStack(ModItems.CABLE_LINEAL))) {
                consumeRequiredItem(player, ModItems.CABLE_LINEAL);
                setActive(true);
                ModSounds.playAt(this, ModSounds.CABLE_PLUG, 0.9F, 1.0F);
            }
            if (getCableType() == 1 && player.getInventory().contains(new ItemStack(ModItems.CABLE_CURVO))) {
                consumeRequiredItem(player, ModItems.CABLE_CURVO);
                setActive(true);
                ModSounds.playAt(this, ModSounds.CABLE_PLUG, 0.9F, 1.0F);
            }
        }
    }

    private void consumeRequiredItem(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, 0);
        builder.define(DATA_STATE, 0);
        builder.define(CORRECT_STATE, 0);
        builder.define(IS_ACTIVE, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Type", getCableType());
        tag.putInt("State", getState());
        tag.putInt("CorrectState", getCorrectState());
        tag.putBoolean("IsActive", isActive());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCableType(tag.getInt("Type"));
        setState(tag.getInt("State"));
        setCorrectState(tag.getInt("CorrectState"));
        setActive(tag.getBoolean("IsActive"));
    }

    public int getCableType() {
        return this.entityData.get(DATA_TYPE);
    }

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    public int getCorrectState() {
        return this.entityData.get(CORRECT_STATE);
    }

    public boolean isCorrect() {
        return this.getState() == this.getCorrectState();
    }

    public void setCableType(int type) {
        this.entityData.set(DATA_TYPE, type);
    }

    public boolean isActive() {
        return this.entityData.get(IS_ACTIVE);
    }

    public void setState(int state) {
        this.entityData.set(DATA_STATE, Math.max(0, Math.min(3, state)));
    }

    public void setCorrectState(int state) {
        this.entityData.set(CORRECT_STATE, Math.max(0, Math.min(3, state)));
    }

    public void setActive(boolean active) {
        this.entityData.set(IS_ACTIVE, active);
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
