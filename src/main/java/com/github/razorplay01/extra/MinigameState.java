package com.github.razorplay01.extra;

import com.github.darkpred.morehitboxes.api.MultiPart;
import com.github.razorplay01.entity.ModEntities;
import com.github.razorplay01.entity.custom.CannonBulletEntity;
import com.github.razorplay01.entity.custom.CannonEntity;
import com.github.razorplay01.network.ServerNetworkManager;
import com.github.razorplay01.network.packet.MinigameStatePacket;
import lombok.Getter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Una partida en marcha del minijuego de cañones. Los cañones aparecen fuera del
 * círculo, esperan un segundo y disparan una bala que lo cruza en línea recta; a
 * quien alcanza lo empuja. No se elimina a nadie: la zona está cerrada y el juego
 * acaba solo cuando se agota el tiempo.
 * <p>
 * Todo lo que hace por tick está acotado a la gente de la arena, nunca a los
 * jugadores del mundo: con 100 jugadores conectados y varias arenas a la vez,
 * recorrer la lista entera del servidor en cada tick y por cada bala no escala.
 */
public class MinigameState {
    /** Margen exterior donde aparecen los cañones y por donde salen las balas. */
    public static final double SPAWN_MARGIN = 4.0;

    private static final int SHOOT_DELAY = 20;
    private static final double PUSH_MULTIPLIER = 1.5;
    /** Distancia pasado el borde a la que la bala se borra. */
    private static final double BULLET_OVERSHOOT = 3.0;

    /** Cada cuántos ticks se recalcula quién está dentro de la arena. */
    private static final int PLAYER_REFRESH_INTERVAL = 10;
    /** Cada cuántos ticks se reenvía el estado a los jugadores de dentro. */
    private static final int SYNC_INTERVAL = 40;

    /** Franja interior donde el muro empieza a empujar hacia el centro. */
    private static final double SOFT_WALL_WIDTH = 1.5;
    private static final double WALL_PUSH = 0.35;
    /** Cuánto hacia dentro se recoloca a quien cruza el borde. */
    private static final double HARD_WALL_INSET = 0.25;

    /** Filtro rápido bala-jugador antes de comparar hitboxes. */
    private static final double HIT_PRECHECK_RANGE_SQ = 9.0;

    @Getter
    private final ServerLevel world;
    @Getter
    private final CannonArena arena;
    private final Vec3 center;
    private final double radius;
    private final int totalShots;
    private final double bulletSpeed;
    private final int bulletMaxTicks;

    private int remainingTicks;
    private int shotsSpawned = 0;
    private int playerRefreshCounter = 0;
    private int syncCounter;

    /**
     * Jugadores dentro de la arena ahora mismo. Se refresca cada PLAYER_REFRESH_INTERVAL
     * ticks con una búsqueda acotada al cubo de la arena, y es la única lista que se
     * recorre en el resto del tick.
     */
    private List<ServerPlayer> playersInside = new ArrayList<>();
    private final Set<UUID> trackedInside = new HashSet<>();

    private final List<PendingShot> pendingShots = new ArrayList<>();
    private final List<MovingBullet> movingBullets = new ArrayList<>();
    private final List<UUID> activeCannons = new ArrayList<>();

    private static class PendingShot {
        final UUID cannonId;
        final Vec3 startPos;
        final Vec3 direction;
        int cooldown;

        PendingShot(UUID cannonId, Vec3 startPos, Vec3 direction, int cooldown) {
            this.cannonId = cannonId;
            this.startPos = startPos;
            this.direction = direction;
            this.cooldown = cooldown;
        }
    }

    private static class MovingBullet {
        final UUID bulletId;
        final UUID cannonId;
        final Vec3 direction;
        /** Una bala empuja a cada jugador una sola vez, no en cada tick que lo atraviesa. */
        final Set<UUID> alreadyHit = new HashSet<>();
        boolean hasEnteredCircle;
        int ticksAlive;

        MovingBullet(UUID bulletId, UUID cannonId, Vec3 direction) {
            this.bulletId = bulletId;
            this.cannonId = cannonId;
            this.direction = direction;
            this.hasEnteredCircle = false;
            this.ticksAlive = 0;
        }
    }

    public MinigameState(ServerLevel world, CannonArena arena, int durationSeconds, int totalShots, double bulletSpeed) {
        this.world = world;
        this.arena = arena;
        this.center = arena.getCenter();
        this.radius = arena.getRadius();
        this.remainingTicks = durationSeconds * 20;
        this.totalShots = totalShots;
        this.bulletSpeed = bulletSpeed;
        // Desfase por arena: con varias partidas a la vez, que no sincronicen todas
        // su envío de estado en el mismo tick.
        this.syncCounter = Math.floorMod(arena.getId().hashCode(), SYNC_INTERVAL);
        // Vida máxima de una bala: lo que tarda en cruzar la arena de lado a lado
        // más un margen. Sin esto, una bala que por la desviación del disparo no
        // llega a entrar en el círculo nunca cumple la condición de borrado y se
        // queda volando para siempre, ella y su cañón.
        this.bulletMaxTicks = (int) ((radius * 2 + SPAWN_MARGIN + BULLET_OVERSHOOT * 2) / bulletSpeed) + 20;
        clearLeftovers();
        refreshPlayers();
    }

    public boolean isActive() {
        return remainingTicks > 0;
    }

    public String getId() {
        return arena.getId();
    }

    /**
     * Cañones y balas que hayan quedado sueltos de una partida anterior mal
     * terminada (un crash, un /reload). Si no se limpian, se quedan para siempre.
     */
    private void clearLeftovers() {
        AABB bounds = arena.getSearchBounds();
        for (Entity entity : world.getEntitiesOfClass(CannonEntity.class, bounds)) {
            entity.discard();
        }
        for (Entity entity : world.getEntitiesOfClass(CannonBulletEntity.class, bounds)) {
            entity.discard();
        }
    }

    public boolean tick() {
        if (!isActive()) return false;

        remainingTicks--;

        if (++playerRefreshCounter >= PLAYER_REFRESH_INTERVAL) {
            playerRefreshCounter = 0;
            refreshPlayers();
        }

        confinePlayers();
        maybeSpawnCannon();
        updatePendingShots();
        updateBullets();

        if (--syncCounter <= 0) {
            syncCounter = SYNC_INTERVAL;
            MinigameStatePacket packet = MinigameStatePacket.of(arena);
            for (ServerPlayer player : playersInside) {
                ServerNetworkManager.sendMinigameStatePacketToPlayer(player, packet);
            }
        }

        if (remainingTicks <= 0) {
            endGame();
            return false;
        }
        return true;
    }

    // ==================== JUGADORES ====================

    /**
     * Recalcula quién está dentro. Los de creativo y espectador quedan fuera del
     * juego a propósito: así un admin puede entrar y salir sin quedarse encerrado.
     */
    private void refreshPlayers() {
        List<ServerPlayer> found = world.getEntitiesOfClass(ServerPlayer.class, arena.getSearchBounds(),
                player -> !player.isSpectator() && !player.isCreative() && arena.contains(player.position()));

        Set<UUID> stillInside = new HashSet<>(found.size());
        for (ServerPlayer player : found) {
            stillInside.add(player.getUUID());
            if (trackedInside.add(player.getUUID())) {
                ServerNetworkManager.sendMinigameStatePacketToPlayer(player, MinigameStatePacket.of(arena));
                player.sendSystemMessage(Component.literal("§a¡Has entrado en la zona de juego! Esquiva los disparos."), false);
            }
        }

        trackedInside.removeIf(uuid -> {
            if (stillInside.contains(uuid)) return false;
            releasePlayer(uuid);
            return true;
        });

        playersInside = found;
    }

    /** Devuelve la cámara normal a quien ya no está en la arena (si sigue conectado). */
    private void releasePlayer(UUID uuid) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player != null) {
            ServerNetworkManager.sendMinigameStatePacketToPlayer(player, MinigameStatePacket.inactive());
        }
    }

    /**
     * El muro: cerca del borde empuja hacia el centro, y a quien lo cruza igualmente
     * (por un balazo fuerte) lo recoloca justo dentro anulándole la velocidad de salida.
     */
    private void confinePlayers() {
        double softRadius = Math.max(radius * 0.5, radius - SOFT_WALL_WIDTH);
        double softRadiusSq = softRadius * softRadius;

        for (ServerPlayer player : playersInside) {
            if (player.isRemoved()) continue;

            double dx = player.getX() - center.x;
            double dz = player.getZ() - center.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < softRadiusSq) continue;

            double dist = Math.sqrt(distSq);
            double nx = dx / dist;
            double nz = dz / dist;

            if (dist >= radius) {
                double target = radius - HARD_WALL_INSET;
                Vec3 motion = player.getDeltaMovement();
                double radial = motion.x * nx + motion.z * nz;
                if (radial > 0) {
                    player.setDeltaMovement(motion.x - radial * nx, motion.y, motion.z - radial * nz);
                }
                player.connection.teleport(center.x + nx * target, player.getY(), center.z + nz * target,
                        player.getYRot(), player.getXRot());
            } else {
                double strength = (dist - softRadius) / (radius - softRadius);
                player.setDeltaMovement(player.getDeltaMovement()
                        .add(-nx * WALL_PUSH * strength, 0, -nz * WALL_PUSH * strength));
                player.hurtMarked = true;
            }
        }
    }

    // ==================== CAÑONES Y BALAS ====================

    private void maybeSpawnCannon() {
        if (shotsSpawned >= totalShots || remainingTicks <= 0) return;

        double spawnChance = (double) (totalShots - shotsSpawned) / remainingTicks;
        if (world.random.nextDouble() < spawnChance) {
            spawnCannon();
            shotsSpawned++;
        }
    }

    private void spawnCannon() {
        double angle = 2 * Math.PI * world.random.nextDouble();
        double spawnDistance = radius + 0.5 + world.random.nextDouble() * 0.5;

        Vec3 cannonPos = new Vec3(
                center.x + spawnDistance * Math.cos(angle),
                center.y,
                center.z + spawnDistance * Math.sin(angle)
        );

        Vec3 toCenter = center.subtract(cannonPos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toCenter.x, toCenter.z));
        float deviation = (float) (world.random.nextDouble() * 40 - 20);
        float finalYaw = yaw + deviation;

        CannonEntity cannon = new CannonEntity(ModEntities.CANNON, world);
        cannon.setPos(cannonPos.x, cannonPos.y, cannonPos.z);
        cannon.setYRot(finalYaw);
        cannon.setYHeadRot(finalYaw);
        cannon.yRotO = finalYaw;
        cannon.yHeadRotO = finalYaw;
        cannon.setNoAi(true);
        cannon.setNoGravity(true);
        cannon.setInvulnerable(true);
        cannon.setPersistenceRequired();
        cannon.setSilent(true);
        world.addFreshEntity(cannon);

        activeCannons.add(cannon.getUUID());

        Vec3 shootDirection = new Vec3(
                -Math.sin(Math.toRadians(finalYaw)),
                0,
                Math.cos(Math.toRadians(finalYaw))
        ).normalize();

        pendingShots.add(new PendingShot(cannon.getUUID(), cannonPos, shootDirection, SHOOT_DELAY));
    }

    private void updatePendingShots() {
        pendingShots.removeIf(shot -> {
            if (--shot.cooldown > 0) return false;
            fire(shot);
            return true;
        });
    }

    private void fire(PendingShot shot) {
        float yaw = (float) Math.toDegrees(Math.atan2(-shot.direction.x, shot.direction.z));

        CannonBulletEntity bullet = new CannonBulletEntity(ModEntities.CANNON_BULLET, world);
        bullet.setPos(shot.startPos.x, shot.startPos.y, shot.startPos.z);
        bullet.setYRot(yaw);
        bullet.setYHeadRot(yaw);
        bullet.yRotO = yaw;
        bullet.yHeadRotO = yaw;
        bullet.setNoAi(true);
        bullet.setNoGravity(true);
        bullet.setInvulnerable(true);
        bullet.setPersistenceRequired();
        bullet.setSilent(true);
        bullet.noPhysics = true;
        world.addFreshEntity(bullet);

        if (world.getEntity(shot.cannonId) instanceof CannonEntity cannon) {
            cannon.triggerShootAnim(world);
        }

        movingBullets.add(new MovingBullet(bullet.getUUID(), shot.cannonId, shot.direction));
    }

    /**
     * Las balas se mueven a mano con setPos (no moveTo: la rotación se fija al
     * dispararlas y no vuelve a cambiar, y reenviarla cada tick es tráfico de más).
     */
    private void updateBullets() {
        Iterator<MovingBullet> it = movingBullets.iterator();
        while (it.hasNext()) {
            MovingBullet bullet = it.next();
            Entity entity = world.getEntity(bullet.bulletId);
            if (entity == null) {
                removeEntity(bullet.cannonId);
                activeCannons.remove(bullet.cannonId);
                it.remove();
                continue;
            }

            Vec3 pos = entity.position().add(bullet.direction.scale(bulletSpeed));
            entity.setPos(pos.x, pos.y, pos.z);
            entity.setDeltaMovement(Vec3.ZERO);

            checkCollision(bullet, entity);

            double distSqToCenter = arena.horizontalDistSq(pos);
            if (!bullet.hasEnteredCircle && distSqToCenter <= arena.getRadiusSq()) {
                bullet.hasEnteredCircle = true;
            }

            double exitRadius = radius + BULLET_OVERSHOOT;
            boolean salio = bullet.hasEnteredCircle && distSqToCenter > exitRadius * exitRadius;
            if (salio || ++bullet.ticksAlive > bulletMaxTicks) {
                entity.discard();
                removeEntity(bullet.cannonId);
                activeCannons.remove(bullet.cannonId);
                it.remove();
            }
        }
    }

    private void checkCollision(MovingBullet bullet, Entity entity) {
        if (playersInside.isEmpty()) return;

        AABB bulletBox = hitboxOf(entity);
        Vec3 bulletPos = entity.position();

        for (ServerPlayer player : playersInside) {
            if (player.isRemoved() || bullet.alreadyHit.contains(player.getUUID())) continue;
            if (player.position().distanceToSqr(bulletPos) > HIT_PRECHECK_RANGE_SQ) continue;
            if (!player.getBoundingBox().intersects(bulletBox)) continue;

            bullet.alreadyHit.add(player.getUUID());
            Vec3 push = bullet.direction.scale(bulletSpeed * PUSH_MULTIPLIER);
            player.setDeltaMovement(player.getDeltaMovement().add(push));
            player.hurtMarked = true;
            world.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.5, player.getZ(),
                    3, 0, 0, 0, 0.1);
        }
    }

    /**
     * Hitbox real de la bala. Las partes de morehitboxes tardan un tick en llegar
     * desde que la entidad aparece, así que hasta entonces vale la caja normal:
     * antes se leía la primera parte a ciegas y una bala recién creada petaba.
     */
    private AABB hitboxOf(Entity entity) {
        if (entity instanceof CannonBulletEntity bullet) {
            List<MultiPart<CannonBulletEntity>> parts = bullet.getEntityHitboxData().getCustomParts();
            if (!parts.isEmpty() && parts.getFirst() != null && parts.getFirst().getEntity() != null) {
                return parts.getFirst().getEntity().getBoundingBox().deflate(0.3);
            }
        }
        return entity.getBoundingBox();
    }

    // ==================== FIN ====================

    public void endGame() {
        for (UUID cannonId : activeCannons) {
            removeEntity(cannonId);
        }
        for (MovingBullet bullet : movingBullets) {
            removeEntity(bullet.bulletId);
        }
        activeCannons.clear();
        movingBullets.clear();
        pendingShots.clear();

        for (ServerPlayer player : playersInside) {
            if (player.isRemoved()) continue;
            player.sendSystemMessage(Component.literal("§e⏰ ¡Minijuego terminado! Has sobrevivido."), false);
        }
        // Por trackedInside y no por playersInside: si la partida se para justo
        // después de que alguien entre, aún no está en la lista de jugadores pero
        // ya tiene la cámara aérea puesta, y hay que quitársela igual.
        for (UUID uuid : trackedInside) {
            releasePlayer(uuid);
        }

        playersInside = new ArrayList<>();
        trackedInside.clear();
        remainingTicks = 0;
    }

    private void removeEntity(UUID uuid) {
        Entity entity = world.getEntity(uuid);
        if (entity != null) entity.discard();
    }
}
