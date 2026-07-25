package com.github.razorplay01.entity.custom;

import com.github.darkpred.morehitboxes.api.EntityHitboxData;
import com.github.darkpred.morehitboxes.api.EntityHitboxDataFactory;
import com.github.darkpred.morehitboxes.api.GeckoLibMultiPartEntity;
import com.github.darkpred.morehitboxes.api.MultiPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.github.razorplay01.entity.custom.util.MultiPartHitboxes;
import com.github.razorplay01.entity.custom.util.SelfHealingHitboxes;

import static com.github.razorplay01.entity.custom.util.Util.*;

public class PanelCodigoEntity extends BaseEntity implements GeckoLibMultiPartEntity<PanelCodigoEntity>, SelfHealingHitboxes {
    private EntityHitboxData<PanelCodigoEntity> hitboxData;
    private final List<Vec3> linkedDoors = new ArrayList<>();

    private static final EntityDataAccessor<String> CURRENT_INPUT =
            SynchedEntityData.defineId(PanelCodigoEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Boolean> PUZZLE_SOLVED =
            SynchedEntityData.defineId(PanelCodigoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> PUZZLE_LOCKED =
            SynchedEntityData.defineId(PanelCodigoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<String> DATA_CORRECT_SEQUENCE =
            SynchedEntityData.defineId(PanelCodigoEntity.class, EntityDataSerializers.STRING);

    // Nuevo: estado de encendido/apagado
    private static final EntityDataAccessor<Boolean> POWERED =
            SynchedEntityData.defineId(PanelCodigoEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Si las puertas enlazadas están abiertas. Es lo único que decide el aspecto de la
     * parte de abajo del panel: verde con el candado abierto, o rojo con el cerrado.
     * Va aparte de PUZZLE_SOLVED a propósito: cerrar la puerta vuelve a poner el panel
     * en rojo, pero el puzzle sigue resuelto y no hay que rehacerlo.
     */
    private static final EntityDataAccessor<Boolean> DOORS_OPEN =
            SynchedEntityData.defineId(PanelCodigoEntity.class, EntityDataSerializers.BOOLEAN);

    // Los nombres tienen que coincidir con los de data/gww/hitboxes/panel_codigo.json.
    // Las cuatro figuras del panel son las mismas que las de la pared: triángulo,
    // hexágono, pentágono y cuadrado. No hay ningún círculo.
    private static final List<String> DEFAULT_SEQUENCE = Arrays.asList(
            "triangulo", "hexagono", "pentagono", "cuadrado"
    );

    /**
     * Los dos botones de la izquierda estaban mal nombrados: el de arriba, que es un
     * hexágono, se llamaba "circulo", y el de abajo, que es un pentágono, se llamaba
     * "hexagono". Las secuencias guardadas antes del arreglo traen los nombres viejos,
     * así que se traducen al leerlas o el botón de abajo se quedaría muerto.
     */
    private static final Map<String, String> LEGACY_PART_NAMES = Map.of(
            "circulo", "hexagono",
            "hexagono", "pentagono"
    );

    private static final int LOCK_DURATION_TICKS = 40;
    private int lockTimer = 0;

    public PanelCodigoEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.hitboxData = EntityHitboxDataFactory.create(this);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CURRENT_INPUT, "");
        builder.define(PUZZLE_SOLVED, false);
        builder.define(PUZZLE_LOCKED, false);
        builder.define(DATA_CORRECT_SEQUENCE, String.join(",", DEFAULT_SEQUENCE));
        builder.define(POWERED, false);
        builder.define(DOORS_OPEN, false);
    }

    /** Estado visual del panel: verde/desbloqueado si las puertas están abiertas. */
    public boolean areDoorsOpen() {
        return this.entityData.get(DOORS_OPEN);
    }

    private void setDoorsOpen(boolean open) {
        this.entityData.set(DOORS_OPEN, open);
    }

    // ---------- Power state ----------
    public boolean isPowered() {
        return this.entityData.get(POWERED);
    }

    public void setPowered(boolean powered) {
        boolean old = isPowered();
        this.entityData.set(POWERED, powered);
        if (powered && isSolved() && !this.level().isClientSide) {
            // Al encender y ya resuelto, actualizar puertas
            updateAllLinkedDoors();
        }
        // El renderizador leerá directamente powered y solved.
    }

    /**
     * Traduce una secuencia guardada con los nombres viejos. Se hace en una sola
     * pasada: si se aplicara en cadena, el "circulo" convertido en "hexagono" se
     * volvería a convertir en "pentagono".
     */
    private static String migrateLegacyNames(String sequence) {
        if (!sequence.contains("circulo")) {
            return sequence;
        }
        String[] parts = sequence.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = LEGACY_PART_NAMES.getOrDefault(parts[i], parts[i]);
        }
        return String.join(",", parts);
    }

    // ---------- Puzzle state ----------
    public List<String> getCorrectSequence() {
        String data = this.entityData.get(DATA_CORRECT_SEQUENCE);
        if (data == null || data.isEmpty()) {
            return new ArrayList<>(DEFAULT_SEQUENCE);
        }
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

    public void setCorrectSequence(List<String> sequence) {
        if (sequence == null || sequence.isEmpty()) {
            this.entityData.set(DATA_CORRECT_SEQUENCE, String.join(",", DEFAULT_SEQUENCE));
        } else {
            this.entityData.set(DATA_CORRECT_SEQUENCE, String.join(",", sequence));
        }
    }

    public List<String> getCurrentInput() {
        String data = this.entityData.get(CURRENT_INPUT);
        if (data.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

    public void setCurrentInput(List<String> input) {
        this.entityData.set(CURRENT_INPUT, String.join(",", input));
    }

    public boolean isSolved() {
        return this.entityData.get(PUZZLE_SOLVED);
    }

    public void setSolved(boolean solved) {
        this.entityData.set(PUZZLE_SOLVED, solved);
        if (solved && isPowered() && !this.level().isClientSide) {
            updateAllLinkedDoors();
        }
    }

    public boolean isLocked() {
        return this.entityData.get(PUZZLE_LOCKED);
    }

    public void setLocked(boolean locked) {
        this.entityData.set(PUZZLE_LOCKED, locked);
    }

    // ---------- NBT ----------
    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PuzzleSolved", isSolved());
        tag.putString("CurrentInput", this.entityData.get(CURRENT_INPUT));
        tag.putBoolean("PuzzleLocked", isLocked());
        tag.putInt("LockTimer", this.lockTimer);
        saveLinkedList(tag, "LinkedDoors", linkedDoors);
        tag.putString("CorrectSequence", this.entityData.get(DATA_CORRECT_SEQUENCE));
        tag.putBoolean("Powered", isPowered());
        tag.putBoolean("DoorsOpen", areDoorsOpen());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSolved(tag.getBoolean("PuzzleSolved"));
        setDoorsOpen(tag.getBoolean("DoorsOpen"));
        this.entityData.set(CURRENT_INPUT, tag.getString("CurrentInput"));
        setLocked(tag.getBoolean("PuzzleLocked"));
        this.lockTimer = tag.getInt("LockTimer");
        linkedDoors.clear();
        linkedDoors.addAll(loadLinkedList(tag, "LinkedDoors"));
        String seq = tag.getString("CorrectSequence");
        if (!seq.isEmpty()) {
            this.entityData.set(DATA_CORRECT_SEQUENCE, migrateLegacyNames(seq));
        }
        boolean powered = tag.getBoolean("Powered");
        this.entityData.set(POWERED, powered);
        // Si está resuelto y encendido, actualizar puertas (se hará al spawn)
    }

    // ---------- Tick ----------
    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && isLocked()) {
            lockTimer--;
            if (lockTimer <= 0) {
                setLocked(false);
                setCurrentInput(new ArrayList<>());
                lockTimer = 0;
                this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(8.0))
                        .forEach(p -> p.displayClientMessage(
                                Component.literal("§7El panel vuelve a estar operativo."), true));
            }
        }
    }

    @Override
    public void rebuildHitboxesIfMissing() {
        if (hitboxData != null && hitboxData.hasCustomParts()) {
            return;
        }
        EntityHitboxData<PanelCodigoEntity> rebuilt = MultiPartHitboxes.rebuild(this);
        if (rebuilt != null) {
            hitboxData = rebuilt;
            MultiPartHitboxes.registerParts(this);
        }
    }

    // ---------- Interacción ----------
    @Override
    public InteractionResult interactAt(Player player, Vec3 hitVec, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || this.level().isClientSide) {
            return InteractionResult.PASS;
        }
        if (!isPowered()) {
            player.displayClientMessage(Component.literal("§cEl panel está apagado."), true);
            return InteractionResult.PASS;
        }

        Vec3 worldHitVec = new Vec3(
                this.getX() + hitVec.x,
                this.getY() + hitVec.y,
                this.getZ() + hitVec.z
        );

        List<MultiPart<PanelCodigoEntity>> panelParts = this.hitboxData.getCustomParts();

        MultiPart<PanelCodigoEntity> closestPart = null;
        double closestDist = Double.MAX_VALUE;

        for (MultiPart<PanelCodigoEntity> part : panelParts) {
            AABB box = part.getEntity().getBoundingBox();

            if (box.contains(worldHitVec)) {
                double dist = distanceToAABB(box, worldHitVec);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestPart = part;
                }
            }
        }

        if (closestPart == null) {
            closestPart = findClosestPartByRaycast(player, panelParts);
        }

        if (closestPart != null) {
            handlePartInteract(player, closestPart);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private double distanceToAABB(AABB box, Vec3 point) {
        double dx = Math.max(box.minX - point.x, Math.max(0, point.x - box.maxX));
        double dy = Math.max(box.minY - point.y, Math.max(0, point.y - box.maxY));
        double dz = Math.max(box.minZ - point.z, Math.max(0, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private MultiPart<PanelCodigoEntity> findClosestPartByRaycast(
            Player player,
            List<MultiPart<PanelCodigoEntity>> parts) {

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        MultiPart<PanelCodigoEntity> closestPart = null;
        double closestHitDist = Double.MAX_VALUE;

        for (MultiPart<PanelCodigoEntity> part : parts) {
            AABB box = part.getEntity().getBoundingBox().inflate(0.03);
            var hit = box.clip(eyePos, endPos);

            if (hit.isPresent()) {
                double dist = eyePos.distanceToSqr(hit.get());
                if (dist < closestHitDist) {
                    closestHitDist = dist;
                    closestPart = part;
                }
            }
        }

        return closestPart;
    }

    private void handlePartInteract(Player player, MultiPart<PanelCodigoEntity> part) {
        String partName = part.getPartName();

        if (partName.equals("button")) {
            handleStatusButton(player);
            return;
        }

        // Si ya resuelto, no hacer nada
        if (isSolved()) {
            return;
        }

        if (isLocked()) {
            playSound(player, false);
            player.displayClientMessage(Component.literal("§7El panel está bloqueado, espera un momento."), true);
            return;
        }

        List<String> correctSeq = getCorrectSequence();
        if (!correctSeq.contains(partName)) {
            return;
        }

        List<String> input = getCurrentInput();
        int currentStep = input.size();

        // Feedback neutral: el mismo mensaje registre lo que registre, sin contador ni
        // pista de acierto/fallo. Lo único que distingue el resultado es el sonido
        // (campana que sube de tono vs. zumbido).
        player.displayClientMessage(Component.literal("§7Pulsación registrada."), true);

        if (correctSeq.get(currentStep).equals(partName)) {
            input.add(partName);
            setCurrentInput(input);

            playSound(player, true);

            if (input.size() == correctSeq.size()) {
                onPuzzleSolved();
            }

        } else {
            onPuzzleFailed(player);
        }
    }

    private void onPuzzleSolved() {
        setSolved(true);
        setCurrentInput(new ArrayList<>());
        updateAllLinkedDoors();
        // El estado visual se actualiza automáticamente ya que isSolved() cambió.
    }

    private void onPuzzleFailed(Player player) {
        playSound(player, false);

        setLocked(true);
        lockTimer = LOCK_DURATION_TICKS;
        setCurrentInput(new ArrayList<>());
        player.displayClientMessage(Component.literal("§7El panel se ha reiniciado y quedará bloqueado un momento."), true);
    }

    private void handleStatusButton(Player player) {
        if (isSolved()) {
            toggleLinkedDoors();
        } else if (isLocked()) {
            playSound(player, false);
            player.displayClientMessage(Component.literal("§7El panel está bloqueado, espera un momento."), true);
        }
    }

    private void playSound(Player player, boolean success) {
        if (success) {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 1.0F + (getCurrentInput().size() * 0.15F));
        } else {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
        }
    }

    public void resetPuzzle() {
        setSolved(false);
        setLocked(false);
        setCurrentInput(new ArrayList<>());
        setDoorsOpen(false); // el panel vuelve a rojo/bloqueado
        lockTimer = 0;
        // No se actualizan puertas porque no está resuelto
    }

    // ---------- Enlaces con puertas ----------
    public void linkDoor(PuertaMetalicaEntity door, Vec3 roomCenter) {
        if (door == null || roomCenter == null) return;

        Vec3 relativePos = door.position().subtract(roomCenter);

        if (!linkedDoors.contains(relativePos)) {
            linkedDoors.add(relativePos);
            updateAllLinkedDoors();
        }
    }

    public void unlinkAllDoors() {
        linkedDoors.clear();
    }

    public int getLinkedDoorsCount() {
        return linkedDoors.size();
    }

    public List<Vec3> getLinkedDoors() {
        return new ArrayList<>(linkedDoors);
    }

    /** Al resolver el puzzle (o al recuperar la corriente ya resuelto) las puertas se abren. */
    public void updateAllLinkedDoors() {
        if (!isSolved()) return;
        applyToLinkedDoors(true);
    }

    /**
     * El botón de estado abre y cierra las puertas una vez resuelto. Solo cambia la
     * puerta y el aspecto del panel: el puzzle sigue resuelto y no se vuelve a pedir.
     */
    private void toggleLinkedDoors() {
        applyToLinkedDoors(!areDoorsOpen());
    }

    private void applyToLinkedDoors(boolean open) {
        setDoorsOpen(open);

        Vec3 panelPos = this.position();
        for (Vec3 relPos : linkedDoors) {
            Vec3 absolutePos = panelPos.add(relPos);
            this.level().getEntitiesOfClass(PuertaMetalicaEntity.class,
                            AABB.ofSize(absolutePos, 5, 5, 5),
                            d -> d.position().distanceToSqr(absolutePos) < 3.0)
                    .forEach(door -> door.setOpen(open));
        }
    }

    /**
     * Cierra las puertas enlazadas sin tocar el puzzle: sigue resuelto, solo que la
     * puerta vuelve a estar cerrada. Lo usa el Ublabla al ordenar la sala, y como una
     * pulsación del botón vuelve a abrirla, no hay que resolver nada de nuevo.
     */
    public void closeDoorsKeepingSolved() {
        if (areDoorsOpen()) {
            applyToLinkedDoors(false);
        }
    }

    // ---------- Métodos requeridos por interfaces ----------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public void handleNormalInteract(Player player) {
    }

    @Override
    public EntityHitboxData<PanelCodigoEntity> getEntityHitboxData() {
        if (hitboxData == null) {
            hitboxData = EntityHitboxDataFactory.create(this);
        }
        return hitboxData;
    }

    @Override
    public boolean partHurt(MultiPart<PanelCodigoEntity> multiPart, @NotNull DamageSource damageSource, float v) {
        return false;
    }
}