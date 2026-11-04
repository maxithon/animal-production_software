package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Livestock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ✅ CHANGED (v3, Aug 2026):
 *
 * 1. REMOVED the dependency on the stored `livestock.reproductive_status =
 *    'READY_TO_BREED'` flag as a pre-filter. That flag is written by a
 *    background job (BreedingReadinessNotificationService) and can go stale
 *    (e.g. a breeding starts, or she gives birth, before the job re-runs).
 *    Readiness is now computed live, every time, from:
 *      - livestock.status = ACTIVE (not sold/dead/transferred/retired)
 *      - age vs category min/max breeding age
 *      - reproductive_status <> 'PREGNANT'
 *      - reproductive_status <> 'NOT_APPLICABLE' (explicit breeding
 *        restriction / management exclusion)
 *      - postpartum recovery completed
 *      - NEW: no active (PENDING) breeding record for her
 *
 * 2. NEW reason: reason_no_active_breeding — she must not already have a
 *    PENDING breeding record. This was previously never checked anywhere
 *    in the ready-to-breed pipeline (spec item "not already in an active
 *    breeding process").
 *
 * 3. reasons_met_count is now out of 5 (was 4).
 */
public interface ReadyToBreedReportRepository extends JpaRepository<Livestock, UUID> {

    String BASE_CTE = """
        WITH RECURSIVE loc_walk AS (
            SELECT a.id AS origin_id, a.id, a.name, a.location_type, a.parent_id
            FROM a_location a
            WHERE a.id IN (
                SELECT DISTINCT l.location_id FROM livestock l
                WHERE l.location_id IS NOT NULL AND l.is_deleted = false
                  AND l.gender = 'FEMALE' AND l.status = 'ACTIVE'
            )
            UNION ALL
            SELECT w.origin_id, p.id, p.name, p.location_type, p.parent_id
            FROM loc_walk w
            JOIN a_location p ON p.id = w.parent_id
        ),
        loc_chain AS (
            SELECT
                origin_id,
                MAX(CASE WHEN location_type = 'PROVINCE' THEN name END) AS province,
                MAX(CASE WHEN location_type = 'DISTRICT' THEN name END) AS district,
                MAX(CASE WHEN location_type = 'SECTOR' THEN name END) AS sector,
                MAX(CASE WHEN location_type = 'CELL' THEN name END) AS cell,
                MAX(CASE WHEN location_type = 'VILLAGE' THEN name END) AS village
            FROM loc_walk
            GROUP BY origin_id
        ),
        last_breeding AS (
            SELECT DISTINCT ON (livestock_id) livestock_id, breeding_date AS last_breeding_date
            FROM livestock_breeding
            WHERE is_deleted = false
            ORDER BY livestock_id, breeding_date DESC
        ),
        active_breeding AS (
            -- NEW: any female with a PENDING breeding record is not ready
            SELECT DISTINCT livestock_id
            FROM livestock_breeding
            WHERE is_deleted = false AND status = 'PENDING'
        ),
        last_birth AS (
            SELECT DISTINCT ON (livestock_id) livestock_id, birth_date AS last_birth_date
            FROM livestock_births
            WHERE is_deleted = false
            ORDER BY livestock_id, birth_date DESC
        ),
        birth_counts AS (
            SELECT livestock_id, COUNT(*) AS births
            FROM livestock_births
            WHERE is_deleted = false
            GROUP BY livestock_id
        ),
        base AS (
            SELECT
                l.id                                     AS livestock_id,
                l.tag_number                              AS tag_number,
                c.name                                     AS category_name,
                l.gender                                   AS gender,
                l.birth_date                               AS birth_date,
                c.min_breeding_age_months                  AS min_breeding_age_months,
                c.max_breeding_age_months                  AS max_breeding_age_months,
                c.postpartum_recovery_days                 AS postpartum_recovery_days,
                COALESCE(bc.births, 0)                     AS births,
                lbr.last_breeding_date                     AS last_breeding_date,
                lbi.last_birth_date                        AS last_birth_date,
                CASE WHEN lbi.last_birth_date IS NOT NULL
                     THEN (CURRENT_DATE - lbi.last_birth_date) END AS days_since_last_birth,
                l.reproductive_status                      AS reproductive_status,
                l.breeding_ready_notified_at                AS notified_at,
                (ab.livestock_id IS NOT NULL)               AS has_active_breeding,
                (EXTRACT(YEAR FROM AGE(CURRENT_DATE, l.birth_date)) * 12
                    + EXTRACT(MONTH FROM AGE(CURRENT_DATE, l.birth_date)))::int AS age_months,
                -- Ready Since: deterministic date, not an estimate. Can be past or future.
                CASE
                    WHEN COALESCE(bc.births, 0) = 0
                        THEN l.birth_date + (c.min_breeding_age_months || ' months')::interval
                    ELSE lbi.last_birth_date + (c.postpartum_recovery_days || ' days')::interval
                END::date AS ready_since,
                b.first_name || ' ' || b.last_name          AS beneficiary_name,
                b.phone                                      AS beneficiary_phone,
                bloc.name                                     AS beneficiary_location_name,
                lc.province                                   AS animal_location_province,
                lc.district                                   AS animal_location_district,
                lc.sector                                     AS animal_location_sector,
                lc.cell                                       AS animal_location_cell,
                lc.village                                    AS animal_location_village
            FROM livestock l
            JOIN livestock_categories c ON c.id = l.livestock_category_id
            LEFT JOIN last_breeding lbr ON lbr.livestock_id = l.id
            LEFT JOIN last_birth lbi ON lbi.livestock_id = l.id
            LEFT JOIN birth_counts bc ON bc.livestock_id = l.id
            LEFT JOIN active_breeding ab ON ab.livestock_id = l.id
            LEFT JOIN beneficiaries b ON b.id = l.beneficiary_id
            LEFT JOIN a_location bloc ON bloc.id = b.location_id
            LEFT JOIN loc_chain lc ON lc.origin_id = l.location_id
            WHERE l.is_deleted = false
              AND l.gender = 'FEMALE'
              AND l.status = 'ACTIVE'
              AND c.is_deleted = false
              AND (:categoryId IS NULL OR l.livestock_category_id = :categoryId)
              AND (:search IS NULL OR l.tag_number ILIKE CONCAT('%', :search, '%'))
        ),
        with_reasons AS (
            SELECT
                base.*,
                (min_breeding_age_months IS NOT NULL AND max_breeding_age_months IS NOT NULL
                    AND age_months >= min_breeding_age_months)                    AS reason_reached_breeding_age,
                (births = 0 OR days_since_last_birth >= postpartum_recovery_days) AS reason_recovery_completed,
                (reproductive_status IS DISTINCT FROM 'PREGNANT')                 AS reason_not_pregnant,
                (reproductive_status IS DISTINCT FROM 'NOT_APPLICABLE')           AS reason_no_breeding_restriction,
                (NOT has_active_breeding)                                        AS reason_no_active_breeding,
                (min_breeding_age_months IS NOT NULL AND max_breeding_age_months IS NOT NULL
                    AND age_months <= max_breeding_age_months)                    AS reason_within_max_age
            FROM base
        ),
        enriched AS (
            SELECT
                with_reasons.*,
                (reason_reached_breeding_age::int + reason_recovery_completed::int
                    + reason_not_pregnant::int + reason_no_breeding_restriction::int
                    + reason_no_active_breeding::int) AS reasons_met_count,
                CASE
                    WHEN reason_reached_breeding_age AND reason_recovery_completed
                         AND reason_not_pregnant AND reason_no_breeding_restriction
                         AND reason_no_active_breeding AND reason_within_max_age
                    THEN 'READY' ELSE 'ALMOST_READY'
                END AS readiness_status,
                CASE
                    WHEN reason_reached_breeding_age AND reason_recovery_completed
                         AND reason_not_pregnant AND reason_no_breeding_restriction
                         AND reason_no_active_breeding AND reason_within_max_age
                    THEN GREATEST(ready_since, CURRENT_DATE)
                    ELSE ready_since
                END AS recommended_breeding_date,
                GREATEST(0, CURRENT_DATE - ready_since) AS days_ready_for_breeding,
                GREATEST(0, ready_since - CURRENT_DATE) AS days_until_ready
            FROM with_reasons
        ),
        final AS (
            SELECT
                enriched.*,
                CASE
                    WHEN notified_at IS NOT NULL THEN 'NOTIFIED'
                    WHEN readiness_status = 'READY' AND days_ready_for_breeding >= 3 THEN 'OVERDUE'
                    ELSE 'AWAITING'
                END AS notification_status
            FROM enriched
        )
        SELECT * FROM final
        WHERE (:readiness IS NULL OR readiness_status = :readiness)
          AND (:notifyStatus IS NULL OR notification_status = :notifyStatus)
        """;

    @Query(value = BASE_CTE + """
             ORDER BY
               CASE WHEN readiness_status = 'READY' THEN 0 ELSE 1 END,
               days_ready_for_breeding DESC,
               tag_number ASC
            """,
            countQuery = "SELECT COUNT(*) FROM (" + BASE_CTE + ") count_wrap",
            nativeQuery = true)
    Page<ReadyRow> findReadyToBreed(@Param("categoryId") UUID categoryId,
                                    @Param("search") String search,
                                    @Param("notifyStatus") String notifyStatus,
                                    @Param("readiness") String readiness,
                                    Pageable pageable);

    /** Unpaged variant for dashboard summary cards / grouping by category in Java. */
    @Query(value = BASE_CTE + """
             ORDER BY
               CASE WHEN readiness_status = 'READY' THEN 0 ELSE 1 END,
               days_ready_for_breeding DESC,
               tag_number ASC
            """, nativeQuery = true)
    java.util.List<ReadyRow> findAllReadyToBreed(@Param("categoryId") UUID categoryId,
                                                 @Param("search") String search,
                                                 @Param("notifyStatus") String notifyStatus,
                                                 @Param("readiness") String readiness);

    @Query(value =
            "SELECT " +
                    "COUNT(*) FILTER (WHERE readiness_status = 'READY') AS total_ready_to_breed, " +
                    "COUNT(*) FILTER (WHERE readiness_status = 'ALMOST_READY') AS almost_ready, " +
                    "COUNT(*) FILTER (WHERE readiness_status = 'READY' AND notification_status = 'NOTIFIED') AS notified, " +
                    "COUNT(*) FILTER (WHERE readiness_status = 'READY' AND notification_status = 'AWAITING') AS awaiting_notification, " +
                    "COUNT(*) FILTER (WHERE readiness_status = 'READY' AND notification_status = 'OVERDUE') AS notification_overdue, " +
                    "COUNT(*) FILTER (WHERE readiness_status = 'READY' AND births = 0) AS first_time_breeders, " +
                    "AVG(days_ready_for_breeding) FILTER (WHERE readiness_status = 'READY') AS average_days_ready " +
                    "FROM (" + BASE_CTE + ") kpi_wrap",
            nativeQuery = true)
    KpiRow findKpis(@Param("categoryId") UUID categoryId,
                    @Param("search") String search,
                    @Param("notifyStatus") String notifyStatus,
                    @Param("readiness") String readiness);

    @Modifying
    @Query(value = "UPDATE livestock SET breeding_ready_notified_at = :now WHERE id = :id AND breeding_ready_notified_at IS NULL",
            nativeQuery = true)
    int markNotified(@Param("id") UUID id, @Param("now") LocalDateTime now);

    default int markNotified(UUID id) {
        return markNotified(id, LocalDateTime.now());
    }

    interface ReadyRow {
        UUID getLivestock_id();
        String getTag_number();
        String getCategory_name();
        String getGender();
        LocalDate getBirth_date();
        Integer getMin_breeding_age_months();
        Integer getMax_breeding_age_months();
        Integer getPostpartum_recovery_days();
        Long getBirths();
        LocalDate getLast_breeding_date();
        LocalDate getLast_birth_date();
        Long getDays_since_last_birth();
        String getReproductive_status();
        LocalDateTime getNotified_at();
        Integer getAge_months();
        LocalDate getReady_since();
        LocalDate getRecommended_breeding_date();
        Long getDays_ready_for_breeding();
        Long getDays_until_ready();
        String getBeneficiary_name();
        String getBeneficiary_phone();
        String getBeneficiary_location_name();
        String getAnimal_location_province();
        String getAnimal_location_district();
        String getAnimal_location_sector();
        String getAnimal_location_cell();
        String getAnimal_location_village();
        Boolean getReason_reached_breeding_age();
        Boolean getReason_recovery_completed();
        Boolean getReason_not_pregnant();
        Boolean getReason_no_breeding_restriction();
        Boolean getReason_no_active_breeding();
        Boolean getReason_within_max_age();
        Integer getReasons_met_count();
        String getReadiness_status();
        String getNotification_status();
    }

    interface KpiRow {
        Long getTotal_ready_to_breed();
        Long getAlmost_ready();
        Long getNotified();
        Long getAwaiting_notification();
        Long getNotification_overdue();
        Long getFirst_time_breeders();
        Double getAverage_days_ready();
    }
}