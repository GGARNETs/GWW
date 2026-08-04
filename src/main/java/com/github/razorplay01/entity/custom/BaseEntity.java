package com.github.razorplay01.entity.custom;

import com.github.darkpred.morehitboxes.MultiPartLevel;
import com.github.darkpred.morehitboxes.api.MultiPart;
import com.github.darkpred.morehitboxes.api.MultiPartEntity;
import com.github.razorplay01.entity.custom.util.NearbyPlayers;
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

import java.util.List;

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
                tickHitboxActivation(multiPart);
                if (hitboxesRegistered) {
                    for (MultiPart<?> part : multiPart.getEntityHitboxData().getCustomParts()) {
                        part.updatePosition();
                    }
                    multiPart.getEntityHitboxData().getAnchorData().updatePositions();
                }
            }
            return;
        }
        super.aiStep();
    }

    /**
     * Radio a partir del cual las sub-hitboxes de esta entidad dejan de existir para el
     * servidor. Muy holgado a propósito: el alcance de clic son 5 bloques, así que 48
     * deja margen de sobra para que nadie llegue nunca a un panel "apagado".
     */
    private static final double HITBOX_ACTIVE_RADIUS = 48.0;
    /** Cada cuánto se revisa. Un segundo contra 48 bloques de margen: imposible colarse. */
    private static final int HITBOX_CHECK_INTERVAL = 20;

    private boolean hitboxesRegistered = true;

    /**
     * Da de alta o de baja las sub-hitboxes de esta entidad en el nivel según haya
     * alguien cerca. Es el arreglo del cuello de botella de morehitboxes.
     * <p>
     * morehitboxes guarda TODAS las sub-hitboxes del servidor en una única lista plana,
     * sin índice espacial, y la recorre entera en CADA consulta de entidades del
     * servidor — las nuestras, las de vanilla (colisiones de los 100 jugadores) y las de
     * cualquier otro mod. Con el mapa entero cargado son ~20.000 entradas (escalera 3 +
     * panel de código 5 + panel de fusibles 6, por sala), y encima el barrido hace un
     * {@code contains} lineal por candidato: ahí se iba la mitad del tick.
     * <p>
     * Las salas sin nadie dentro no necesitan hitboxes clicables, así que se dan de baja
     * y la lista global cae a lo que de verdad está en uso. De paso se ahorra el
     * {@code updatePosition} por parte y por tick, que con esas 20.000 partes tampoco
     * era gratis.
     * <p>
     * El alta/baja es idempotente (morehitboxes indexa por id de entidad), así que
     * convive sin problema con el registro que hace la librería al cargar el chunk y con
     * {@link com.github.razorplay01.entity.custom.util.MultiPartHitboxes#registerParts}.
     */
    private void tickHitboxActivation(MultiPartEntity<?> multiPart) {
        if ((this.tickCount + this.getId()) % HITBOX_CHECK_INTERVAL != 0) {
            return;
        }
        boolean shouldBeActive = NearbyPlayers.anyWithin(this, HITBOX_ACTIVE_RADIUS);
        if (shouldBeActive == hitboxesRegistered) {
            return;
        }
        List<? extends MultiPart<?>> parts = multiPart.getEntityHitboxData().getCustomParts();
        if (parts == null || parts.isEmpty() || !(this.level() instanceof MultiPartLevel multiPartLevel)) {
            // Sin parts todavía no hay nada que dar de alta; dejar el flag como está para
            // no quedarnos creyendo que las dimos de baja cuando aparezcan más tarde.
            return;
        }
        for (MultiPart<?> part : parts) {
            if (shouldBeActive) {
                multiPartLevel.moreHitboxes$addMultiPart(part);
            } else {
                multiPartLevel.moreHitboxes$removeMultiPart(part);
            }
        }
        hitboxesRegistered = shouldBeActive;
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
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (interactionHand.equals(InteractionHand.MAIN_HAND) && !this.level().isClientSide) {
            handleNormalInteract(player);
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, interactionHand);
    }
}
