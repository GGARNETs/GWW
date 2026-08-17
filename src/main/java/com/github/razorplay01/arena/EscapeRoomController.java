package com.github.razorplay01.arena;

import com.github.razorplay01.config.GwwSettings;
import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.custom.PanelEnergiaEntity;
import com.github.razorplay01.entity.custom.UblablaEntity;
import com.github.razorplay01.entity.custom.ValvulaEntity;
import com.github.razorplay01.instance.Instance;
import com.github.razorplay01.instance.InstanceManager;
import com.github.razorplay01.integration.GeoWarePointsIntegration;
import com.github.razorplay01.sound.ModSounds;
import com.github.razorplay01.system.NoiseDetectionSystem;
import com.github.razorplay01.system.SneakSpeedSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Momentos "de partida" de cada arena: el aviso de escape (válvulas resueltas + cable
 * cortado, solo con la partida en marcha) y la victoria al pisar la meta.
 * <p>
 * La meta es independiente del resto: basta con que UN jugador (en survival o aventura)
 * entre en su zona para que ganen todos, sin importar si resolvieron algo. La única
 * condición es estar dentro del área de una arena.
 * <p>
 * Sobre el coste: la lista de jugadores se recorre UNA vez por ronda y cada jugador
 * resuelve su arena por el índice espacial de {@link ArenaManager}. Antes era al revés
 * —por cada arena se recorrían todos los jugadores del servidor— y con 50 salas y 100
 * jugadores eso son 5.000 comprobaciones cada media vuelta, para nada: la inmensa
 * mayoría de las salas no tienen a nadie dentro.
 */
public final class EscapeRoomController {
    private EscapeRoomController() {
    }

    private static final int CHECK_INTERVAL = 10; // medio segundo
    /**
     * Cada cuántos ticks se mira si la sala cumple la condición de escape. Va mucho
     * más espaciado que el resto porque son dos barridos de entidades de la zona, que
     * con morehitboxes recorren la lista global de sub-hitboxes y son de lo más caro
     * que puede hacer el mod. Dos segundos de retraso en un aviso no los nota nadie.
     */
    private static final int ESCAPE_SCAN_INTERVAL = 40;

    /** Arenas a las que ya se les lanzó el aviso de "¡ESCAPA!" en la ronda actual. */
    private static final Set<String> escapeAlerted = new HashSet<>();
    /** Arenas cuya victoria ya se disparó; se rearma al volver a arrancar la arena. */
    private static final Set<String> won = new HashSet<>();
    /** Contornos de meta que se están mostrando temporalmente (por comando). */
    private static final Map<String, MetaPreview> previews = new HashMap<>();
    /** Ticks que le faltan a cada arena para su próximo escaneo de condición de escape. */
    private static final Map<String, Integer> escapeScanCooldown = new HashMap<>();
    private static int tickCounter = 0;

    /** Reinicia el estado de partida de una arena (al arrancarla de nuevo). */
    public static void onArenaStarted(String arenaId) {
        won.remove(arenaId);
        escapeAlerted.remove(arenaId);
        escapeScanCooldown.remove(arenaId);
    }

    public static void showMetaPreview(ServerLevel level, String arenaId, AABB box, int durationTicks) {
        previews.put(arenaId, new MetaPreview(level, box, durationTicks));
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        escapeAlerted.removeIf(id -> !ArenaManager.isRunning(id));
        renderPreviews();

        if (ArenaManager.getAll().isEmpty()) {
            return;
        }

        // Un solo barrido: cada jugador cae en su arena por el índice espacial.
        // Las salas vacías (que son casi todas) ni se tocan.
        Map<String, List<ServerPlayer>> byArena = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!NoiseDetectionSystem.isEligible(player)) {
                continue;
            }
            Arena arena = ArenaManager.getArenaAt(player.position());
            if (arena == null) {
                continue;
            }
            if (byArena == null) {
                byArena = new HashMap<>();
            }
            byArena.computeIfAbsent(arena.getId(), k -> new ArrayList<>(4)).add(player);
        }

        Set<UUID> playersInGame = new HashSet<>();
        if (byArena != null) {
            for (Map.Entry<String, List<ServerPlayer>> entry : byArena.entrySet()) {
                Arena arena = ArenaManager.get(entry.getKey());
                if (arena != null) {
                    processArena(arena, entry.getValue(), playersInGame);
                }
            }
        }
        SneakSpeedSystem.sync(server, playersInGame);
    }

    private static void processArena(Arena arena, List<ServerPlayer> players, Set<UUID> playersInGame) {
        ServerLevel level = players.get(0).serverLevel();

        // El aviso de escape es parte del juego: solo con la partida en marcha.
        if (ArenaManager.isRunning(arena.getId())) {
            for (ServerPlayer player : players) {
                playersInGame.add(player.getUUID());
            }
            checkEscapeAlert(level, arena, players);
        }
        // La meta es independiente: se comprueba siempre.
        checkVictory(level, arena, players);
    }

    /**
     * Cuando por primera vez coinciden válvulas resueltas y cable cortado, se lanza el
     * aviso de escape y se altera al Ublabla. Si la condición se deshace, se rearma.
     */
    private static void checkEscapeAlert(ServerLevel level, Arena arena, List<ServerPlayer> players) {
        // El escaneo es caro: se hace a su propio ritmo, no en cada ronda.
        int cooldown = escapeScanCooldown.getOrDefault(arena.getId(), 0) - CHECK_INTERVAL;
        if (cooldown > 0) {
            escapeScanCooldown.put(arena.getId(), cooldown);
            return;
        }
        escapeScanCooldown.put(arena.getId(), ESCAPE_SCAN_INTERVAL);

        AABB zone = arena.getZoneAABB();
        GwwDebug.count(GwwDebug.ENTITY_SCANS, 2);
        boolean cableCut = !level.getEntitiesOfClass(PanelEnergiaEntity.class, zone,
                PanelEnergiaEntity::isActive).isEmpty();
        boolean escapeReady = cableCut && ValvulaEntity.allSolved(level, zone);

        if (!escapeReady) {
            escapeAlerted.remove(arena.getId());
            return;
        }
        if (!escapeAlerted.add(arena.getId())) {
            return; // ya avisado en esta racha
        }

        GwwDebug.log(GwwDebug.Category.ARENA, "Arena {}: condición de escape cumplida, avisando a {} jugadores",
                arena.getId(), players.size());

        Component title = Component.literal("¡ESCAPA!")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.literal("Escapa por el ducto de ventilación en el ático")
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 100, 20)); // ~5 s
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            // El aviso de escape es el bicho, no una sirena: lo que se oye es al
            // Ublabla rugiendo, que es quien los tiene ahí encerrados.
            player.playNotifySound(ModSounds.UBLABLA_ROAR, SoundSource.MASTER, 1.0f, 1.0f);
            GwwDebug.count(GwwDebug.PACKETS_MESSAGES, 3);
        }

        BlockPos target = players.get(0).blockPosition();
        GwwDebug.count(GwwDebug.ENTITY_SCANS);
        level.getEntitiesOfClass(UblablaEntity.class, zone, u -> true)
                .forEach(ublabla -> ublabla.alertTo(target));
    }

    /**
     * Victoria: basta con que un jugador entre en la meta. Ganan todos los jugadores de
     * la zona: se les felicita, se les dan puntos, pasan a espectador, se calma al
     * Ublabla y se para la arena. Solo se dispara una vez por partida.
     */
    private static void checkVictory(ServerLevel level, Arena arena, List<ServerPlayer> players) {
        if (won.contains(arena.getId())) {
            return;
        }
        Instance instance = InstanceManager.get(arena.getInstanceName());
        if (instance == null || !instance.hasMeta()) {
            return;
        }
        AABB meta = instance.resolveMetaBox(arena.getOrigin());
        if (meta == null) {
            return;
        }

        boolean anyInMeta = false;
        for (ServerPlayer player : players) {
            if (meta.contains(player.position())) {
                anyInMeta = true;
                break;
            }
        }
        if (!anyInMeta) {
            return;
        }

        won.add(arena.getId());
        GwwDebug.log(GwwDebug.Category.ARENA, "Arena {}: victoria de {} jugadores",
                arena.getId(), players.size());

        Component title = Component.literal("¡HAN ESCAPADO!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        Component subtitle = Component.literal("¡Felicidades!").withStyle(ChatFormatting.YELLOW);
        int victoryPoints = GwwSettings.victoryPoints();
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.playNotifySound(ModSounds.VICTORY, SoundSource.MASTER, 1.0f, 1.0f);
            GeoWarePointsIntegration.award(player, victoryPoints);
            player.setGameMode(GameType.SPECTATOR);
            GwwDebug.count(GwwDebug.PACKETS_MESSAGES, 3);
        }

        GwwDebug.count(GwwDebug.ENTITY_SCANS);
        level.getEntitiesOfClass(UblablaEntity.class, arena.getZoneAABB(), u -> true)
                .forEach(UblablaEntity::resetToPatrol);

        ArenaLight.refresh(level, arena, false);
        escapeAlerted.remove(arena.getId());
        ArenaManager.setRunning(arena.getId(), false);
    }

    // ==================== PREVISUALIZACIÓN DE LA META ====================

    private static void renderPreviews() {
        if (previews.isEmpty()) {
            return;
        }
        previews.entrySet().removeIf(entry -> {
            MetaPreview preview = entry.getValue();
            drawBoxOutline(preview.level, preview.box);
            preview.ticksLeft -= CHECK_INTERVAL;
            return preview.ticksLeft <= 0;
        });
    }

    private static void drawBoxOutline(ServerLevel level, AABB box) {
        double step = 0.5;
        for (double x = box.minX; x <= box.maxX; x += step) {
            spawnMarker(level, x, box.minY, box.minZ);
            spawnMarker(level, x, box.minY, box.maxZ);
            spawnMarker(level, x, box.maxY, box.minZ);
            spawnMarker(level, x, box.maxY, box.maxZ);
        }
        for (double z = box.minZ; z <= box.maxZ; z += step) {
            spawnMarker(level, box.minX, box.minY, z);
            spawnMarker(level, box.maxX, box.minY, z);
            spawnMarker(level, box.minX, box.maxY, z);
            spawnMarker(level, box.maxX, box.maxY, z);
        }
        for (double y = box.minY; y <= box.maxY; y += step) {
            spawnMarker(level, box.minX, y, box.minZ);
            spawnMarker(level, box.maxX, y, box.minZ);
            spawnMarker(level, box.minX, y, box.maxZ);
            spawnMarker(level, box.maxX, y, box.maxZ);
        }
    }

    private static void spawnMarker(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static final class MetaPreview {
        private final ServerLevel level;
        private final AABB box;
        private int ticksLeft;

        private MetaPreview(ServerLevel level, AABB box, int ticksLeft) {
            this.level = level;
            this.box = box;
            this.ticksLeft = ticksLeft;
        }
    }
}
