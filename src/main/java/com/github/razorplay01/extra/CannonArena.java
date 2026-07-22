package com.github.razorplay01.extra;

import lombok.Getter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Una arena del minijuego de cañones definida en config/GWW/cannon.yml.
 * Solo guarda la forma de la zona (centro y radio); el tiempo, la dificultad
 * y la velocidad de las balas se pasan al arrancar la partida.
 * <p>
 * La zona es un cilindro: limita en horizontal, no en altura. El jugador puede
 * saltar libremente, es lo coherente con el círculo que ve dibujado en el suelo.
 */
@Getter
public class CannonArena {
    /**
     * Alto y bajo del cilindro a efectos de buscar entidades. No es un límite de
     * juego, solo el cubo que se le pasa al mundo para no escanear medio chunk.
     */
    private static final double VERTICAL_RANGE = 16.0;

    private final String id;
    private final Vec3 center;
    private final double radius;
    private final double radiusSq;

    /**
     * Cubo que envuelve la zona más el anillo exterior donde aparecen los cañones.
     * Se calcula una sola vez: se usa en cada búsqueda de jugadores y crear un AABB
     * nuevo cada vez, con 50 arenas activas, son miles de objetos por segundo.
     */
    private final AABB searchBounds;

    public CannonArena(String id, Vec3 center, double radius) {
        this.id = id;
        this.center = center;
        this.radius = radius;
        this.radiusSq = radius * radius;
        double reach = radius + MinigameState.SPAWN_MARGIN;
        this.searchBounds = new AABB(
                center.x - reach, center.y - VERTICAL_RANGE, center.z - reach,
                center.x + reach, center.y + VERTICAL_RANGE, center.z + reach);
    }

    /** Distancia horizontal al cuadrado. Al cuadrado para ahorrarse la raíz en los filtros. */
    public double horizontalDistSq(Vec3 pos) {
        double dx = pos.x - center.x;
        double dz = pos.z - center.z;
        return dx * dx + dz * dz;
    }

    public boolean contains(Vec3 pos) {
        return horizontalDistSq(pos) <= radiusSq
                && Math.abs(pos.y - center.y) <= VERTICAL_RANGE;
    }
}
