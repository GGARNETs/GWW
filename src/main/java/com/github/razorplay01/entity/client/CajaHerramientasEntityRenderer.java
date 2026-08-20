package com.github.razorplay01.entity.client;

import com.github.razorplay01.entity.custom.CajaHerramientasEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CajaHerramientasEntityRenderer extends GeoEntityRenderer<CajaHerramientasEntity> {
    public CajaHerramientasEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CajaHerramientasEntityModel());
    }

    /** El nombre es solo la clave de puzzles.yml: nunca se dibuja como cartel. */
    @Override
    public boolean shouldShowName(CajaHerramientasEntity entity) {
        return false;
    }
}
