package com.github.razorplay01.entity.client;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.custom.NumeroParedEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class NumeroParedEntityModel extends GeoModel<NumeroParedEntity> {

    /**
     * Una textura por número, pre-resueltas para no crear ResourceLocations
     * en cada frame de render.
     */
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[NumeroParedEntity.MAX_NUMBER + 1];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID,
                    "textures/entity/numeros/numero_" + i + ".png");
        }
    }

    @Override
    public ResourceLocation getModelResource(NumeroParedEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "geo/numero_pared.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NumeroParedEntity animatable) {
        int number = animatable.getNumber();
        if (number < 0 || number >= TEXTURES.length) {
            number = 0;
        }
        return TEXTURES[number];
    }

    @Override
    public ResourceLocation getAnimationResource(NumeroParedEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "animations/numero_pared.animation.json");
    }
}
