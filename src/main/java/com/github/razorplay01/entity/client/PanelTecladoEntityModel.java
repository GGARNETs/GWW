package com.github.razorplay01.entity.client;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.custom.PanelTecladoEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * OJO con panel_teclado.geo.json: sus teclas están al revés A PROPÓSITO. Abierto en
 * Blockbench el teclado se lee 3-2-1 / 6-5-4 / 9-8-7, pero en el juego se ve 1-2-3
 * porque Minecraft renderiza la entidad rotada 180° (invierte el eje X).
 * <p>
 * Las sub-hitboxes de data/gww/hitboxes/panel_teclado.json comparten ese mismo eje X
 * que los cubos, así que van espejadas junto a ellos: la hitbox "1" está en x=-2.9,
 * igual que el cubo que muestra el 1. Como el dígito que se pulsa sale del NOMBRE de
 * la hitbox, tocar uno de los dos archivos sin el otro deja el teclado cruzado (se ve
 * un 1 y registra un 3). Al reexportar desde Blockbench hay que volver a intercambiar
 * las columnas exteriores en AMBOS.
 */
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
