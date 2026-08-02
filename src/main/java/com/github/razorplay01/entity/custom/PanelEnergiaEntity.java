package com.github.razorplay01.entity.custom;

import com.github.razorplay01.arena.Arena;
import com.github.razorplay01.arena.ArenaLight;
import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.sound.ModSounds;
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
            cutArenaLight();
        }
    }

    /**
     * Cortar el cable deja la sala a oscuras: se les quita la visión nocturna a los
     * jugadores de la arena aunque el interruptor industrial siga encendido.
     */
    private void cutArenaLight() {
        if (this.level().isClientSide) return;

        // tickCount == 0 es la carga del chunk: el estado viene del NBT y la sala ya
        // estaba a oscuras, así que el apagón no se vuelve a oír.
        if (this.tickCount > 0) {
            ModSounds.playAt(this, ModSounds.POWER_DOWN, 1.0F, 1.0F);
            ModSounds.playAt(this, ModSounds.LIGHT_OFF, 0.9F, 1.0F);
        }

        Arena arena = ArenaManager.getArenaAt(this.position());
        if (arena != null) {
            ArenaLight.refresh(this.level(), arena, false);
        }
    }

    /**
     * Margen de búsqueda de rejas cuando el panel no está dentro de ninguna arena
     * configurada. Antes eran 5 bloques y la reja del ducto, que está al otro lado de
     * la sala, no se encontraba nunca; dentro de una arena manda la zona de la arena.
     */
    private static final double REJA_SEARCH_FALLBACK = 32.0;

    private void notifyLinkedRejas() {
        if (this.level().isClientSide) return;

        // El enlace lo guarda la reja, no el panel, así que se barren las rejas de la
        // sala y se le pregunta a cada una si apunta a este panel.
        this.level().getEntitiesOfClass(RejaDuctoEntity.class,
                        ArenaManager.searchAreaAround(this.position(),
                                this.getBoundingBox().inflate(REJA_SEARCH_FALLBACK)),
                        reja -> reja.isLinkedTo(this))
                .forEach(RejaDuctoEntity::tryOpenAutomatically);
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
                    ModSounds.playAt(this, ModSounds.LOCK_PICK, 0.8F, 1.0F);
                    ModSounds.playAt(this, ModSounds.LOCK_OPEN, 0.85F, 1.0F);
                    setOpen(true);
                    player.sendSystemMessage(Component.literal("§a¡Has abierto el panel eléctrico!"));
                } else {
                    ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 1.0F);
                    player.sendSystemMessage(Component.literal("§cNecesitas un §bobjeto §cpara abrir el panel eléctrico"));
                }
            } else {
                if (!isActive()) {
                    if (hasRequiredItem(player, new ItemStack(ModItems.ALICATE_CORTACABLES))) {
                        consumeRequiredItem(player, new ItemStack(ModItems.ALICATE_CORTACABLES));
                        ModSounds.playAt(this, ModSounds.CABLE_CUT, 1.0F, 1.0F);
                        setActive(true);
                        NoiseDetectionSystem.addNoise(player, 0.5f);
                        player.sendSystemMessage(Component.literal("§a¡Has cortado los cables!"));
                    } else {
                        ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 1.0F);
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