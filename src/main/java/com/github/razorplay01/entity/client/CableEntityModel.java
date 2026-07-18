package com.github.razorplay01.entity.client;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.custom.CableEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class CableEntityModel extends GeoModel<CableEntity> {
    @Override
    public ResourceLocation getModelResource(CableEntity animatable) {
        if (animatable.getCableType() == 0) {
            return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "geo/cable_1.geo.json");
        } else {
            return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "geo/cable_2.geo.json");
        }
    }

    @Override
    public ResourceLocation getTextureResource(CableEntity animatable) {
        if (animatable.getCableType() == 0) {
            return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/cable_1.png");
        } else {
            return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "textures/entity/cable_2.png");
        }
    }

    @Override
    public ResourceLocation getAnimationResource(CableEntity animatable) {
        if (animatable.getCableType() == 0) {
            return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "animations/cable_1.animation.json");
        } else {
            return ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "animations/cable_2.animation.json");
        }
    }

    @Override
    public void setCustomAnimations(CableEntity animatable, long instanceId, AnimationState<CableEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone root = this.getAnimationProcessor().getBone("root");
        if (root != null) {
            float rotationY = animatable.getState() * 90.0f;
            root.setRotY((float) Math.toRadians(rotationY));
        }
    }
}
