package com.github.razorplay01.entity.client;

import com.github.razorplay01.entity.custom.ValvulaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.FastBoneFilterGeoLayer;

import java.util.List;

public class ValvulaEntityRenderer extends GeoEntityRenderer<ValvulaEntity> {
    public ValvulaEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new ValvulaEntityModel());
        addRenderLayer(new FastBoneFilterGeoLayer<>(this,
                () -> List.of("bone"),
                (bone, entity, partialTick) -> {
                    bone.setHidden(!entity.hasManivela());
                }
        ));
    }

    /** El nombre es solo la clave de puzzles.yml: nunca se dibuja como cartel. */
    @Override
    public boolean shouldShowName(ValvulaEntity entity) {
        return false;
    }
}