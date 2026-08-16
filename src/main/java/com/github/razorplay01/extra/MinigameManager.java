package com.github.razorplay01.extra;

import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.custom.CannonBulletEntity;
import com.github.razorplay01.entity.custom.CannonEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Partidas del minijuego de cañones en marcha, una por arena. Antes solo podía
 * haber una partida en todo el servidor (una variable estática global), lo que
 * impedía tener varias salas jugando a la vez.
 */
public class MinigameManager {
    private MinigameManager() {
        /* This utility class should not be instantiated */
    }

    private static final Map<String, MinigameState> ACTIVE = new LinkedHashMap<>();

    /**
     * Arranca una partida en la arena. Si ya había una, se para primero.
     */
    public static void start(ServerLevel level, CannonArena arena, int durationSeconds,
                             int totalShots, double bulletSpeed, float bulletDamage) {
        stop(arena.getId());
        ACTIVE.put(arena.getId(), new MinigameState(level, arena, durationSeconds, totalShots, bulletSpeed, bulletDamage));
        GwwDebug.log(GwwDebug.Category.MINIGAME, "Arena {} arranca: {}s, {} disparos, vel {}, daño {}",
                arena.getId(), durationSeconds, totalShots, bulletSpeed, bulletDamage);
    }

    /** Para la partida de una arena. Devuelve false si no había ninguna. */
    public static boolean stop(String arenaId) {
        MinigameState game = ACTIVE.remove(arenaId);
        if (game == null) return false;
        game.endGame();
        GwwDebug.log(GwwDebug.Category.MINIGAME, "Arena {} parada", arenaId);
        return true;
    }

    /**
     * Barre los cañones y balas huérfanos de la zona de una arena: los que dejó
     * un crash o un cierre en seco y ya no pertenecen a ninguna partida.
     * Devuelve cuántos borró. Solo ve los chunks cargados, así que conviene
     * ejecutarlo estando cerca de la arena.
     */
    public static int clearOrphans(ServerLevel level, CannonArena arena) {
        int removed = 0;
        GwwDebug.count(GwwDebug.ENTITY_SCANS, 2);
        for (Entity entity : level.getEntitiesOfClass(CannonEntity.class, arena.getSearchBounds())) {
            entity.discard();
            removed++;
        }
        for (Entity entity : level.getEntitiesOfClass(CannonBulletEntity.class, arena.getSearchBounds())) {
            entity.discard();
            removed++;
        }
        return removed;
    }

    /** Para todas las partidas. Devuelve cuántas había. */
    public static int stopAll() {
        int count = ACTIVE.size();
        for (MinigameState game : new ArrayList<>(ACTIVE.values())) {
            game.endGame();
        }
        ACTIVE.clear();
        return count;
    }

    /**
     * Un jugador está a punto de morir: si estaba jugando en alguna partida,
     * esta lo convierte en espectador y devuelve true para anular la muerte.
     */
    public static boolean onPlayerDeath(ServerPlayer player) {
        if (ACTIVE.isEmpty()) return false;
        for (MinigameState game : ACTIVE.values()) {
            if (game.eliminate(player)) return true;
        }
        return false;
    }

    public static boolean isRunning(String arenaId) {
        return ACTIVE.containsKey(arenaId);
    }

    public static Collection<String> getRunningIds() {
        return ACTIVE.keySet();
    }

    public static List<String> getRunningIdsSnapshot() {
        return new ArrayList<>(ACTIVE.keySet());
    }

    /**
     * Sin partidas en marcha esto sale al primer if, que es lo normal el 99% del
     * tiempo: no cuesta nada tenerlo enganchado al tick del servidor.
     */
    public static void tick() {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<String, MinigameState>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue().tick()) {
                it.remove();
            }
        }
    }
}
