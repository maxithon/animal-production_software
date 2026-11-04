package rw.animalproduct.animal.production.entity;

import java.time.LocalDate;

/**
 * Resolved [from, to] bounds for a ProductionPeriod. Either side may be null,
 * meaning "no lower/upper bound" (that's how ALL_TIME is represented — both
 * null — so the native queries can keep using their existing
 * "(:fromDate IS NULL OR ...)" style null-safe filters unchanged).
 */
public record DateRange(LocalDate from, LocalDate to) {
}
