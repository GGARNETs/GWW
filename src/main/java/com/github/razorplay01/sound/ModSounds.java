package com.github.razorplay01.sound;

import com.github.razorplay01.GWW;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Sonidos propios del escape room. Todos son SFX hechos a medida: no se usa
 * ninguna muestra de vanilla, que sonaba a Minecraft y rompia la ambientacion.
 * <p>
 * Reproducir siempre desde el servidor con los helpers de abajo: mandan un solo
 * paquete a los jugadores dentro del alcance del volumen, sin trabajo por tick.
 */
public final class ModSounds {

    private ModSounds() {
    }

    // ==================== MANIPULACION DE OBJETOS ====================
    public static final SoundEvent BLOCKED_THUD = register("blocked_thud");
    public static final SoundEvent DROP_METAL = register("drop_metal");
    public static final SoundEvent FALL_SMALL_METAL = register("fall_small_metal");
    public static final SoundEvent GRAB_METAL = register("grab_metal");
    public static final SoundEvent PAINTING_SET = register("painting_set");
    public static final SoundEvent PAINTING_SLIDE = register("painting_slide");
    public static final SoundEvent PAPER_OPEN = register("paper_open");
    public static final SoundEvent PENDANT_FALL = register("pendant_fall");

    // ==================== PANELES DE CODIGO Y FUSIBLES ====================
    public static final SoundEvent BUTTON_PRESS = register("button_press");
    public static final SoundEvent CIRCUIT_COMPLETE = register("circuit_complete");
    public static final SoundEvent CODE_CORRECT = register("code_correct");
    public static final SoundEvent CODE_WRONG = register("code_wrong");
    public static final SoundEvent FUSE_INSERT = register("fuse_insert");
    public static final SoundEvent FUSE_REMOVE = register("fuse_remove");
    public static final SoundEvent LIGHT_INDICATOR = register("light_indicator");
    public static final SoundEvent PANEL_LOCKED = register("panel_locked");
    public static final SoundEvent PANEL_OFF = register("panel_off");
    public static final SoundEvent PANEL_READY = register("panel_ready");

    // ==================== CORRIENTE, LUCES E INTERRUPTORES ====================
    public static final SoundEvent CABLE_CUT = register("cable_cut");
    public static final SoundEvent CABLE_PLUG = register("cable_plug");
    public static final SoundEvent CABLE_ROTATE = register("cable_rotate");
    public static final SoundEvent ELECTROCUTE = register("electrocute");
    public static final SoundEvent LIGHT_OFF = register("light_off");
    public static final SoundEvent LIGHT_ON = register("light_on");
    public static final SoundEvent POWER_DOWN = register("power_down");
    public static final SoundEvent POWER_UP = register("power_up");
    public static final SoundEvent SPARK_FAIL = register("spark_fail");
    public static final SoundEvent SWITCH_OFF = register("switch_off");
    public static final SoundEvent SWITCH_ON = register("switch_on");

    // ==================== MOMENTOS DE PARTIDA ====================
    public static final SoundEvent CANNON_FIRE = register("cannon_fire");
    public static final SoundEvent NOISE_ALERT = register("noise_alert");
    public static final SoundEvent UBLABLA_ROAR = register("ublabla_roar");
    public static final SoundEvent VICTORY = register("victory");

    // ==================== VALVULAS, MANIVELA Y PRESION ====================
    public static final SoundEvent CRANK_ATTACH = register("crank_attach");
    public static final SoundEvent GAS_LEAK = register("gas_leak");
    public static final SoundEvent VALVE_TURN = register("valve_turn");

    // ==================== CAJAS Y CONTENEDORES ====================
    public static final SoundEvent CRATE_BREAK = register("crate_break");
    public static final SoundEvent ITEMS_SPILL = register("items_spill");
    public static final SoundEvent TOOLBOX_OPEN = register("toolbox_open");

    // ==================== PUERTAS, REJAS Y CANDADOS ====================
    public static final SoundEvent DOOR_ATTIC_OPEN = register("door_attic_open");
    public static final SoundEvent DOOR_CAGE_CLOSE = register("door_cage_close");
    public static final SoundEvent DOOR_CAGE_OPEN = register("door_cage_open");
    public static final SoundEvent DOOR_METAL_CLOSE = register("door_metal_close");
    public static final SoundEvent DOOR_METAL_OPEN = register("door_metal_open");
    public static final SoundEvent LOCK_OPEN = register("lock_open");
    public static final SoundEvent LOCK_PICK = register("lock_pick");
    public static final SoundEvent VENT_OPEN = register("vent_open");
    public static final SoundEvent VENT_STUCK = register("vent_stuck");

    private static SoundEvent register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Fuerza la carga de la clase para que los sonidos queden en el registro. */
    public static void registerModSounds() {
        GWW.LOGGER.info("Registering Mod Sounds for " + GWW.MOD_ID);
    }

    // ==================== HELPERS ====================

    /** Sonido de interaccion en la posicion de una entidad. No hace nada en cliente. */
    public static void playAt(Entity source, SoundEvent sound, float volume, float pitch) {
        if (source == null || source.level().isClientSide) {
            return;
        }
        source.level().playSound(null, source.blockPosition(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static void playAt(Entity source, SoundEvent sound) {
        playAt(source, sound, 1.0F, 1.0F);
    }

    /** Variante con posicion suelta, para cuando el emisor no es una entidad. */
    public static void playAt(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return;
        }
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** Ambiente (vapor, alarmas): mismo canal que usa el resto del mod para no-diegetico. */
    public static void playAmbient(Entity source, SoundEvent sound, float volume, float pitch) {
        if (source == null || source.level().isClientSide) {
            return;
        }
        source.level().playSound(null, source.blockPosition(), sound, SoundSource.AMBIENT, volume, pitch);
    }

    public static void playAmbient(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return;
        }
        level.playSound(null, pos, sound, SoundSource.AMBIENT, volume, pitch);
    }
}
