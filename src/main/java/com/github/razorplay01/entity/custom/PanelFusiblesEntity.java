package com.github.razorplay01.entity.custom;

import com.github.darkpred.morehitboxes.api.EntityHitboxData;
import com.github.darkpred.morehitboxes.api.EntityHitboxDataFactory;
import com.github.darkpred.morehitboxes.api.GeckoLibMultiPartEntity;
import com.github.darkpred.morehitboxes.api.MultiPart;
import com.github.razorplay01.config.GwwPuzzles;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.custom.util.MultiPartHitboxes;
import com.github.razorplay01.entity.custom.util.SelfHealingHitboxes;
import com.github.razorplay01.item.ModItems;
import com.github.razorplay01.sound.ModSounds;
import lombok.Getter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.ArrayList;
import java.util.List;

import static com.github.razorplay01.entity.custom.util.Util.loadLinkedList;
import static com.github.razorplay01.entity.custom.util.Util.saveLinkedList;

@Getter
public class PanelFusiblesEntity extends BaseEntity implements GeckoLibMultiPartEntity<PanelFusiblesEntity>, SelfHealingHitboxes {
    private EntityHitboxData<PanelFusiblesEntity> hitboxData;

    public static final int FUSE_NONE = 0;
    public static final int FUSE_ROJO = 1;
    public static final int FUSE_VERDE = 2;
    public static final int FUSE_AZUL = 3;
    public static final int FUSE_AMARILLO = 4;
    public static final int FUSE_VIOLETA = 5;

    /** 8 slots: los 4 primeros son el circuito 1, los 4 últimos el circuito 2. */
    public static final int TOTAL_SLOTS = 8;
    public static final int SLOTS_PER_CIRCUIT = 4;

    private static final EntityDataAccessor<Integer> SLOT_1 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT_2 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT_3 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT_4 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT_5 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT_6 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT_7 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT_8 = SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.DIRECTION);

    private static final EntityDataAccessor<Boolean> PUZZLE_1_SOLVED =
            SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PUZZLE_2_SOLVED =
            SynchedEntityData.defineId(PanelFusiblesEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer>[] SLOTS = new EntityDataAccessor[]{
            SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7, SLOT_8
    };

    private final List<Vec3> linkedTurtlesPuzzle1 = new ArrayList<>();
    private final List<Vec3> linkedTurtlesPuzzle2 = new ArrayList<>();
    private final List<Vec3> linkedDoors = new ArrayList<>();
    private static final String[] PART_NAMES = {"1", "2", "3", "4", "5", "6", "7", "8"};

    public static final String[] FUSE_BONE_NAMES = {
            "fusil_1", "fusil_2", "fusil_3", "fusil_4",
            "fusil_5", "fusil_6", "fusil_7", "fusil_8"
    };

    /** Huesos de la forma alternativa: la llevan los fusibles amarillo y violeta. */
    public static final String[] FUSE_ALT_BONE_NAMES = {
            "fusil_1_b", "fusil_2_b", "fusil_3_b", "fusil_4_b",
            "fusil_5_b", "fusil_6_b", "fusil_7_b", "fusil_8_b"
    };

    /** true si ese tipo de fusible se dibuja con la forma alternativa (con anillo). */
    public static boolean usesAltShape(int fuseType) {
        return fuseType == FUSE_AMARILLO || fuseType == FUSE_VIOLETA;
    }

    private static final int[] DEFAULT_SOLUTION = {FUSE_ROJO, FUSE_VERDE, FUSE_AZUL, FUSE_AMARILLO};

    // Solución de respaldo por panel (NBT, vía setsolution). La manda puzzles.yml:
    // si el yml trae el circuito, esto solo se usa cuando esa sección falta.
    private int[] puzzle1Solution = DEFAULT_SOLUTION.clone();
    private int[] puzzle2Solution = DEFAULT_SOLUTION.clone();

    // Solución efectiva del ciclo de vida de esta entidad. Se resuelve (yml o NBT)
    // en el primer uso y no cambia hasta el próximo paste/reset de la sala: un
    // /escaperoom reload no le cambia la solución a una partida en curso.
    private int[] activeSolution1 = null;
    private int[] activeSolution2 = null;

    public PanelFusiblesEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
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
        builder.define(SLOT_1, FUSE_NONE);
        builder.define(SLOT_2, FUSE_NONE);
        builder.define(SLOT_3, FUSE_NONE);
        builder.define(SLOT_4, FUSE_NONE);
        builder.define(SLOT_5, FUSE_NONE);
        builder.define(SLOT_6, FUSE_NONE);
        builder.define(SLOT_7, FUSE_NONE);
        builder.define(SLOT_8, FUSE_NONE);
        builder.define(DATA_FACING, Direction.NORTH);
        builder.define(PUZZLE_1_SOLVED, false);
        builder.define(PUZZLE_2_SOLVED, false);
    }

    public int getFuseSlot(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return FUSE_NONE;
        return entityData.get(SLOTS[index]);
    }

    public void setSlot(int index, int fuseType) {
        if (index < 0 || index >= TOTAL_SLOTS) return;
        entityData.set(SLOTS[index], fuseType);
    }

    public boolean hasSlot(int index) {
        return getFuseSlot(index) != FUSE_NONE;
    }

    public boolean isPuzzle1Solved() {
        return entityData.get(PUZZLE_1_SOLVED);
    }

    public boolean isPuzzle2Solved() {
        return entityData.get(PUZZLE_2_SOLVED);
    }

    public boolean areBothPuzzlesSolved() {
        return isPuzzle1Solved() && isPuzzle2Solved();
    }

    public void linkTurtle(int puzzleId, LuzTortugaEntity turtle, Vec3 roomCenter) {
        if (turtle == null || roomCenter == null) return;

        Vec3 relativePos = turtle.position().subtract(roomCenter);

        List<Vec3> list = (puzzleId == 1) ? linkedTurtlesPuzzle1 : linkedTurtlesPuzzle2;

        if (!list.contains(relativePos)) {
            list.add(relativePos);

            int currentState = (puzzleId == 1)
                    ? (isPuzzle1Solved() ? 2 : (areAllSlotsFilled(1) ? 1 : 0))
                    : (isPuzzle2Solved() ? 2 : (areAllSlotsFilled(2) ? 1 : 0));

            turtle.setState(currentState);

            updateLinkedTurtles(puzzleId, currentState);
        }
    }

    public boolean areAllSlotsFilled(int puzzleId) {
        int base = (puzzleId == 1) ? 0 : SLOTS_PER_CIRCUIT;
        for (int i = 0; i < SLOTS_PER_CIRCUIT; i++) {
            if (!hasSlot(base + i)) {
                return false;
            }
        }
        return true;
    }

    public void unlinkAllTurtles() {
        linkedTurtlesPuzzle1.clear();
        linkedTurtlesPuzzle2.clear();
    }

    public void linkDoor(PuertaMetalicaEntity door, Vec3 roomCenter) {
        if (door == null || roomCenter == null) return;

        Vec3 relativePos = door.position().subtract(roomCenter);

        if (!linkedDoors.contains(relativePos)) {
            linkedDoors.add(relativePos);
            updateDoorState(door);
        }
    }

    public void unlinkAllDoors() {
        linkedDoors.clear();
    }

    public int getLinkedDoorsCount() {
        return linkedDoors.size();
    }

    private void updateDoorState(PuertaMetalicaEntity door) {
        door.setOpen(areBothPuzzlesSolved());
    }

    public void updateAllLinkedDoors() {
        if (linkedDoors.isEmpty()) return;

        Vec3 panelPos = this.position();
        boolean shouldOpen = areBothPuzzlesSolved();

        for (Vec3 relPos : linkedDoors) {
            Vec3 absolutePos = panelPos.add(relPos);

            this.level().getEntitiesOfClass(PuertaMetalicaEntity.class,
                            AABB.ofSize(absolutePos, 5, 5, 5),
                            d -> d.position().distanceToSqr(absolutePos) < 3.0)
                    .forEach(door -> door.setOpen(shouldOpen));
        }
    }

    public void updateLinkedTurtles(int puzzleId, int targetState) {
        List<Vec3> linked = (puzzleId == 1) ? linkedTurtlesPuzzle1 : linkedTurtlesPuzzle2;
        if (linked.isEmpty()) return;

        Vec3 panelPos = this.position();

        for (Vec3 relPos : linked) {
            Vec3 absoluteTargetPos = panelPos.add(relPos);

            AABB searchArea = AABB.ofSize(absoluteTargetPos, 5.0, 5.0, 5.0);

            // Solo la tortuga más cercana a la posición guardada: si las tortugas de
            // ambos puzzles están montadas juntas, actualizar todas las del área
            // cambiaría también la del otro puzzle.
            LuzTortugaEntity closest = null;
            double closestDist = 2.0;
            for (LuzTortugaEntity t : this.level().getEntitiesOfClass(LuzTortugaEntity.class, searchArea)) {
                double dist = t.position().distanceToSqr(absoluteTargetPos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = t;
                }
            }
            if (closest != null) {
                closest.setState(targetState);
            }
        }
    }

    /** Verifica si el circuito indicado tiene la combinación correcta. */
    private boolean checkPuzzle(int puzzleId) {
        int base = (puzzleId == 1) ? 0 : SLOTS_PER_CIRCUIT;
        int[] solution = getSolution(puzzleId);
        for (int i = 0; i < SLOTS_PER_CIRCUIT; i++) {
            if (getFuseSlot(base + i) != solution[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Solución efectiva del circuito: la de puzzles.yml si existe, si no la guardada
     * en el panel. Se congela en el primer uso de esta entidad (ver activeSolution).
     */
    public int[] getSolution(int puzzleId) {
        int[] active = (puzzleId == 1) ? activeSolution1 : activeSolution2;
        if (active == null) {
            int[] fromYml = GwwPuzzles.fusiblesCircuito(puzzleId);
            active = (fromYml != null) ? fromYml
                    : (puzzleId == 1 ? puzzle1Solution : puzzle2Solution).clone();
            if (puzzleId == 1) {
                activeSolution1 = active;
            } else {
                activeSolution2 = active;
            }
            GwwDebug.log(GwwDebug.Category.PUZZLE, "Panel de fusibles fija circuito {} = {} ({})",
                    puzzleId, formatFuseList(active), fromYml != null ? "puzzles.yml" : "NBT");
        }
        return active.clone();
    }

    private static String formatFuseList(int[] solution) {
        StringBuilder sb = new StringBuilder();
        for (int fuse : solution) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(getColorName(fuse));
        }
        return sb.toString();
    }

    /**
     * Cambia la solución de respaldo del panel y re-evalúa con los fusibles ya
     * colocados. Ojo: si puzzles.yml define ese circuito, el yml sigue mandando.
     */
    public void setSolution(int puzzleId, int[] solution) {
        if (solution == null || solution.length != SLOTS_PER_CIRCUIT) return;
        if (puzzleId == 1) {
            puzzle1Solution = solution.clone();
            activeSolution1 = null;
        } else {
            puzzle2Solution = solution.clone();
            activeSolution2 = null;
        }
        reevaluatePuzzle(puzzleId);
        updateAllLinkedDoors();
    }

    private void reevaluatePuzzle(int puzzleId) {
        boolean bothWereSolved = areBothPuzzlesSolved();
        boolean allFilled = areAllSlotsFilled(puzzleId);
        boolean solved = allFilled && checkPuzzle(puzzleId);
        entityData.set(puzzleId == 1 ? PUZZLE_1_SOLVED : PUZZLE_2_SOLVED, solved);
        updateLinkedTurtles(puzzleId, solved ? 2 : (allFilled ? 1 : 0));

        // El circuito solo canta cuando cierran las dos mitades: es el aviso de que
        // el panel entero está resuelto, no de que un hueco quedó bien.
        if (!bothWereSolved && areBothPuzzlesSolved()) {
            ModSounds.playAt(this, ModSounds.CIRCUIT_COMPLETE, 1.0F, 1.0F);
        }
    }

    /**
     * Click seco al manipular un fusible. Es el único aviso que recibe el jugador:
     * el resultado del puzzle se lee en las luces del panel, no en el chat.
     */
    private void playFuseSound(boolean inserting) {
        ModSounds.playAt(this, inserting ? ModSounds.FUSE_INSERT : ModSounds.FUSE_REMOVE, 0.8F, 1.0F);
    }

    public int getLinkedCount(int puzzleId) {
        return (puzzleId == 1 ? linkedTurtlesPuzzle1 : linkedTurtlesPuzzle2).size();
    }

    private int partNameToIndex(String partName) {
        for (int i = 0; i < PART_NAMES.length; i++) {
            if (PART_NAMES[i].equals(partName)) {
                return i;
            }
        }
        return -1;
    }

    private int itemToFuseType(ItemStack stack) {
        if (stack.is(ModItems.FUSIBLE_ROJO)) return FUSE_ROJO;
        if (stack.is(ModItems.FUSIBLE_VERDE)) return FUSE_VERDE;
        if (stack.is(ModItems.FUSIBLE_AZUL)) return FUSE_AZUL;
        if (stack.is(ModItems.FUSIBLE_AMARILLO)) return FUSE_AMARILLO;
        if (stack.is(ModItems.FUSIBLE_VIOLETA)) return FUSE_VIOLETA;
        return FUSE_NONE;
    }

    private Item fuseTypeToItem(int fuseType) {
        return switch (fuseType) {
            case FUSE_ROJO -> ModItems.FUSIBLE_ROJO;
            case FUSE_VERDE -> ModItems.FUSIBLE_VERDE;
            case FUSE_AZUL -> ModItems.FUSIBLE_AZUL;
            case FUSE_AMARILLO -> ModItems.FUSIBLE_AMARILLO;
            case FUSE_VIOLETA -> ModItems.FUSIBLE_VIOLETA;
            default -> null;
        };
    }

    public static String getColorName(int fuseType) {
        return switch (fuseType) {
            case FUSE_ROJO -> "Rojo";
            case FUSE_VERDE -> "Verde";
            case FUSE_AZUL -> "Azul";
            case FUSE_AMARILLO -> "Amarillo";
            case FUSE_VIOLETA -> "Violeta";
            default -> "Desconocido";
        };
    }

    /**
     * Lee una solución de 4 fusibles del NBT; si falta, es inválida o viene de un
     * panel de 6 slots (longitud 3), conserva el fallback.
     */
    private static int[] readSolution(CompoundTag tag, String key, int[] fallback) {
        int[] loaded = tag.getIntArray(key);
        if (loaded.length != SLOTS_PER_CIRCUIT) return fallback;
        for (int fuse : loaded) {
            if (fuse < FUSE_ROJO || fuse > FUSE_VIOLETA) return fallback;
        }
        return loaded;
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
    public void tick() {
        super.tick();
    }

    @Override
    public void rebuildHitboxesIfMissing() {
        if (hitboxData != null && hitboxData.hasCustomParts()) {
            return;
        }
        EntityHitboxData<PanelFusiblesEntity> rebuilt = MultiPartHitboxes.rebuild(this);
        if (rebuilt != null) {
            hitboxData = rebuilt;
            MultiPartHitboxes.registerParts(this);
        }
    }

    @Override
    public void handleNormalInteract(Player player) {
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hitVec, InteractionHand hand) {
        // El clic sobre las sub-hitboxes llega directo por interactAt, sin pasar por el
        // filtro vanilla de espectadores: hay que cortarlo aquí.
        if (hand != InteractionHand.MAIN_HAND || this.level().isClientSide || player.isSpectator()) {
            return super.interactAt(player, hitVec, hand);
        }

        Vec3 worldHitVec = this.position().add(hitVec);
        final double TOLERANCE = 0.05;

        List<MultiPart<PanelFusiblesEntity>> panelParts = this.hitboxData.getCustomParts();

        MultiPart<PanelFusiblesEntity> closestPart = null;
        double closestDist = Double.MAX_VALUE;

        for (MultiPart<PanelFusiblesEntity> part : panelParts) {
            AABB area = part.getEntity().getBoundingBox().inflate(TOLERANCE);

            if (area.contains(worldHitVec)) {
                Vec3 center = area.getCenter();
                double dist = center.distanceToSqr(worldHitVec);

                if (dist < closestDist) {
                    closestDist = dist;
                    closestPart = part;
                }
            }
        }

        if (closestPart != null) {
            handlePartInteract(player, closestPart);
            return InteractionResult.SUCCESS;
        }

        return super.interactAt(player, hitVec, hand);
    }

    private void handlePartInteract(Player player, MultiPart<PanelFusiblesEntity> part) {
        String partName = part.getPartName();
        int slotIndex = partNameToIndex(partName);

        if (slotIndex == -1) return;

        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        int currentFuse = getFuseSlot(slotIndex);
        int heldFuse = itemToFuseType(stack);

        boolean changed = false;

        if (currentFuse == FUSE_NONE && heldFuse != FUSE_NONE) {
            // === COLOCAR fusible ===
            setSlot(slotIndex, heldFuse);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            playFuseSound(true);
            changed = true;

        } else if (currentFuse != FUSE_NONE && heldFuse == FUSE_NONE) {
            // === QUITAR fusible ===
            Item fuseItem = fuseTypeToItem(currentFuse);
            setSlot(slotIndex, FUSE_NONE);

            if (!player.isCreative() && fuseItem != null) {
                ItemStack returned = new ItemStack(fuseItem);
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false);
                }
            }
            playFuseSound(false);
            changed = true;

        } else if (currentFuse != FUSE_NONE && heldFuse != FUSE_NONE) {
            // === INTERCAMBIAR fusible ===
            Item oldFuseItem = fuseTypeToItem(currentFuse);
            setSlot(slotIndex, heldFuse);

            if (!player.isCreative()) {
                stack.shrink(1);
                if (oldFuseItem != null) {
                    ItemStack returned = new ItemStack(oldFuseItem);
                    if (!player.getInventory().add(returned)) {
                        player.drop(returned, false);
                    }
                }
            }
            playFuseSound(true);
            changed = true;
        }

        // === VERIFICAR PUZZLE después de cualquier cambio ===
        if (changed) {
            reevaluatePuzzle(slotIndex < SLOTS_PER_CIRCUIT ? 1 : 2);
            updateAllLinkedDoors();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            tag.putInt("Slot" + i, getFuseSlot(i));
        }
        tag.putInt("SlotLayout", TOTAL_SLOTS);
        tag.putString("Facing", getFacing().getSerializedName());
        tag.putBoolean("Puzzle1Solved", isPuzzle1Solved());
        tag.putBoolean("Puzzle2Solved", isPuzzle2Solved());
        tag.putIntArray("Puzzle1Solution", puzzle1Solution);
        tag.putIntArray("Puzzle2Solution", puzzle2Solution);
        saveLinkedList(tag, "LinkedDoors", linkedDoors);
        saveLinkedList(tag, "LinkedPuzzle1", linkedTurtlesPuzzle1);
        saveLinkedList(tag, "LinkedPuzzle2", linkedTurtlesPuzzle2);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SlotLayout")) {
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (tag.contains("Slot" + i)) {
                    setSlot(i, tag.getInt("Slot" + i));
                }
            }
        } else if (tag.contains("Slot0")) {
            // Panel guardado con el modelo viejo de 6 slots (3 por circuito): los
            // fusibles del circuito 2 (slots 3-5) pasan a los slots 4-6 del nuevo.
            for (int i = 0; i < 3; i++) {
                setSlot(i, tag.getInt("Slot" + i));
                setSlot(SLOTS_PER_CIRCUIT + i, tag.getInt("Slot" + (3 + i)));
            }
        }
        if (tag.contains("Facing")) {
            Direction dir = Direction.byName(tag.getString("Facing"));
            if (dir != null) setFacing(dir);
        }
        if (tag.contains("Puzzle1Solved")) {
            entityData.set(PUZZLE_1_SOLVED, tag.getBoolean("Puzzle1Solved"));
        }
        if (tag.contains("Puzzle2Solved")) {
            entityData.set(PUZZLE_2_SOLVED, tag.getBoolean("Puzzle2Solved"));
        }
        puzzle1Solution = readSolution(tag, "Puzzle1Solution", puzzle1Solution);
        puzzle2Solution = readSolution(tag, "Puzzle2Solution", puzzle2Solution);
        linkedTurtlesPuzzle1.clear();
        linkedTurtlesPuzzle1.addAll(loadLinkedList(tag, "LinkedPuzzle1"));

        linkedTurtlesPuzzle2.clear();
        linkedTurtlesPuzzle2.addAll(loadLinkedList(tag, "LinkedPuzzle2"));
        linkedDoors.clear();
        linkedDoors.addAll(loadLinkedList(tag, "LinkedDoors"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    protected AABB makeBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        double height = 1.5;
        double width = 2.5;
        double depth = 0.7;
        double hw = width / 2.0;
        double hd = depth / 2.0;

        if (getFacing().getAxis() == Direction.Axis.Z) {
            return new AABB(x - hw, y, z - hd, x + hw, y + height, z + hd);
        } else {
            return new AABB(x - hd, y, z - hw, x + hd, y + height, z + hw);
        }
    }

    @Override
    public void setYRot(float yaw) {
        super.setYRot(yaw);
        setFacing(Direction.fromYRot(yaw));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        for (EntityDataAccessor<Integer> slot : SLOTS) {
            if (key.equals(slot)) {
                this.refreshDimensions();
                return;
            }
        }
        if (key.equals(DATA_FACING)) {
            this.refreshDimensions();
        }
    }

    @Override
    public EntityHitboxData<PanelFusiblesEntity> getEntityHitboxData() {
        if (hitboxData == null) {
            hitboxData = EntityHitboxDataFactory.create(this);
        }
        return hitboxData;
    }

    @Override
    public boolean partHurt(MultiPart<PanelFusiblesEntity> multiPart, @NotNull DamageSource damageSource, float v) {
        return false;
    }

    public static int getFuseColor(int fuseType) {
        return switch (fuseType) {
            case FUSE_ROJO -> 0xFFFF0000;
            case FUSE_VERDE -> 0xFF00FF00;
            case FUSE_AZUL -> 0xFF0000FF;
            case FUSE_AMARILLO -> 0xFFF2C41D;
            case FUSE_VIOLETA -> 0xFF9C4FE8;
            default -> 0xFFFFFFFF;
        };
    }
}