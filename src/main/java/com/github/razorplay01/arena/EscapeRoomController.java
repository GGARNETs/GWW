package com.github.razorplay01.arena;

import com.github.razorplay01.entity.custom.PanelEnergiaEntity;
import com.github.razorplay01.entity.custom.UblablaEntity;
import com.github.razorplay01.entity.custom.ValvulaEntity;
import com.github.razorplay01.instance.Instance;
import com.github.razorplay01.instance.InstanceManager;
import com.github.razorplay01.integration.GeoWarePointsIntegration;
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
import net.minecraft.sounds.SoundEvents;
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
 */
public final class EscapeRoomController {
    private EscapeRoomController() {
    }

    private static final int CHECK_INTERVAL = 10; // medio segundo
    private static final int VICTORY_POINTS = 200;

    /** Arenas a las que ya se les lanzó el aviso de "¡ESCAPA!" en la ronda actual. */
    private static final Set<String> escapeAlerted = new HashSet<>();
    /** Arenas cuya victoria ya se disparó; se rearma al volver a arrancar la arena. */
    private static final Set<String> won = new HashSet<>();
    /** Contornos de meta que se están mostrando temporalmente (por comando). */
    private static final Map<String, MetaPreview> previews = new HashMap<>();
    private static int tickCounter = 0;

    /** Reinicia el estado de partida de una arena (al arrancarla de nuevo). */
    public static void onArenaStarted(String arenaId) {
        won.remove(arenaId);
        escapeAlerted.remove(arenaId);
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

        // Jugadores dentro de alguna arena en marcha: son los que llevan el impulso
        // de velocidad al agacharse mientras dura la partida.
        Set<UUID> playersInGame = new HashSet<>();
        for (String id : List.copyOf(ArenaManager.getIds())) {
            Arena arena = ArenaManager.get(id);
            if (arena != null) {
                processArena(server, arena, playersInGame);
            }
        }
        SneakSpeedSystem.sync(server, playersInGame);

        renderPreviews();
    }

    private static void processArena(MinecraftServer server, Arena arena, Set<UUID> playersInGame) {
        AABB zone = arena.getZoneAABB();

        // Jugadores que cuentan: los que están jugando (survival/aventura) dentro de la
        // zona. De ellos sale también el nivel en el que mirar las entidades de la sala.
        List<ServerPlayer> players = new ArrayList<>();
        ServerLevel level = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (NoiseDetectionSystem.isEligible(player) && zone.contains(player.position())) {
                players.add(player);
                level = player.serverLevel();
            }
        }
        if (level == null) {
            return; // nadie jugando en la zona
        }

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
        AABB zone = arena.getZoneAABB();
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

        Component title = Component.literal("¡ESCAPA!")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component subtitle = Component.literal("Escapa por el ducto de ventilación en el sótano")
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 100, 20)); // ~5 s
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0f, 1.0f);
        }

        BlockPos target = players.get(0).blockPosition();
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

        Component title = Component.literal("¡HAN ESCAPADO!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        Component subtitle = Component.literal("¡Felicidades!").withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
            GeoWarePointsIntegration.award(player, VICTORY_POINTS);
            player.setGameMode(GameType.SPECTATOR);
        }

        level.getEntitiesOfClass(UblablaEntity.class, arena.getZoneAABB(), u -> true)
                .forEach(UblablaEntity::resetToPatrol);

        ArenaLight.refresh(level, arena, false);
        escapeAlerted.remove(arena.getId());
        ArenaManager.setRunning(arena.getId(), false);
    }

    // ==================== PREVISUALIZACIÓN DE LA META ====================

    private static void renderPreviews() {
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
