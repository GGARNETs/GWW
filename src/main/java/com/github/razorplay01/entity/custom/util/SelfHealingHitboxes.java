package com.github.razorplay01.entity.custom.util;

/**
 * La implementan las entidades multipart que pueden quedar con 0 sub-hitboxes si se
 * construyen en el cliente ANTES de que llegue el paquete {@code morehitboxes:sync_hitbox_data}
 * (la race del aluvión de paquetes del join, descrita en {@link MultiPartHitboxes}).
 * <p>
 * El mixin {@code HitboxDataLoaderMixin} recorre las entidades cargadas justo cuando
 * la data de morehitboxes aterriza y llama a {@link #rebuildHitboxesIfMissing()} sobre
 * cada una — reemplazando el sondeo por-tick que antes vivía en {@code tick()}.
 */
public interface SelfHealingHitboxes {
    /**
     * Reconstruye los sub-hitboxes si la entidad se creó vacía y los datos de morehitboxes
     * ya están disponibles. Idempotente: si ya tiene parts (o la data aún no llegó), no hace nada.
     */
    void rebuildHitboxesIfMissing();
}
