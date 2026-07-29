package com.github.razorplay01.entity.custom;

import com.github.razorplay01.entity.BaseInteractiveEntity;
import com.github.razorplay01.entity.custom.util.ValvulaType;
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

public class ManivelaEntity extends BaseInteractiveEntity {

    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(ManivelaEntity.class, EntityDataSerializers.INT);

    /** Cada cuántos ticks la manivela mira si la han dejado sobre su válvula. */
    private static final int VALVULA_CHECK_INTERVAL = 5;
    private int valvulaCheckCounter = 0;

    public ManivelaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
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
        builder.define(DATA_TYPE, ValvulaType.NARANJA.getId());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Type", getValType().getId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setType(ValvulaType.byId(tag.getInt("Type")));
    }

    public ValvulaType getValType() {
        return ValvulaType.byId(this.entityData.get(DATA_TYPE));
    }

    public void setType(ValvulaType type) {
        this.entityData.set(DATA_TYPE, type.getId());
    }

    @Override
    public void handleNormalInteract(Player player) {
        // Pista de que la manivela se transporta (shift) y encaja en una válvula.
        player.displayClientMessage(
                Component.literal("§eParece que esta válvula va en algún lugar..."), true);
    }

    @Override
    protected boolean canBound() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        // Encajar la manivela es un evento único por partida, así que no hace falta
        // barrer válvulas en cada tick: cada cuarto de segundo es imperceptible al
        // colocarla y ahorra una consulta de entidades por manivela y por tick.
        if (!this.level().isClientSide && ++valvulaCheckCounter >= VALVULA_CHECK_INTERVAL) {
            valvulaCheckCounter = 0;
            this.level().getEntitiesOfClass(ValvulaEntity.class, this.getBoundingBox().inflate(0.5D))
                    .forEach(valvula -> {
                        if (valvula.getValType() == this.getValType() && !valvula.hasManivela()) {
                            valvula.attachManivela(this.getValType());
                            if (this.getCachedBoundPlayer() != null) {
                                onUnbound(this.getCachedBoundPlayer());
                            }
                            this.discard();
                        }
                    });
        }
        if (!this.level().isClientSide && !isBound()) {
            handleGravityAndMovement();
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