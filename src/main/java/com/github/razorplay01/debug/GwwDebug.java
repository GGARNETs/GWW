package com.github.razorplay01.debug;

import com.github.razorplay01.GWW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interruptor de logs y contadores del mod, para poder ver en consola qué está
 * haciendo el server cuando va lento o spamea.
 * <p>
 * Todo está APAGADO por defecto: en producción no cuesta nada, porque cada punto
 * de log empieza comprobando un booleano y los contadores no se acumulan si nadie
 * los pidió. Se enciende en caliente con {@code /escaperoom debug ...}.
 * <p>
 * Regla al añadir logs nuevos: nunca construir el mensaje antes de comprobar la
 * categoría. Se usa {@code log(...)} con parámetros ({@code {}}), no concatenación.
 */
public final class GwwDebug {
    private GwwDebug() {
    }

    /**
     * Qué se quiere ver. Cada zona del mod tiene la suya para poder encender solo
     * la que se está investigando y no ahogar la consola con el resto.
     */
    public enum Category {
        /** Ruido acumulado, decay, grupos por arena. */
        NOISE,
        /** Envíos de paquetes del mod a los clientes. Es el que interesa si hay lag de red. */
        PACKETS,
        /** Cambios de estado del Ublabla, investigaciones, capturas. */
        UBLABLA,
        /** Entradas y salidas de arena, arranque y parada de partidas. */
        ARENA,
        /** Minijuego de cañones: spawns, disparos, impactos. */
        MINIGAME,
        /** Resolución de puzzles y re-enlazado tras un reset. */
        PUZZLE;

        public static Category parse(String name) {
            for (Category category : values()) {
                if (category.name().equalsIgnoreCase(name)) {
                    return category;
                }
            }
            return null;
        }
    }

    private static final EnumSet<Category> ENABLED = EnumSet.noneOf(Category.class);

    // ==================== LOGS ====================

    public static boolean isOn(Category category) {
        return ENABLED.contains(category);
    }

    public static boolean anyOn() {
        return !ENABLED.isEmpty();
    }

    public static void set(Category category, boolean on) {
        if (on) {
            ENABLED.add(category);
        } else {
            ENABLED.remove(category);
        }
    }

    public static void setAll(boolean on) {
        if (on) {
            ENABLED.addAll(EnumSet.allOf(Category.class));
        } else {
            ENABLED.clear();
        }
    }

    public static void log(Category category, String message) {
        if (isOn(category)) {
            GWW.LOGGER.info("[GWW/{}] {}", category, message);
        }
    }

    public static void log(Category category, String format, Object arg) {
        if (isOn(category)) {
            GWW.LOGGER.info("[GWW/" + category + "] " + format, arg);
        }
    }

    public static void log(Category category, String format, Object arg1, Object arg2) {
        if (isOn(category)) {
            GWW.LOGGER.info("[GWW/" + category + "] " + format, arg1, arg2);
        }
    }

    public static void log(Category category, String format, Object... args) {
        if (isOn(category)) {
            GWW.LOGGER.info("[GWW/" + category + "] " + format, args);
        }
    }

    // ==================== CONTADORES ====================
    // Para responder "¿cuántos paquetes por segundo está mandando el mod?" sin
    // tener que leer un log de miles de líneas.

    /** Contadores del segundo en curso. */
    private static final Map<String, int[]> COUNTERS = new LinkedHashMap<>();
    /** Foto del último segundo completo: es lo que muestra el comando. */
    private static final Map<String, Integer> LAST_SECOND = new LinkedHashMap<>();

    private static boolean statsEnabled = false;
    private static boolean statsToConsole = false;
    private static int statsTickCounter = 0;

    public static boolean isStatsEnabled() {
        return statsEnabled;
    }

    public static boolean isStatsToConsole() {
        return statsToConsole;
    }

    public static void setStats(boolean enabled, boolean toConsole) {
        statsEnabled = enabled;
        statsToConsole = enabled && toConsole;
        if (!enabled) {
            COUNTERS.clear();
            LAST_SECOND.clear();
            statsTickCounter = 0;
        }
    }

    /**
     * Suma uno al contador. Sin stats encendidas sale en la primera línea, así que
     * se puede llamar desde donde sea, incluso en caminos calientes.
     */
    public static void count(String key) {
        count(key, 1);
    }

    public static void count(String key, int amount) {
        if (!statsEnabled) {
            return;
        }
        COUNTERS.computeIfAbsent(key, k -> new int[1])[0] += amount;
    }

    /**
     * Cierra el segundo: pasa los contadores a la foto y los pone a cero.
     * Se llama desde el tick del servidor.
     */
    public static void tick() {
        if (!statsEnabled || ++statsTickCounter < 20) {
            return;
        }
        statsTickCounter = 0;

        LAST_SECOND.clear();
        for (Map.Entry<String, int[]> entry : COUNTERS.entrySet()) {
            LAST_SECOND.put(entry.getKey(), entry.getValue()[0]);
            entry.getValue()[0] = 0;
        }

        if (statsToConsole && !LAST_SECOND.isEmpty()) {
            GWW.LOGGER.info("[GWW/STATS] {}", formatStats());
        }
    }

    /** Última foto en una línea: {@code clave=valor/s}. */
    public static String formatStats() {
        if (LAST_SECOND.isEmpty()) {
            return "sin actividad";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : LAST_SECOND.entrySet()) {
            if (entry.getValue() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append("/s");
        }
        return sb.length() == 0 ? "sin actividad" : sb.toString();
    }

    /** Última foto en líneas sueltas, para mandársela al jugador por chat. */
    public static List<String> statLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : LAST_SECOND.entrySet()) {
            if (entry.getValue() > 0) {
                lines.add(entry.getKey() + ": " + entry.getValue() + "/s");
            }
        }
        return lines;
    }

    // ==================== CLAVES DE CONTADOR ====================
    // En un sitio para que no se dupliquen con nombres parecidos.

    /** Paquetes de la barra de ruido enviados a clientes. */
    public static final String PACKETS_NOISE = "paquetes_ruido";
    /** Paquetes de estado del minijuego enviados a clientes. */
    public static final String PACKETS_MINIGAME = "paquetes_minijuego";
    /** Títulos, action bars y mensajes de chat enviados por el mod. */
    public static final String PACKETS_MESSAGES = "mensajes";
    /** Barridos de entidades del mundo (los caros con morehitboxes). */
    public static final String ENTITY_SCANS = "escaneos_entidades";
    /** Ublablas que pasaron por su tick de IA. */
    public static final String UBLABLA_TICKS = "ticks_ublabla";
    /** Jugadores procesados por el sistema de ruido. */
    public static final String NOISE_PLAYER_TICKS = "ticks_ruido";
    /** Envíos de partículas del mod (cada uno es un paquete a todos los que estén cerca). */
    public static final String PARTICLES = "particulas";
    /** Correcciones de velocidad forzadas a un jugador (vapor, muro del minijuego). */
    public static final String VELOCITY_PUSHES = "empujones";
}
