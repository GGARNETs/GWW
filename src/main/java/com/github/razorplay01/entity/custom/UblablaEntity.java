package com.github.razorplay01.entity.custom;

import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.config.GwwSettings;
import com.github.razorplay01.entity.custom.util.EscapeRoomPersistable;
import com.github.razorplay01.entity.custom.util.PuzzleEntityChecker;
import com.github.razorplay01.system.NoiseDetectionSystem;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

public class UblablaEntity extends PathfinderMob implements GeoEntity, EscapeRoomPersistable {

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(UblablaEntity.class, EntityDataSerializers.INT);

    public static final int STATE_PATROL = 0;
    public static final int STATE_ALERT = 1;
    public static final int STATE_INVESTIGATING = 2;
    public static final int STATE_CHASING = 3;
    public static final int STATE_ATTACKING = 4;
    public static final int STATE_CHECKING = 5;

    // Los tiempos de alerta / revisión / investigación y el umbral de ruido salen
    // de config/GWW/settings.yml (GwwSettings), comunes a todas las arenas.
    private static final int CHASE_MAX_DURATION = 200;
    private static final int NOISE_CHECK_INTERVAL = 12;
    private static final double CATCH_DISTANCE_SQ = 5.29;
    private static final double LOSE_TARGET_DISTANCE_SQ = 2500.0;

    @Getter
    @Setter
    private Vec3 patrolCenter;
    @Getter
    private double patrolRadius = 25.0;
    @Getter
    @Setter
    private BlockPos spawnPos;
    @Getter
    @Setter
    private BlockPos investigationTarget;
    @Getter
    private BlockPos jailMin;
    @Getter
    private BlockPos jailMax;

    private int noiseCheckCooldown = 0;
    private int alertTimer = 0;
    private int investigationTimer = 0;
    private int chaseTimer = 0;
    private int chaseMessageCooldown = 0;
    private int checkWaitTimer = 0;
    private final List<Vec3> linkedDoors = new ArrayList<>();
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.walk");
    private static final RawAnimation CHECK = RawAnimation.begin().thenPlay("animation.check");
    private static final RawAnimation CHASE_ANIM = RawAnimation.begin().thenLoop("animation.chase");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.ataque");

    private static final String[] CHASE_MESSAGES = {
            "¡No huyas!",
            "¡Vuelve aquí!",
            "No te haré nada... malo.",
            "¡Todo debe estar en su lugar!"
    };

    public UblablaEntity(EntityType<? extends UblablaEntity> type, Level level) {
        super(type, level);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
        this.setPersistenceRequired();
        this.spawnPos = BlockPos.containing(this.position());
        this.patrolCenter = Vec3.atCenterOf(spawnPos);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 90.0);
    }

    public void linkDoor(PuertaMetalicaUblablaEntity door) {
        Vec3 rel = door.position().subtract(Vec3.atCenterOf(spawnPos));
        linkedDoors.add(rel);
    }

    public void unlinkDoor(PuertaMetalicaUblablaEntity door) {
        Vec3 rel = door.position().subtract(Vec3.atCenterOf(spawnPos));
        linkedDoors.removeIf(v -> v.distanceToSqr(rel) < 0.01);
    }

    public void unlinkAllDoors() {
        linkedDoors.clear();
    }

    public List<Vec3> getLinkedDoors() {
        return Collections.unmodifiableList(linkedDoors);
    }

    private List<PuertaMetalicaUblablaEntity> getLinkedDoorEntities() {
        List<PuertaMetalicaUblablaEntity> doors = new ArrayList<>();
        Vec3 spawnCenter = Vec3.atCenterOf(spawnPos);
        for (Vec3 rel : linkedDoors) {
            Vec3 absPos = spawnCenter.add(rel);
            AABB box = new AABB(absPos.x - 3, absPos.y - 3, absPos.z - 3,
                    absPos.x + 3, absPos.y + 3, absPos.z + 3);
            List<PuertaMetalicaUblablaEntity> found = level().getEntitiesOfClass(
                    PuertaMetalicaUblablaEntity.class, box, e -> true);
            if (!found.isEmpty()) {
                doors.add(found.get(0));
            }
        }
        return doors;
    }

    private void openAllLinkedDoors() {
        for (PuertaMetalicaUblablaEntity door : getLinkedDoorEntities()) {
            door.setOpen(true);
        }
    }

    private void closeAllLinkedDoors() {
        for (PuertaMetalicaUblablaEntity door : getLinkedDoorEntities()) {
            door.setOpen(false);
        }
    }

    public void setJailArea(BlockPos min, BlockPos max) {
        this.jailMin = min;
        this.jailMax = max;
    }

    public void setPatrolRadius(double radius) {
        this.patrolRadius = Math.max(8.0, radius);
    }

    public int getState() {
        return this.entityData.get(STATE);
    }

    private void setState(int state) {
        this.entityData.set(STATE, state);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_PATROL);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ublabla_controller", 0, this::animationPredicate));
    }

    private <T extends GeoEntity> software.bernie.geckolib.animation.PlayState animationPredicate(software.bernie.geckolib.animation.AnimationState<T> state) {
        int s = getState();
        switch (s) {
            case STATE_ATTACKING:
                state.setAnimation(ATTACK);
                break;
            case STATE_CHASING:
                state.setAnimation(CHASE_ANIM);
                break;
            case STATE_ALERT, STATE_CHECKING:
                state.setAnimation(CHECK);
                break;
            case STATE_INVESTIGATING:
                state.setAnimation(state.isMoving() ? WALK : CHECK);
                break;
            default:
                state.setAnimation(state.isMoving() ? WALK : IDLE);
                break;
        }
        return software.bernie.geckolib.animation.PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // Mientras la arena no esté en marcha (/escaperoom start) el Ublabla no
        // patrulla, no oye y no persigue: se queda plantado en su puesto.
        if (ArenaManager.isInsideStoppedArena(this.position())) {
            if (!isNoAi()) {
                resetToPatrol();
                setNoAi(true);
            }
            return;
        }
        if (isNoAi()) {
            setNoAi(false);
        }

        decrementCooldowns();

        switch (getState()) {
            case STATE_PATROL:
                tickPatrol();
                break;
            case STATE_ALERT:
                tickAlert();
                break;
            case STATE_INVESTIGATING:
                tickInvestigating();
                break;
            case STATE_CHECKING:
                tickChecking();
                break;
            case STATE_CHASING:
                tickChasing();
                break;
        }
    }

    private void decrementCooldowns() {
        if (noiseCheckCooldown > 0) noiseCheckCooldown--;
        if (chaseMessageCooldown > 0) chaseMessageCooldown--;
    }

    private void tickPatrol() {
        if (noiseCheckCooldown > 0) return;

        noiseCheckCooldown = NOISE_CHECK_INTERVAL;
        float noise = getHighestGroupNoise();

        if (noise > GwwSettings.noiseThreshold()) {
            setState(STATE_ALERT);
            alertTimer = 0;
            broadcastMessage("¿Qué ha sido eso?");
        }
    }

    private void tickAlert() {
        alertTimer++;
        int alertDuration = GwwSettings.alertTicks();
        int remainingSeconds = (alertDuration - alertTimer) / 20;

        if (remainingSeconds > 0) {
            showActionBarMessage("§eUblabla viene en camino... §c" + remainingSeconds + "s");
        } else {
            showActionBarMessage("§c¡Ublabla está aquí!");
        }

        if (alertTimer < alertDuration) return;

        if (investigationTarget != null) {
            setState(STATE_INVESTIGATING);
            investigationTimer = 0;
            navigateTo(investigationTarget);
            openAllLinkedDoors();
        } else {
            broadcastMessage("No sé a dónde ir...");
            resetToPatrol();
        }
    }

    private void tickInvestigating() {
        investigationTimer++;

        if (investigationTarget != null && this.position().distanceToSqr(Vec3.atCenterOf(investigationTarget)) <= 2.25) {
            setState(STATE_CHECKING);
            getNavigation().stop();
            checkWaitTimer = GwwSettings.checkTicks();
            broadcastMessage("Voy a revisar esto con atención...");
            return;
        }

        if (investigationTimer > GwwSettings.investigationTimeoutTicks()) {
            broadcastMessage("No encuentro el lugar... mejor me retiro.");
            resetToPatrol();
        }
    }

    private void tickChecking() {
        if (checkWaitTimer > 0) {
            checkWaitTimer--;
            return;
        }

        Optional<String> anomalyMessage = detectAnomalies();

        if (anomalyMessage.isPresent()) {
            broadcastMessage(anomalyMessage.get());

            if (areAllPlayersInsideJail()) {
                broadcastMessage("§c¡Sé que han sido ustedes! No escaparán del castigo.");
                punishAndReset();
            } else {
                startChasing();
            }
        } else {
            broadcastMessage("Solo fue mi imaginación...");
            resetToPatrol();
        }
    }

    private boolean areAllPlayersInsideJail() {
        if (jailMin == null || jailMax == null) return false;

        AABB jailBox = new AABB(jailMin.getCenter(), jailMax.getCenter());
        List<Player> players = level().getEntitiesOfClass(Player.class, buildPatrolArea());

        if (players.isEmpty()) return false;

        for (Player p : players) {
            if (!jailBox.contains(p.position())) {
                return false;
            }
        }
        return true;
    }

    private void punishAndReset() {
        setState(STATE_ATTACKING);
        playPunishSound();
        teleportPlayersToJail();
        resetToPatrol();
    }

    private void playPunishSound() {
        AABB area = buildPatrolArea();
        level().getEntitiesOfClass(Player.class, area).forEach(player -> {
            if (player instanceof ServerPlayer sp) {
                sp.level().playSound(
                        null,
                        sp.blockPosition(),
                        SoundEvents.WARDEN_ROAR,
                        SoundSource.HOSTILE,
                        1.0f,
                        0.8f
                );
            }
        });
    }

    private void tickChasing() {
        chaseTimer++;

        if (!hasValidTarget()) {
            Player nearest = level().getNearestPlayer(this, 50.0);
            if (nearest != null) {
                this.setTarget(nearest);
            } else {
                resetToPatrol();
                return;
            }
        }

        if (this.getTarget() != null && this.distanceToSqr(this.getTarget()) <= CATCH_DISTANCE_SQ) {
            captureAndReset();
            return;
        }

        int chaseTimerSeconds = (CHASE_MAX_DURATION - chaseTimer) / 20;
        if (chaseTimerSeconds > 0) {
            showActionBarMessage("§eUblabla te atrapará en... §c" + chaseTimerSeconds + "s");
        }

        if (chaseTimer >= CHASE_MAX_DURATION) {
            broadcastMessage("§cNo me queda otra opción que utilizar mi arma secreta.");
            captureAndReset();
            return;
        }

        if (chaseMessageCooldown <= 0 && getRandom().nextInt(60) == 0) {
            broadcastMessage(CHASE_MESSAGES[getRandom().nextInt(CHASE_MESSAGES.length)]);
            chaseMessageCooldown = 80;
        }
    }

    private Optional<String> detectAnomalies() {
        Optional<String> outsideCheck = checkPlayersOutsideJail();
        if (outsideCheck.isPresent()) {
            return outsideCheck;
        }

        AABB area = buildPatrolArea();
        Optional<PuzzleEntityChecker.AnomalyResult> result = PuzzleEntityChecker.findFirstAnomaly(level(), area);

        return result.map(PuzzleEntityChecker.AnomalyResult::message);
    }

    private Optional<String> checkPlayersOutsideJail() {
        if (jailMin == null || jailMax == null) return Optional.empty();

        AABB jailBox = new AABB(jailMin.getCenter(), jailMax.getCenter());
        List<Player> players = level().getEntitiesOfClass(Player.class,
                new AABB(spawnPos).inflate(patrolRadius * 2));

        for (Player p : players) {
            if (!jailBox.contains(p.position())) {
                return Optional.of("§c¡Has salido de la zona segura! Eso no está permitido.");
            }
        }
        return Optional.empty();
    }

    private void startChasing() {
        setState(STATE_CHASING);
        chaseTimer = 0;
        Player nearest = level().getNearestPlayer(this, 50.0);
        if (nearest != null) {
            this.setTarget(nearest);
        } else {
            resetToPatrol();
        }
    }

    private void captureAndReset() {
        setState(STATE_ATTACKING);
        playPunishSound();
        teleportPlayersToJail();
        resetToPatrol();
    }

    public void resetToPatrol() {
        getNavigation().stop();
        closeAllLinkedDoors();
        if (spawnPos != null) {
            this.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.1, spawnPos.getZ() + 0.5);
        }
        this.setTarget(null);
        setState(STATE_PATROL);
        alertTimer = 0;
        chaseTimer = 0;
        investigationTimer = 0;
        checkWaitTimer = 0;
        broadcastMessage("Volviendo a mi puesto...");
    }

    private void teleportPlayersToJail() {
        AABB area = buildPatrolArea();
        List<Player> players = level().getEntitiesOfClass(Player.class, area);

        BlockPos destination;
        String message;

        if (jailMin != null && jailMax != null) {
            destination = new BlockPos(
                    (jailMin.getX() + jailMax.getX()) / 2,
                    (jailMin.getY() + jailMax.getY()) / 2,
                    (jailMin.getZ() + jailMax.getZ()) / 2
            );
            message = "§cUblabla te ha encerrado en la cárcel.";
        } else {
            destination = spawnPos != null ? spawnPos : BlockPos.containing(this.position());
            message = "§cUblabla te ha devuelto al inicio.";
        }

        for (Player p : players) {
            if (p instanceof ServerPlayer sp) {
                sp.teleportTo(destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5);
                sp.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private boolean hasValidTarget() {
        LivingEntity target = this.getTarget();
        return target != null && target.isAlive() && this.distanceToSqr(target) <= LOSE_TARGET_DISTANCE_SQ;
    }

    private float getHighestGroupNoise() {
        if (patrolCenter == null || level().getServer() == null) return 0;

        AABB area = buildPatrolArea();
        float max = 0;

        for (ServerPlayer player : level().getServer().getPlayerList().getPlayers()) {
            if (area.contains(player.position())) {
                float noise = NoiseDetectionSystem.getNoiseLevel(player.getUUID());
                if (noise > max) max = noise;
            }
        }
        return max;
    }

    private void navigateTo(BlockPos target) {
        if (target == null) return;
        getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.1, target.getZ() + 0.5, 1.15);
    }

    private AABB buildPatrolArea() {
        double cx = patrolCenter != null ? patrolCenter.x : position().x;
        double cy = patrolCenter != null ? patrolCenter.y : position().y;
        double cz = patrolCenter != null ? patrolCenter.z : position().z;

        return new AABB(
                cx - patrolRadius, cy - 30, cz - patrolRadius,
                cx + patrolRadius, cy + 30, cz + patrolRadius
        );
    }

    private void broadcastMessage(String message) {
        AABB area = buildPatrolArea();
        level().getEntitiesOfClass(Player.class, area).forEach(player ->
                player.sendSystemMessage(Component.literal("§6[Ublabla] §f" + message))
        );
    }

    private void showActionBarMessage(String message) {
        AABB area = buildPatrolArea();
        level().getEntitiesOfClass(Player.class, area).forEach(player ->
                player.displayClientMessage(Component.literal(message), true));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new UblablaChaseGoal(this, 1.35D));
        this.targetSelector.addGoal(1, new UblablaChaseTargetGoal(this));
    }

    static class UblablaChaseTargetGoal extends NearestAttackableTargetGoal<Player> {
        private final UblablaEntity mob;

        public UblablaChaseTargetGoal(UblablaEntity mob) {
            super(mob, Player.class, 0, true, false, null);
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return mob.getState() == STATE_CHASING && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getState() == STATE_CHASING && super.canContinueToUse();
        }

        @Override
        public void stop() {
            super.stop();
            if (mob.getState() != STATE_CHASING) {
                mob.setTarget(null);
            }
        }
    }

    static class UblablaChaseGoal extends Goal {
        private final UblablaEntity mob;
        private final double speed;

        public UblablaChaseGoal(UblablaEntity mob, double speed) {
            this.mob = mob;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getState() == STATE_CHASING && mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            if (mob.distanceToSqr(target) <= CATCH_DISTANCE_SQ) return;

            if (!mob.getNavigation().isInProgress()) {
                mob.getNavigation().moveTo(target, speed);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("State", getState());
        tag.putInt("AlertTimer", alertTimer);
        tag.putInt("CheckWaitTimer", checkWaitTimer);

        saveBlockPos(tag, "Spawn", spawnPos);
        saveBlockPos(tag, "Invest", investigationTarget);
        saveBlockPos(tag, "JailMin", jailMin);
        saveBlockPos(tag, "JailMax", jailMax);

        if (patrolCenter != null) {
            tag.putDouble("PatrolX", patrolCenter.x);
            tag.putDouble("PatrolY", patrolCenter.y);
            tag.putDouble("PatrolZ", patrolCenter.z);
            tag.putDouble("PatrolRadius", patrolRadius);
        }
        ListTag doorsList = new ListTag();
        for (Vec3 v : linkedDoors) {
            ListTag posTag = new ListTag();
            posTag.add(DoubleTag.valueOf(v.x));
            posTag.add(DoubleTag.valueOf(v.y));
            posTag.add(DoubleTag.valueOf(v.z));
            doorsList.add(posTag);
        }
        tag.put("LinkedDoors", doorsList);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(tag.getInt("State"));
        alertTimer = tag.getInt("AlertTimer");
        checkWaitTimer = tag.getInt("CheckWaitTimer");

        spawnPos = loadBlockPos(tag, "Spawn");
        investigationTarget = loadBlockPos(tag, "Invest");
        jailMin = loadBlockPos(tag, "JailMin");
        jailMax = loadBlockPos(tag, "JailMax");

        if (tag.contains("PatrolX")) {
            patrolCenter = new Vec3(
                    tag.getDouble("PatrolX"),
                    tag.getDouble("PatrolY"),
                    tag.getDouble("PatrolZ")
            );
            patrolRadius = tag.getDouble("PatrolRadius");
        }
        linkedDoors.clear();
        ListTag doorsList = tag.getList("LinkedDoors", 9); // 9 = ListTag of doubles
        for (int i = 0; i < doorsList.size(); i++) {
            ListTag posTag = doorsList.getList(i);
            double x = posTag.getDouble(0);
            double y = posTag.getDouble(1);
            double z = posTag.getDouble(2);
            linkedDoors.add(new Vec3(x, y, z));
        }
    }

    private void saveBlockPos(CompoundTag tag, String prefix, BlockPos pos) {
        if (pos == null) return;
        tag.putInt(prefix + "X", pos.getX());
        tag.putInt(prefix + "Y", pos.getY());
        tag.putInt(prefix + "Z", pos.getZ());
    }

    private BlockPos loadBlockPos(CompoundTag tag, String prefix) {
        if (!tag.contains(prefix + "X")) return null;
        return new BlockPos(
                tag.getInt(prefix + "X"),
                tag.getInt(prefix + "Y"),
                tag.getInt(prefix + "Z")
        );
    }

    @Override
    public void saveEscapeRoomData(CompoundTag tag, Vec3 centerPos) {
        if (getPatrolCenter() != null) {
            BlockPos relPatrol = BlockPos.containing(getPatrolCenter().subtract(centerPos));
            tag.putInt("PatrolCenterX", relPatrol.getX());
            tag.putInt("PatrolCenterY", relPatrol.getY());
            tag.putInt("PatrolCenterZ", relPatrol.getZ());
        }
        tag.putDouble("PatrolRadius", getPatrolRadius());

        if (getSpawnPos() != null) {
            BlockPos relSpawn = getSpawnPos().subtract(BlockPos.containing(centerPos));
            tag.putInt("SpawnX", relSpawn.getX());
            tag.putInt("SpawnY", relSpawn.getY());
            tag.putInt("SpawnZ", relSpawn.getZ());
        }

        if (getJailMin() != null && getJailMax() != null) {
            BlockPos relMin = getJailMin().subtract(BlockPos.containing(centerPos));
            BlockPos relMax = getJailMax().subtract(BlockPos.containing(centerPos));
            tag.putInt("JailMinX", relMin.getX());
            tag.putInt("JailMinY", relMin.getY());
            tag.putInt("JailMinZ", relMin.getZ());
            tag.putInt("JailMaxX", relMax.getX());
            tag.putInt("JailMaxY", relMax.getY());
            tag.putInt("JailMaxZ", relMax.getZ());
        }

        if (getInvestigationTarget() != null) {
            BlockPos relInvest = getInvestigationTarget().subtract(BlockPos.containing(centerPos));
            tag.putInt("InvestX", relInvest.getX());
            tag.putInt("InvestY", relInvest.getY());
            tag.putInt("InvestZ", relInvest.getZ());
        }
    }

    @Override
    public void restoreEscapeRoomData(CompoundTag tag, BlockPos newCenterPos) {
        if (tag.contains("PatrolCenterX")) {
            BlockPos relPatrol = new BlockPos(
                    tag.getInt("PatrolCenterX"),
                    tag.getInt("PatrolCenterY"),
                    tag.getInt("PatrolCenterZ")
            );
            setPatrolCenter(Vec3.atCenterOf(newCenterPos.offset(relPatrol)));
        }
        if (tag.contains("PatrolRadius")) {
            setPatrolRadius(tag.getDouble("PatrolRadius"));
        }

        if (tag.contains("SpawnX")) {
            BlockPos relSpawn = new BlockPos(
                    tag.getInt("SpawnX"),
                    tag.getInt("SpawnY"),
                    tag.getInt("SpawnZ")
            );
            setSpawnPos(newCenterPos.offset(relSpawn));
        }

        if (tag.contains("JailMinX")) {
            BlockPos relMin = new BlockPos(
                    tag.getInt("JailMinX"),
                    tag.getInt("JailMinY"),
                    tag.getInt("JailMinZ")
            );
            BlockPos relMax = new BlockPos(
                    tag.getInt("JailMaxX"),
                    tag.getInt("JailMaxY"),
                    tag.getInt("JailMaxZ")
            );
            setJailArea(newCenterPos.offset(relMin), newCenterPos.offset(relMax));
        }

        if (tag.contains("InvestX")) {
            BlockPos relInvest = new BlockPos(
                    tag.getInt("InvestX"),
                    tag.getInt("InvestY"),
                    tag.getInt("InvestZ")
            );
            setInvestigationTarget(newCenterPos.offset(relInvest));
        }
    }

    @Override
    public void resetPuzzleState() {
        resetToPatrol();
    }
}
