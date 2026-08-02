package com.github.razorplay01.entity.custom;

import com.github.razorplay01.sound.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;

public class LuzTortugaEntity extends BaseEntity {

    // ==================== ESTADO ====================
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(
            LuzTortugaEntity.class, EntityDataSerializers.INT);

    public LuzTortugaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, 0); // 0 = apagada (default)
    }

    public int getState() {
        return this.entityData.get(STATE);
    }

    public void setState(int state) {
        if (state < 0 || state > 2) state = 0;
        int previous = this.entityData.get(STATE);
        this.entityData.set(STATE, state);

        // El piloto avisa al cambiar, y sube de tono según lo cerca que esté el puzzle:
        // apagado -> lleno -> resuelto. tickCount == 0 es la carga del chunk.
        if (previous != state && this.tickCount > 0) {
            ModSounds.playAt(this, ModSounds.LIGHT_INDICATOR, 0.6F, 0.85F + state * 0.15F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("State", getState());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(tag.getInt("State"));
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
    }
}