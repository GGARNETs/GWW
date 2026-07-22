package com.github.razorplay01.network.packet;

import com.github.razorplay.packet_handler.exceptions.PacketSerializationException;
import com.github.razorplay.packet_handler.network.IPacket;
import com.github.razorplay.packet_handler.network.network_util.PacketDataSerializer;
import com.github.razorplay01.extra.CannonArena;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.world.phys.Vec3;

/**
 * Estado del minijuego de cañones para un jugador concreto. Solo lo reciben los
 * jugadores que están dentro de una arena en marcha, así que 'isActive' significa
 * "estás jugando": es lo que enciende la cámara aérea y el borde de partículas.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MinigameStatePacket implements IPacket {

    private boolean isActive;
    private double x;
    private double y;
    private double z;
    private double radius;

    public static MinigameStatePacket of(CannonArena arena) {
        Vec3 center = arena.getCenter();
        return new MinigameStatePacket(true, center.x, center.y, center.z, arena.getRadius());
    }

    public static MinigameStatePacket inactive() {
        return new MinigameStatePacket(false, 0, 0, 0, 0);
    }

    @Override
    public void read(PacketDataSerializer serializer) throws PacketSerializationException {
        this.isActive = serializer.readBoolean();
        this.x = serializer.readDouble();
        this.y = serializer.readDouble();
        this.z = serializer.readDouble();
        this.radius = serializer.readDouble();
    }

    @Override
    public void write(PacketDataSerializer serializer) throws PacketSerializationException {
        serializer.writeBoolean(this.isActive);
        serializer.writeDouble(this.x);
        serializer.writeDouble(this.y);
        serializer.writeDouble(this.z);
        serializer.writeDouble(this.radius);
    }
}
