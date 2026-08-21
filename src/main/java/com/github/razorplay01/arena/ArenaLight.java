package com.github.razorplay01.arena;

import com.github.razorplay01.debug.GwwDebug;
import com.github.razorplay01.entity.custom.InterruptorIndustrialEntity;
import com.github.razorplay01.entity.custom.PanelCodigoEntity;
import com.github.razorplay01.entity.custom.PanelEnergiaEntity;
import com.github.razorplay01.entity.custom.PanelTecladoEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * La "luz" de una arena: visión nocturna para los jugadores que hay dentro mientras
 * el interruptor industrial esté encendido.
 * <p>
 * El efecto es infinito y se aplica una sola vez, en los dos únicos momentos en los
 * que puede cambiar: cuando se acciona el interruptor y cuando un jugador cruza el
 * borde de la arena. No hay ninguna comprobación por tick.
 */
public final class ArenaLight {
    private ArenaLight() {
    }

    /** Sin partículas ni icono: tiene que parecer que se ha encendido la luz, no una poción. */
    public static void apply(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    /**
     * Apaga la luz. Solo retira la visión nocturna infinita que da el mod: una
     * poción que se haya bebido el jugador se queda como está.
     */
    public static void remove(Player player) {
        MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
        if (effect != null && effect.isInfiniteDuration()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /**
     * True si la arena tiene luz: algún interruptor encendido y ningún panel de energía
     * con el cable cortado. Cortar el cable deja la sala a oscuras aunque el interruptor
     * siga puesto — es justo lo que hace ese panel.
     */
    public static boolean isOn(Level level, Arena arena) {
        boolean powerCut = !level.getEntitiesOfClass(PanelEnergiaEntity.class, arena.getZoneAABB(),
                PanelEnergiaEntity::isActive).isEmpty();
        if (powerCut) {
            return false;
        }
        return !level.getEntitiesOfClass(InterruptorIndustrialEntity.class, arena.getZoneAABB(),
                InterruptorIndustrialEntity::isOn).isEmpty();
    }

    /** Enciende o apaga la luz para todos los jugadores que hay ahora en la arena. */
    public static void refresh(Level level, Arena arena, boolean on) {
        for (Player player : level.getEntitiesOfClass(Player.class, arena.getZoneAABB())) {
            if (on) {
                apply(player);
            } else {
                remove(player);
            }
        }
        powerPanels(level, arena, on);
    }

    /**
     * Los paneles de código y los teclados de la sala siguen a la luz: sin energía
     * quedan apagados y sin números, sin necesidad de vincularlos a mano al
     * interruptor. Se llama solo cuando la luz cambia de verdad, nunca por tick.
     */
    public static void powerPanels(Level level, Arena arena, boolean on) {
        List<PanelCodigoEntity> panels = level.getEntitiesOfClass(
                PanelCodigoEntity.class, arena.getZoneAABB(), p -> p.isPowered() != on);
        panels.forEach(p -> p.setPowered(on));
        List<PanelTecladoEntity> teclados = level.getEntitiesOfClass(
                PanelTecladoEntity.class, arena.getZoneAABB(), t -> t.isPowered() != on);
        teclados.forEach(t -> t.setPowered(on));
        if (!panels.isEmpty() || !teclados.isEmpty()) {
            GwwDebug.log(GwwDebug.Category.PUZZLE, "Luz {}: {} panel(es) de codigo y {} teclado(s) {}",
                    on ? "ON" : "OFF", panels.size(), teclados.size(), on ? "encendidos" : "apagados");
        }
    }
}
