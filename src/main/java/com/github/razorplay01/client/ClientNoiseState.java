package com.github.razorplay01.client;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientNoiseState {
    private static final ClientNoiseState INSTANCE = new ClientNoiseState();

    private float currentNoiseLevel = 0.0f;
    private float targetNoiseLevel = 0.0f;
    private float decayRate = 0.02f;
    private boolean enabled = false;
    private long lastUpdateTime = 0;

    // Configuración visual
    private float visualIntensity = 1.0f;
    private boolean showDebugInfo = false;

    public static ClientNoiseState get() {
        return INSTANCE;
    }

    /**
     * Actualiza el estado desde el servidor
     */
    public void update(float noiseLevel, float decayRate, boolean enabled) {
        this.targetNoiseLevel = noiseLevel;
        this.decayRate = decayRate;
        this.enabled = enabled;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Interpolación suave del nivel de ruido.
     * <p>
     * La bajada por silencio se calcula aquí, en el cliente: el ritmo de decay viene
     * en el paquete y es constante, así que no hace falta que el servidor mande la
     * barra cada pocos ticks solo para verla bajar. El servidor únicamente habla
     * cuando pasa algo que el cliente no puede adivinar (una subida de ruido) y de
     * vez en cuando para corregir la deriva.
     */
    public void tick() {
        if (!enabled) {
            currentNoiseLevel = 0;
            return;
        }

        if (targetNoiseLevel > 0 && decayRate > 0) {
            targetNoiseLevel = Math.max(0.0f, targetNoiseLevel - decayRate);
        }

        // Interpolación suave hacia el target
        float diff = targetNoiseLevel - currentNoiseLevel;
        if (Math.abs(diff) > 0.001f) {
            currentNoiseLevel += diff * 0.2f; // Suavizado
        } else {
            currentNoiseLevel = targetNoiseLevel;
        }

        // Clamp entre 0 y 1
        currentNoiseLevel = Math.clamp(currentNoiseLevel, 0.0f, 1.0f);
    }

    public void reset() {
        currentNoiseLevel = 0.0f;
        targetNoiseLevel = 0.0f;
        enabled = false;
    }
}