package com.github.razorplay01.entity.client;

import com.github.razorplay01.entity.custom.NumeroParedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NumeroParedEntityRenderer extends GeoEntityRenderer<NumeroParedEntity> {
    public NumeroParedEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new NumeroParedEntityModel());
    }
}
