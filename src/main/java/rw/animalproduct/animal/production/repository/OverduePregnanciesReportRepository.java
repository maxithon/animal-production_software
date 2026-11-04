package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.util.UUID;

public interface OverduePregnanciesReportRepository extends JpaRepository<LivestockBreeding, UUID> {

    // Shared WHERE clause between the paginated query and its count query, and the
    // KPI query — kept as one constant so the three can never drift out of sync.
    // NOTE: this must stay a compile-time constant (string-literal concatenation
    // only) since it's spliced into @Query's countQuery, which requires a constant
    // expression.
    String OVERDUE_BASE_WHERE =
            " FROM livestock_breeding b " +
            " JOIN livestock l ON l.id = b.livestock_id " +
            " LEFT JOIN livestock_categories c ON c.id = l.livestock_category_id " +
            " WHERE b.is_deleted = false " +
            "   AND b.status IN ('PREGNANT','CONFIRMED_PREGNANT') " +
            "   AND b.expected_due_date IS NOT NULL " +
            "   AND b.expected_due_date < CURRENT_DATE " +
            "   AND (:categoryId IS NULL OR l.livestock_category_id = :categoryId) " +
            "   AND (:search IS NULL OR l.tag_number ILIKE CONCAT('%', CAST(:search AS text), '%')) ";

    /**
     * Sort is fixed inside this native query (expected_due_date ASC — the
     * longest-overdue animal appears first) and must NOT have a Pageable-level
     * Sort layered on top. Callers must pass an unsorted Pageable — see
     * OverduePregnanciesReportService.getReport().
     */
    @Query(value =
            "SELECT " +
            "  b.id AS breeding_id, " +
            "  l.id AS livestock_id, " +
            "  l.tag_number AS tag_number, " +
            "  COALESCE(c.name, 'Uncategorized') AS category_name, " +
            "  l.gender AS gender, " +
            "  l.status AS animal_status, " +
            "  b.status AS breeding_status, " +
            "  b.conception_date AS conception_date, " +
            "  b.expected_due_date AS expected_due_date, " +
            "  b.pregnancy_confirmed_date AS pregnancy_confirmed_date, " +
            "  b.breeding_method AS breeding_method, " +
            "  b.insemination_method AS insemination_method, " +
            "  l.acquisition_method AS acquisition_method, " +
            "  (CURRENT_DATE - b.expected_due_date) AS days_overdue " +
            OVERDUE_BASE_WHERE +
            " ORDER BY b.expected_due_date ASC ",
            countQuery = "SELECT COUNT(*) " + OVERDUE_BASE_WHERE,
            nativeQuery = true)
    Page<OverdueRow> findOverduePregnancies(@Param("categoryId") UUID categoryId,
                                             @Param("search") String search,
                                             Pageable pageable);

    @Query(value =
            "SELECT " +
            "  COUNT(*) AS total_overdue, " +
            "  COUNT(*) FILTER (WHERE (CURRENT_DATE - b.expected_due_date) BETWEEN 1 AND 7)  AS watch_count, " +
            "  COUNT(*) FILTER (WHERE (CURRENT_DATE - b.expected_due_date) BETWEEN 8 AND 14) AS concern_count, " +
            "  COUNT(*) FILTER (WHERE (CURRENT_DATE - b.expected_due_date) >= 15)            AS critical_count, " +
            "  COALESCE(AVG(CURRENT_DATE - b.expected_due_date), 0) AS average_days_overdue, " +
            "  MAX(CURRENT_DATE - b.expected_due_date) AS max_days_overdue " +
            OVERDUE_BASE_WHERE,
            nativeQuery = true)
    KpiRow findKpis(@Param("categoryId") UUID categoryId, @Param("search") String search);

    interface OverdueRow {
        UUID getBreeding_id();
        UUID getLivestock_id();
        String getTag_number();
        String getCategory_name();
        String getGender();
        String getAnimal_status();
        String getBreeding_status();
        LocalDate getConception_date();
        LocalDate getExpected_due_date();
        LocalDate getPregnancy_confirmed_date();
        String getBreeding_method();
        String getInsemination_method();
        String getAcquisition_method();
        Long getDays_overdue();
    }

    interface KpiRow {
        Long getTotal_overdue();
        Long getWatch_count();
        Long getConcern_count();
        Long getCritical_count();
        Double getAverage_days_overdue();
        Long getMax_days_overdue();
    }
}
