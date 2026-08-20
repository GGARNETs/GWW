package com.github.razorplay01.entity.custom;

import com.github.razorplay01.arena.Arena;
import com.github.razorplay01.arena.ArenaLight;
import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.entity.custom.util.Util;
import com.github.razorplay01.sound.ModSounds;
import lombok.Getter;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;

@Getter
public class InterruptorIndustrialEntity extends BaseEntity {

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(
            InterruptorIndustrialEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation ANIMATION_ON = RawAnimation.begin().thenPlayAndHold("On");
    private static final RawAnimation ANIMATION_OFF = RawAnimation.begin().thenPlayAndHold("Off");

    private final List<Vec3> linkedCables = new ArrayList<>();
    private final List<Vec3> linkedUblablas = new ArrayList<>();
    private final List<Vec3> linkedPanels = new ArrayList<>();
    private final List<Vec3> linkedTeclados = new ArrayList<>();

    public InterruptorIndustrialEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, 0); // 0 = OFF, 1 = ON
    }

    public int getState() {
        return this.entityData.get(STATE);
    }

    public void setState(int state) {
        int newState = (state == 1) ? 1 : 0;
        int oldState = this.entityData.get(STATE);
        this.entityData.set(STATE, newState);
        if (newState != oldState) {
            onStateChanged(newState == 1);
        }
    }

    private void onStateChanged(boolean nowOn) {
        PanelCodigoEntity panel = getLinkedPanel();
        if (panel != null) {
            panel.setPowered(nowOn); // ON → encendido, OFF → apagado
        }
        forEachLinkedTeclado(teclado -> teclado.setPowered(nowOn));

        // Accionar el interruptor es uno de los dos únicos momentos en los que la luz
        // puede cambiar. El otro (alguien entra o sale de la arena) lo lleva
        // ArenaManager, así que aquí no hace falta comprobar nada por tick.
        // Se recalcula en vez de usar 'nowOn' a secas porque un panel de energía con el
        // cable cortado manda sobre el interruptor: encenderlo no devuelve la luz.
        if (!this.level().isClientSide) {
            Arena arena = ArenaManager.getArenaAt(this.position());
            if (arena != null) {
                boolean lightOn = ArenaLight.isOn(this.level(), arena);
                ArenaLight.refresh(this.level(), arena, lightOn);
                playLightSound(nowOn, lightOn);
            }
        }
    }

    /**
     * El sonido sigue a la luz real, no a la posición de la palanca: con el cable
     * cortado, subir el interruptor solo da un chispazo y la sala sigue a oscuras.
     * <p>
     * tickCount == 0 es la carga del chunk: ahí el estado se restaura desde NBT y no
     * debe sonar nada.
     */
    private void playLightSound(boolean nowOn, boolean lightOn) {
        if (this.tickCount == 0) {
            return;
        }
        if (lightOn) {
            ModSounds.playAt(this, ModSounds.POWER_UP, 0.8F, 1.0F);
            ModSounds.playAt(this, ModSounds.LIGHT_ON, 0.9F, 1.0F);
        } else if (nowOn) {
            ModSounds.playAt(this, ModSounds.SPARK_FAIL, 0.6F, 0.9F);
        } else {
            ModSounds.playAt(this, ModSounds.POWER_DOWN, 0.8F, 1.0F);
            ModSounds.playAt(this, ModSounds.LIGHT_OFF, 0.9F, 1.0F);
        }
    }

    public boolean isOn() {
        return getState() == 1;
    }

    public void linkCable(CableEntity cable) {
        if (cable == null) return;
        Vec3 relativePos = cable.position().subtract(this.position());
        if (!linkedCables.contains(relativePos)) {
            linkedCables.add(relativePos);
        }
    }

    public void unlinkAllCables() {
        linkedCables.clear();
    }

    public boolean areAllCablesReady() {
        if (linkedCables.isEmpty()) return true;

        Vec3 interruptorPos = this.position();

        for (Vec3 relPos : linkedCables) {
            Vec3 absolutePos = interruptorPos.add(relPos);

            List<CableEntity> found = this.level().getEntitiesOfClass(CableEntity.class,
                    AABB.ofSize(absolutePos, 5, 5, 5),
                    c -> c.position().distanceToSqr(absolutePos) < 1.5);

            if (found.isEmpty() || !found.get(0).isActive() || !found.get(0).isCorrect()) {
                return false;
            }
        }
        return true;
    }

    public void linkUblabla(UblablaEntity ublabla) {
        if (ublabla == null) return;
        linkedUblablas.clear();
        linkedUblablas.add(ublabla.position().subtract(this.position()));
    }

    public void unlinkUblabla() {
        linkedUblablas.clear();
    }

    public void linkPanel(PanelCodigoEntity panel) {
        if (panel == null) return;
        linkedPanels.clear();
        linkedPanels.add(panel.position().subtract(this.position()));
    }

    public void unlinkPanel() {
        linkedPanels.clear();
    }

    public PanelCodigoEntity getLinkedPanel() {
        if (linkedPanels.isEmpty()) return null;
        Vec3 relPos = linkedPanels.get(0);
        Vec3 expectedPos = this.position().add(relPos);
        List<PanelCodigoEntity> found = this.level().getEntitiesOfClass(PanelCodigoEntity.class,
                AABB.ofSize(expectedPos, 5, 5, 5),
                p -> p.position().distanceToSqr(expectedPos) < 1.5);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Un interruptor puede alimentar varios teclados (el de salida y el del ático). */
    public void linkTeclado(PanelTecladoEntity teclado) {
        if (teclado == null) return;
        Vec3 relativePos = teclado.position().subtract(this.position());
        if (!linkedTeclados.contains(relativePos)) {
            linkedTeclados.add(relativePos);
        }
        // El teclado nace encendido; al colgarlo de un interruptor, este manda.
        teclado.setPowered(isOn());
    }

    public void unlinkAllTeclados() {
        forEachLinkedTeclado(teclado -> teclado.setPowered(true)); // sueltos funcionan por su cuenta
        linkedTeclados.clear();
    }

    public void forEachLinkedTeclado(java.util.function.Consumer<PanelTecladoEntity> action) {
        for (Vec3 relPos : linkedTeclados) {
            Vec3 expectedPos = this.position().add(relPos);
            List<PanelTecladoEntity> found = this.level().getEntitiesOfClass(PanelTecladoEntity.class,
                    AABB.ofSize(expectedPos, 5, 5, 5),
                    p -> p.position().distanceToSqr(expectedPos) < 1.5);
            if (!found.isEmpty()) {
                action.accept(found.get(0));
            }
        }
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (player.level().isClientSide) return;

        if (areAllCablesReady()) {
            boolean newState = !isOn();
            ModSounds.playAt(this, newState ? ModSounds.SWITCH_ON : ModSounds.SWITCH_OFF, 0.9F, 1.0F);
            setState(newState ? 1 : 0);
        } else {
            // Chispazo y aviso vago: que la palanca no responda es la pista, sin
            // decirle al jugador que el fallo está en los cables.
            ModSounds.playAt(this, ModSounds.SWITCH_ON, 0.5F, 1.0F);
            ModSounds.playAt(this, ModSounds.SPARK_FAIL, 0.8F, 1.0F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cNo pasa corriente."), true);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("State", getState());
        Util.saveLinkedList(tag, "LinkedCables", linkedCables);
        Util.saveLinkedList(tag, "LinkedUblablas", linkedUblablas);
        Util.saveLinkedList(tag, "LinkedPanels", linkedPanels);
        Util.saveLinkedList(tag, "LinkedTeclados", linkedTeclados);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(tag.getInt("State"));
        linkedCables.clear();
        linkedCables.addAll(Util.loadLinkedList(tag, "LinkedCables"));
        linkedUblablas.clear();
        linkedUblablas.addAll(Util.loadLinkedList(tag, "LinkedUblablas"));
        linkedPanels.clear();
        linkedPanels.addAll(Util.loadLinkedList(tag, "LinkedPanels"));
        linkedTeclados.clear();
        linkedTeclados.addAll(Util.loadLinkedList(tag, "LinkedTeclados"));
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "interruptor_controller",
                0,
                state -> isOn() ? state.setAndContinue(ANIMATION_ON) : state.setAndContinue(ANIMATION_OFF)
        ));
    }

    public List<Vec3> getLinkedCables() {
        return linkedCables;
    }
}