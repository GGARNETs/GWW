package com.github.razorplay01.instance;

import com.github.razorplay01.GWW;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Fuerza la carga de los chunks de una sala antes de tocar sus entidades.
 * <p>
 * Sin esto, resetear una arena descargada duplicaba entidades: el borrado previo
 * del paste no veía nada (las entidades de un chunk descargado no existen en
 * memoria) pero el pegado sí añadía las nuevas, y cada reset dejaba una copia más.
 * <p>
 * Las entidades cargan asíncronas respecto a su chunk, así que no vale con pedir
 * el chunk y seguir: se piden con tickets y la acción espera en cola hasta que
 * {@link ServerLevel#areEntitiesLoaded} lo confirme. Los trabajos van de uno en
 * uno para que un reset masivo cargue las salas en orden y no medio mapa de golpe.
 * Los tickets son de solo-carga (nivel FULL): las entidades quedan consultables
 * pero sin tickear, y caducan solos para que la sala se descargue después.
 */
public final class ChunkPreloader {
    private ChunkPreloader() {
    }

    /** Los tickets caducan solos a los 30 s: de sobra para pegar, y la sala se descarga después. */
    private static final int TICKET_LIFESPAN_TICKS = 600;
    /** Si en 20 s los chunks no cargaron con sus entidades, se aborta el trabajo avisando. */
    private static final int JOB_TIMEOUT_TICKS = 400;

    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("gww_preload", Comparator.comparingLong(ChunkPos::toLong), TICKET_LIFESPAN_TICKS);

    private static final Queue<Job> JOBS = new ArrayDeque<>();

    /**
     * Ejecuta la acción cuando todos los chunks del cubo centro±radio estén
     * cargados con sus entidades. Si ya lo están (lo normal con el admin delante),
     * corre en el acto y sin pasar por la cola.
     */
    public static void runWhenLoaded(ServerLevel level, BlockPos center, int radius,
                                     Runnable action, Consumer<String> onTimeout) {
        List<ChunkPos> chunks = chunksAround(center, radius);
        if (areEntitiesLoaded(level, chunks)) {
            action.run();
            return;
        }
        boolean idle = JOBS.isEmpty();
        JOBS.add(new Job(level, chunks, action, onTimeout));
        if (idle) {
            requestChunks(JOBS.peek());
        }
    }

    /** Avanza el trabajo en cabeza. Se llama en cada tick del servidor. */
    public static void tick() {
        Job job = JOBS.peek();
        if (job == null) {
            return;
        }

        if (areEntitiesLoaded(job.level, job.chunks)) {
            JOBS.poll();
            job.action.run();
        } else if (++job.ticksWaiting > JOB_TIMEOUT_TICKS) {
            JOBS.poll();
            GWW.LOGGER.warn("[GWW] Timeout esperando {} chunks para una operación de sala", job.chunks.size());
            job.onTimeout.accept("Los chunks de la sala no cargaron a tiempo. Vuelve a intentarlo.");
        } else {
            return;
        }

        if (!JOBS.isEmpty()) {
            requestChunks(JOBS.peek());
        }
    }

    /** Al apagar el server no debe quedar nada pendiente que arrastrar al siguiente arranque. */
    public static void reset() {
        JOBS.clear();
    }

    private static void requestChunks(Job job) {
        for (ChunkPos chunk : job.chunks) {
            job.level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
        }
    }

    private static List<ChunkPos> chunksAround(BlockPos center, int radius) {
        int minX = (center.getX() - radius) >> 4;
        int maxX = (center.getX() + radius) >> 4;
        int minZ = (center.getZ() - radius) >> 4;
        int maxZ = (center.getZ() + radius) >> 4;
        List<ChunkPos> chunks = new ArrayList<>((maxX - minX + 1) * (maxZ - minZ + 1));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    private static boolean areEntitiesLoaded(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            if (!level.areEntitiesLoaded(chunk.toLong())) {
                return false;
            }
        }
        return true;
    }

    private static final class Job {
        private final ServerLevel level;
        private final List<ChunkPos> chunks;
        private final Runnable action;
        private final Consumer<String> onTimeout;
        private int ticksWaiting;

        private Job(ServerLevel level, List<ChunkPos> chunks, Runnable action, Consumer<String> onTimeout) {
            this.level = level;
            this.chunks = chunks;
            this.action = action;
            this.onTimeout = onTimeout;
        }
    }
}
