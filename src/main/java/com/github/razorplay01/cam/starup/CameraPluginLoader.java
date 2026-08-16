package com.github.razorplay01.cam.starup;

import com.github.razorplay01.GWW;
import com.github.razorplay01.cam.api.CameraModifier;
import com.github.razorplay01.cam.api.ModifierPriority;
import com.github.razorplay01.cam.core.Modifier;
import com.github.razorplay01.cam.core.ModifierRegistry;
import com.github.razorplay01.extra.MinigameCameraPlugin;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Registra los plugins de cámara del mod al arrancar el cliente.
 * <p>
 * Antes esto se hacía con un escáner de anotaciones que abría el JAR de CADA mod
 * instalado y leía CADA clase con ASM buscando la anotación @Plugin. En un cliente
 * con un modpack normal eso son decenas de miles de clases descomprimidas y
 * analizadas en cada arranque, para acabar encontrando el único plugin que hay,
 * que además es de este mismo mod. Se registra a mano y listo.
 */
public final class CameraPluginLoader {
    private CameraPluginLoader() {
    }

    public static void clientLoading() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "minigame_aerial");
        CameraModifier modifier = new Modifier(id);

        ModifierRegistry.INSTANCE.register(new MinigameCameraPlugin(modifier), ModifierPriority.HIGH, modifier);
        ModifierRegistry.INSTANCE.freeze(List.of(id.toString()), List.of());

        GWW.LOGGER.info("[GWW] Plugin de cámara registrado: {}", id);
    }
}
