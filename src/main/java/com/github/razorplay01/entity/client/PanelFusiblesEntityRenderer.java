package com.github.razorplay01.entity.client;

import com.github.razorplay01.entity.custom.PanelFusiblesEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Optional;

public class PanelFusiblesEntityRenderer extends GeoEntityRenderer<PanelFusiblesEntity> {

    public PanelFusiblesEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PanelFusiblesEntityModel());
    }

    @Override
    public void actuallyRender(PoseStack poseStack, PanelFusiblesEntity entity, BakedGeoModel model,
                               @Nullable RenderType renderType, MultiBufferSource bufferSource,
                               @Nullable VertexConsumer buffer, boolean isReRender,
                               float partialTick, int packedLight, int packedOverlay,
                               int colour) {
        Optional<GeoBone> lOff = model.getBone("luz_izquierda_apagada");
        Optional<GeoBone> lOn = model.getBone("luz_izquierda_encendida");
        Optional<GeoBone> rOn = model.getBone("luz_derecha_encendida");
        Optional<GeoBone> rOff = model.getBone("luz_derecha_apagada");

        lOff.ifPresent(geoBone -> geoBone.setHidden(entity.areAllSlotsFilled(2)));
        lOn.ifPresent(geoBone -> geoBone.setHidden(!entity.areAllSlotsFilled(2)));
        rOff.ifPresent(geoBone -> geoBone.setHidden(entity.areAllSlotsFilled(1)));
        rOn.ifPresent(geoBone -> geoBone.setHidden(!entity.areAllSlotsFilled(1)));

        // Antes de renderizar, configurar visibilidad de los huesos de fusible.
        // Cada slot tiene dos formas: la clásica (rojo/verde/azul) y la de anillo
        // (amarillo/violeta); se muestra solo la que corresponde al tipo puesto.
        for (int i = 0; i < PanelFusiblesEntity.FUSE_BONE_NAMES.length; i++) {
            int fuseType = entity.getFuseSlot(i);
            boolean empty = fuseType == PanelFusiblesEntity.FUSE_NONE;
            boolean alt = PanelFusiblesEntity.usesAltShape(fuseType);

            model.getBone(PanelFusiblesEntity.FUSE_BONE_NAMES[i])
                    .ifPresent(bone -> bone.setHidden(empty || alt));
            model.getBone(PanelFusiblesEntity.FUSE_ALT_BONE_NAMES[i])
                    .ifPresent(bone -> bone.setHidden(empty || !alt));
        }

        // Renderizar normalmente (esto renderiza el panel SIN tinte en los fusibles)
        super.actuallyRender(poseStack, entity, model, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, PanelFusiblesEntity entity, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  int colour) {

        // Verificar si este hueso es un fusible para aplicar tinte
        int slotIndex = getFuseSlotIndex(bone.getName());

        if (slotIndex >= 0) {
            int fuseType = entity.getFuseSlot(slotIndex);

            if (fuseType != PanelFusiblesEntity.FUSE_NONE) {
                // Obtener color ARGB del fusible
                int tintedColour = PanelFusiblesEntity.getFuseColor(fuseType);

                // Renderizar este hueso con el color tintado
                super.renderRecursively(poseStack, entity, bone, renderType, bufferSource,
                        buffer, isReRender, partialTick, packedLight, packedOverlay, tintedColour);
                return;
            }
        }

        // Renderizar normalmente (sin tinte)
        super.renderRecursively(poseStack, entity, bone, renderType, bufferSource,
                buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /**
     * Devuelve el índice de slot (0-7) si el nombre del hueso es un fusible
     * (forma clásica o alternativa), o -1 si no lo es.
     */
    private int getFuseSlotIndex(String boneName) {
        for (int i = 0; i < PanelFusiblesEntity.FUSE_BONE_NAMES.length; i++) {
            if (PanelFusiblesEntity.FUSE_BONE_NAMES[i].equals(boneName)
                    || PanelFusiblesEntity.FUSE_ALT_BONE_NAMES[i].equals(boneName)) {
                return i;
            }
        }
        return -1;
    }
}