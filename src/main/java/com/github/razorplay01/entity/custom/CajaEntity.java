package com.github.razorplay01.entity.custom;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.ModEntities;
import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.system.NoiseDetectionSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;

public class CajaEntity extends BaseEntity {

    private static final EntityDataAccessor<Boolean> IS_OPEN =
            SynchedEntityData.defineId(CajaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIMATION_IDLE = RawAnimation.begin().thenLoop("animation.idle");
    private static final RawAnimation ANIMATION_OPEN = RawAnimation.begin().thenPlayAndHold("animation.open");

    private final List<ItemStack> boxContents = new ArrayList<>();

    private CompoundTag spawnNbt = new CompoundTag();
    private Vec3 spawnOffset = new Vec3(0, 0.2, 0);

    private boolean itemsSpawned = false;
    private int openAnimationTicks = 0;
    private int collisionCheckCooldown = 0;

    private static final int SPAWN_DELAY = 10;
    private static final int COLLISION_CHECK_INTERVAL = 5;

    public CajaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
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
            this.level().playSound(null, this.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            // La caja no la abre nadie con la mano, salta por una colisión: el ruido
            // se le apunta al jugador jugable más cercano.
            NoiseDetectionSystem.addNoiseNearby(this, 20.0D, 1.0f);
        }
    }

    public CompoundTag getSpawnNbt() {
        return spawnNbt;
    }

    public void setSpawnNbt(CompoundTag nbt) {
        this.spawnNbt = nbt.copy();
    }

    public Vec3 getSpawnOffset() {
        return spawnOffset;
    }

    public void setSpawnOffset(Vec3 offset) {
        this.spawnOffset = offset;
    }

    public void setSpawnOffset(double x, double y, double z) {
        this.spawnOffset = new Vec3(x, y, z);
    }

    public List<ItemStack> getBoxContents() {
        return boxContents;
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

        if (!isOpen()) {
            collisionCheckCooldown--;
            if (collisionCheckCooldown <= 0) {
                collisionCheckCooldown = COLLISION_CHECK_INTERVAL;
                checkForInteractiveEntityCollision();
            }
        }
    }

    private void checkForInteractiveEntityCollision() {
        this.level().getEntitiesOfClass(PalancaEntity.class, this.getBoundingBox().inflate(0.5D))
                .forEach(palancaEntity -> {
                    setOpen(true);
                    onOpenedByCollision(palancaEntity);
                });
    }

    protected void onOpenedByCollision(PalancaEntity triggerEntity) {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5F, 1.5F);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "caja_controller", 0,
                state -> isOpen() ? state.setAndContinue(ANIMATION_OPEN) : state.setAndContinue(ANIMATION_IDLE)));
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (!player.level().isClientSide) {
            if (!isOpen()) {
                player.sendSystemMessage(Component.literal("§7Esta caja se abre cuando algo colisiona con ella"));
            } else {
                player.sendSystemMessage(Component.literal("§aLa caja ya está abierta"));
            }
        }
    }

    private void spawnBoxContents() {
        if (this.level().isClientSide) return;

        if (!spawnNbt.isEmpty() && spawnNbt.contains("id")) {
            spawnSpecialEntity();
            return;
        }

        if (!boxContents.isEmpty()) {
            spawnItemsFromList(boxContents);
            return;
        }
    }

    private void spawnItemsFromList(List<ItemStack> items) {
        Vec3 center = this.position().add(0, 0.5, 0);
        int count = items.size();

        for (int i = 0; i < count; i++) {
            ItemStack stack = items.get(i).copy();
            if (stack.isEmpty()) continue;

            ItemEntity itemEntity = new ItemEntity(this.level(), center.x, center.y, center.z, stack);

            double angle = (Math.PI * 2 * i) / Math.max(count, 1);
            double speed = 0.15;

            itemEntity.setDeltaMovement(
                    Math.cos(angle) * speed,
                    0.45 + (i * 0.05),
                    Math.sin(angle) * speed
            );
            itemEntity.setPickUpDelay(20);

            this.level().addFreshEntity(itemEntity);
        }

        this.level().playSound(null, this.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    private void spawnSpecialEntity() {
        CompoundTag finalNbt = spawnNbt.copy();

        try {
            ServerLevel serverLevel = (ServerLevel) this.level();

            Vec3 spawnPos = this.position().add(spawnOffset);

            Entity entity = EntityType.loadEntityRecursive(finalNbt, serverLevel, (e) -> {
                e.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, e.getYRot(), e.getXRot());
                return e;
            });

            if (entity == null) {
                GWW.LOGGER.error("§c[Falló al crear entidad desde NBT: {}]", finalNbt);
                return;
            }

            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(entity.blockPosition()),
                        MobSpawnType.COMMAND, (SpawnGroupData) null);
            }

            if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
                GWW.LOGGER.error("§c[No se pudo agregar la entidad (UUID duplicado?)]");
            }

        } catch (Exception e) {
            GWW.LOGGER.error("§c[Error al invocar entidad desde NBT: {}]", e.getMessage());
            e.printStackTrace();
        }
    }

    public void reset() {
        setOpen(false);
        itemsSpawned = false;
        openAnimationTicks = 0;
        collisionCheckCooldown = 0;
    }

    public void forceOpen() {
        if (!isOpen()) setOpen(true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsOpen", isOpen());
        tag.putBoolean("ItemsSpawned", itemsSpawned);

        ListTag itemList = new ListTag();
        var provider = this.level().registryAccess();
        for (ItemStack stack : boxContents) {
            if (!stack.isEmpty()) {
                itemList.add(stack.save(provider));
            }
        }
        tag.put("BoxContents", itemList);

        if (!spawnNbt.isEmpty()) {
            tag.put("SpawnNbt", spawnNbt);
        }

        tag.putDouble("SpawnOffsetX", spawnOffset.x);
        tag.putDouble("SpawnOffsetY", spawnOffset.y);
        tag.putDouble("SpawnOffsetZ", spawnOffset.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Directo al dato, sin pasar por setOpen: una caja que ya estaba abierta al
        // guardarse no debe sonar ni generar ruido al volver a cargarse.
        this.entityData.set(IS_OPEN, tag.getBoolean("IsOpen"));
        itemsSpawned = tag.getBoolean("ItemsSpawned");

        var provider = this.level().registryAccess();
        boolean hasCustomContent = false;

        if (tag.contains("BoxContents")) {
            Tag raw = tag.get("BoxContents");
            if (raw instanceof ListTag list) {
                boxContents.clear();
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag itemTag = list.getCompound(i);
                    ItemStack stack = ItemStack.parseOptional(provider, itemTag);
                    if (!stack.isEmpty()) {
                        boxContents.add(stack);
                    }
                }
                if (!boxContents.isEmpty()) {
                    hasCustomContent = true;
                }
            }
        }

        if (tag.contains("SpawnNbt", 10)) {
            spawnNbt = tag.getCompound("SpawnNbt").copy();
            if (!spawnNbt.isEmpty() && spawnNbt.contains("id")) {
                hasCustomContent = true;
            }
        }

        double ox = tag.getDouble("SpawnOffsetX");
        double oy = tag.getDouble("SpawnOffsetY");
        double oz = tag.getDouble("SpawnOffsetZ");
        this.spawnOffset = new Vec3(ox, oy, oz);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
