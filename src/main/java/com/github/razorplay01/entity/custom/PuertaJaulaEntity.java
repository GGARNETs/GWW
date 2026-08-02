package com.github.razorplay01.entity.custom;

import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.sound.ModSounds;
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
import software.bernie.geckolib.animation.*;

public class PuertaJaulaEntity extends BaseEntity {

    private static final EntityDataAccessor<Boolean> IS_OPEN =
            SynchedEntityData.defineId(PuertaJaulaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> IS_UNLOCKED =
            SynchedEntityData.defineId(PuertaJaulaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> ANIMATION_STATE =
            SynchedEntityData.defineId(PuertaJaulaEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(PuertaJaulaEntity.class, EntityDataSerializers.DIRECTION);

    private enum AnimState {
        LOCKED(0),      // Bloqueada (lock.idle)
        UNLOCKING(1),   // Desbloqueando (unlock)
        OPENING(2),     // Abriéndose (open)
        OPEN(3),        // Abierta (open.idle)
        CLOSING(4),     // Cerrándose (close)
        CLOSED(5);      // Cerrada desbloqueada (close.idle)

        private final int id;
        AnimState(int id) { this.id = id; }

        public int getId() { return id; }

        public static AnimState fromId(int id) {
            for (AnimState state : values()) {
                if (state.id == id) return state;
            }
            return LOCKED;
        }
    }

    private static final RawAnimation ANIM_LOCK_IDLE =
            RawAnimation.begin().thenLoop("animation.lock.idle");
    private static final RawAnimation ANIM_UNLOCK =
            RawAnimation.begin().thenPlay("animation.unlock");
    private static final RawAnimation ANIM_OPEN =
            RawAnimation.begin().thenPlay("animation.open");
    private static final RawAnimation ANIM_OPEN_IDLE =
            RawAnimation.begin().thenLoop("animation.open.idle");
    private static final RawAnimation ANIM_CLOSE =
            RawAnimation.begin().thenPlay("animation.close");
    private static final RawAnimation ANIM_CLOSE_IDLE =
            RawAnimation.begin().thenLoop("animation.close.idle");

    private static final int UNLOCK_ANIM_DURATION = 15;  // 0.75s * 20 = 15 ticks
    private static final int OPEN_ANIM_DURATION = 9;     // 0.45s * 20 = 9 ticks
    private static final int CLOSE_ANIM_DURATION = 9;    // 0.45s * 20 = 9 ticks

    private static final String NBT_OPEN = "IsOpen";
    private static final String NBT_UNLOCKED = "IsUnlocked";
    private static final String NBT_ANIM_STATE = "AnimState";
    private static final String NBT_FACING = "Facing";
    private static final String NBT_ANIM_TIMER = "AnimTimer";

    private int animationTimer = 0;

    public PuertaJaulaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_OPEN, false);
        builder.define(IS_UNLOCKED, false);
        builder.define(ANIMATION_STATE, AnimState.LOCKED.getId());
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

    private void setOpen(boolean open) {
        this.entityData.set(IS_OPEN, open);
    }

    public boolean isUnlocked() {
        return this.entityData.get(IS_UNLOCKED);
    }

    private void setUnlocked(boolean unlocked) {
        this.entityData.set(IS_UNLOCKED, unlocked);
    }

    private AnimState getAnimState() {
        return AnimState.fromId(this.entityData.get(ANIMATION_STATE));
    }

    private void setAnimState(AnimState state) {
        this.entityData.set(ANIMATION_STATE, state.getId());
        animationTimer = 0;
    }

    private void startUnlocking() {
        setAnimState(AnimState.UNLOCKING);
        ModSounds.playAt(this, ModSounds.LOCK_PICK, 0.9F, 1.0F);
    }

    private void startOpening() {
        setAnimState(AnimState.OPENING);
        ModSounds.playAt(this, ModSounds.DOOR_CAGE_OPEN, 1.0F, 1.0F);
    }

    private void completeOpen() {
        setOpen(true);
        setAnimState(AnimState.OPEN);
    }

    private void startClosing() {
        setAnimState(AnimState.CLOSING);
        ModSounds.playAt(this, ModSounds.DOOR_CAGE_CLOSE, 1.0F, 1.0F);
    }

    private void completeClose() {
        setOpen(false);
        setAnimState(AnimState.CLOSED);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            updateAnimationState();
        }
    }

    /**
     * Actualiza el estado de animación basado en timers
     */
    private void updateAnimationState() {
        AnimState currentState = getAnimState();
        animationTimer++;

        switch (currentState) {
            case UNLOCKING:
                // Cuando termina la animación de unlock, pasa a abrir
                if (animationTimer >= UNLOCK_ANIM_DURATION) {
                    setUnlocked(true);
                    ModSounds.playAt(this, ModSounds.LOCK_OPEN, 1.0F, 1.0F);
                    startOpening();
                }
                break;

            case OPENING:
                // Cuando termina la animación de apertura, queda abierta
                if (animationTimer >= OPEN_ANIM_DURATION) {
                    completeOpen();
                }
                break;

            case CLOSING:
                // Cuando termina la animación de cierre, queda cerrada
                if (animationTimer >= CLOSE_ANIM_DURATION) {
                    completeClose();
                }
                break;

            // Estados estables no necesitan timer
            case LOCKED:
            case OPEN:
            case CLOSED:
            default:
                break;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "puerta_controller",
                0,
                this::animationPredicate
        ));
    }

    /**
     * Determina qué animación reproducir según el estado actual
     */
    protected <E extends PuertaJaulaEntity> PlayState animationPredicate(final AnimationState<E> state) {
        AnimState currentState = getAnimState();

        switch (currentState) {
            case LOCKED:
                state.setAnimation(ANIM_LOCK_IDLE);
                break;

            case UNLOCKING:
                state.setAnimation(ANIM_UNLOCK);
                break;

            case OPENING:
                state.setAnimation(ANIM_OPEN);
                break;

            case OPEN:
                state.setAnimation(ANIM_OPEN_IDLE);
                break;

            case CLOSING:
                state.setAnimation(ANIM_CLOSE);
                break;

            case CLOSED:
                state.setAnimation(ANIM_CLOSE_IDLE);
                break;
        }

        return PlayState.CONTINUE;
    }

    /**
     * Cierra la puerta si estaba abierta, dejando el candado como estaba (desbloqueado).
     * Es lo que hace el Ublabla: la vuelve a cerrar, pero no la re-bloquea.
     */
    public void forceClose() {
        if (this.level().isClientSide) {
            return;
        }
        AnimState current = getAnimState();
        if (current == AnimState.OPEN || current == AnimState.OPENING) {
            startClosing();
        }
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (player.level().isClientSide) return;

        AnimState currentState = getAnimState();

        switch (currentState) {
            case LOCKED:
                if (hasRequiredItem(player)) {
                    consumeRequiredItem(player);
                    startUnlocking();
                    player.sendSystemMessage(Component.literal("§aDesbloqueando la puerta..."));
                } else {
                    ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 1.0F);
                    player.sendSystemMessage(Component.literal(
                            "§cNecesitas un §bGanzúa §cpara desbloquear esta puerta"));
                }
                break;

            case OPEN:
                startClosing();
                player.sendSystemMessage(Component.literal("§eCerrando puerta..."));
                break;

            case CLOSED:
                startOpening();
                player.sendSystemMessage(Component.literal("§aAbriendo puerta..."));
                break;

            case UNLOCKING:
            case OPENING:
            case CLOSING:
                break;
        }
    }

    /**
     * Verifica si el jugador tiene una ganzúa
     */
    private boolean hasRequiredItem(Player player) {
        return player.getInventory().contains(new ItemStack(ModItems.GANZUA));
    }

    /**
     * Consume una ganzúa del inventario del jugador
     */
    private void consumeRequiredItem(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.GANZUA)) {
                stack.shrink(1);
                return;
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(NBT_OPEN, isOpen());
        tag.putBoolean(NBT_UNLOCKED, isUnlocked());
        tag.putInt(NBT_ANIM_STATE, getAnimState().getId());
        tag.putString(NBT_FACING, getFacing().getSerializedName());
        tag.putInt(NBT_ANIM_TIMER, animationTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOpen(tag.getBoolean(NBT_OPEN));
        setUnlocked(tag.getBoolean(NBT_UNLOCKED));

        if (tag.contains(NBT_ANIM_STATE)) {
            this.entityData.set(ANIMATION_STATE, tag.getInt(NBT_ANIM_STATE));
        }

        if (tag.contains(NBT_ANIM_TIMER)) {
            animationTimer = tag.getInt(NBT_ANIM_TIMER);
        }

        if (tag.contains(NBT_FACING)) {
            Direction dir = Direction.byName(tag.getString(NBT_FACING));
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
    public boolean canBeCollidedWith() {
        return !isOpen();
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

        double height = 2.0;
        double width = 1.0;
        double depth = 0.4;

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
}