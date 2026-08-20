package com.github.razorplay01.entity;

import com.github.razorplay01.GWW;
import com.github.razorplay01.entity.attribute.ModAttributes;
import com.github.razorplay01.entity.custom.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {

    public static final EntityType<CannonEntity> CANNON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "cannon"),
            EntityType.Builder.of(CannonEntity::new, MobCategory.MISC)
                    .sized(1.5f, 1.8f).build());

    public static final EntityType<CannonBulletEntity> CANNON_BULLET = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "cannon_bullet"),
            EntityType.Builder.of(CannonBulletEntity::new, MobCategory.MISC)
                    .sized(1.6f, 1.6f).build());

    public static final EntityType<UblablaEntity> UBLABLA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "ublabla"),
            EntityType.Builder.of(UblablaEntity::new, MobCategory.MISC)
                    .sized(1f, 2.5f).build());

    public static final EntityType<CajaEntity> CAJA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "caja"),
            EntityType.Builder.of(CajaEntity::new, MobCategory.MISC)
                    .sized(1.1f, 1.1f).build());

    public static final EntityType<Cuadro1Entity> CUADRO1 = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "cuadro_lilith_1"),
            EntityType.Builder.of(Cuadro1Entity::new, MobCategory.MISC)
                    .sized(2f, 2f).build());
    public static final EntityType<Cuadro2Entity> CUADRO2 = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "cuadro_lilith_2"),
            EntityType.Builder.of(Cuadro2Entity::new, MobCategory.MISC)
                    .sized(2f, 2f).build());
    public static final EntityType<Cuadro3Entity> CUADRO3 = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "cuadro_lilith_3"),
            EntityType.Builder.of(Cuadro3Entity::new, MobCategory.MISC)
                    .sized(2f, 2f).build());

    public static final EntityType<RejaDuctoEntity> REJA_DUCTO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "reja_ducto"),
            EntityType.Builder.of(RejaDuctoEntity::new, MobCategory.MISC)
                    .sized(2f, 2f).build());

    public static final EntityType<PuertaAticoEntity> PUERTA_ATICO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "puerta_atico"),
            EntityType.Builder.of(PuertaAticoEntity::new, MobCategory.MISC)
                    .sized(2f, 0.2f).build());

    public static final EntityType<LuzTortugaEntity> LUZ_TORTUGA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "luz_tortuga"),
            EntityType.Builder.of(LuzTortugaEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.6f).build());

    public static final EntityType<InterruptorIndustrialEntity> INTERRUPTOR_INDUSTRIAL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "interruptor_industrial"),
            EntityType.Builder.of(InterruptorIndustrialEntity::new, MobCategory.MISC)
                    .sized(0.5f, 1f).build());

    public static final EntityType<CajaHerramientasEntity> CAJA_HERRAMIENTAS = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "caja_herramientas"),
            EntityType.Builder.of(CajaHerramientasEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).build());

    public static final EntityType<PanelFusiblesEntity> PANEL_FUSIBLES = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "panel_fusibles"),
            EntityType.Builder.of(PanelFusiblesEntity::new, MobCategory.MISC)
                    .sized(2.5f, 1.5f).build());
    public static final EntityType<PuertaMetalicaEntity> PUERTA_METALICA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "puerta_metalica"),
            EntityType.Builder.of(PuertaMetalicaEntity::new, MobCategory.MISC)
                    .sized(2f, 1.5f).build());
    public static final EntityType<PuertaMetalicaUblablaEntity> PUERTA_METALICA_UBLABLA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "puerta_metalica_ublabla"),
            EntityType.Builder.of(PuertaMetalicaUblablaEntity::new, MobCategory.MISC)
                    .sized(2f, 1.5f).build());

    public static final EntityType<FigurasParedEntity> FIGURAS_PARED = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "figuras_pared"),
            EntityType.Builder.of(FigurasParedEntity::new, MobCategory.MISC)
                    .sized(2f, 2f).build());

    public static final EntityType<ManivelaEntity> MANIVELA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "manivela"),
            EntityType.Builder.of(ManivelaEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).build());
    public static final EntityType<PalancaEntity> PALANCA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "palanca"),
            EntityType.Builder.of(PalancaEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.5f).build());
    public static final EntityType<ValvulaEntity> VALVULA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "valvula"),
            EntityType.Builder.of(ValvulaEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).build());
    public static final EntityType<EscaleraEntity> ESCALERA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "escalera"),
            EntityType.Builder.of(EscaleraEntity::new, MobCategory.MISC)
                    .sized(1f, 1.5f).build());
    public static final EntityType<CableEntity> CABLE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "cable"),
            EntityType.Builder.of(CableEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).build());
    public static final EntityType<PuertaJaulaEntity> PUERTA_JAULA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "puerta_jaula"),
            EntityType.Builder.of(PuertaJaulaEntity::new, MobCategory.MISC)
                    .sized(0.4f, 2f).build());
    public static final EntityType<PanelEnergiaEntity> PANEL_ENERGIA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "panel_energia"),
            EntityType.Builder.of(PanelEnergiaEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).build());
    public static final EntityType<PanelCodigoEntity> PANEL_CODIGO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "panel_codigo"),
            EntityType.Builder.of(PanelCodigoEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.7f).build());
    public static final EntityType<PanelTecladoEntity> PANEL_TECLADO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "panel_teclado"),
            EntityType.Builder.of(PanelTecladoEntity::new, MobCategory.MISC)
                    .sized(0.9f, 1.5f).build());
    public static final EntityType<NumeroParedEntity> NUMERO_PARED = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(GWW.MOD_ID, "numero_pared"),
            EntityType.Builder.of(NumeroParedEntity::new, MobCategory.MISC)
                    .sized(2f, 2f).build());

    public static void registerModEntities() {
        GWW.LOGGER.info("Registering Mod Entities for " + GWW.MOD_ID);
    }
}
