package com.github.razorplay01.integration;

import com.github.razorplay01.GWW;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Puente hacia el mod GeoWarePoints para sumar y restar puntos.
 * <p>
 * Se usa por reflexión a propósito: GeoWarePoints está compilado con mappings Yarn y
 * GWW con los de Mojang, así que no se puede referenciar su API en tiempo de compilación.
 * En ejecución ambas asignan {@code ServerPlayer} a la misma clase, de modo que el
 * método se resuelve sin problema. Además así GeoWarePoints es una dependencia opcional:
 * si no está instalado, cada llamada no hace nada en vez de romper el mod.
 */
public final class GeoWarePointsIntegration {
    private GeoWarePointsIntegration() {
    }

    private static final boolean AVAILABLE =
            FabricLoader.getInstance().isModLoaded("geowarepoints");

    private static Method showPointsUpdate;
    private static boolean resolved;

    /** Suma (positivo) o resta (negativo) puntos a un jugador, con su HUD y sonido. */
    public static void award(ServerPlayer player, int points) {
        if (!AVAILABLE || points == 0) {
            return;
        }
        Method method = method();
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, player, points);
        } catch (Throwable t) {
            GWW.LOGGER.error("[GWW] Error otorgando puntos con GeoWarePoints", t);
        }
    }

    private static synchronized Method method() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> api = Class.forName("ethernal.shimonsolo.geowarepoints.api.GeoWarePointsAPI");
                showPointsUpdate = api.getMethod("showPointsUpdate", ServerPlayer.class, int.class);
            } catch (Throwable t) {
                GWW.LOGGER.warn("[GWW] GeoWarePoints presente pero no se pudo enlazar su API: {}", t.toString());
            }
        }
        return showPointsUpdate;
    }
}
