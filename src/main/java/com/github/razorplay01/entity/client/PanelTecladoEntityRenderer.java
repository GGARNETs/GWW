package com.github.razorplay01.entity.client;

import com.github.razorplay01.entity.custom.PanelTecladoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.FastBoneFilterGeoLayer;

import java.util.Arrays;

public class PanelTecladoEntityRenderer extends GeoEntityRenderer<PanelTecladoEntity> {
    public PanelTecladoEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PanelTecladoEntityModel());
        this.addRenderLayer(new FastBoneFilterGeoLayer<>(this,
                () -> Arrays.asList(
                        "apagado",
                        "encendido_cerrado",
                        "encendido_abierto"
                ),
                (bone, entity, partialTick) -> {
                    // Mismo criterio que el panel de código: la barra de estado sigue a
                    // la PUERTA, no al puzzle.
                    String name = bone.getName();

                    boolean shouldShow;
                    if (!entity.isPowered()) {
                        shouldShow = name.equalsIgnoreCase("apagado");
                    } else if (entity.areDoorsOpen()) {
                        shouldShow = name.equalsIgnoreCase("encendido_abierto");
                    } else {
                        shouldShow = name.equalsIgnoreCase("encendido_cerrado");
                    }
                    bone.setHidden(!shouldShow);
                }
        ));
    }
}
