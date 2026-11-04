package rw.animalproduct.animal.production.entity;

/**
 * Production Period filter for the Offspring Production Report.
 * ALL_TIME keeps the report's original "lifetime production" behaviour.
 * The others scope every KPI, the table, the ranking and the trend chart to
 * birth events whose birth_date falls inside the resolved [from, to] range —
 * see OffspringProductionReportService#resolvePeriod().
 */
public enum ProductionPeriod {
    ALL_TIME,
    THIS_YEAR,
    LAST_YEAR,
    THIS_MONTH,
    CUSTOM
}
