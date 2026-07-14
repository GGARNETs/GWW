package com.github.razorplay01.system;

import com.github.razorplay01.GWW;
import com.github.razorplay01.api.noise.NoiseAPI;
import com.github.razorplay01.config.GwwSettings;
import com.github.razorplay01.network.FabricCustomPayload;
import com.github.razorplay01.network.packet.NoisePacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoiseDetectionSystem {

    private static final Map<UUID, PlayerNoiseData> PLAYER_NOISE_DATA = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_GROUPS = new HashMap<>(); // player -> groupLeader

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

    public static void addNoise(ServerPlayer player, float amount) {
        if (player == null || !isEligible(player)) return;
        UUID leaderId = getGroupLeader(player.getUUID());
        PlayerNoiseData data = getPlayerData(leaderId);
        if (!data.isEnabled()) return;

        amount *= data.getMultiplier();
        float newNoise = Math.min(data.getCurrentNoise() + amount, GwwSettings.noiseMax());
        data.setCurrentNoise(newNoise);
        data.setLastNoiseTime(System.currentTimeMillis());

        syncGroupToClients(leaderId);
    }

    public static void tick(ServerPlayer player) {
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

        detectMovementNoise(player, data);
        applyGroupDecay(getGroupLeader(player.getUUID()));

        data.setLastPosition(player.position());
        data.setWasOnGround(player.onGround());
        data.setWasSneaking(player.isCrouching());
        data.setWasSprinting(player.isSprinting());
        data.setTicksSinceLastStep(data.getTicksSinceLastStep() + 1);

        if (player.tickCount % 5 == 0) {
            syncGroupToClients(getGroupLeader(player.getUUID()));
        }
    }

    private static void applyGroupDecay(UUID leaderId) {
        PlayerNoiseData data = getPlayerData(leaderId);
        if (data.getCurrentNoise() > 0) {
            data.setCurrentNoise(Math.max(0, data.getCurrentNoise() - GwwSettings.noiseDecayPerTick()));
        }
    }

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
        if (!enabled) data.reset();
        syncGroupToClients(leader);
    }

    /** Oculta la barra a un jugador concreto. */
    private static void sendHidden(ServerPlayer player) {
        ServerPlayNetworking.send(player, new FabricCustomPayload(new NoisePacket(0f, 0f, false)));
    }

    /**
     * La barra del cliente siempre va de 0 a 1, así que el ruido se manda como
     * fracción de la capacidad configurada: subir 'noise.max' hace que se llene
     * más despacio, bajarlo que se llene antes.
     */
    private static float asBarFraction(float noise) {
        return Math.min(1.0f, noise / GwwSettings.noiseMax());
    }

    private static void syncGroupToClients(UUID leaderId) {
        PlayerNoiseData data = getPlayerData(leaderId);
        NoisePacket packet = new NoisePacket(
                asBarFraction(data.getCurrentNoise()),
                asBarFraction(GwwSettings.noiseDecayPerTick()),
                data.isEnabled());

        // Sincronizar a todos los miembros del grupo
        for (Map.Entry<UUID, UUID> entry : PLAYER_GROUPS.entrySet()) {
            if (entry.getValue().equals(leaderId)) {
                ServerPlayer member = getPlayerByUUID(entry.getKey());
                if (member != null) {
                    ServerPlayNetworking.send(member, new FabricCustomPayload(packet));
                }
            }
        }
        // También al líder
        ServerPlayer leader = getPlayerByUUID(leaderId);
        if (leader != null) {
            ServerPlayNetworking.send(leader, new FabricCustomPayload(packet));
        }
    }

    private static ServerPlayer getPlayerByUUID(UUID uuid) {
        return GWW.server.getPlayerList().getPlayer(uuid);
    }

    // ==================== GRUPOS POR ARENA ====================

    /**
     * Mete al jugador en el grupo de ruido de una arena. El líder es un UUID
     * sintético por arena, así el grupo no depende de ningún jugador concreto
     * y admite cualquier cantidad de miembros.
     */
    public static void joinArenaGroup(ServerPlayer player, UUID arenaGroupId) {
        PLAYER_GROUPS.put(player.getUUID(), arenaGroupId);

        PlayerNoiseData groupData = getPlayerData(arenaGroupId);
        groupData.setEnabled(true);

        // El flag propio controla la detección de movimiento en tick()
        PlayerNoiseData own = getPlayerData(player.getUUID());
        own.setEnabled(true);
        own.setLastPosition(player.position());

        syncGroupToClients(arenaGroupId);
    }

    /**
     * Saca al jugador del grupo de su arena y le oculta la barra de ruido.
     */
    public static void leaveArenaGroup(ServerPlayer player) {
        removeFromArenaGroup(player.getUUID());
        sendHidden(player);
    }

    /**
     * Variante sin jugador conectado (desconexiones): limpia el estado
     * sin intentar enviar paquetes.
     */
    public static void removeFromArenaGroup(UUID playerId) {
        PLAYER_GROUPS.remove(playerId);
        PlayerNoiseData own = getPlayerData(playerId);
        own.reset();
        own.setEnabled(false);
    }

    public static void linkPlayers(UUID p1, UUID p2) {
        UUID leader = getGroupLeader(p1);
        PLAYER_GROUPS.put(p2, leader);
        PlayerNoiseData d1 = getPlayerData(leader);
        PlayerNoiseData d2 = getPlayerData(p2);
        d2.setCurrentNoise(d1.getCurrentNoise());
        d2.setEnabled(d1.isEnabled());
    }

    public static void unlinkPlayer(UUID playerId) {
        PLAYER_GROUPS.remove(playerId);
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

    public static void removePlayer(UUID playerId) {
        PLAYER_NOISE_DATA.remove(playerId);
        PLAYER_GROUPS.remove(playerId);
    }

    public static void clearAll() {
        PLAYER_NOISE_DATA.clear();
        PLAYER_GROUPS.clear();
    }
}