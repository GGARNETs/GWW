package com.github.razorplay01.extra;

import com.github.razorplay01.GWW;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga y guarda las arenas del minijuego de cañones en config/GWW/cannon.yml.
 * <p>
 * Va en su propio archivo y no en config.yml porque los comandos de arena del
 * escape room reescriben ese archivo entero y se llevarían por delante esta sección.
 */
public class CannonArenaManager {
    private CannonArenaManager() {
        /* This utility class should not be instantiated */
    }

    private static final Map<String, CannonArena> ARENAS = new LinkedHashMap<>();

    private static final String FILE_HEADER = """
            # ============================================================
            #  GWW - Arenas del minijuego de cañones (esquivar disparos)
            # ============================================================
            # Cada arena guarda solo la forma de la zona:
            #   center : centro del círculo [x, y, z]. La 'y' es la altura del suelo,
            #            es donde se dibuja el borde y a la altura que vuelan las balas.
            #   radius : radio del círculo en bloques.
            #
            # El tiempo, la dificultad y la velocidad de las balas NO se guardan aquí:
            # se eligen al arrancar la partida, para poder repetir la misma arena con
            # distintos ajustes.
            #
            # Comandos:
            #   /cc arena create <id> <centro> <radio>  -> crea la arena y la guarda aquí
            #   /cc arena remove <id>                   -> elimina la arena
            #   /cc arena list                          -> lista las arenas cargadas
            #   /cc arena reload                        -> recarga este archivo sin reiniciar
            #   /cc start <id|all> <segundos> <dificultad> <velocidad_bala> [danio]
            #     danio: vida que quita cada balazo (2 = 1 corazón). Opcional,
            #     por defecto 4. Con 0 las balas solo empujan.
            #   /cc stop <id|all>
            #     además de parar la partida, limpia cañones y balas huérfanos
            #     de la zona (los que deja un crash o un cierre en seco)
            #
            # OJO: los comandos de arena reescriben este archivo; los comentarios
            # que añadas fuera de esta cabecera se perderán.
            #
            # Formato de cada arena:
            #  arena_1:
            #    center: [100.5, 64.0, 200.5]
            #    radius: 20.0
            """;

    private static final String DEFAULT_CONFIG = FILE_HEADER + "arenas:\n";

    public static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("GWW").resolve("cannon.yml");
    }

    /**
     * Carga (o recarga) el archivo. Devuelve la lista de errores encontrados
     * para poder mostrarlos al ejecutor de /cc arena reload.
     */
    public static synchronized List<String> load() {
        List<String> errors = new ArrayList<>();
        ARENAS.clear();
        Path file = getConfigFile();

        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_CONFIG);
                GWW.LOGGER.info("[GWW] Config de arenas de cañones creado en {}", file);
                return errors;
            }

            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                root = new Yaml().load(in);
            }

            Object arenasObj = root != null ? root.get("arenas") : null;
            if (arenasObj == null) {
                GWW.LOGGER.info("[GWW] cannon.yml sin arenas definidas");
                return errors;
            }
            if (!(arenasObj instanceof Map<?, ?> arenas)) {
                errors.add("La sección 'arenas' de cannon.yml tiene un formato inválido.");
                return errors;
            }

            for (Map.Entry<?, ?> entry : arenas.entrySet()) {
                String id = String.valueOf(entry.getKey());
                try {
                    if (!(entry.getValue() instanceof Map<?, ?> def)) {
                        throw new IllegalArgumentException("formato inválido (debe ser una sección)");
                    }
                    ARENAS.put(id, parseArena(id, def));
                } catch (Exception e) {
                    errors.add("Arena '" + id + "': " + e.getMessage());
                }
            }
            GWW.LOGGER.info("[GWW] {} arenas de cañones cargadas desde cannon.yml", ARENAS.size());
        } catch (Exception e) {
            errors.add("Error leyendo cannon.yml: " + e.getMessage());
            GWW.LOGGER.error("[GWW] Error leyendo cannon.yml", e);
        }
        return errors;
    }

    private static CannonArena parseArena(String id, Map<?, ?> def) {
        Vec3 center = parsePos(def.get("center"));

        Object radiusObj = def.get("radius");
        if (!(radiusObj instanceof Number number) || number.doubleValue() < MIN_RADIUS
                || number.doubleValue() > MAX_RADIUS) {
            throw new IllegalArgumentException(
                    "'radius' debe ser un número entre " + MIN_RADIUS + " y " + MAX_RADIUS);
        }

        return new CannonArena(id, center, number.doubleValue());
    }

    private static Vec3 parsePos(Object value) {
        if (value instanceof List<?> list && list.size() == 3
                && list.get(0) instanceof Number x
                && list.get(1) instanceof Number y
                && list.get(2) instanceof Number z) {
            return new Vec3(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        throw new IllegalArgumentException("'center' debe ser [x, y, z]");
    }

    // ==================== ESCRITURA ====================

    public static final double MIN_RADIUS = 1.0;
    public static final double MAX_RADIUS = 100.0;

    /**
     * Añade (o reemplaza) una arena y reescribe cannon.yml.
     */
    public static synchronized void addOrUpdate(CannonArena arena) throws IOException {
        ARENAS.put(arena.getId(), arena);
        save();
    }

    /**
     * Elimina una arena y reescribe cannon.yml. Devuelve false si no existía.
     */
    public static synchronized boolean remove(String id) throws IOException {
        if (ARENAS.remove(id) == null) {
            return false;
        }
        save();
        return true;
    }

    private static void save() throws IOException {
        StringBuilder sb = new StringBuilder(FILE_HEADER);
        sb.append("arenas:\n");
        for (CannonArena arena : ARENAS.values()) {
            Vec3 center = arena.getCenter();
            sb.append("  ").append(arena.getId()).append(":\n");
            sb.append("    center: [")
                    .append(fmt(center.x)).append(", ")
                    .append(fmt(center.y)).append(", ")
                    .append(fmt(center.z)).append("]\n");
            sb.append("    radius: ").append(fmt(arena.getRadius())).append("\n");
        }

        Path file = getConfigFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString());
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    // ==================== CONSULTAS ====================

    public static CannonArena get(String id) {
        return ARENAS.get(id);
    }

    public static Collection<CannonArena> getAll() {
        return ARENAS.values();
    }

    public static Collection<String> getIds() {
        return ARENAS.keySet();
    }
}
