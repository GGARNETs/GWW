package com.github.razorplay01.entity.custom;

import com.github.darkpred.morehitboxes.api.EntityHitboxData;
import com.github.darkpred.morehitboxes.api.EntityHitboxDataFactory;
import com.github.darkpred.morehitboxes.api.GeckoLibMultiPartEntity;
import com.github.darkpred.morehitboxes.api.MultiPart;
import com.github.razorplay01.config.GwwPuzzles;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.custom.util.MultiPartHitboxes;
import com.github.razorplay01.entity.custom.util.NearbyPlayers;
import com.github.razorplay01.entity.custom.util.SelfHealingHitboxes;
import com.github.razorplay01.sound.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import java.util.List;

import static com.github.razorplay01.entity.custom.util.Util.loadLinkedList;
import static com.github.razorplay01.entity.custom.util.Util.saveLinkedList;

/**
 * Teclado numérico 0-9. Es el sucesor del panel de figuras, pero como entidad
 * NUEVA: el panel viejo sigue existiendo tal cual y las salas migran cambiando
 * la entidad, no el mod.
 * <p>
 * El código no vive en la entidad sino en config/GWW/puzzles.yml, buscado por el
 * NOMBRE del teclado (se pone con /escaperoom config teclado ... setname). Se
 * relee al empezar cada intento (primer dígito con el buffer vacío): una sala a
 * mitad de intento no cambia de código por un /escaperoom reload.
 * <p>
 * A diferencia del panel de figuras, el fallo no se detecta tecla a tecla: se
 * evalúa el código completo al juntar todos los dígitos. Si fallara al primer
 * dígito equivocado, se podría sacar el código probando dígito a dígito.
 */
public class PanelTecladoEntity extends BaseEntity implements GeckoLibMultiPartEntity<PanelTecladoEntity>, SelfHealingHitboxes {
    private EntityHitboxData<PanelTecladoEntity> hitboxData;
    private final List<Vec3> linkedDoors = new ArrayList<>();

    private static final EntityDataAccessor<Boolean> PUZZLE_SOLVED =
            SynchedEntityData.defineId(PanelTecladoEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ver el comentario homónimo en PanelCodigoEntity: sigue a la puerta, no al puzzle. */
    private static final EntityDataAccessor<Boolean> DOORS_OPEN =
            SynchedEntityData.defineId(PanelTecladoEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Encendido por defecto: el teclado del ático no cuelga de ningún interruptor.
     * Si se vincula a un interruptor industrial, este manda sobre el estado.
     */
    private static final EntityDataAccessor<Boolean> POWERED =
            SynchedEntityData.defineId(PanelTecladoEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int LOCK_DURATION_TICKS = 40;
    private static final String FALLBACK_CODE = "1234";

    // Solo el servidor necesita el progreso del intento: no se sincroniza nada
    // que el cliente no dibuje.
    private String currentInput = "";
    private String cachedCode = "";
    private boolean locked = false;
    private int lockTimer = 0;
    private boolean missingConfigWarned = false;

    public PanelTecladoEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
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
        builder.define(PUZZLE_SOLVED, false);
        builder.define(DOORS_OPEN, false);
        builder.define(POWERED, true);
    }

    /** Nombre con el que este teclado busca su código en puzzles.yml. */
    public String getTecladoName() {
        return this.getCustomName() == null ? "" : this.getCustomName().getString();
    }

    public boolean areDoorsOpen() {
        return this.entityData.get(DOORS_OPEN);
    }

    private void setDoorsOpen(boolean open) {
        this.entityData.set(DOORS_OPEN, open);
    }

    public boolean isPowered() {
        return this.entityData.get(POWERED);
    }

    public void setPowered(boolean powered) {
        this.entityData.set(POWERED, powered);
        if (powered && isSolved() && !this.level().isClientSide) {
            updateAllLinkedDoors();
        }
    }

    public boolean isSolved() {
        return this.entityData.get(PUZZLE_SOLVED);
    }

    private void setSolved(boolean solved) {
        this.entityData.set(PUZZLE_SOLVED, solved);
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * El código del intento en curso. Se congela al teclear el primer dígito y se
     * suelta al resolver, fallar o resetear: así un reload del yml no cambia la
     * clave debajo de los dedos de un equipo.
     */
    private String activeCode() {
        if (cachedCode.isEmpty()) {
            String configured = GwwPuzzles.tecladoCode(getTecladoName());
            if (configured == null) {
                if (!missingConfigWarned) {
                    missingConfigWarned = true;
                    com.github.razorplay01.GWW.LOGGER.warn(
                            "[GWW] El teclado '{}' en {} no tiene codigo en puzzles.yml; usa el codigo {} por defecto.",
                            getTecladoName().isEmpty() ? "(sin nombre)" : getTecladoName(),
                            this.blockPosition().toShortString(), FALLBACK_CODE);
                }
                configured = FALLBACK_CODE;
            }
            cachedCode = configured;
            GwwDebug.log(GwwDebug.Category.PUZZLE, "Teclado '{}' arranca intento con codigo de {} digitos",
                    getTecladoName(), cachedCode.length());
        }
        return cachedCode;
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PuzzleSolved", isSolved());
        tag.putBoolean("DoorsOpen", areDoorsOpen());
        tag.putBoolean("Powered", isPowered());
        tag.putString("CurrentInput", currentInput);
        tag.putString("CachedCode", cachedCode);
        tag.putBoolean("PuzzleLocked", locked);
        tag.putInt("LockTimer", lockTimer);
        saveLinkedList(tag, "LinkedDoors", linkedDoors);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSolved(tag.getBoolean("PuzzleSolved"));
        setDoorsOpen(tag.getBoolean("DoorsOpen"));
        // Directo al dato: setPowered dispararía updateAllLinkedDoors en plena carga.
        this.entityData.set(POWERED, !tag.contains("Powered") || tag.getBoolean("Powered"));
        currentInput = tag.getString("CurrentInput");
        cachedCode = tag.getString("CachedCode");
        locked = tag.getBoolean("PuzzleLocked");
        lockTimer = tag.getInt("LockTimer");
        linkedDoors.clear();
        linkedDoors.addAll(loadLinkedList(tag, "LinkedDoors"));
    }

    // ==================== TICK ====================

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && locked) {
            lockTimer--;
            if (lockTimer <= 0) {
                locked = false;
                lockTimer = 0;
                currentInput = "";
                ModSounds.playAt(this, ModSounds.PANEL_READY, 0.8F, 1.0F);
                NearbyPlayers.within(this, this.getBoundingBox().inflate(8.0))
                        .forEach(p -> p.displayClientMessage(
                                Component.literal("§7El teclado vuelve a estar operativo."), true));
            }
        }
    }

    @Override
    public void rebuildHitboxesIfMissing() {
        if (hitboxData != null && hitboxData.hasCustomParts()) {
            return;
        }
        EntityHitboxData<PanelTecladoEntity> rebuilt = MultiPartHitboxes.rebuild(this);
        if (rebuilt != null) {
            hitboxData = rebuilt;
            MultiPartHitboxes.registerParts(this);
        }
    }

    // ==================== INTERACCIÓN ====================

    @Override
    public InteractionResult interactAt(Player player, Vec3 hitVec, InteractionHand hand) {
        // El clic sobre las sub-hitboxes llega directo por interactAt, sin pasar por el
        // filtro vanilla de espectadores: hay que cortarlo aquí.
        if (hand != InteractionHand.MAIN_HAND || this.level().isClientSide || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!isPowered()) {
            ModSounds.playAt(this, ModSounds.PANEL_OFF, 0.7F, 1.0F);
            player.displayClientMessage(Component.literal("§cEl teclado está apagado."), true);
            return InteractionResult.PASS;
        }

        Vec3 worldHitVec = this.position().add(hitVec);

        List<MultiPart<PanelTecladoEntity>> panelParts = this.hitboxData.getCustomParts();

        MultiPart<PanelTecladoEntity> closestPart = null;
        double closestDist = Double.MAX_VALUE;

        for (MultiPart<PanelTecladoEntity> part : panelParts) {
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

    private MultiPart<PanelTecladoEntity> findClosestPartByRaycast(
            Player player,
            List<MultiPart<PanelTecladoEntity>> parts) {

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        MultiPart<PanelTecladoEntity> closestPart = null;
        double closestHitDist = Double.MAX_VALUE;

        for (MultiPart<PanelTecladoEntity> part : parts) {
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

    private void handlePartInteract(Player player, MultiPart<PanelTecladoEntity> part) {
        String partName = part.getPartName();

        if (partName.equals("button")) {
            handleStatusButton(player);
            return;
        }
        if (partName.length() != 1 || !Character.isDigit(partName.charAt(0))) {
            return;
        }
        if (isSolved()) {
            return;
        }

        int digit = partName.charAt(0) - '0';
        // Cada tecla con su tono, como un teléfono: el beep es el tacto de la tecla,
        // no dice nada del resultado.
        ModSounds.playAt(this, ModSounds.BUTTON_PRESS, 0.8F, 0.76F + digit * 0.045F);

        if (locked) {
            ModSounds.playAt(this, ModSounds.PANEL_LOCKED, 0.8F, 1.0F);
            player.displayClientMessage(Component.literal("§7El teclado está bloqueado, espera un momento."), true);
            return;
        }

        String code = activeCode();
        currentInput += partName;
        GwwDebug.log(GwwDebug.Category.PUZZLE, "Teclado '{}': digito {} ({}/{})",
                getTecladoName(), digit, currentInput.length(), code.length());
        player.displayClientMessage(Component.literal(progressLine(code.length())), true);

        if (currentInput.length() >= code.length()) {
            if (currentInput.equals(code)) {
                onPuzzleSolved();
            } else {
                onPuzzleFailed(player);
            }
        }
    }

    /** Progreso enmascarado del intento: "§7[* * _ _]". Cuántos van, nunca cuáles. */
    private String progressLine(int codeLength) {
        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < codeLength; i++) {
            sb.append(i < currentInput.length() ? '*' : '_');
            if (i < codeLength - 1) {
                sb.append(' ');
            }
        }
        return sb.append(']').toString();
    }

    private void onPuzzleSolved() {
        ModSounds.playAt(this, ModSounds.CODE_CORRECT, 1.0F, 1.0F);
        setSolved(true);
        currentInput = "";
        cachedCode = "";
        GwwDebug.log(GwwDebug.Category.PUZZLE, "Teclado '{}' RESUELTO en {}",
                getTecladoName(), this.blockPosition().toShortString());
        updateAllLinkedDoors();
    }

    private void onPuzzleFailed(Player player) {
        ModSounds.playAt(this, ModSounds.CODE_WRONG, 1.0F, 1.0F);
        locked = true;
        lockTimer = LOCK_DURATION_TICKS;
        currentInput = "";
        cachedCode = "";
        GwwDebug.log(GwwDebug.Category.PUZZLE, "Teclado '{}': codigo incorrecto", getTecladoName());
        player.displayClientMessage(Component.literal("§cCódigo incorrecto. El teclado se ha reiniciado."), true);
    }

    private void handleStatusButton(Player player) {
        ModSounds.playAt(this, ModSounds.BUTTON_PRESS, 0.8F, 0.92F);
        if (isSolved()) {
            toggleLinkedDoors();
        } else if (locked) {
            ModSounds.playAt(this, ModSounds.PANEL_LOCKED, 0.8F, 1.0F);
            player.displayClientMessage(Component.literal("§7El teclado está bloqueado, espera un momento."), true);
        }
    }

    public void resetPuzzle() {
        setSolved(false);
        setDoorsOpen(false);
        locked = false;
        lockTimer = 0;
        currentInput = "";
        cachedCode = "";
        missingConfigWarned = false;
    }

    // ==================== ENLACES CON PUERTAS ====================

    /** Acepta puertas metálicas y la puerta del ático (que deja de pedir la llave). */
    public boolean linkDoor(BaseEntity door) {
        if (!(door instanceof PuertaMetalicaEntity) && !(door instanceof PuertaAticoEntity)) {
            return false;
        }
        Vec3 relativePos = door.position().subtract(this.position());
        if (!linkedDoors.contains(relativePos)) {
            linkedDoors.add(relativePos);
            if (door instanceof PuertaAticoEntity atico) {
                atico.setKeypadControlled(true);
            }
            updateAllLinkedDoors();
        }
        return true;
    }

    /** Al desvincular, las puertas de ático vuelven a aceptar la llave. */
    public void unlinkAllDoors() {
        forEachLinkedAtico(atico -> atico.setKeypadControlled(false));
        linkedDoors.clear();
    }

    public int getLinkedDoorsCount() {
        return linkedDoors.size();
    }

    public void updateAllLinkedDoors() {
        if (!isSolved() || !isPowered()) {
            return;
        }
        applyToLinkedDoors(true);
    }

    private void toggleLinkedDoors() {
        applyToLinkedDoors(!areDoorsOpen());
    }

    private void applyToLinkedDoors(boolean open) {
        setDoorsOpen(open);

        Vec3 panelPos = this.position();
        for (Vec3 relPos : linkedDoors) {
            Vec3 absolutePos = panelPos.add(relPos);
            AABB area = AABB.ofSize(absolutePos, 5, 5, 5);

            this.level().getEntitiesOfClass(PuertaMetalicaEntity.class, area,
                            d -> d.position().distanceToSqr(absolutePos) < 3.0)
                    .forEach(door -> door.setOpen(open));

            this.level().getEntitiesOfClass(PuertaAticoEntity.class, area,
                            d -> d.position().distanceToSqr(absolutePos) < 3.0)
                    .forEach(open ? PuertaAticoEntity::openFromKeypad : PuertaAticoEntity::closeFromKeypad);
        }
    }

    private void forEachLinkedAtico(java.util.function.Consumer<PuertaAticoEntity> action) {
        Vec3 panelPos = this.position();
        for (Vec3 relPos : linkedDoors) {
            Vec3 absolutePos = panelPos.add(relPos);
            this.level().getEntitiesOfClass(PuertaAticoEntity.class,
                            AABB.ofSize(absolutePos, 5, 5, 5),
                            d -> d.position().distanceToSqr(absolutePos) < 3.0)
                    .forEach(action);
        }
    }

    // ==================== INTERFACES ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public void handleNormalInteract(Player player) {
    }

    @Override
    public EntityHitboxData<PanelTecladoEntity> getEntityHitboxData() {
        if (hitboxData == null) {
            hitboxData = EntityHitboxDataFactory.create(this);
        }
        return hitboxData;
    }

    @Override
    public boolean partHurt(MultiPart<PanelTecladoEntity> multiPart, @NotNull DamageSource damageSource, float v) {
        return false;
    }
}
