package com.github.razorplay01.entity.client;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.custom.PanelTecladoEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PanelTecladoEntityModel extends GeoModel<PanelTecladoEntity> {
    @Override
    public ResourceLocation getModelResource(PanelTecladoEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "geo/panel_teclado.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PanelTecladoEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/panel_teclado.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PanelTecladoEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "animations/panel_teclado.animation.json");
    }
}
