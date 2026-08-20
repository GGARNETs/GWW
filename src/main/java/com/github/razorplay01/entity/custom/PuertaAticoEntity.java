package com.github.razorplay01.entity.custom;

import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.sound.ModSounds;
import com.github.razorplay01.system.NoiseDetectionSystem;
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
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

public class PuertaAticoEntity extends BaseEntity {

    private static final EntityDataAccessor<Boolean> IS_OPEN = SynchedEntityData.defineId(
            PuertaAticoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIMATION_IDLE = RawAnimation.begin().thenLoop("animation.idle");
    private static final RawAnimation ANIMATION_OPEN = RawAnimation.begin().thenPlayAndHold("animation.open");
    private static final RawAnimation ANIMATION_CLOSE = RawAnimation.begin()
            .thenPlay("animation.close").thenLoop("animation.idle");

    /**
     * Solo cliente: si esta entidad se vio abierta y se cierra, toca reproducir la
     * animación de cierre. Sin este flag, una puerta que carga ya cerrada
     * reproduciría el cierre al aparecer el chunk.
     */
    private boolean clientWasOpen = false;
    private boolean clientPlayClose = false;

    /**
     * Con un teclado numérico vinculado, la puerta deja de aceptar la llave: la abre
     * el teclado. Lo marca el propio teclado al vincularse/desvincularse, y sin
     * teclado la puerta sigue funcionando con la llave como siempre.
     */
    private boolean keypadControlled = false;

    public PuertaAticoEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
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
    }

    public boolean isKeypadControlled() {
        return keypadControlled;
    }

    public void setKeypadControlled(boolean controlled) {
        this.keypadControlled = controlled;
    }

    /** Apertura desde el teclado numérico: mismos sonidos y ruido que abrirla con la llave. */
    public void openFromKeypad() {
        if (this.level().isClientSide || isOpen()) {
            return;
        }
        setOpen(true);
        ModSounds.playAt(this, ModSounds.LOCK_OPEN, 0.9F, 0.95F);
        ModSounds.playAt(this, ModSounds.DOOR_ATTIC_OPEN, 1.0F, 1.0F);
        NoiseDetectionSystem.addNoiseNearby(this, 20.0D, 1.0f);
    }

    /** Cierre desde el teclado: mismo sonido de trampilla, más grave. */
    public void closeFromKeypad() {
        if (this.level().isClientSide || !isOpen()) {
            return;
        }
        setOpen(false);
        ModSounds.playAt(this, ModSounds.DOOR_ATTIC_OPEN, 1.0F, 0.8F);
        NoiseDetectionSystem.addNoiseNearby(this, 20.0D, 1.0f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsOpen", isOpen());
        tag.putBoolean("KeypadControlled", keypadControlled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOpen(tag.getBoolean("IsOpen"));
        keypadControlled = tag.getBoolean("KeypadControlled");
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
                state -> {
                    if (isOpen()) {
                        clientPlayClose = false;
                        return state.setAndContinue(ANIMATION_OPEN);
                    }
                    return state.setAndContinue(clientPlayClose ? ANIMATION_CLOSE : ANIMATION_IDLE);
                }
        ));
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (!player.level().isClientSide) {
            if (!isOpen()) {
                if (keypadControlled) {
                    // La cerradura de llave está anulada: manda el teclado.
                    ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 0.95F);
                    player.sendSystemMessage(Component.literal(
                            "§cLa cerradura está sellada. Un §bteclado §ccontrola esta puerta."));
                } else if (hasRequiredItem(player)) {
                    consumeRequiredItem(player);
                    setOpen(true);
                    ModSounds.playAt(this, ModSounds.LOCK_OPEN, 0.9F, 0.95F);
                    ModSounds.playAt(this, ModSounds.DOOR_ATTIC_OPEN, 1.0F, 1.0F);
                    NoiseDetectionSystem.addNoise(player, 1.0f);
                    player.sendSystemMessage(Component.literal("§a¡Has abierto la puerta del ático!"));
                } else {
                    ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 0.95F);
                    player.sendSystemMessage(Component.literal("§cNecesitas un §bobjeto §cpara abrir esta puerta"));
                }
            }
        }
    }

    private boolean hasRequiredItem(Player player) {
        return player.getInventory().contains(new ItemStack(ModItems.LLAVE_ATICO));
    }

    private void consumeRequiredItem(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.LLAVE_ATICO)) {
                stack.shrink(1);
                return;
            }
        }
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
        if (IS_OPEN.equals(key)) {
            this.refreshDimensions();
            if (this.level().isClientSide) {
                boolean nowOpen = isOpen();
                if (clientWasOpen && !nowOpen) {
                    clientPlayClose = true;
                }
                clientWasOpen = nowOpen;
            }
        }
    }
}