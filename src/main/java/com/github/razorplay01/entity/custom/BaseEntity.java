package com.github.razorplay01.entity.custom;

import com.github.darkpred.morehitboxes.api.MultiPart;
import com.github.darkpred.morehitboxes.api.MultiPartEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

public abstract class BaseEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    protected BaseEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    /**
     * En el servidor estas entidades se saltan la maquinaria de movimiento vanilla
     * (travel, fluidos, salto): son atrezzo/puzzles y su movimiento real (caída,
     * transporte por el jugador) lo hacen sus ticks a mano con move(). Con cientos
     * de ellas por mapa, ese trabajo vanilla domina el MSPT. Incondicional a
     * propósito: no depende del flag NoAI, que las instances viejas cargan en false.
     * El lado cliente sigue entero, que es donde vive la interpolación visual.
     * <p>
     * morehitboxes posiciona las sub-hitboxes con un mixin DENTRO de aiStep; al
     * saltarlo hay que hacer ese trabajo aquí igualmente, o las entidades multipart
     * (panel de fusibles, panel de código, escalera) dejan de responder al clic.
     */
    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            if (this instanceof MultiPartEntity<?> multiPart) {
                for (MultiPart<?> part : multiPart.getEntityHitboxData().getCustomParts()) {
                    part.updatePosition();
                }
                multiPart.getEntityHitboxData().getAnchorData().updatePositions();
            }
            return;
        }
        super.aiStep();
    }

    /**
     * Los snapshots de instance capturados con versiones viejas traen NBT sin
     * "NoAI"/"Silent", y el load vanilla pisa esos flags a false: el atrezzo queda
     * corriendo IA y física vanilla para siempre (9 ms/tick en el spark con 50
     * salas). Reafirmarlos al cargar autocorrige cualquier sala ya pegada.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setNoAi(true);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    /**
     * El barrido de fluidos del baseTick (¿estoy en agua/lava?) marcó 2,3 ms/tick en
     * el spark con 50 salas montadas. Al atrezzo nunca lo empuja un fluido, así que
     * se corta el barrido de raíz.
     */
    @Override
    public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> fluidTag, double motionScale) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    public abstract void handleNormalInteract(Player player);

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
        if (interactionHand.equals(InteractionHand.MAIN_HAND) && !this.level().isClientSide) {
            handleNormalInteract(player);
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, interactionHand);
    }
}
