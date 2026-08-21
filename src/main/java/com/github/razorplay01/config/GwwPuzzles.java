package com.github.razorplay01.config;

import com.github.razorplay01.GWW;
import com.github.razorplay01.item.ModComponents;
import com.github.razorplay01.item.PistaItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import java.util.Set;

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

    /** Claves que van dentro de un item de caja; verlas como nombre de caja delata mala indentación. */
    private static final Set<String> ITEM_FIELDS = Set.of("item", "nombre", "comando", "cantidad");

    /** Colores de válvula/manivela, en el orden de ValvulaType (índice = id del tipo). */
    private static final String[] VALVULA_COLORS = {"roja", "morada", "naranja"};

    /** Valores de 'entidad:' que dejan la caja sin entidad que soltar. */
    private static final Set<String> NO_ENTITY = Set.of("ninguna", "ninguno", "no", "none", "nada");

    private static Map<String, String> tecladoCodes = new LinkedHashMap<>();
    private static int[] fusiblesCircuito1 = null;
    private static int[] fusiblesCircuito2 = null;
    private static int[] cablesRotaciones = null;
    private static int[] cablesTipos = null;
    private static int[] valvulasEstados = null;
    private static Map<String, Integer> valvulasPorNombre = new LinkedHashMap<>();
    private static Map<String, CajaConfig> cajasContenido = new LinkedHashMap<>();

    /**
     * Lo que suelta una caja al abrirse. La entidad es null cuando el yml no la
     * menciona: en ese caso la caja conserva la que trae guardada en su NBT, así
     * que agregar items a una caja vieja no le borra su manivela.
     */
    public record CajaConfig(List<CajaItem> items, CompoundTag entidad) {
    }

    /**
     * Un item del contenido de una caja. Además del id lleva lo que necesita una
     * hoja de pista para servir de pista: el nombre con el que se ve y el comando
     * que dispara al usarla.
     */
    public record CajaItem(String id, int cantidad, String nombre, String comando) {
    }

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
            #  # Cada valvula por su NOMBRE, con el estado en el que corta la
            #  # presion (0 = cerrada .. 4 = abierta).
            #  # Ponle nombre con: /escaperoom config valvula <entidad> setname <nombre>
            #  # Se graba en la valvula al hacer /escaperoom reload.
            #  valvula_patio: 3
            #  valvula_taller: 1
            #  valvula_sotano: 4
            #
            #  # Alternativa vieja, por orden en la sala en vez de por nombre
            #  # (mismo criterio de orden que los cables). El nombre manda sobre esto.
            #  #estados_correctos: [3, 3, 3]

            #cajas:
            #  # Contenido de cada caja, identificada por su nombre.
            #  # Ponle nombre con: /escaperoom config caja <entidad> setname <nombre>
            #  # Items del mod: alicate_cortacables, llave_atico, cable_lineal,
            #  # cable_curvo, ganzua, fusible_rojo, fusible_verde, fusible_azul,
            #  # fusible_amarillo, fusible_violeta, colgante_cuadros, hoja_pista
            #  # Repite un item para dar mas de uno.
            #  caja_taller: [alicate_cortacables, cable_lineal]
            #  caja_deposito: [llave_atico]
            #
            #  # Para darle detalles a un item, escribelo como bloque. OJO con la
            #  # indentacion: nombre/comando/cantidad van alineados con 'item',
            #  # DOS espacios a la derecha del '-'. Si los pones a la altura del
            #  # '-', el yml los lee como cajas sueltas y avisa al recargar.
            #  caja_sotano:
            #    - ganzua
            #    - item: hoja_pista
            #      nombre: "&ePista del sotano"
            #      # Comando que ejecuta la hoja al click derecho (solo hoja_pista).
            #      comando: "/mediaplayer show @s pista_sotano"
            #      cantidad: 1
            #
            #  # Para que la caja suelte tambien una entidad, usa 'entidad' + 'items'.
            #  # Colores: manivela_roja, manivela_morada, manivela_naranja
            #  #          (igual para valvula_*). Sin color, sale naranja.
            #  # La manivela SOLO encaja en una valvula del mismo color.
            #  # Si no pones 'entidad', la caja conserva la que ya tenga guardada;
            #  # 'entidad: ninguna' se la quita.
            #  caja_patio:
            #    entidad: manivela_morada
            #    items:
            #      - item: hoja_pista
            #        nombre: "&ePista del atico"
            #        comando: "/playinteractiveimage @s pista_atico"
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

                // Además de la lista posicional de siempre, cada clave suelta es el
                // nombre de una válvula: es lo que evita depender del orden en la sala.
                Map<String, Integer> porNombre = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : valvulas.entrySet()) {
                    String name = String.valueOf(entry.getKey());
                    if (name.equals("estados_correctos")) {
                        continue;
                    }
                    if (!(entry.getValue() instanceof Number number)
                            || number.intValue() < 0 || number.intValue() > 4) {
                        errors.add("puzzles.yml: la valvula '" + name + "' debe llevar un numero de 0"
                                + " (cerrada) a 4 (abierta), no '" + entry.getValue() + "'.");
                        continue;
                    }
                    porNombre.put(name, number.intValue());
                }
                valvulasPorNombre = porNombre;
            }

            if (root.get("cajas") instanceof Map<?, ?> cajas) {
                Map<String, CajaConfig> parsed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : cajas.entrySet()) {
                    String name = String.valueOf(entry.getKey());
                    CajaConfig config = cajaConfig(name, entry.getValue(), errors);
                    if (config != null) {
                        parsed.put(name, config);
                    }
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

    /**
     * Lee una caja del yml. Admite las dos formas: la lista de items pelada de
     * siempre, o el bloque con 'entidad:' e 'items:' cuando la caja además suelta
     * una manivela o una válvula. Devuelve null si la entrada no se entiende.
     */
    private static CajaConfig cajaConfig(String name, Object value, List<String> errors) {
        if (value instanceof List<?> rawItems) {
            return new CajaConfig(cajaItems(name, rawItems, errors), null);
        }

        if (value instanceof Map<?, ?> map) {
            List<CajaItem> items = List.of();
            Object rawItemList = map.get("items");
            if (rawItemList instanceof List<?> list) {
                items = cajaItems(name, list, errors);
            } else if (rawItemList != null) {
                errors.add("puzzles.yml: la caja '" + name + "': 'items' debe ser una lista.");
            }

            CompoundTag entidad = null;
            Object rawEntidad = map.get("entidad");
            if (rawEntidad != null) {
                entidad = parseEntidad(name, String.valueOf(rawEntidad), errors);
            }
            return new CajaConfig(items, entidad);
        }

        // El fallo típico: los detalles de un item quedaron a la altura del guion,
        // así que el yml los lee como cajas sueltas en vez de como parte del item
        // de arriba.
        if (ITEM_FIELDS.contains(name.toLowerCase(Locale.ROOT))) {
            errors.add("puzzles.yml: '" + name + "' esta mal indentado. Los detalles de"
                    + " un item van alineados con 'item:', dos espacios a la derecha del '-'.");
        } else {
            errors.add("puzzles.yml: la caja '" + name + "' debe ser una lista de items.");
        }
        return null;
    }

    /**
     * Traduce lo que dice 'entidad:' al NBT con el que la caja invoca la entidad.
     * Acepta el nombre con color ("manivela_morada"), el id a secas ("manivela",
     * que sale del color por defecto) o "ninguna" para dejar la caja sin entidad.
     * Devuelve null si el valor no sirve, para no pisar lo que la caja ya tenga.
     */
    private static CompoundTag parseEntidad(String caja, String raw, List<String> errors) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (NO_ENTITY.contains(value)) {
            return new CompoundTag();
        }

        int color = -1;
        String id = value;
        for (int i = 0; i < VALVULA_COLORS.length; i++) {
            String suffix = "_" + VALVULA_COLORS[i];
            if (value.endsWith(suffix)) {
                color = i;
                id = value.substring(0, value.length() - suffix.length());
                break;
            }
        }

        ResourceLocation location = id.contains(":")
                ? ResourceLocation.tryParse(id)
                : ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, id);
        if (location == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(location)) {
            errors.add("puzzles.yml: la caja '" + caja + "': entidad desconocida '" + raw
                    + "' (se conserva la que tenga la caja).");
            return null;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString("id", location.toString());
        if (color >= 0) {
            tag.putInt("Type", color);
        }
        return tag;
    }

    /**
     * Lee la lista de items de una caja. Cada entrada puede ser el id suelto
     * ("ganzua") o un bloque con sus detalles, y un item con problemas se descarta
     * solo: el resto de la caja se carga igual.
     */
    private static List<CajaItem> cajaItems(String caja, List<?> rawItems, List<String> errors) {
        List<CajaItem> items = new ArrayList<>();
        for (Object raw : rawItems) {
            if (!(raw instanceof Map<?, ?> map)) {
                String id = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
                if (resolveItem(id) == null) {
                    errors.add(unknownItem(caja, id));
                } else {
                    items.add(new CajaItem(id, 1, null, null));
                }
                continue;
            }

            Object rawId = map.get("item");
            if (rawId == null) {
                errors.add("puzzles.yml: la caja '" + caja + "' tiene una entrada sin 'item:' (se ignora).");
                continue;
            }
            String id = String.valueOf(rawId).trim().toLowerCase(Locale.ROOT);
            Item item = resolveItem(id);
            if (item == null) {
                errors.add(unknownItem(caja, id));
                continue;
            }

            int cantidad = 1;
            Object rawCantidad = map.get("cantidad");
            if (rawCantidad instanceof Number number) {
                cantidad = Math.max(1, Math.min(number.intValue(), new ItemStack(item).getMaxStackSize()));
            } else if (rawCantidad != null) {
                errors.add("puzzles.yml: la caja '" + caja + "', item '" + id
                        + "': 'cantidad' debe ser un numero (se usa 1).");
            }

            String comando = map.get("comando") == null ? null : String.valueOf(map.get("comando")).trim();
            if (comando != null && !comando.isEmpty() && !(item instanceof PistaItem)) {
                errors.add("puzzles.yml: la caja '" + caja + "', item '" + id
                        + "': solo hoja_pista ejecuta un 'comando' (se ignora).");
                comando = null;
            }

            String nombre = map.get("nombre") == null ? null : String.valueOf(map.get("nombre"));
            items.add(new CajaItem(id, cantidad, nombre, comando));
        }
        return items;
    }

    private static String unknownItem(String caja, String id) {
        return "puzzles.yml: la caja '" + caja + "' tiene un item desconocido: '" + id + "' (se ignora).";
    }

    /** Texto del yml a Component: acepta códigos de color con & y sale sin cursiva. */
    private static Component colored(String text) {
        return Component.literal(text.replace('&', '§'))
                .withStyle(style -> style.withItalic(false));
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

    /** Estado correcto de una válvula por su nombre, o null si el yml no la nombra. */
    public static synchronized Integer valvulaEstado(String name) {
        return name == null || name.isEmpty() ? null : valvulasPorNombre.get(name);
    }

    public static synchronized List<String> valvulaNames() {
        return new ArrayList<>(valvulasPorNombre.keySet());
    }

    // ==================== CAJAS ====================

    /** true si el yml define contenido para una caja con ese nombre. */
    public static synchronized boolean hasCajaContenido(String name) {
        return name != null && cajasContenido.containsKey(name);
    }

    /**
     * NBT de la entidad que el yml le manda soltar a la caja, o null si no la
     * menciona (entonces la caja se queda con la que ya tenía). Un tag vacío
     * significa "esta caja no suelta ninguna entidad".
     */
    public static synchronized CompoundTag cajaEntidad(String name) {
        CajaConfig config = cajasContenido.get(name);
        return config == null || config.entidad() == null ? null : config.entidad().copy();
    }

    /** Contenido configurado para la caja, ya convertido a stacks (uno por entrada). */
    public static synchronized List<ItemStack> cajaContenido(String name) {
        List<ItemStack> stacks = new ArrayList<>();
        CajaConfig config = cajasContenido.get(name);
        if (config == null) {
            return stacks;
        }
        for (CajaItem entry : config.items()) {
            Item item = resolveItem(entry.id());
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item, entry.cantidad());
            if (entry.comando() != null && !entry.comando().isEmpty()) {
                stack.set(ModComponents.PISTA_COMMAND, entry.comando());
            }
            if (entry.nombre() != null && !entry.nombre().isEmpty()) {
                stack.set(DataComponents.CUSTOM_NAME, colored(entry.nombre()));
            }
            stacks.add(stack);
        }
        return stacks;
    }

    public static synchronized List<String> cajaNames() {
        return new ArrayList<>(cajasContenido.keySet());
    }
}
