package com.github.razorplay01.instance;

import com.github.razorplay01.entity.custom.util.EscapeRoomPersistable;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Una instance es la captura de una sala: todas sus entidades guardadas con la
 * posición relativa al punto desde el que se capturó (donde estaba el jugador).
 * Funciona como una schematic: se puede pegar en cualquier coordenada.
 */
@Getter
public class Instance {
    /**
     * Punto desde el que se capturó. Solo se usa para reubicar las entidades
     * colgantes, que guardan su bloque de anclaje en coordenadas absolutas.
     */
    private final BlockPos origin;
    private final int radius;
    private final List<EntitySnapshot> entities = new ArrayList<>();

    /**
     * Zona de meta, guardada relativa al origen (como el resto de la schematic) para
     * que viaje con ella a cualquier arena. Null si esta instance no tiene meta.
     */
    private BlockPos metaMin;
    private BlockPos metaMax;

    public Instance(BlockPos origin, int radius) {
        this.origin = origin;
        this.radius = radius;
    }

    public void add(EntitySnapshot snapshot) {
        entities.add(snapshot);
    }

    public boolean hasMeta() {
        return metaMin != null && metaMax != null;
    }

    /**
     * Define la meta a partir de coordenadas absolutas del mundo, guardándola relativa
     * a {@code referenceOrigin} (normalmente el origen de la arena donde se está
     * definiendo). Así {@link #resolveMetaBox} la reconstruye exactamente en ese punto
     * y, a la vez, viaja a cualquier otra arena donde se pegue la instance.
     */
    public void setMeta(BlockPos referenceOrigin, BlockPos absMin, BlockPos absMax) {
        this.metaMin = absMin.subtract(referenceOrigin);
        this.metaMax = absMax.subtract(referenceOrigin);
    }

    public void clearMeta() {
        this.metaMin = null;
        this.metaMax = null;
    }

    /**
     * Caja de la meta en coordenadas del mundo para una arena pegada en {@code arenaOrigin}.
     * Devuelve null si esta instance no tiene meta. La meta se desplaza igual que los
     * bloques al pegar: origen de la arena + desplazamiento relativo guardado.
     */
    public AABB resolveMetaBox(BlockPos arenaOrigin) {
        if (!hasMeta()) {
            return null;
        }
        BlockPos a = arenaOrigin.offset(metaMin);
        BlockPos b = arenaOrigin.offset(metaMax);
        return new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1.0, Math.max(a.getY(), b.getY()) + 1.0, Math.max(a.getZ(), b.getZ()) + 1.0);
    }

    // Las claves NBT conservan el nombre "Center" para que los .dat capturados
    // con la versión anterior del sistema se sigan pudiendo leer.
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CenterX", origin.getX());
        tag.putInt("CenterY", origin.getY());
        tag.putInt("CenterZ", origin.getZ());
        tag.putInt("Radius", radius);

        ListTag list = new ListTag();
        for (EntitySnapshot snapshot : entities) {
            list.add(snapshot.serialize());
        }
        tag.put("Entities", list);

        if (hasMeta()) {
            tag.put("MetaMin", saveBlockPos(metaMin));
            tag.put("MetaMax", saveBlockPos(metaMax));
        }

        return tag;
    }

    public static Instance deserialize(CompoundTag tag) {
        BlockPos origin = new BlockPos(
                tag.getInt("CenterX"),
                tag.getInt("CenterY"),
                tag.getInt("CenterZ")
        );
        int radius = tag.contains("Radius") ? tag.getInt("Radius") : 15;

        Instance instance = new Instance(origin, radius);

        ListTag list = tag.getList("Entities", 10);
        for (int i = 0; i < list.size(); i++) {
            instance.add(EntitySnapshot.deserialize(list.getCompound(i)));
        }

        if (tag.contains("MetaMin") && tag.contains("MetaMax")) {
            instance.metaMin = loadBlockPos(tag.getCompound("MetaMin"));
            instance.metaMax = loadBlockPos(tag.getCompound("MetaMax"));
        }

        return instance;
    }

    private static CompoundTag saveBlockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    private static BlockPos loadBlockPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    @Getter
    public static class EntitySnapshot {
        private final String entityType;
        private final Vec3 relativePos;
        private final float yaw;
        private final float pitch;
        private final CompoundTag entityData;

        public EntitySnapshot(Entity entity, Vec3 origin) {
            this.relativePos = entity.position().subtract(origin);
            this.yaw = entity.getYRot();
            this.pitch = entity.getXRot();

            this.entityData = new CompoundTag();
            entity.save(entityData);
            this.entityType = entityData.getString("id");

            // Los puzzles guardan sus enlaces y posiciones iniciales relativos
            // al mismo origen, para que sobrevivan al pegado en otra coordenada.
            if (entity instanceof EscapeRoomPersistable persistable) {
                persistable.saveEscapeRoomData(this.entityData, origin);
            }
        }

        private EntitySnapshot(String entityType, Vec3 relativePos, float yaw, float pitch,
                               CompoundTag entityData) {
            this.entityType = entityType;
            this.relativePos = relativePos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.entityData = entityData;
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", entityType);
            tag.putDouble("RelX", relativePos.x);
            tag.putDouble("RelY", relativePos.y);
            tag.putDouble("RelZ", relativePos.z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            tag.put("EntityData", entityData);
            return tag;
        }

        public static EntitySnapshot deserialize(CompoundTag tag) {
            Vec3 relativePos = new Vec3(
                    tag.getDouble("RelX"),
                    tag.getDouble("RelY"),
                    tag.getDouble("RelZ")
            );
            return new EntitySnapshot(
                    tag.getString("Type"),
                    relativePos,
                    tag.getFloat("Yaw"),
                    tag.getFloat("Pitch"),
                    tag.getCompound("EntityData")
            );
        }
    }
}
