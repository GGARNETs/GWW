package com.github.razorplay01.entity.client;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.custom.FigurasParedEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FigurasParedEntityModel extends GeoModel<FigurasParedEntity> {

    /**
     * Una textura por número (estados 4-103), pre-resueltas para no crear
     * ResourceLocations en cada frame de render.
     */
    private static final ResourceLocation[] NUMERO_TEXTURES = new ResourceLocation[100];

    static {
        for (int i = 0; i < NUMERO_TEXTURES.length; i++) {
            NUMERO_TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID,
                    "textures/entity/numeros/numero_" + i + ".png");
        }
    }

    @Override
    public ResourceLocation getModelResource(FigurasParedEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "geo/figuras_pared.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(FigurasParedEntity animatable) {
        int state = animatable.getState();
        if (state >= FigurasParedEntity.FIGURA_STATES) {
            int numero = state - FigurasParedEntity.FIGURA_STATES;
            return NUMERO_TEXTURES[Math.min(numero, NUMERO_TEXTURES.length - 1)];
        }
        return switch (state) {
            case 1 -> ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/triangulo.png");
            case 2 -> ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/hexagono.png");
            case 3 -> ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/pentagono.png");
            default -> ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/cuadrado.png");
        };
    }

    @Override
    public ResourceLocation getAnimationResource(FigurasParedEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "animations/figuras_pared.animation.json");
    }
}
