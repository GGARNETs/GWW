package com.github.razorplay01.config;

import com.github.razorplay01.GWW;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ajustes generales del escape room, comunes a todas las arenas:
 * los tiempos del Ublabla y la dificultad de la barra de ruido.
 * <p>
 * Vive en su propio archivo (config/GWW/settings.yml) y no en config.yml porque
 * los comandos de arena reescriben ese archivo entero cada vez que crean o borran
 * una arena, y se llevarían por delante cualquier otra sección.
 */
public class GwwSettings {
    private GwwSettings() {
    }

    private static final int TICKS_PER_SECOND = 20;

    // ---- Ublabla: valores por defecto = los que tenía hardcodeados la entidad ----
    private static double alertSeconds = 5.0;
    private static double checkSeconds = 3.0;
    private static double investigationTimeoutSeconds = 15.0;
    private static float noiseThreshold = 0.72f;

    // ---- Barra de ruido ----
    private static float noiseMax = 1.0f;
    private static float noiseDecayPerTick = 0.015f;
    private static float walking = 0.1f;
    private static float sneaking = 0.05f;
    private static float sprinting = 0.2f;
    private static float jumping = 0.25f;
    private static float landingSoft = 0.2f;
    private static float landingNormal = 0.4f;
    private static float landingHard = 0.7f;

    // ---- Puntos de GeoWarePoints ----
    private static int victoryPoints = 200;
    private static int jailPenalty = -50;
    private static int minigameSurvivorPoints = 20;
    private static int minigameDeathPenalty = -10;

    private static final String DEFAULT_CONFIG = """
            # ============================================================
            #  GWW - Ajustes generales del Escape Room
            # ============================================================
            # Estos ajustes son comunes a TODAS las arenas.
            # Recárgalos sin reiniciar con /escaperoom reload
            #
            # Las arenas se configuran aparte, en config.yml.

            ublabla:
              # Segundos desde que oye el ruido hasta que llega a la sala.
              # Es la cuenta atrás de "Ublabla viene en camino...".
              # Súbelo para dar más margen a los jugadores para deshacer el desastre.
              alert_seconds: 5.0

              # Segundos que se queda parado revisando la sala antes de dar el veredicto.
              check_seconds: 3.0

              # Segundos máximos buscando el sitio antes de rendirse y volver a su puesto.
              investigation_timeout_seconds: 15.0

              # Cuánto tiene que llenarse la barra (0.0 a 1.0) para que se despierte.
              # Bájalo y saltará con cualquier ruidito.
              noise_threshold: 0.72

            noise:
              # Capacidad de la barra. La barra que ven los jugadores siempre va de
              # vacía a llena; esto es cuánto ruido hace falta para llenarla.
              # SUBIRLO = más fácil (cuesta más llenarla).
              # BAJARLO = más difícil (se llena enseguida).
              max: 1.0

              # Cuánto ruido se pierde por tick estando en silencio (20 ticks = 1 segundo).
              # Súbelo para que el ruido se disipe rápido.
              decay_per_tick: 0.015

              # Ruido que genera cada acción. Se multiplica luego por el tipo de suelo
              # (la lana hace menos ruido, la grava más) y por la velocidad.
              walking: 0.1
              sneaking: 0.05
              sprinting: 0.2
              jumping: 0.25
              landing_soft: 0.2
              landing_normal: 0.4
              landing_hard: 0.7

            points:
              # Puntos que reparte el mod GeoWarePoints. Si ese mod no esta
              # instalado, estos valores no hacen nada.
              # Positivo = suma, negativo = resta. Pon 0 para desactivarlo.

              # Al pisar la zona de meta: lo cobran TODOS los jugadores de la sala.
              victory: 200

              # Al ser devuelto a la jaula por el Ublabla.
              jail_penalty: -50

              # Minijuego de los cañones: sobrevivir hasta el final / morir.
              minigame_survivor: 20
              minigame_death: -10
            """;

    public static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("GWW").resolve("settings.yml");
    }

    /**
     * Carga (o recarga) los ajustes. Devuelve la lista de errores encontrados;
     * cualquier valor que falte o esté mal se queda con su valor por defecto.
     */
    public static synchronized List<String> load() {
        List<String> errors = new ArrayList<>();
        Path file = getConfigFile();

        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_CONFIG);
                GWW.LOGGER.info("[GWW] Ajustes creados en {}", file);
                return errors;
            }

            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                root = new Yaml().load(in);
            }
            if (root == null) {
                return errors;
            }

            if (root.get("ublabla") instanceof Map<?, ?> ublabla) {
                alertSeconds = positive(ublabla, "alert_seconds", alertSeconds, errors);
                checkSeconds = positive(ublabla, "check_seconds", checkSeconds, errors);
                investigationTimeoutSeconds = positive(ublabla, "investigation_timeout_seconds",
                        investigationTimeoutSeconds, errors);
                noiseThreshold = (float) positive(ublabla, "noise_threshold", noiseThreshold, errors);
            }

            if (root.get("noise") instanceof Map<?, ?> noise) {
                noiseMax = (float) positive(noise, "max", noiseMax, errors);
                noiseDecayPerTick = (float) positive(noise, "decay_per_tick", noiseDecayPerTick, errors);
                walking = (float) positive(noise, "walking", walking, errors);
                sneaking = (float) positive(noise, "sneaking", sneaking, errors);
                sprinting = (float) positive(noise, "sprinting", sprinting, errors);
                jumping = (float) positive(noise, "jumping", jumping, errors);
                landingSoft = (float) positive(noise, "landing_soft", landingSoft, errors);
                landingNormal = (float) positive(noise, "landing_normal", landingNormal, errors);
                landingHard = (float) positive(noise, "landing_hard", landingHard, errors);
            }

            if (root.get("points") instanceof Map<?, ?> points) {
                victoryPoints = integer(points, "victory", victoryPoints, errors);
                jailPenalty = integer(points, "jail_penalty", jailPenalty, errors);
                minigameSurvivorPoints = integer(points, "minigame_survivor", minigameSurvivorPoints, errors);
                minigameDeathPenalty = integer(points, "minigame_death", minigameDeathPenalty, errors);
            }

            GWW.LOGGER.info("[GWW] Ajustes cargados desde {}", file);
        } catch (Exception e) {
            errors.add("Error leyendo settings.yml: " + e.getMessage());
            GWW.LOGGER.error("[GWW] Error leyendo settings.yml", e);
        }
        return errors;
    }

    /**
     * Lee un número mayor que cero. Si falta se queda con el valor actual; si está
     * puesto pero es basura, avisa y también se queda con el actual.
     */
    private static double positive(Map<?, ?> section, String key, double fallback, List<String> errors) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number) || number.doubleValue() <= 0) {
            errors.add("settings.yml: '" + key + "' debe ser un número mayor que 0 (se usa " + fallback + ")");
            return fallback;
        }
        return number.doubleValue();
    }

    /**
     * Lee un entero que puede ser negativo o cero, porque las penalizaciones restan
     * y un 0 es la forma de desactivar ese premio.
     */
    private static int integer(Map<?, ?> section, String key, int fallback, List<String> errors) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            errors.add("settings.yml: '" + key + "' debe ser un número entero (se usa " + fallback + ")");
            return fallback;
        }
        return number.intValue();
    }

    // ==================== UBLABLA ====================

    public static int alertTicks() {
        return (int) Math.round(alertSeconds * TICKS_PER_SECOND);
    }

    public static int checkTicks() {
        return (int) Math.round(checkSeconds * TICKS_PER_SECOND);
    }

    public static int investigationTimeoutTicks() {
        return (int) Math.round(investigationTimeoutSeconds * TICKS_PER_SECOND);
    }

    /** Fracción de barra llena (0..1) a la que el Ublabla se despierta. */
    public static float noiseThreshold() {
        return noiseThreshold;
    }

    // ==================== RUIDO ====================

    public static float noiseMax() {
        return noiseMax;
    }

    public static float noiseDecayPerTick() {
        return noiseDecayPerTick;
    }

    public static float walking() {
        return walking;
    }

    public static float sneaking() {
        return sneaking;
    }

    public static float sprinting() {
        return sprinting;
    }

    public static float jumping() {
        return jumping;
    }

    public static float landingSoft() {
        return landingSoft;
    }

    public static float landingNormal() {
        return landingNormal;
    }

    public static float landingHard() {
        return landingHard;
    }

    // ==================== PUNTOS ====================

    /** Puntos que gana cada jugador de la sala al pisar la meta. */
    public static int victoryPoints() {
        return victoryPoints;
    }

    /** Puntos (normalmente negativos) al ser devuelto a la jaula por el Ublabla. */
    public static int jailPenalty() {
        return jailPenalty;
    }

    public static int minigameSurvivorPoints() {
        return minigameSurvivorPoints;
    }

    /** Puntos (normalmente negativos) al morir en el minijuego de cañones. */
    public static int minigameDeathPenalty() {
        return minigameDeathPenalty;
    }
}
