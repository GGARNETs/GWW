package com.github.razorplay01.entity.custom;

import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.sound.ModSounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;

public class CajaHerramientasEntity extends BaseEntity {

    private static final EntityDataAccessor<Boolean> IS_OPEN = SynchedEntityData.defineId(
            CajaHerramientasEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIMATION_IDLE = RawAnimation.begin().thenLoop("animation.idle");
    private static final RawAnimation ANIMATION_OPEN = RawAnimation.begin().thenPlayAndHold("animation.open");

    private final List<ItemStack> boxContents = new ArrayList<>();

    private boolean itemsSpawned = false;
    private int openAnimationTicks = 0;
    private static final int SPAWN_DELAY = 10;

    public CajaHerramientasEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_OPEN, false);
    }

    public boolean isOpen() {
        return this.entityData.get(IS_OPEN);
    }

    public void setOpen(boolean open) {
        this.entityData.set(IS_OPEN, open);
        if (open && !this.level().isClientSide) {
            openAnimationTicks = 0;
            ModSounds.playAt(this, ModSounds.TOOLBOX_OPEN, 1.0F, 1.0F);
        }
    }

    public void setBoxContents(List<ItemStack> contents) {
        this.boxContents.clear();
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                this.boxContents.add(stack.copy());
            }
        }
    }

    public void addBoxContent(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.boxContents.add(stack.copy());
        }
    }

    public List<ItemStack> getBoxContents() {
        return new ArrayList<>(boxContents);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        if (isOpen() && !itemsSpawned) {
            openAnimationTicks++;
            if (openAnimationTicks >= SPAWN_DELAY) {
                spawnBoxContents();
                itemsSpawned = true;
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "caja_controller",
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
                if (hasRequiredItem(player)) {
                    consumeRequiredItem(player);
                    ModSounds.playAt(this, ModSounds.LOCK_PICK, 0.8F, 1.05F);
                    ModSounds.playAt(this, ModSounds.LOCK_OPEN, 0.85F, 1.1F);
                    setOpen(true);
                    player.sendSystemMessage(Component.literal("§a¡Has abierto la caja de herramientas!"));
                } else {
                    ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 1.1F);
                    player.sendSystemMessage(Component.literal("§cNecesitas una §bganzúa §cpara abrir esta caja"));
                }
            } else {
                player.sendSystemMessage(Component.literal("§7La caja ya está abierta"));
            }
        }
    }

    private boolean hasRequiredItem(Player player) {
        return player.getInventory().contains(new ItemStack(ModItems.GANZUA));
    }

    private void consumeRequiredItem(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.GANZUA)) {
                stack.shrink(1);
                return;
            }
        }
    }

    private void spawnBoxContents() {
        if (this.level().isClientSide || boxContents.isEmpty()) {
            return;
        }

        Vec3 centerPos = this.position().add(0, 0.5, 0);
        int count = boxContents.size();

        for (int i = 0; i < count; i++) {
            ItemStack stack = boxContents.get(i).copy();
            if (stack.isEmpty()) continue;

            ItemEntity itemEntity = new ItemEntity(
                    this.level(),
                    centerPos.x,
                    centerPos.y,
                    centerPos.z,
                    stack
            );

            double angle = (Math.PI * 2 * i) / count;
            double horizontalSpeed = 0.1;
            double motionX = Math.cos(angle) * horizontalSpeed;
            double motionY = 0.4 + (i * 0.05);
            double motionZ = Math.sin(angle) * horizontalSpeed;

            itemEntity.setDeltaMovement(motionX, motionY, motionZ);
            itemEntity.setPickUpDelay(20);

            this.level().addFreshEntity(itemEntity);
        }

        ModSounds.playAt(this, ModSounds.ITEMS_SPILL, 0.85F, 1.05F);
    }

    public void reset() {
        setOpen(false);
        itemsSpawned = false;
        openAnimationTicks = 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsOpen", isOpen());
        tag.putBoolean("ItemsSpawned", itemsSpawned);

        ListTag itemList = new ListTag();
        HolderLookup.Provider provider = this.level().registryAccess();
        for (ItemStack stack : boxContents) {
            if (!stack.isEmpty()) {
                itemList.add(stack.save(provider));
            }
        }
        tag.put("BoxContents", itemList);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOpen(tag.getBoolean("IsOpen"));
        itemsSpawned = tag.getBoolean("ItemsSpawned");

        boxContents.clear();

        if (tag.contains("BoxContents", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BoxContents", Tag.TAG_COMPOUND);
            HolderLookup.Provider provider = this.level().registryAccess();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                ItemStack stack = ItemStack.parseOptional(provider, itemTag);
                if (!stack.isEmpty()) {
                    boxContents.add(stack);
                }
            }
        }
    }
}
