package com.github.razorplay01.entity.custom.util;

import com.github.razorplay01.entity.custom.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PuzzleEntityChecker {

    private static final List<AnomalyChecker> CHECKERS = new ArrayList<>();

    /**
     * Resultado de una detección de anomalía.
     */
    public record AnomalyResult(boolean found, String message) {
        public static final AnomalyResult NONE = new AnomalyResult(false, null);

        public static AnomalyResult detected(String message) {
            return new AnomalyResult(true, message);
        }
    }

    /**
     * Interfaz para cada tipo de chequeo de anomalía.
     */
    @FunctionalInterface
    public interface AnomalyChecker {
        AnomalyResult check(Level level, AABB area);
    }

    public static void registerChecker(AnomalyChecker checker) {
        CHECKERS.add(checker);
    }

    /**
     * Devuelve el primer resultado de anomalía encontrado, o vacío si no hay ninguna.
     */
    public static Optional<AnomalyResult> findFirstAnomaly(Level level, AABB area) {
        for (AnomalyChecker checker : CHECKERS) {
            AnomalyResult result = checker.check(level, area);
            if (result.found()) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    /**
     * Devuelve todos los resultados de anomalías encontradas.
     */
    public static List<AnomalyResult> findAllAnomalies(Level level, AABB area) {
        List<AnomalyResult> results = new ArrayList<>();
        for (AnomalyChecker checker : CHECKERS) {
            AnomalyResult result = checker.check(level, area);
            if (result.found()) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * Comprobación rápida: ¿hay alguna anomalía?
     */
    public static boolean hasAnyAnomaly(Level level, AABB area) {
        return findFirstAnomaly(level, area).isPresent();
    }

    /**
     * Registra los checkers por defecto con sus mensajes correspondientes.
     */
    public static void registerDefaultCheckers() {
        // Cuadros movidos sin puzzle resuelto
        registerChecker((level, area) -> {
            boolean found = level.getEntitiesOfClass(BaseCuadroEntity.class, area).stream()
                    .anyMatch(cuadro -> cuadro.hasBeenMoved() && !cuadro.isPuzzleSolved());
            return found
                    ? AnomalyResult.detected("§e¡Un cuadro ha sido movido de su lugar!")
                    : AnomalyResult.NONE;
        });

        // Puerta de jaula abierta
        registerChecker((level, area) -> {
            boolean found = level.getEntitiesOfClass(PuertaJaulaEntity.class, area).stream()
                    .anyMatch(PuertaJaulaEntity::isOpen);
            return found
                    ? AnomalyResult.detected("§e¡La puerta de la jaula está abierta!")
                    : AnomalyResult.NONE;
        });

        // Puerta metálica abierta
        registerChecker((level, area) -> {
            boolean found = level.getEntitiesOfClass(PuertaMetalicaEntity.class, area).stream()
                    .anyMatch(PuertaMetalicaEntity::isOpen);
            return found
                    ? AnomalyResult.detected("§e¡La puerta metálica está abierta!")
                    : AnomalyResult.NONE;
        });

        // Interruptor industrial encendido: apagado es su estado normal, encender la
        // luz es precisamente lo que delata a los jugadores.
        registerChecker((level, area) -> {
            boolean found = level.getEntitiesOfClass(InterruptorIndustrialEntity.class, area).stream()
                    .anyMatch(InterruptorIndustrialEntity::isOn);
            return found
                    ? AnomalyResult.detected("§e¡Un interruptor industrial está encendido!")
                    : AnomalyResult.NONE;
        });

        registerChecker((level, area) -> {
            boolean found = level.getEntitiesOfClass(PalancaEntity.class, area).stream()
                    .anyMatch(cuadro -> cuadro.hasBeenMoved() && !cuadro.isPuzzleSolved());
            return found
                    ? AnomalyResult.detected("§e¡La palanca ha sido movida de su lugar!")
                    : AnomalyResult.NONE;
        });
    }
}