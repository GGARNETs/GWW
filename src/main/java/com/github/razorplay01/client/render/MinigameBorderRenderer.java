package com.github.razorplay01.client.render;

import com.github.razorplay01.extra.ClientMinigameState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Dibuja el borde de la arena de cañones en el propio cliente.
 * <p>
 * Antes lo pintaba el servidor con un sendParticles por punto y por tick: con un
 * radio de 20 eran más de 8.000 paquetes por segundo, y con 100 jugadores eso solo
 * ya tumbaba el tick rate. El cliente ya recibe el centro y el radio, así que puede
 * pintarlo él mismo sin gastar ni un paquete.
 */
public class MinigameBorderRenderer {
    private MinigameBorderRenderer() {
        /* This utility class should not be instantiated */
    }

    /** Cada cuántos ticks se repinta el anillo. Las partículas duran más que esto. */
    private static final int DRAW_INTERVAL = 3;
    /** Separación entre partículas del anillo, en bloques. */
    private static final double SPACING = 0.6;
    private static final int MIN_POINTS = 40;
    private static final int MAX_POINTS = 400;

    private static int tickCounter = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MinigameBorderRenderer::tick);
    }

    private static void tick(Minecraft client) {
        ClientMinigameState state = ClientMinigameState.get();

        if (client.level == null || client.player == null) {
            state.clear();
            return;
        }
        if (!state.isActive()) return;

        if (++tickCounter < DRAW_INTERVAL) return;
        tickCounter = 0;
        draw(client.level, state);
    }

    private static void draw(ClientLevel level, ClientMinigameState state) {
        Vec3 center = state.getCenter();
        double radius = state.getRadius();
        if (radius <= 0) return;

        int points = Mth.clamp((int) (radius * 2 * Math.PI / SPACING), MIN_POINTS, MAX_POINTS);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            level.addParticle(ParticleTypes.END_ROD,
                    center.x + radius * Math.cos(angle),
                    center.y,
                    center.z + radius * Math.sin(angle),
                    0, 0, 0);
        }
    }
}
