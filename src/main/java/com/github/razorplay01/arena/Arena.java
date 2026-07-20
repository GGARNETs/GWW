package com.github.razorplay01.arena;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Una arena del escape room definida en config/GWW/config.yml:
 * zona de juego, jaula opcional, instance a pegar y coordenada donde pegarla.
 */
@Getter
public class Arena {
    private final String id;
    private final String instanceName;
    private final BlockPos origin;
    private final BlockPos zoneMin;
    private final BlockPos zoneMax;
    private final BlockPos jailMin;
    private final BlockPos jailMax;
    /**
     * Líder sintético del grupo de ruido: todos los jugadores dentro de la
     * zona comparten la barra de ruido bajo este UUID.
     */
    private final UUID noiseGroupId;

    /**
     * La zona no cambia una vez creada la arena, así que el AABB se calcula una sola
     * vez. Antes se construía uno nuevo en cada contains(), y getArenaAt() lo llama
     * para cada arena: con 50 salas y 100 jugadores eran miles de objetos por segundo.
     */
    private final AABB zoneAABB;

    public Arena(String id, String instanceName, BlockPos origin,
                 BlockPos zoneA, BlockPos zoneB,
                 BlockPos jailMin, BlockPos jailMax) {
        this.id = id;
        this.instanceName = instanceName;
        this.origin = origin;
        this.zoneMin = new BlockPos(
                Math.min(zoneA.getX(), zoneB.getX()),
                Math.min(zoneA.getY(), zoneB.getY()),
                Math.min(zoneA.getZ(), zoneB.getZ()));
        this.zoneMax = new BlockPos(
                Math.max(zoneA.getX(), zoneB.getX()),
                Math.max(zoneA.getY(), zoneB.getY()),
                Math.max(zoneA.getZ(), zoneB.getZ()));
        this.jailMin = jailMin;
        this.jailMax = jailMax;
        this.noiseGroupId = UUID.nameUUIDFromBytes(("gww:arena:" + id).getBytes(StandardCharsets.UTF_8));
        this.zoneAABB = new AABB(
                this.zoneMin.getX(), this.zoneMin.getY(), this.zoneMin.getZ(),
                this.zoneMax.getX() + 1.0, this.zoneMax.getY() + 1.0, this.zoneMax.getZ() + 1.0);
    }

    public boolean contains(Vec3 pos) {
        return zoneAABB.contains(pos);
    }

    public boolean hasJail() {
        return jailMin != null && jailMax != null;
    }
}
