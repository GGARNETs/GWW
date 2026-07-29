package com.github.razorplay01.entity.client;

import com.github.razorplay01.entity.custom.CannonBulletEntity;
import com.github.razorplay01.entity.custom.CannonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CannonBulletEntityRenderer extends GeoEntityRenderer<CannonBulletEntity> {
    public CannonBulletEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CannonBulletEntityModel());
        // El doble de grande, igual que su hitbox (sized de la entidad y json de morehitboxes).
        withScale(2.0f);
    }
}
