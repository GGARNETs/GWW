package com.github.razorplay01.extra;

import lombok.Getter;
import net.minecraft.world.phys.Vec3;

/**
 * Lo que sabe el cliente del minijuego de cañones. El servidor solo manda este
 * estado a los jugadores que están dentro de una arena, así que 'active' quiere
 * decir "estoy jugando": de ahí cuelgan la cámara aérea y el borde de partículas.
 */
@Getter
public class ClientMinigameState {
    private static ClientMinigameState instance;

    private boolean active;
    private Vec3 center = Vec3.ZERO;
    private double radius;

    public static ClientMinigameState get() {
        if (instance == null) instance = new ClientMinigameState();
        return instance;
    }

    public void update(boolean active, Vec3 center, double radius) {
        this.active = active;
        this.center = center;
        this.radius = radius;
    }

    /**
     * Al salir del mundo (desconexión, cambio de servidor) hay que olvidarse del
     * juego: si no, se vuelve a entrar con la cámara aérea puesta y sin forma de
     * quitarla, porque el paquete que la apagaba ya no va a llegar.
     */
    public void clear() {
        if (!active) return;
        active = false;
        center = Vec3.ZERO;
        radius = 0;
    }
}
