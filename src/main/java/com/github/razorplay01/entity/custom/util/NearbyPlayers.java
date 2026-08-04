package com.github.razorplay01.entity.custom.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Búsqueda de jugadores cercanos sin pasar por {@code Level#getEntitiesOfClass}.
 * <p>
 * Con morehitboxes instalado, TODA consulta de entidades del servidor recorre la lista
 * global de sub-hitboxes ({@code FabricLevelMixin#addMultiPartsToEntityQuery}), que no
 * tiene ningún índice espacial, y encima hace un {@code List#contains} lineal por
 * candidato. Con el mapa entero cargado esa lista son decenas de miles de entradas: una
 * escalera preguntando "¿quién hay cerca?" acababa costando más que el resto de su tick.
 * <p>
 * La lista de jugadores del nivel, en cambio, es directa: son ~100 comparaciones contra
 * las ~20.000 del barrido de multiparts.
 */
public final class NearbyPlayers {
    private NearbyPlayers() {
    }

    /** Jugadores cuyo hitbox toca la caja dada. Devuelve la lista vacía si no hay ninguno. */
    public static List<Player> within(Entity source, AABB box) {
        List<Player> found = null;
        for (Player player : source.level().players()) {
            if (player.isRemoved() || !box.intersects(player.getBoundingBox())) {
                continue;
            }
            if (found == null) {
                found = new ArrayList<>(4);
            }
            found.add(player);
        }
        return found == null ? List.of() : found;
    }

    /** True en cuanto encuentra un jugador dentro del radio, sin construir ninguna lista. */
    public static boolean anyWithin(Entity source, double radius) {
        double radiusSq = radius * radius;
        for (Player player : source.level().players()) {
            if (!player.isRemoved() && player.distanceToSqr(source) <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
