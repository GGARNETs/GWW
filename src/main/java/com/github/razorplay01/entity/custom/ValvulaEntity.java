package com.github.razorplay01.entity.custom;

import com.github.razorplay01.arena.ArenaManager;
import com.github.razorplay01.entity.custom.util.ValvulaType;
import com.github.razorplay01.sound.ModSounds;
import com.github.razorplay01.system.NoiseDetectionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.*;

public class ValvulaEntity extends BaseEntity {

    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(ValvulaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(ValvulaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HAS_MANIVELA =
            SynchedEntityData.defineId(ValvulaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CORRECT_STATE =
            SynchedEntityData.defineId(ValvulaEntity.class, EntityDataSerializers.INT);

    /** Válvula cerrada del todo: no gira más hacia la izquierda. */
    public static final int MIN_STATE = 0;
    /** Válvula abierta del todo: no gira más hacia la derecha. */
    public static final int MAX_STATE = 4;

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.idle");
    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlay("animation.open");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlay("animation.close");

    private static final int PARTICLE_INTERVAL = 2;
    private static final int DIRECTION_EXPIRE_TICKS = 10;
    private static final int NEARBY_REFRESH_INTERVAL = 5;

    /**
     * Cada cuántos ticks se relanza el siseo del vapor. El .ogg dura 2,25 s y esto son
     * 2,1 s: el solape mínimo evita cortes y el bucle se oye continuo. No se puede
     * bajar mucho más sin repetir la muestra encima de sí misma.
     */
    private static final int GAS_SOUND_INTERVAL = 42;
    private int gasSoundCooldown = 0;

    private List<Player> cachedNearbyPlayers = List.of();
    private int nearbyRefreshCooldown = 0;

    /**
     * Margen para avisar a las rejas cuando la válvula no está dentro de ninguna arena
     * configurada. Dentro de una arena manda la zona de esa arena.
     */
    private static final double REJA_SEARCH_FALLBACK = 32.0;

    private final List<ParticleEmitter> particleEmitters = new ArrayList<>();
    private final Map<UUID, Vec3> playerEntryDirections = new HashMap<>();
    private final Map<UUID, Integer> playerLastSeenTick = new HashMap<>();
    private int particleTickCounter = 0;
    /** Radio del emisor más ancho. -1 = hay que recalcularlo. */
    private double maxEmitterRadius = -1;

    public static class ParticleEmitter {
        public double offsetX, offsetY, offsetZ;
        public double dirX, dirY, dirZ;
        public double speed;
        public double radius;
        public double pushForce;
        public int count;

        public ParticleEmitter(double offsetX, double offsetY, double offsetZ,
                               double dirX, double dirY, double dirZ,
                               double speed, double radius, double pushForce, int count) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.speed = speed;
            this.radius = radius;
            this.pushForce = pushForce;
            this.count = count;
        }

        public static ParticleEmitter fromWorldCoordinates(
                ValvulaEntity entity,
                double worldX, double worldY, double worldZ,
                double dirX, double dirY, double dirZ,
                double speed, double radius, double pushForce, int count) {

            double diffX = worldX - entity.getX();
            double diffY = worldY - entity.getY();
            double diffZ = worldZ - entity.getZ();

            double yawRad = Math.toRadians(-entity.getYRot());
            double cos = Math.cos(-yawRad);
            double sin = Math.sin(-yawRad);

            double localX = diffX * cos - diffZ * sin;
            double localZ = diffX * sin + diffZ * cos;

            double localDirX = dirX * cos - dirZ * sin;
            double localDirZ = dirX * sin + dirZ * cos;

            return new ParticleEmitter(localX, diffY, localZ, localDirX, dirY, localDirZ, speed, radius, pushForce, count);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("OffsetX", offsetX);
            tag.putDouble("OffsetY", offsetY);
            tag.putDouble("OffsetZ", offsetZ);
            tag.putDouble("DirX", dirX);
            tag.putDouble("DirY", dirY);
            tag.putDouble("DirZ", dirZ);
            tag.putDouble("Speed", speed);
            tag.putDouble("Radius", radius);
            tag.putDouble("PushForce", pushForce);
            tag.putInt("Count", count);
            return tag;
        }

        public static ParticleEmitter load(CompoundTag tag) {
            return new ParticleEmitter(
                    tag.getDouble("OffsetX"), tag.getDouble("OffsetY"), tag.getDouble("OffsetZ"),
                    tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ"),
                    tag.getDouble("Speed"), tag.getDouble("Radius"), tag.getDouble("PushForce"),
                    tag.getInt("Count")
            );
        }

        public Vec3 getWorldPosition(ValvulaEntity entity) {
            double yawRad = Math.toRadians(-entity.getYRot());
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);
            double rotatedX = offsetX * cos - offsetZ * sin;
            double rotatedZ = offsetX * sin + offsetZ * cos;
            return new Vec3(entity.getX() + rotatedX, entity.getY() + offsetY, entity.getZ() + rotatedZ);
        }

        public Vec3 getWorldDirection(ValvulaEntity entity) {
            double yawRad = Math.toRadians(-entity.getYRot());
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);
            double rotatedDirX = dirX * cos - dirZ * sin;
            double rotatedDirZ = dirX * sin + dirZ * cos;
            return new Vec3(rotatedDirX, dirY, rotatedDirZ);
        }
    }

    public ValvulaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Double.POSITIVE_INFINITY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, ValvulaType.NARANJA.getId());
        builder.define(DATA_STATE, 0);
        builder.define(DATA_HAS_MANIVELA, false);
        builder.define(DATA_CORRECT_STATE, 3);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Type", getValType().getId());
        tag.putInt("State", getState());
        tag.putBoolean("HasManivela", hasManivela());
        ListTag emitterList = new ListTag();
        for (ParticleEmitter emitter : particleEmitters) {
            emitterList.add(emitter.save());
        }
        tag.put("ParticleEmitters", emitterList);
        tag.putInt("CorrectState", getCorrectState());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setType(ValvulaType.byId(tag.getInt("Type")));
        setState(tag.getInt("State"));
        setHasManivela(tag.getBoolean("HasManivela"));
        particleEmitters.clear();
        if (tag.contains("ParticleEmitters", Tag.TAG_LIST)) {
            ListTag emitterList = tag.getList("ParticleEmitters", Tag.TAG_COMPOUND);
            for (int i = 0; i < emitterList.size(); i++) {
                particleEmitters.add(ParticleEmitter.load(emitterList.getCompound(i)));
            }
        }
        maxEmitterRadius = -1;
        setCorrectState(tag.getInt("CorrectState"));
    }

    /** Estado en el que la presión se estabiliza y las partículas desaparecen. */
    public int getCorrectState() {
        return this.entityData.get(DATA_CORRECT_STATE);
    }

    public void setCorrectState(int correctState) {
        this.entityData.set(DATA_CORRECT_STATE, clampState(correctState));
    }

    private static int clampState(int state) {
        return Math.max(MIN_STATE, Math.min(MAX_STATE, state));
    }

    public ValvulaType getValType() {
        return ValvulaType.byId(this.entityData.get(DATA_TYPE));
    }

    public void setType(ValvulaType type) {
        this.entityData.set(DATA_TYPE, type.getId());
    }

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    public void setState(int state) {
        this.entityData.set(DATA_STATE, clampState(state));
    }

    public boolean hasManivela() {
        return this.entityData.get(DATA_HAS_MANIVELA);
    }

    public void setHasManivela(boolean has) {
        this.entityData.set(DATA_HAS_MANIVELA, has);
    }

    /**
     * La presión solo se corta en el estado exacto: pasarse de vuelta también deja
     * escapar el vapor. Hay que acertar, no simplemente abrir del todo.
     */
    public boolean areParticlesActive() {
        return getState() != getCorrectState();
    }

    public List<ParticleEmitter> getParticleEmitters() {
        return particleEmitters;
    }

    public void attachManivela(ValvulaType type) {
        if (this.getValType() == type) {
            setHasManivela(true);
        }
    }

    public void addParticleEmitter(ParticleEmitter emitter) {
        particleEmitters.add(emitter);
        maxEmitterRadius = -1;
    }

    public void removeParticleEmitter(int index) {
        if (index >= 0 && index < particleEmitters.size()) {
            particleEmitters.remove(index);
            maxEmitterRadius = -1;
        }
    }

    public void clearParticleEmitters() {
        particleEmitters.clear();
        maxEmitterRadius = -1;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || particleEmitters.isEmpty()) return;

        if (!areParticlesActive()) {
            playerEntryDirections.clear();
            playerLastSeenTick.clear();
            gasSoundCooldown = 0;
            return;
        }

        // Una válvula sin resolver echa vapor para siempre; con nadie cerca no hay
        // que emitir partículas ni empujar. La lista de jugadores se refresca cada
        // 5 ticks: la consulta de entidades por tick era lo caro, no el empuje.
        if (--nearbyRefreshCooldown <= 0) {
            nearbyRefreshCooldown = NEARBY_REFRESH_INTERVAL;
            refreshNearbyPlayers();
        }
        if (cachedNearbyPlayers.isEmpty()) {
            return;
        }

        particleTickCounter++;
        if (particleTickCounter >= PARTICLE_INTERVAL) {
            particleTickCounter = 0;
            spawnAllParticles();
        }

        if (--gasSoundCooldown <= 0) {
            gasSoundCooldown = GAS_SOUND_INTERVAL;
            playGasLoop();
        }

        pushNearbyPlayers();
        cleanupExpiredEntries();
    }

    /**
     * Siseo del vapor en cada boquilla. Cuelga de la misma condición que las
     * partículas (válvula sin resolver Y alguien cerca), así que una sala vacía no
     * manda ni un paquete: es un sonido cada 2 s por emisor, no un trabajo por tick.
     */
    private void playGasLoop() {
        for (ParticleEmitter emitter : particleEmitters) {
            Vec3 pos = emitter.getWorldPosition(this);
            ModSounds.playAmbient(this.level(), BlockPos.containing(pos),
                    ModSounds.GAS_LEAK, 0.55F, 0.95F + this.random.nextFloat() * 0.1F);
        }
    }

    private void refreshNearbyPlayers() {
        double maxRadius = getMaxEmitterRadius();
        if (maxRadius <= 0 || !(this.level() instanceof ServerLevel serverLevel)) {
            cachedNearbyPlayers = List.of();
            return;
        }
        cachedNearbyPlayers = serverLevel.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(maxRadius + 12));
    }

    private void spawnAllParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        for (ParticleEmitter emitter : particleEmitters) {
            Vec3 worldPos = emitter.getWorldPosition(this);
            Vec3 worldDir = emitter.getWorldDirection(this);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    worldPos.x, worldPos.y, worldPos.z, emitter.count,
                    worldDir.x, worldDir.y, worldDir.z, emitter.speed);
        }
    }

    private void cleanupExpiredEntries() {
        int currentTick = this.tickCount;
        playerLastSeenTick.entrySet().removeIf(entry -> {
            if (currentTick - entry.getValue() > DIRECTION_EXPIRE_TICKS) {
                playerEntryDirections.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void pushNearbyPlayers() {
        for (Player player : cachedNearbyPlayers) {
            if (player.isSpectator() || player.isCreative() || player.isRemoved()) continue;
            boolean isInAnyZone = false;

            for (ParticleEmitter emitter : particleEmitters) {
                Vec3 emitterWorldPos = emitter.getWorldPosition(this);
                Vec3 playerFeet = player.position();
                Vec3 playerCenter = playerFeet.add(0, player.getBbHeight() / 2.0, 0);
                double distSq = Math.min(playerFeet.distanceToSqr(emitterWorldPos), playerCenter.distanceToSqr(emitterWorldPos));
                double radiusSq = emitter.radius * emitter.radius;

                if (distSq <= radiusSq) {
                    isInAnyZone = true;
                    applyPush(player, emitterWorldPos, emitter.pushForce, distSq, radiusSq);
                }
            }

            if (isInAnyZone) {
                playerLastSeenTick.put(player.getUUID(), this.tickCount);
            }
        }
    }

    private void applyPush(Player player, Vec3 emitterPos, double force, double distSq, double radiusSq) {
        UUID playerId = player.getUUID();
        double dist = Math.sqrt(distSq);
        double radius = Math.sqrt(radiusSq);
        double attenuation = Math.max(0.1, Math.min(1.0, 1.0 - (dist / radius)));
        double finalForce = force * attenuation;

        Vec3 pushDirection;

        if (playerEntryDirections.containsKey(playerId)) {
            pushDirection = playerEntryDirections.get(playerId);
        } else {
            Vec3 playerMovement = player.getDeltaMovement();
            double horizontalSpeedSq = playerMovement.x * playerMovement.x + playerMovement.z * playerMovement.z;

            if (horizontalSpeedSq > 0.0001) {
                double length = Math.sqrt(horizontalSpeedSq);
                pushDirection = new Vec3(-playerMovement.x / length, 0, -playerMovement.z / length);
            } else {
                Vec3 away = player.position().subtract(emitterPos);
                double awayLengthSq = away.x * away.x + away.z * away.z;
                if (awayLengthSq < 0.001) {
                    double angle = player.getRandom().nextDouble() * Math.PI * 2;
                    pushDirection = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                } else {
                    double awayLength = Math.sqrt(awayLengthSq);
                    pushDirection = new Vec3(away.x / awayLength, 0, away.z / awayLength);
                }
            }
            playerEntryDirections.put(playerId, pushDirection);
        }

        Vec3 currentMotion = player.getDeltaMovement();
        Vec3 oppositeDir = new Vec3(-pushDirection.x, 0, -pushDirection.z);
        double movingIntoZone = currentMotion.x * oppositeDir.x + currentMotion.z * oppositeDir.z;

        double newX = currentMotion.x;
        double newZ = currentMotion.z;

        if (movingIntoZone > 0) {
            newX -= oppositeDir.x * movingIntoZone;
            newZ -= oppositeDir.z * movingIntoZone;
        }

        newX += pushDirection.x * finalForce;
        newZ += pushDirection.z * finalForce;

        double newY = currentMotion.y;
        if (player.onGround()) {
            newY = 0.05 * attenuation;
        }

        player.setDeltaMovement(newX, newY, newZ);
        player.hurtMarked = true;

        if (player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    private double getMaxEmitterRadius() {
        if (maxEmitterRadius < 0) {
            double max = 0;
            for (ParticleEmitter emitter : particleEmitters) {
                if (emitter.radius > max) max = emitter.radius;
            }
            maxEmitterRadius = max;
        }
        return maxEmitterRadius;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller",
                state -> state.setAndContinue(IDLE_ANIM)));
        controllers.add(new AnimationController<>(this, "open_controller",
                state -> PlayState.STOP).triggerableAnim("open", OPEN_ANIM));
        controllers.add(new AnimationController<>(this, "close_controller",
                state -> PlayState.STOP).triggerableAnim("close", CLOSE_ANIM));
    }

    public void triggerTurnUpAnim(Level level) {
        if (level instanceof ServerLevel) triggerAnim("open_controller", "open");
    }

    public void triggerTurnDownAnim(Level level) {
        if (level instanceof ServerLevel) triggerAnim("close_controller", "close");
    }

    /** Clic derecho: gira en el sentido de las agujas del reloj, es decir, abre. */
    @Override
    public void handleNormalInteract(Player player) {
        turn(player, 1);
    }

    @Override
    public boolean skipAttackInteraction(net.minecraft.world.entity.Entity entity) {
        return !(entity instanceof Player);
    }

    /** Clic izquierdo: gira en sentido contrario a las agujas del reloj, o sea, cierra. */
    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (damageSource.getEntity() instanceof Player attacker) {
            if (!this.level().isClientSide && !attacker.isSpectator()) {
                turn(attacker, -1);
            }
            return false; // la válvula no recibe daño, solo gira
        }
        return super.hurt(damageSource, amount);
    }

    /**
     * Gira la válvula un paso: delta +1 la abre y -1 la cierra. Sin manivela no se
     * mueve, y en los topes (0 cerrada, {@value #MAX_STATE} abierta) no pasa de ahí.
     */
    private void turn(Player player, int delta) {
        if (this.level().isClientSide) {
            return;
        }

        if (!hasManivela()) {
            ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 1.05F);
            actionBar(player, "§cLa válvula no tiene manivela: no puedes girarla.");
            return;
        }

        int next = getState() + delta;
        if (next < MIN_STATE) {
            ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 0.9F);
            actionBar(player, "§cLa válvula ya está cerrada del todo.");
            return;
        }
        if (next > MAX_STATE) {
            ModSounds.playAt(this, ModSounds.BLOCKED_THUD, 0.6F, 0.9F);
            actionBar(player, "§cLa válvula ya está abierta del todo.");
            return;
        }

        setState(next);
        if (delta > 0) {
            triggerTurnUpAnim(this.level());
        } else {
            triggerTurnDownAnim(this.level());
        }
        // Todos los giros suenan exactamente igual, a propósito: el oído no puede
        // delatar ni la dirección ni el punto correcto. Dar con la posición buena es
        // el puzzle, y se averigua por el vapor, no por el sonido.
        ModSounds.playAt(this, ModSounds.VALVE_TURN, 0.8F, 1.0F);
        NoiseDetectionSystem.addNoise(player, 0.5f);

        // Si al ajustar la presión esta válvula quedó en su sitio, las rejas a medio
        // abrir de la zona vuelven a comprobar si ya pueden terminar de abrirse.
        // Se busca dentro de la arena: un radio fijo alcanzaría las rejas de la sala
        // de al lado y las abriría sin que nadie hubiese tocado sus válvulas.
        if (!areParticlesActive()) {
            this.level().getEntitiesOfClass(RejaDuctoEntity.class,
                            ArenaManager.searchAreaAround(this.position(),
                                    this.getBoundingBox().inflate(REJA_SEARCH_FALLBACK)))
                    .forEach(RejaDuctoEntity::notifyValvesChanged);
        }

        // Solo se avisa en los extremos; los estados intermedios se dejan en silencio
        // para que dar con el punto correcto sea parte del misterio del puzzle.
        if (next == MIN_STATE) {
            actionBar(player, "§eLa válvula está cerrada del todo.");
        } else if (next == MAX_STATE) {
            actionBar(player, "§eLa válvula está abierta del todo.");
        }
    }

    private static void actionBar(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    /**
     * True si todas las válvulas del área están en su estado correcto (sin presión).
     * Si no hay ninguna válvula, se considera resuelto: una sala sin válvulas no
     * bloquea nada.
     */
    public static boolean allSolved(Level level, net.minecraft.world.phys.AABB area) {
        return level.getEntitiesOfClass(ValvulaEntity.class, area, ValvulaEntity::areParticlesActive).isEmpty();
    }
}
