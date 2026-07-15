package com.github.razorplay01.entity.custom;

import com.github.razorplay01.entity.custom.util.Util;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;

public class RejaDuctoEntity extends BaseEntity {

    /** Alcance en el que la reja busca válvulas para saber si la presión ya está bien. */
    private static final double VALVE_SEARCH_RANGE = 100.0;

    public static final int STATE_CLOSED = 0;
    /** A medio abrir: sin corriente pero con la presión aún sin ajustar. No deja pasar. */
    public static final int STATE_SEMI_OPEN = 1;
    public static final int STATE_OPEN = 2;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(
            RejaDuctoEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(RejaDuctoEntity.class, EntityDataSerializers.DIRECTION);

    private static final RawAnimation ANIMATION_IDLE = RawAnimation.begin().thenLoop("animation.idle");
    private static final RawAnimation ANIMATION_SEMI_OPEN = RawAnimation.begin().thenPlayAndHold("animation.semi_open");
    private static final RawAnimation ANIMATION_OPEN = RawAnimation.begin().thenPlayAndHold("animation.open");

    private final List<Vec3> linkedPowerPanels = new ArrayList<>();

    public RejaDuctoEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, STATE_CLOSED);
        builder.define(DATA_FACING, Direction.NORTH);
    }

    public int getRejaState() {
        return this.entityData.get(DATA_STATE);
    }

    public void setRejaState(int state) {
        this.entityData.set(DATA_STATE, state);
    }

    /** Solo cuenta como "abierta" (deja pasar) el estado totalmente abierto. */
    public boolean isOpen() {
        return getRejaState() == STATE_OPEN;
    }

    public boolean isSemiOpen() {
        return getRejaState() == STATE_SEMI_OPEN;
    }

    public void setOpen(boolean open) {
        setRejaState(open ? STATE_OPEN : STATE_CLOSED);
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


    public void linkPowerPanel(PanelEnergiaEntity panel, Vec3 roomCenter) {
        if (panel == null || roomCenter == null) return;
        Vec3 relativePos = panel.position().subtract(roomCenter);
        if (!linkedPowerPanels.contains(relativePos)) {
            linkedPowerPanels.add(relativePos);
        }
    }

    public void unlinkAllPowerPanels() {
        linkedPowerPanels.clear();
    }

    /** Margen al resolver un enlace guardado a la entidad real que hay en esa posición. */
    private static final double LINK_TOLERANCE_SQ = 2.5;

    public boolean isPowerPanelActive() {
        if (linkedPowerPanels.isEmpty()) return true;

        Vec3 rejaPos = this.position();

        for (Vec3 relPos : linkedPowerPanels) {
            Vec3 absolutePos = rejaPos.add(relPos);
            List<PanelEnergiaEntity> panels = this.level().getEntitiesOfClass(PanelEnergiaEntity.class,
                    AABB.ofSize(absolutePos, 5, 5, 5),
                    p -> p.position().distanceToSqr(absolutePos) < LINK_TOLERANCE_SQ);

            if (!panels.isEmpty() && panels.get(0).isActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * True si esta reja está enlazada a ese panel concreto. El enlace lo guarda la
     * reja (como desplazamiento hasta el panel), así que es ella quien puede
     * responder a la pregunta; el panel no tiene referencia de vuelta.
     */
    public boolean isLinkedTo(PanelEnergiaEntity panel) {
        Vec3 rejaPos = this.position();
        for (Vec3 relPos : linkedPowerPanels) {
            if (rejaPos.add(relPos).distanceToSqr(panel.position()) < LINK_TOLERANCE_SQ) {
                return true;
            }
        }
        return false;
    }

    /** True si todas las válvulas de la zona ya están en su estado correcto. */
    private boolean valvesSolved() {
        return ValvulaEntity.allSolved(this.level(), this.getBoundingBox().inflate(VALVE_SEARCH_RANGE));
    }

    /**
     * Se llama cuando el panel de energía corta la corriente. Si las válvulas ya
     * están ajustadas, la reja se abre del todo (como siempre). Si no, queda a medio
     * abrir: la presión no deja pasar hasta que se resuelvan las válvulas.
     */
    public void tryOpenAutomatically() {
        if (isOpen() || !isPowerPanelActive()) {
            return;
        }
        setRejaState(valvesSolved() ? STATE_OPEN : STATE_SEMI_OPEN);
    }

    /**
     * Aviso desde una válvula de que ha cambiado de estado: si la reja estaba a medio
     * abrir y ya no queda presión, termina de abrirse.
     */
    public void notifyValvesChanged() {
        if (isSemiOpen() && valvesSolved()) {
            setRejaState(STATE_OPEN);
        }
    }

    @Override
    public void handleNormalInteract(Player player) {
        if (this.level().isClientSide || !isSemiOpen()) {
            return;
        }
        // Por si las válvulas se resolvieron entre medias, se recomprueba al interactuar.
        if (valvesSolved()) {
            setRejaState(STATE_OPEN);
        } else {
            player.displayClientMessage(Component.literal(
                    "§cLa presión es demasiada para pasar. Ajusta las válvulas."), true);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("State", getRejaState());
        tag.putString("Facing", getFacing().getSerializedName());
        Util.saveLinkedList(tag, "LinkedPowerPanels", linkedPowerPanels);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // "State" es el formato nuevo; los .dat viejos solo traían el booleano "IsOpen".
        if (tag.contains("State")) {
            setRejaState(tag.getInt("State"));
        } else {
            setOpen(tag.getBoolean("IsOpen"));
        }
        if (tag.contains("Facing")) {
            Direction dir = Direction.byName(tag.getString("Facing"));
            if (dir != null) setFacing(dir);
        }
        linkedPowerPanels.clear();
        linkedPowerPanels.addAll(Util.loadLinkedList(tag, "LinkedPowerPanels"));
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "reja_controller",
                0,
                state -> {
                    if (isOpen()) {
                        return state.setAndContinue(ANIMATION_OPEN);
                    }
                    if (isSemiOpen()) {
                        return state.setAndContinue(ANIMATION_SEMI_OPEN);
                    }
                    return state.setAndContinue(ANIMATION_IDLE);
                }
        ));
    }

    public List<Vec3> getLinkedPowerPanels() {
        return linkedPowerPanels;
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
        if (!isOpen()) super.push(entity);
    }

    @Override
    protected void pushEntities() {
        if (!isOpen()) super.pushEntities();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key) || DATA_FACING.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public void setYRot(float yaw) {
        super.setYRot(yaw);
        setFacing(Direction.fromYRot(yaw));
    }

    @Override
    protected @NotNull AABB makeBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        double height = 2.0;
        double width = 2.0;
        double depth = 0.5;
        double hw = width / 2.0;
        double hd = depth / 2.0;

        if (getFacing().getAxis() == Direction.Axis.Z) {
            return new AABB(x - hw, y, z - hd, x + hw, y + height, z + hd);
        } else {
            return new AABB(x - hd, y, z - hw, x + hd, y + height, z + hw);
        }
    }
}