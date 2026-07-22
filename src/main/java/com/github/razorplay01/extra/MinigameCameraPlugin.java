package com.github.razorplay01.extra;

import com.github.razorplay01.cam.api.CameraModifier;
import com.github.razorplay01.cam.api.CameraPlugin;
import com.github.razorplay01.cam.api.ModifierPriority;
import com.github.razorplay01.cam.api.Plugin;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.razorplay01.cam.ClientUtil.*;

/**
 * Cámara cenital del minijuego de cañones: sigue al jugador desde arriba en vez
 * de quedarse clavada en el centro de la arena.
 * <p>
 * Solo se activa para quien está jugando. Antes el servidor mandaba el estado a
 * todos los jugadores del mundo, así que a alguien minando a 5.000 bloques se le
 * ponía la cámara aérea sin comerlo ni beberlo.
 */
@Plugin(value = "minigame_aerial", priority = ModifierPriority.HIGH)
public class MinigameCameraPlugin implements CameraPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinigameCameraPlugin");

    private static final float HEIGHT = 15.0f;
    /**
     * Velocidad del suavizado. Se aplica sobre el tiempo real transcurrido, no por
     * frame, para que la cámara se sienta igual a 30 que a 144 fps.
     */
    private static final float SMOOTH_SPEED = 12.0f;
    private static final float MAX_DELTA_SECONDS = 0.25f;
    /** A partir de este salto se recoloca de golpe: es un teletransporte, no un paseo. */
    private static final float SNAP_DISTANCE_SQ = 100.0f;

    private final CameraModifier modifier;
    private final Vector3f target = new Vector3f();
    private final Vector3f smoothed = new Vector3f();
    private boolean wasActive = false;
    private long lastFrameNanos = 0;

    public MinigameCameraPlugin(CameraModifier modifier) {
        this.modifier = modifier;
        LOGGER.info("Plugin de cámara aérea para minijuego cargado");
    }

    @Override
    public void update(float partialTicks) {
        boolean shouldBeActive = ClientMinigameState.get().isActive() && player() != null;

        if (shouldBeActive) {
            applyAerialCamera();
            if (!wasActive) {
                disableBobView();
                toThirdView();
                wasActive = true;
            }
        } else if (wasActive) {
            resetBobView();
            resetCameraType();
            modifier.disableAll().reset();
            wasActive = false;
            lastFrameNanos = 0;
        }
    }

    private void applyAerialCamera() {
        playerPos(target);

        if (!wasActive || target.distanceSquared(smoothed) > SNAP_DISTANCE_SQ) {
            smoothed.set(target);
        } else {
            smoothed.lerp(target, smoothingAlpha());
        }

        modifier.enable()
                .enablePos()
                .enableRotation()
                .enableGlobalMode()
                .setToVanilla()
                .setPos(smoothed.x, smoothed.y + HEIGHT, smoothed.z)
                .setRotationYXZ(90.0f, 0.0f, 0.0f)
                .enableObstacle();
    }

    /**
     * Interpolación exponencial: cuanto más tarda el frame, más se acerca la cámara
     * al objetivo. Con un factor fijo por frame la cámara iría el doble de lenta a
     * 30 fps que a 60.
     */
    private float smoothingAlpha() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
            return 1.0f;
        }
        float delta = Math.min((now - lastFrameNanos) / 1_000_000_000.0f, MAX_DELTA_SECONDS);
        lastFrameNanos = now;
        return 1.0f - (float) Math.exp(-SMOOTH_SPEED * delta);
    }
}
