package com.github.razorplay01.config;

import com.github.razorplay01.GWW;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Soluciones de los puzzles, comunes a TODAS las salas: como el escape room se
 * juega como competencia, cada sala es un clon con la misma solución y rotarla
 * es editar este archivo y recargar, sin re-guardar instances ni tocar comandos.
 * <p>
 * Vive en config/GWW/puzzles.yml, junto a settings.yml, y sigue sus mismas
 * reglas: valor ausente o mal escrito → se conserva el anterior y se avisa.
 * Cada sección es opcional; sin ella, las entidades usan lo que traen guardado
 * en NBT (así las salas viejas siguen funcionando igual).
 */
public class GwwPuzzles {
    private GwwPuzzles() {
    }

    /** Colores de fusible en el orden de sus constantes FUSE_* (índice 0 = tipo 1). */
    private static final String[] FUSE_NAMES = {"rojo", "verde", "azul", "amarillo", "violeta"};

    private static Map<String, String> tecladoCodes = new LinkedHashMap<>();
    private static int[] fusiblesCircuito1 = null;
    private static int[] fusiblesCircuito2 = null;
    private static int[] cablesRotaciones = null;
    private static int[] cablesTipos = null;
    private static int[] valvulasEstados = null;
    private static Map<String, List<String>> cajasContenido = new LinkedHashMap<>();

    private static final String DEFAULT_CONFIG = """
            # ============================================================
            #  GWW - Soluciones de los puzzles del Escape Room
            # ============================================================
            # TODAS las salas comparten estas soluciones (competencia justa).
            # Recarga sin reiniciar con /escaperoom reload
            #
            # - El codigo de un teclado aplica cuando el equipo empieza un
            #   intento nuevo: una sala a mitad de intento lo termina con el
            #   codigo que tenia.
            # - Cables, valvulas y cajas se aplican al montar o resetear la
            #   sala (/escaperoom reset).
            # - Si un valor esta mal escrito, el mod avisa en consola al
            #   recargar y conserva el valor anterior.

            teclados:
              # Cada teclado de la sala se identifica por su nombre.
              # Ponle nombre con: /escaperoom config teclado <entidad> setname <nombre>
              # Codigos de solo digitos (recomendado: 4 digitos).
              teclado_salida: "4729"
              teclado_atico: "8153"

            fusibles:
              # 4 fusibles por circuito.
              # Colores: rojo, verde, azul, amarillo, violeta
              circuito_1: [rojo, amarillo, azul, verde]
              circuito_2: [violeta, verde, rojo, azul]

            #cables:
            #  # Rotacion correcta de cada cable de la sala (0 a 3), en orden.
            #  # El orden es por posicion en la sala: primero por altura (de
            #  # abajo a arriba) y a igual altura de oeste a este y de norte
            #  # a sur. Parado en la sala, /escaperoom config cables list
            #  # muestra el orden real con sus rotaciones actuales.
            #  rotaciones: [0, 1, 2, 3]
            #  # Opcional: que ranura pide cable recto y cual curvo.
            #  #tipos: [recto, curvo, recto, curvo]

            #valvulas:
            #  # Estado correcto de cada valvula (0 = cerrada .. 4 = abierta),
            #  # en orden. Mismo criterio de orden que los cables.
            #  estados_correctos: [3, 3, 3]

            #cajas:
            #  # Contenido de cada caja, identificada por su nombre.
            #  # Ponle nombre con: /escaperoom config caja <entidad> setname <nombre>
            #  # Items del mod: alicate_cortacables, llave_atico, cable_lineal,
            #  # cable_curvo, ganzua, fusible_rojo, fusible_verde, fusible_azul,
            #  # fusible_amarillo, fusible_violeta, colgante_cuadros, hoja_pista
            #  # Repite un item para dar mas de uno.
            #  caja_taller: [alicate_cortacables, cable_lineal]
            #  caja_deposito: [llave_atico]
            """;

    public static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("GWW").resolve("puzzles.yml");
    }

    /**
     * Carga (o recarga) las soluciones. Devuelve la lista de errores encontrados;
     * la sección que venga mal conserva su valor anterior.
     */
    public static synchronized List<String> load() {
        List<String> errors = new ArrayList<>();
        Path file = getConfigFile();

        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_CONFIG);
                GWW.LOGGER.info("[GWW] Soluciones de puzzles creadas en {}", file);
                // El archivo recién creado trae los mismos valores que los defaults
                // de abajo, así que se sigue leyendo normal.
            }

            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                root = new Yaml().load(in);
            }
            if (root == null) {
                return errors;
            }

            if (root.get("teclados") instanceof Map<?, ?> teclados) {
                Map<String, String> parsed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : teclados.entrySet()) {
                    String name = String.valueOf(entry.getKey());
                    String code = String.valueOf(entry.getValue());
                    if (!code.matches("\\d{1,8}")) {
                        errors.add("puzzles.yml: el codigo del teclado '" + name
                                + "' debe ser solo digitos (1 a 8). Se conserva el anterior.");
                        String previous = tecladoCodes.get(name);
                        if (previous != null) {
                            parsed.put(name, previous);
                        }
                    } else {
                        parsed.put(name, code);
                    }
                }
                tecladoCodes = parsed;
            }

            if (root.get("fusibles") instanceof Map<?, ?> fusibles) {
                fusiblesCircuito1 = fuseCircuit(fusibles, "circuito_1", fusiblesCircuito1, errors);
                fusiblesCircuito2 = fuseCircuit(fusibles, "circuito_2", fusiblesCircuito2, errors);
            }

            if (root.get("cables") instanceof Map<?, ?> cables) {
                cablesRotaciones = intList(cables, "rotaciones", 0, 3, cablesRotaciones, errors);
                cablesTipos = cableTypeList(cables, cablesTipos, errors);
            }

            if (root.get("valvulas") instanceof Map<?, ?> valvulas) {
                valvulasEstados = intList(valvulas, "estados_correctos", 0, 4, valvulasEstados, errors);
            }

            if (root.get("cajas") instanceof Map<?, ?> cajas) {
                Map<String, List<String>> parsed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : cajas.entrySet()) {
                    String name = String.valueOf(entry.getKey());
                    if (!(entry.getValue() instanceof List<?> rawItems)) {
                        errors.add("puzzles.yml: la caja '" + name + "' debe ser una lista de items.");
                        continue;
                    }
                    List<String> items = new ArrayList<>();
                    for (Object raw : rawItems) {
                        String id = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
                        if (resolveItem(id) == null) {
                            errors.add("puzzles.yml: la caja '" + name + "' tiene un item desconocido: '"
                                    + id + "' (se ignora).");
                        } else {
                            items.add(id);
                        }
                    }
                    parsed.put(name, items);
                }
                cajasContenido = parsed;
            }

            GWW.LOGGER.info("[GWW] Soluciones de puzzles cargadas desde {} ({} teclados, {} cajas)",
                    file, tecladoCodes.size(), cajasContenido.size());
        } catch (Exception e) {
            errors.add("Error leyendo puzzles.yml: " + e.getMessage());
            GWW.LOGGER.error("[GWW] Error leyendo puzzles.yml", e);
        }
        return errors;
    }

    /** Lee un circuito de fusibles: lista de exactamente 4 colores válidos. */
    private static int[] fuseCircuit(Map<?, ?> section, String key, int[] fallback, List<String> errors) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof List<?> list) || list.size() != 4) {
            errors.add("puzzles.yml: '" + key + "' debe ser una lista de exactamente 4 colores.");
            return fallback;
        }
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            int fuse = parseFuseName(String.valueOf(list.get(i)));
            if (fuse == 0) {
                errors.add("puzzles.yml: '" + key + "' tiene un color desconocido: '" + list.get(i)
                        + "'. Validos: " + String.join(", ", FUSE_NAMES) + ".");
                return fallback;
            }
            result[i] = fuse;
        }
        return result;
    }

    /** Lee una lista de enteros dentro de un rango; cualquier fallo conserva la anterior. */
    private static int[] intList(Map<?, ?> section, String key, int min, int max,
                                 int[] fallback, List<String> errors) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            errors.add("puzzles.yml: '" + key + "' debe ser una lista de numeros.");
            return fallback;
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Number number)
                    || number.intValue() < min || number.intValue() > max) {
                errors.add("puzzles.yml: '" + key + "' debe llevar numeros entre " + min + " y " + max
                        + " (posicion " + (i + 1) + " = '" + list.get(i) + "').");
                return fallback;
            }
            result[i] = number.intValue();
        }
        return result;
    }

    /** Lee la lista opcional de tipos de cable: recto/lineal (0) o curvo (1). */
    private static int[] cableTypeList(Map<?, ?> section, int[] fallback, List<String> errors) {
        Object value = section.get("tipos");
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            errors.add("puzzles.yml: 'tipos' debe ser una lista (recto/curvo).");
            return fallback;
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String name = String.valueOf(list.get(i)).trim().toLowerCase(Locale.ROOT);
            switch (name) {
                case "recto", "lineal" -> result[i] = 0;
                case "curvo" -> result[i] = 1;
                default -> {
                    errors.add("puzzles.yml: tipo de cable desconocido: '" + name
                            + "' (posicion " + (i + 1) + "). Usa recto o curvo.");
                    return fallback;
                }
            }
        }
        return result;
    }

    /** Nombre de color → tipo de fusible (1..5), o 0 si no existe. */
    public static int parseFuseName(String name) {
        String clean = name.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < FUSE_NAMES.length; i++) {
            if (FUSE_NAMES[i].equals(clean)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Item por id corto ("ganzua") o completo ("minecraft:paper"); null si no existe. */
    private static Item resolveItem(String id) {
        ResourceLocation location = id.contains(":")
                ? ResourceLocation.tryParse(id)
                : ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, id);
        if (location == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        return item == Items.AIR ? null : item;
    }

    // ==================== TECLADOS ====================

    /** Código configurado para un teclado por nombre, o null si no está en el yml. */
    public static synchronized String tecladoCode(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return tecladoCodes.get(name);
    }

    public static synchronized Map<String, String> tecladoCodes() {
        return new LinkedHashMap<>(tecladoCodes);
    }

    // ==================== FUSIBLES ====================

    /** Solución del circuito (1 o 2) del panel de fusibles, o null si no está en el yml. */
    public static synchronized int[] fusiblesCircuito(int puzzleId) {
        int[] circuit = (puzzleId == 1) ? fusiblesCircuito1 : fusiblesCircuito2;
        return circuit == null ? null : circuit.clone();
    }

    public static String fuseName(int fuseType) {
        if (fuseType < 1 || fuseType > FUSE_NAMES.length) {
            return "?";
        }
        return FUSE_NAMES[fuseType - 1];
    }

    // ==================== CABLES Y VALVULAS ====================

    /** Rotaciones correctas de los cables de la sala, en orden, o null. */
    public static synchronized int[] cablesRotaciones() {
        return cablesRotaciones == null ? null : cablesRotaciones.clone();
    }

    /** Tipo de cada ranura de cable (0 = recto, 1 = curvo), en orden, o null. */
    public static synchronized int[] cablesTipos() {
        return cablesTipos == null ? null : cablesTipos.clone();
    }

    /** Estados correctos de las válvulas de la sala, en orden, o null. */
    public static synchronized int[] valvulasEstados() {
        return valvulasEstados == null ? null : valvulasEstados.clone();
    }

    // ==================== CAJAS ====================

    /** true si el yml define contenido para una caja con ese nombre. */
    public static synchronized boolean hasCajaContenido(String name) {
        return name != null && cajasContenido.containsKey(name);
    }

    /** Contenido configurado para la caja, ya convertido a stacks (uno por entrada). */
    public static synchronized List<ItemStack> cajaContenido(String name) {
        List<ItemStack> stacks = new ArrayList<>();
        List<String> ids = cajasContenido.get(name);
        if (ids == null) {
            return stacks;
        }
        for (String id : ids) {
            Item item = resolveItem(id);
            if (item != null) {
                stacks.add(new ItemStack(item));
            }
        }
        return stacks;
    }

    public static synchronized List<String> cajaNames() {
        return new ArrayList<>(cajasContenido.keySet());
    }
}
