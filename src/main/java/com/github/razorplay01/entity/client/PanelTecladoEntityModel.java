package com.github.razorplay01.entity.client;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.custom.PanelTecladoEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PanelTecladoEntityModel extends GeoModel<PanelTecladoEntity> {
    private static final ResourceLocation TEXTURE_ON =
            ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/panel_teclado.png");
    /** Sin energía las teclas no muestran número: pantalla y botones muertos. */
    private static final ResourceLocation TEXTURE_OFF =
            ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/panel_teclado_off.png");

    @Override
    public ResourceLocation getModelResource(PanelTecladoEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "geo/panel_teclado.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PanelTecladoEntity animatable) {
        return animatable.isPowered() ? TEXTURE_ON : TEXTURE_OFF;
    }

    @Override
    public ResourceLocation getAnimationResource(PanelTecladoEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "animations/panel_teclado.animation.json");
    }
}
