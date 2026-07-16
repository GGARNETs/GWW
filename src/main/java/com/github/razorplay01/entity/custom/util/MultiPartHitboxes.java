package com.github.razorplay01.entity.custom.util;

import com.github.darkpred.morehitboxes.MultiPartLevel;
import com.github.darkpred.morehitboxes.api.EntityHitboxData;
import com.github.darkpred.morehitboxes.api.EntityHitboxDataFactory;
import com.github.darkpred.morehitboxes.api.MultiPart;
import com.github.darkpred.morehitboxes.api.MultiPartEntity;
import com.github.darkpred.morehitboxes.internal.HitboxDataLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Reparación de sub-hitboxes para entidades multipart que se construyeron en el
 * cliente ANTES de que llegaran los datos de morehitboxes.
 * <p>
 * MoreHitboxes crea los MultiPart una única vez, en el constructor de la entidad,
 * leyendo de {@code HitboxDataLoader.HITBOX_DATA}. En el cliente esa tabla se llena
 * con el paquete {@code morehitboxes:sync_hitbox_data}, cuyo handler se re-encola
 * con {@code client.execute(...)}: en el aluvión de paquetes del join, los spawns de
 * entidades ya encolados se procesan antes que los datos y la entidad queda con
 * 0 parts para siempre. Por eso al reconectar contra un servidor "caliente" (chunks
 * en memoria, spawns inmediatos) se rompen todas, y tras reiniciar el servidor
 * (chunks fríos, spawns tardíos) funcionan.
 * <p>
 * Estos helpers los invoca {@code HitboxDataLoaderMixin} en el momento exacto en que la
 * data de morehitboxes aterriza en el cliente ({@code HitboxDataLoader.replaceData}), vía
 * {@link SelfHealingHitboxes#rebuildHitboxesIfMissing()}: si la entidad no tiene parts pero
 * los datos ya llegaron, se reconstruyen y se re-registran. (Antes se sondeaba cada tick.)
 */
public final class MultiPartHitboxes {
    private MultiPartHitboxes() {
    }

    /**
     * Devuelve un EntityHitboxData nuevo con parts, o null si los datos de hitbox
     * de este tipo de entidad todavía no están disponibles (se reintenta el
     * próximo tick).
     */
    public static <T extends Mob & MultiPartEntity<T>> EntityHitboxData<T> rebuild(T entity) {
        if (HitboxDataLoader.HITBOX_DATA.getHitboxes(EntityType.getKey(entity.getType())).isEmpty()) {
            return null;
        }
        return EntityHitboxDataFactory.create(entity);
    }

    /**
     * Llamar justo DESPUÉS de asignar el resultado de {@link #rebuild} al campo de la
     * entidad (el mixin de morehitboxes lee los parts a través del getter). Re-dispara
     * Entity#setId para que los parts reciban los ids parentId+1+i que la librería
     * espera, y los registra en el nivel — lo que normalmente hace el callback de
     * tracking-start de morehitboxes, que para esta entidad ya pasó.
     */
    public static <T extends Mob & MultiPartEntity<T>> void registerParts(T entity) {
        entity.setId(entity.getId());
        if (entity.level() instanceof MultiPartLevel multiPartLevel) {
            for (MultiPart<T> part : entity.getEntityHitboxData().getCustomParts()) {
                multiPartLevel.moreHitboxes$addMultiPart(part);
            }
        }
    }
}
