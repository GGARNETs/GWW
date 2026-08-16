package com.github.razorplay01.system;

import com.github.razorplay01.GWW;
import com.github.razorplay01.api.noise.NoiseAPI;
import com.github.razorplay01.config.GwwSettings;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.network.FabricCustomPayload;
import com.github.razorplay01.network.packet.NoisePacket;
import com.github.razorplay01.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Barra de ruido compartida por sala.
 * <p>
 * El ruido se acumula por GRUPO (una sala = un grupo), no por jugador: todos los de
 * la sala ven la misma barra. Dos cosas importantes de cómo está montado:
 * <ul>
 *   <li><b>El decay se aplica una vez por grupo y por tick.</b> Antes se aplicaba
 *       dentro del tick de cada jugador, así que una sala de 4 personas veía bajar el
 *       ruido 4 veces más rápido que una de 1: el juego era más fácil cuantos más eran.</li>
 *   <li><b>Los paquetes salen por grupo, con freno, y solo si el número cambió.</b>
 *       Antes se mandaba un paquete a cada miembro en CADA ruido generado (cada paso,
 *       salto y caída de cada jugador) y además cada 5 ticks por jugador; con 100
 *       jugadores eso eran miles de paquetes por segundo solo para una barrita.
 *       El cliente adivina solo la bajada (conoce el decay), así que entre envíos la
 *       barra sigue viva sin que el servidor diga nada.</li>
 * </ul>
 */
public class NoiseDetectionSystem {

    private static final Map<UUID, PlayerNoiseData> PLAYER_NOISE_DATA = new HashMap<>();
    /** Jugador -> líder de su grupo. */
    private static final Map<UUID, UUID> PLAYER_GROUPS = new HashMap<>();
    /**
     * Índice inverso líder -> miembros. Sin esto, cada envío recorría el mapa entero
     * de jugadores del servidor para averiguar a quién le tocaba el paquete.
     */
    private static final Map<UUID, Set<UUID>> GROUP_MEMBERS = new HashMap<>();

    /** Fracción de barra a la que salta el aviso sonoro, y a la que se rearma. */
    private static final float ALERT_THRESHOLD = 0.85f;
    private static final float ALERT_RESET = 0.6f;
    private static final Set<UUID> alertedGroups = new HashSet<>();

    /** Ticks mínimos entre dos envíos del mismo grupo: como mucho 4 paquetes por segundo. */
    private static final int MIN_TICKS_BETWEEN_SENDS = 5;
    /** Reenvío de seguridad aunque no haya cambios, para corregir la deriva del cliente. */
    private static final int RESYNC_INTERVAL = 40;
    /** Cambio mínimo de barra que justifica gastar un paquete. */
    private static final float SEND_EPSILON = 0.02f;

    /**
     * Valores que no son configurables desde settings.yml. Los que sí lo son
     * (el ruido de cada acción, el máximo y el decay) viven en {@link GwwSettings}.
     */
    public static class NoiseConfig {
        public static final float SURFACE_MULTIPLIER_SOFT = 0.7f;
        public static final float SURFACE_MULTIPLIER_NORMAL = 1.0f;
        public static final float SURFACE_MULTIPLIER_HARD = 1.3f;
        public static final float SURFACE_MULTIPLIER_LOUD = 1.6f;

        public static final float BLOCK_BREAK = 0.5f;
        public static final float BLOCK_PLACE = 0.4f;
        public static final float DAMAGE_TAKEN = 0.6f;
        public static final float ATTACK = 0.35f;
        public static final float ITEM_USE = 0.25f;

        public static final double MIN_MOVEMENT_SPEED = 0.001;
        public static final double SPRINT_THRESHOLD = 0.1;
    }

    /**
     * El sistema de ruido solo existe para quien está jugando: en creativo o
     * espectador no se acumula ruido ni se ve la barra.
     */
    public static boolean isEligible(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
    }

    public static PlayerNoiseData getPlayerData(UUID playerId) {
        return PLAYER_NOISE_DATA.computeIfAbsent(playerId, id -> new PlayerNoiseData());
    }

    private static UUID getGroupLeader(UUID playerId) {
        return PLAYER_GROUPS.getOrDefault(playerId, playerId);
    }

    public static String getGroupId(UUID playerId) {
        return getGroupLeader(playerId).toString().substring(0, 8);
    }

    // ==================== ENTRADA DE RUIDO ====================

    /**
     * Ruido generado por el jugador que interactúa con algo. Es el que hay que usar
     * desde las entidades: el ruido es de quien toca la palanca, no de quien pasaba
     * más cerca.
     */
    public static void addNoise(Player player, float amount) {
        if (player instanceof ServerPlayer serverPlayer) {
            addNoise(serverPlayer, amount);
        }
    }

    /**
     * Ruido de algo que se dispara solo (una caja que se abre de un golpe): al no
     * haber autor, se le apunta al jugador más cercano que esté jugando de verdad,
     * saltándose a los que miran en creativo o espectador.
     */
    public static void addNoiseNearby(Entity source, double radius, float amount) {
        if (!(source.level() instanceof ServerLevel level)) return;

        ServerPlayer closest = null;
        double closestDist = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (!isEligible(player)) continue;
            double dist = player.distanceToSqr(source);
            if (dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        addNoise(closest, amount);
    }

    /**
     * Acumula ruido en el grupo del jugador. NO manda paquetes: de eso se encarga el
     * barrido por grupos de {@link #tickAll}, que decide si el cambio merece la pena.
     */
    public static void addNoise(ServerPlayer player, float amount) {
        if (player == null || !isEligible(player)) return;
        UUID leaderId = getGroupLeader(player.getUUID());
        PlayerNoiseData data = getPlayerData(leaderId);
        if (!data.isEnabled()) return;

        amount *= data.getMultiplier();
        float newNoise = Math.min(data.getCurrentNoise() + amount, GwwSettings.noiseMax());
        data.setCurrentNoise(newNoise);
        data.setLastNoiseTime(System.currentTimeMillis());

        GwwDebug.log(GwwDebug.Category.NOISE, "{} suma {} -> grupo {} queda en {}",
                player.getGameProfile().getName(), amount, getGroupId(player.getUUID()), newNoise);
    }

    // ==================== TICK ====================

    /**
     * Único punto de entrada por tick. Antes había dos bucles sobre todos los
     * jugadores del servidor (uno aquí y otro en el handler de eventos); ahora es uno
     * solo, y el decay y los envíos van aparte, por grupo.
     */
    public static void tickAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
        tickGroups();
    }

    private static void tickPlayer(ServerPlayer player) {
        PlayerNoiseData data = getPlayerData(player.getUUID());

        // Al pasar a creativo o espectador se corta en seco: se apaga el ruido
        // acumulado y se le oculta la barra.
        if (!isEligible(player)) {
            if (data.isEnabled()) {
                data.setEnabled(false);
                data.reset();
                sendHidden(player);
            }
            return;
        }

        if (!data.isEnabled()) return;

        GwwDebug.count(GwwDebug.NOISE_PLAYER_TICKS);
        detectMovementNoise(player, data);

        data.setLastPosition(player.position());
        data.setWasOnGround(player.onGround());
        data.setWasSneaking(player.isCrouching());
        data.setWasSprinting(player.isSprinting());
        data.setTicksSinceLastStep(data.getTicksSinceLastStep() + 1);
    }

    /**
     * Decay, aviso sonoro y envío, una vez por grupo. Un grupo sin ruido y ya
     * sincronizado no cuesta nada: sale por el primer if sin tocar la red.
     */
    private static void tickGroups() {
        if (GROUP_MEMBERS.isEmpty()) return;

        float decayFraction = asBarFraction(GwwSettings.noiseDecayPerTick());

        for (Map.Entry<UUID, Set<UUID>> entry : GROUP_MEMBERS.entrySet()) {
            UUID leaderId = entry.getKey();
            PlayerNoiseData data = getPlayerData(leaderId);

            if (data.getCurrentNoise() > 0) {
                data.setCurrentNoise(Math.max(0, data.getCurrentNoise() - GwwSettings.noiseDecayPerTick()));
            }

            // El cliente baja su barra sola con este mismo ritmo, así que la referencia
            // de "lo que el cliente cree" tiene que envejecer igual. Sin esto, el simple
            // decay ya haría que servidor y referencia se separasen y se mandaría un
            // paquete en cuanto pasara el freno, que es justo lo que se quiere evitar.
            if (data.getLastSentNoise() > 0) {
                data.setLastSentNoise(Math.max(0, data.getLastSentNoise() - decayFraction));
            }

            float fraction = asBarFraction(data.getCurrentNoise());
            checkNoiseAlert(leaderId, entry.getValue(), fraction);
            maybeSync(leaderId, entry.getValue(), data, fraction);
        }
    }

    /**
     * Decide si el estado del grupo merece un paquete. La bajada por decay el cliente
     * la calcula solo, así que lo único que obliga a hablar es una subida, un cambio
     * de visibilidad, o el reenvío de seguridad para que no se desvíe.
     */
    private static void maybeSync(UUID leaderId, Set<UUID> members, PlayerNoiseData data, float fraction) {
        int waited = data.getTicksSinceSync() + 1;
        data.setTicksSinceSync(waited);

        boolean visibilityChanged = data.isEnabled() != data.isLastSentEnabled();
        boolean valueJumped = Math.abs(fraction - data.getLastSentNoise()) >= SEND_EPSILON;

        // Grupo en silencio y ya sincronizado: ni el reenvío de seguridad hace falta,
        // porque con la barra a cero no hay decay que el cliente pueda calcular mal.
        // Una sala parada no manda absolutamente nada.
        if (!visibilityChanged && fraction <= 0f && data.getLastSentNoise() == 0f) {
            return;
        }

        boolean send;
        if (visibilityChanged) {
            send = true;
        } else if (waited < MIN_TICKS_BETWEEN_SENDS) {
            send = false;
        } else {
            send = valueJumped || waited >= RESYNC_INTERVAL;
        }
        if (!send) return;

        data.setTicksSinceSync(0);
        data.setLastSentNoise(fraction);
        data.setLastSentEnabled(data.isEnabled());
        broadcastToGroup(leaderId, members, new NoisePacket(
                fraction, asBarFraction(GwwSettings.noiseDecayPerTick()), data.isEnabled()));
    }

    /**
     * Aviso sonoro cuando la barra del grupo se acerca al tope. Suena una sola vez por
     * subida: hasta que el ruido no baja del umbral de rearme no vuelve a avisar, o
     * cada paso encima de la línea dispararía el sonido.
     */
    private static void checkNoiseAlert(UUID leaderId, Set<UUID> members, float fraction) {
        if (fraction >= ALERT_THRESHOLD) {
            if (alertedGroups.add(leaderId)) {
                GwwDebug.log(GwwDebug.Category.NOISE, "Grupo {} pasó el umbral de aviso ({})",
                        leaderId.toString().substring(0, 8), fraction);
                playToGroup(members, ModSounds.NOISE_ALERT);
            }
        } else if (fraction < ALERT_RESET) {
            alertedGroups.remove(leaderId);
        }
    }

    private static void playToGroup(Set<UUID> members, SoundEvent sound) {
        for (UUID memberId : members) {
            ServerPlayer member = getPlayerByUUID(memberId);
            if (member != null) {
                member.playNotifySound(sound, SoundSource.AMBIENT, 0.9f, 1.0f);
            }
        }
    }

    // ==================== DETECCIÓN DE MOVIMIENTO ====================

    private static void detectMovementNoise(ServerPlayer player, PlayerNoiseData data) {
        Vec3 currentPos = player.position();
        Vec3 lastPos = data.getLastPosition();

        if (lastPos.equals(Vec3.ZERO)) {
            data.setLastPosition(currentPos);
            return;
        }

        double deltaX = currentPos.x - lastPos.x;
        double deltaZ = currentPos.z - lastPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (horizontalDistance < NoiseConfig.MIN_MOVEMENT_SPEED) {
            return;
        }

        if (player.onGround()) {
            handleFootsteps(player, data, horizontalDistance);
        }

        if (player.onGround() && !data.isWasOnGround()) {
            handleLanding(player, data);
        }

        if (!player.onGround() && data.isWasOnGround()) {
            handleJump(player, data);
        }
    }

    private static void handleFootsteps(ServerPlayer player, PlayerNoiseData data, double distance) {
        data.setAccumulatedDistance(data.getAccumulatedDistance() + (float) distance);
        float requiredDistance = data.getStepDistance();

        if (data.getAccumulatedDistance() >= requiredDistance) {
            float noiseAmount = calculateFootstepNoise(player, data);
            addNoise(player, noiseAmount);
            data.setAccumulatedDistance(0);
            data.setTicksSinceLastStep(0);
        }
    }

    private static float calculateFootstepNoise(ServerPlayer player, PlayerNoiseData data) {
        float baseNoise = player.isSprinting() ? GwwSettings.sprinting() :
                player.isCrouching() ? GwwSettings.sneaking() : GwwSettings.walking();

        float surfaceMultiplier = getSurfaceMultiplier(player);
        float speedMultiplier = getSpeedMultiplier(player);

        return baseNoise * surfaceMultiplier * speedMultiplier;
    }

    private static float getSurfaceMultiplier(ServerPlayer player) {
        BlockPos blockBelow = player.blockPosition().below();
        BlockState blockState = player.level().getBlockState(blockBelow);
        Block block = blockState.getBlock();

        float customNoise = NoiseAPI.getBlockNoise(block);
        if (customNoise > 0) return customNoise * 2;

        SoundType soundType = blockState.getSoundType();

        if (soundType == SoundType.WOOL || soundType == SoundType.MOSS_CARPET)
            return NoiseConfig.SURFACE_MULTIPLIER_SOFT;
        if (soundType == SoundType.GRAVEL || soundType == SoundType.SNOW || soundType == SoundType.SAND || soundType == SoundType.SOUL_SAND)
            return NoiseConfig.SURFACE_MULTIPLIER_LOUD;
        if (soundType == SoundType.METAL || soundType == SoundType.CHAIN || soundType == SoundType.ANVIL)
            return NoiseConfig.SURFACE_MULTIPLIER_HARD * 1.2f;
        if (soundType == SoundType.WOOD || soundType == SoundType.BAMBOO_WOOD)
            return NoiseConfig.SURFACE_MULTIPLIER_HARD;

        return NoiseConfig.SURFACE_MULTIPLIER_NORMAL;
    }

    private static float getSpeedMultiplier(ServerPlayer player) {
        Vec3 delta = player.getDeltaMovement();
        double speed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (speed > NoiseConfig.SPRINT_THRESHOLD * 1.5) return 1.3f;
        if (speed > NoiseConfig.SPRINT_THRESHOLD) return 1.15f;
        return 1.0f;
    }

    private static void handleLanding(ServerPlayer player, PlayerNoiseData data) {
        double fallSpeed = Math.abs(player.getDeltaMovement().y);
        float landingNoise = fallSpeed < 0.3 ? GwwSettings.landingSoft() :
                fallSpeed < 0.6 ? GwwSettings.landingNormal() : GwwSettings.landingHard();

        landingNoise *= getSurfaceMultiplier(player);
        if (fallSpeed >= 0.6) landingNoise *= Math.min(2.0f, (float) fallSpeed);

        addNoise(player, landingNoise);
    }

    private static void handleJump(ServerPlayer player, PlayerNoiseData data) {
        float jumpNoise = GwwSettings.jumping();
        if (player.isSprinting()) jumpNoise *= 1.5f;
        else if (player.isCrouching()) jumpNoise *= 0.5f;
        addNoise(player, jumpNoise);
    }

    public static void onBlockBreak(ServerPlayer player, BlockState state) {
        float noise = NoiseConfig.BLOCK_BREAK;
        SoundType st = state.getSoundType();
        if (st == SoundType.GLASS) noise *= 1.5f;
        else if (st == SoundType.METAL || st == SoundType.ANVIL) noise *= 1.3f;
        else if (st == SoundType.WOOL) noise *= 0.5f;
        addNoise(player, noise);
    }

    public static void onBlockPlace(ServerPlayer player, BlockState state) {
        float noise = NoiseConfig.BLOCK_PLACE;
        SoundType st = state.getSoundType();
        if (st == SoundType.METAL || st == SoundType.ANVIL) noise *= 1.4f;
        else if (st == SoundType.WOOL || st == SoundType.MOSS_CARPET) noise *= 0.6f;
        addNoise(player, noise);
    }

    public static void onDamageTaken(ServerPlayer player, float damage) {
        float noise = NoiseConfig.DAMAGE_TAKEN * Math.min(2.0f, damage / 10f);
        addNoise(player, noise);
    }

    public static void onAttack(ServerPlayer player) {
        float noise = NoiseConfig.ATTACK;
        if (player.isSprinting()) noise *= 1.3f;
        addNoise(player, noise);
    }

    public static void onItemUse(ServerPlayer player, ItemStack stack) {
        float itemNoise = NoiseAPI.getItemNoise(stack.getItem());
        addNoise(player, itemNoise > 0 ? itemNoise : NoiseConfig.ITEM_USE);
    }

    public static void toggleSystem(ServerPlayer player, boolean enabled) {
        UUID leader = getGroupLeader(player.getUUID());
        PlayerNoiseData data = getPlayerData(leader);
        data.setEnabled(enabled);
        if (!enabled) {
            data.reset();
        }
        // Un jugador suelto (sin arena) es su propio grupo: hay que registrarlo para
        // que el barrido por grupos lo vea, o se le quedaría la barra congelada.
        if (enabled) {
            GROUP_MEMBERS.computeIfAbsent(leader, k -> new LinkedHashSet<>()).add(player.getUUID());
            getPlayerData(player.getUUID()).setEnabled(true);
        } else if (leader.equals(player.getUUID())) {
            GROUP_MEMBERS.remove(leader);
        }
        forceSync(leader);
    }

    // ==================== ENVÍO ====================

    /** Oculta la barra a un jugador concreto. */
    private static void sendHidden(ServerPlayer player) {
        sendTo(player, new NoisePacket(0f, 0f, false));
    }

    private static void sendTo(ServerPlayer player, NoisePacket packet) {
        ServerPlayNetworking.send(player, new FabricCustomPayload(packet));
        GwwDebug.count(GwwDebug.PACKETS_NOISE);
    }

    private static void broadcastToGroup(UUID leaderId, Set<UUID> members, NoisePacket packet) {
        for (UUID memberId : members) {
            ServerPlayer member = getPlayerByUUID(memberId);
            if (member != null) {
                sendTo(member, packet);
            }
        }
        GwwDebug.log(GwwDebug.Category.PACKETS, "Barra de ruido -> grupo {} ({} miembros), valor {}",
                leaderId.toString().substring(0, 8), members.size(), packet.getNoiseLevel());
    }

    /** Envío inmediato saltándose el freno: para cambios que el jugador debe ver ya. */
    private static void forceSync(UUID leaderId) {
        Set<UUID> members = GROUP_MEMBERS.get(leaderId);
        if (members == null || members.isEmpty()) {
            return;
        }
        PlayerNoiseData data = getPlayerData(leaderId);
        float fraction = asBarFraction(data.getCurrentNoise());
        data.setTicksSinceSync(0);
        data.setLastSentNoise(fraction);
        data.setLastSentEnabled(data.isEnabled());
        broadcastToGroup(leaderId, members, new NoisePacket(
                fraction, asBarFraction(GwwSettings.noiseDecayPerTick()), data.isEnabled()));
    }

    /**
     * La barra del cliente siempre va de 0 a 1, así que el ruido se manda como
     * fracción de la capacidad configurada: subir 'noise.max' hace que se llene
     * más despacio, bajarlo que se llene antes.
     */
    private static float asBarFraction(float noise) {
        return Math.min(1.0f, noise / GwwSettings.noiseMax());
    }

    private static ServerPlayer getPlayerByUUID(UUID uuid) {
        return GWW.server == null ? null : GWW.server.getPlayerList().getPlayer(uuid);
    }

    // ==================== GRUPOS POR ARENA ====================

    /**
     * Mete al jugador en el grupo de ruido de una arena. El líder es un UUID
     * sintético por arena, así el grupo no depende de ningún jugador concreto
     * y admite cualquier cantidad de miembros.
     */
    public static void joinArenaGroup(ServerPlayer player, UUID arenaGroupId) {
        PLAYER_GROUPS.put(player.getUUID(), arenaGroupId);
        GROUP_MEMBERS.computeIfAbsent(arenaGroupId, k -> new LinkedHashSet<>()).add(player.getUUID());

        PlayerNoiseData groupData = getPlayerData(arenaGroupId);
        groupData.setEnabled(true);

        // El flag propio controla la detección de movimiento en el tick del jugador
        PlayerNoiseData own = getPlayerData(player.getUUID());
        own.setEnabled(true);
        own.setLastPosition(player.position());

        GwwDebug.log(GwwDebug.Category.NOISE, "{} entra al grupo {}",
                player.getGameProfile().getName(), arenaGroupId.toString().substring(0, 8));
        forceSync(arenaGroupId);
    }

    /**
     * Saca al jugador del grupo de su arena y le oculta la barra de ruido.
     */
    public static void leaveArenaGroup(ServerPlayer player) {
        removeFromArenaGroup(player.getUUID());
        sendHidden(player);
        GwwDebug.log(GwwDebug.Category.NOISE, "{} sale de su grupo de ruido",
                player.getGameProfile().getName());
    }

    /**
     * Variante sin jugador conectado (desconexiones): limpia el estado
     * sin intentar enviar paquetes.
     */
    public static void removeFromArenaGroup(UUID playerId) {
        UUID leader = PLAYER_GROUPS.remove(playerId);
        detachFromGroup(leader, playerId);
        PlayerNoiseData own = getPlayerData(playerId);
        own.reset();
        own.setEnabled(false);
    }

    /** Quita al jugador del índice inverso, borrando el grupo si se queda vacío. */
    private static void detachFromGroup(UUID leaderId, UUID playerId) {
        if (leaderId == null) {
            // Podía ser su propio líder (jugador suelto con /noise toggle)
            leaderId = playerId;
        }
        Set<UUID> members = GROUP_MEMBERS.get(leaderId);
        if (members == null) {
            return;
        }
        members.remove(playerId);
        if (members.isEmpty()) {
            GROUP_MEMBERS.remove(leaderId);
            alertedGroups.remove(leaderId);
            // Sala vacía: el ruido no se queda esperando al siguiente grupo.
            PlayerNoiseData data = PLAYER_NOISE_DATA.get(leaderId);
            if (data != null) {
                data.reset();
                data.setLastSentNoise(-1f);
            }
        }
    }

    public static void linkPlayers(UUID p1, UUID p2) {
        UUID leader = getGroupLeader(p1);
        PLAYER_GROUPS.put(p2, leader);
        GROUP_MEMBERS.computeIfAbsent(leader, k -> new LinkedHashSet<>()).add(p1);
        GROUP_MEMBERS.get(leader).add(p2);
        PlayerNoiseData d1 = getPlayerData(leader);
        PlayerNoiseData d2 = getPlayerData(p2);
        d2.setCurrentNoise(d1.getCurrentNoise());
        d2.setEnabled(d1.isEnabled());
        forceSync(leader);
    }

    public static void unlinkPlayer(UUID playerId) {
        UUID leader = PLAYER_GROUPS.remove(playerId);
        detachFromGroup(leader, playerId);
        getPlayerData(playerId).setGroupLeader(null);
    }

    public static int linkPlayersInArea(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        UUID first = null;
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(center) <= radius * radius) {
                if (first == null) {
                    first = p.getUUID();
                } else {
                    linkPlayers(first, p.getUUID());
                }
                count++;
            }
        }
        return count;
    }

    public static boolean isEnabledFor(UUID playerId) {
        return getPlayerData(getGroupLeader(playerId)).isEnabled();
    }

    /** Ruido del grupo como fracción de la barra (0..1), que es como lo lee el Ublabla. */
    public static float getNoiseLevel(UUID playerId) {
        return asBarFraction(getPlayerData(getGroupLeader(playerId)).getCurrentNoise());
    }

    /** Ruido de un grupo concreto (el de una arena), sin pasar por ningún jugador. */
    public static float getGroupNoiseLevel(UUID groupId) {
        PlayerNoiseData data = PLAYER_NOISE_DATA.get(groupId);
        return data == null ? 0f : asBarFraction(data.getCurrentNoise());
    }

    public static void removePlayer(UUID playerId) {
        UUID leader = PLAYER_GROUPS.remove(playerId);
        detachFromGroup(leader, playerId);
        PLAYER_NOISE_DATA.remove(playerId);
        alertedGroups.remove(playerId);
    }

    /** Limpia jugadores desconectados de todos los grupos de una pasada. */
    public static void pruneOffline(Set<UUID> online) {
        Iterator<Map.Entry<UUID, UUID>> it = PLAYER_GROUPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            if (!online.contains(entry.getKey())) {
                detachFromGroup(entry.getValue(), entry.getKey());
                PLAYER_NOISE_DATA.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public static void clearAll() {
        PLAYER_NOISE_DATA.clear();
        PLAYER_GROUPS.clear();
        GROUP_MEMBERS.clear();
        alertedGroups.clear();
    }

    /** Solo para el comando de debug: cuántos grupos hay vivos ahora mismo. */
    public static int getGroupCount() {
        return GROUP_MEMBERS.size();
    }
}
