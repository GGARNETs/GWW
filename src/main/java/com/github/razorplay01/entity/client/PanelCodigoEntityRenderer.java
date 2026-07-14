package com.github.razorplay01.entity.client;

import com.github.razorplay01.entity.custom.PanelCodigoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.FastBoneFilterGeoLayer;

import java.util.Arrays;

public class PanelCodigoEntityRenderer extends GeoEntityRenderer<PanelCodigoEntity> {
    public PanelCodigoEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PanelCodigoEntityModel());
        this.addRenderLayer(new FastBoneFilterGeoLayer<>(this,
                () -> Arrays.asList(
                        "apagado",
                        "encendido_cerrado",
                        "encendido_abierto"
                ),
                (bone, entity, partialTick) -> {
                    // El candado de abajo sigue a la PUERTA, no al puzzle: verde abierto
                    // mientras la puerta lo esté, rojo en cuanto se vuelva a cerrar. El
                    // puzzle sigue resuelto por dentro; esto es solo el aspecto.
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