package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UpcomingBirthsReportRepository extends JpaRepository<LivestockBreeding, UUID> {

    String UPCOMING_BREEDING_WHERE =
            " FROM livestock_breeding b " +
                    " JOIN livestock l ON l.id = b.livestock_id " +
                    " LEFT JOIN livestock_categories c ON c.id = l.livestock_category_id " +
                    " LEFT JOIN a_location loc ON loc.id = l.location_id " +
                    " WHERE b.is_deleted = false " +
                    "   AND b.status IN ('PREGNANT','CONFIRMED_PREGNANT') " +
                    "   AND b.expected_due_date IS NOT NULL " +
                    "   AND b.expected_due_date >= (CURRENT_DATE - INTERVAL '30 days') " +
                    "   AND b.expected_due_date <= (CURRENT_DATE + CAST(:windowDays AS integer)) " +
                    "   AND (:categoryId IS NULL OR l.livestock_category_id = :categoryId) " +
                    "   AND (:search IS NULL OR l.tag_number ILIKE CONCAT('%', CAST(:search AS text), '%')) ";

    String UPCOMING_BIRTHS_WHERE =
            " FROM (" +
                    "   SELECT DISTINCT ON (lb.livestock_id) " +
                    "          lb.livestock_id, " +
                    "          lb.next_breeding_date " +
                    "   FROM livestock_births lb " +
                    "   WHERE lb.is_deleted = false " +
                    "     AND lb.next_breeding_date IS NOT NULL " +
                    "   ORDER BY lb.livestock_id, lb.birth_date DESC NULLS LAST, lb.created_at DESC NULLS LAST " +
                    " ) last_birth " +
                    " JOIN livestock l ON l.id = last_birth.livestock_id " +
                    " LEFT JOIN livestock_categories c ON c.id = l.livestock_category_id " +
                    " LEFT JOIN a_location loc ON loc.id = l.location_id " +
                    " WHERE l.is_deleted = false " +
                    "   AND l.status = 'ACTIVE' " +
                    "   AND last_birth.next_breeding_date >= (CURRENT_DATE - INTERVAL '30 days') " +
                    "   AND last_birth.next_breeding_date <= (CURRENT_DATE + CAST(:windowDays AS integer)) " +
                    "   AND (:categoryId IS NULL OR l.livestock_category_id = :categoryId) " +
                    "   AND (:search IS NULL OR l.tag_number ILIKE CONCAT('%', CAST(:search AS text), '%')) ";

    String UPCOMING_UNION =
            " FROM ( " +
                    "   SELECT " +
                    "     b.id                    AS breeding_id, " +
                    "     l.id                    AS livestock_id, " +
                    "     l.tag_number            AS tag_number, " +
                    "     COALESCE(c.name,'Uncategorized') AS category_name, " +
                    "     l.gender                AS gender, " +
                    "     l.status                AS animal_status, " +
                    "     b.status                AS breeding_status, " +
                    "     b.breeding_date         AS breeding_date, " +
                    "     b.conception_date       AS conception_date, " +
                    "     b.expected_due_date     AS expected_due_date, " +
                    "     b.pregnancy_confirmed_date AS pregnancy_confirmed_date, " +
                    "     b.breeding_method       AS breeding_method, " +
                    "     b.insemination_method   AS insemination_method, " +
                    "     l.acquisition_method    AS acquisition_method, " +
                    "     loc.name                AS location, " +
                    "     c.typical_litter_size   AS expected_offspring, " +
                    "     (b.expected_due_date - CURRENT_DATE) AS days_until_due " +
                    UPCOMING_BREEDING_WHERE +
                    "   UNION ALL " +
                    "   SELECT " +
                    "     NULL::uuid              AS breeding_id, " +
                    "     l.id                    AS livestock_id, " +
                    "     l.tag_number            AS tag_number, " +
                    "     COALESCE(c.name,'Uncategorized') AS category_name, " +
                    "     l.gender                AS gender, " +
                    "     l.status                AS animal_status, " +
                    "     NULL                    AS breeding_status, " +
                    "     NULL::date              AS breeding_date, " +
                    "     NULL::date              AS conception_date, " +
                    "     last_birth.next_breeding_date AS expected_due_date, " +
                    "     NULL::date              AS pregnancy_confirmed_date, " +
                    "     NULL                    AS breeding_method, " +
                    "     NULL                    AS insemination_method, " +
                    "     l.acquisition_method    AS acquisition_method, " +
                    "     loc.name                AS location, " +
                    "     c.typical_litter_size   AS expected_offspring, " +
                    "     (last_birth.next_breeding_date - CURRENT_DATE) AS days_until_due " +
                    UPCOMING_BIRTHS_WHERE +
                    " ) u ";

    @Query(value =
            "SELECT u.* " + UPCOMING_UNION +
                    " ORDER BY u.expected_due_date ASC ",
            countQuery = "SELECT COUNT(*) " + UPCOMING_UNION,
            nativeQuery = true)
    Page<UpcomingRow> findUpcomingBirths(@Param("categoryId") UUID categoryId,
                                         @Param("search") String search,
                                         @Param("windowDays") Integer windowDays,
                                         Pageable pageable);

    @Query(value =
            "SELECT " +
                    "  COUNT(*) AS total_pregnant, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) BETWEEN -30 AND 3)  AS due_within_3_days, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) BETWEEN -30 AND 7)  AS due_this_week, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) BETWEEN -30 AND 30) AS due_this_month, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) < 0)                AS overdue_pregnancies, " +
                    "  COALESCE(AVG(u.expected_due_date - CURRENT_DATE) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) >= 0), 0) AS average_days_until_due " +
                    UPCOMING_UNION,
            nativeQuery = true)
    KpiRow findKpis(@Param("categoryId") UUID categoryId,
                    @Param("search") String search,
                    @Param("windowDays") Integer windowDays);

    @Query(value =
            "SELECT " +
                    "  u.category_name AS category_name, " +
                    "  COUNT(*) AS total_pregnant, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) BETWEEN -30 AND 3)  AS due_within_3_days, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) BETWEEN -30 AND 7)  AS due_this_week, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) BETWEEN -30 AND 30) AS due_this_month, " +
                    "  COUNT(*) FILTER (WHERE (u.expected_due_date - CURRENT_DATE) < 0)                AS overdue_pregnancies " +
                    UPCOMING_UNION +
                    " GROUP BY u.category_name " +
                    " ORDER BY u.category_name ASC ",
            nativeQuery = true)
    List<CategoryBreakdownRow> findCategoryBreakdown(@Param("categoryId") UUID categoryId,
                                                     @Param("search") String search,
                                                     @Param("windowDays") Integer windowDays);

    interface UpcomingRow {
        UUID getBreeding_id();
        UUID getLivestock_id();
        String getTag_number();
        String getCategory_name();
        String getGender();
        String getAnimal_status();
        String getBreeding_status();
        LocalDate getBreeding_date();
        LocalDate getConception_date();
        LocalDate getExpected_due_date();
        LocalDate getPregnancy_confirmed_date();
        String getBreeding_method();
        String getInsemination_method();
        String getAcquisition_method();
        String getLocation();
        Integer getExpected_offspring();
        Long getDays_until_due();
    }

    interface KpiRow {
        Long getTotal_pregnant();
        Long getDue_within_3_days();
        Long getDue_this_week();
        Long getDue_this_month();
        Long getOverdue_pregnancies();
        Double getAverage_days_until_due();
    }

    interface CategoryBreakdownRow {
        String getCategory_name();
        Long getTotal_pregnant();
        Long getDue_within_3_days();
        Long getDue_this_week();
        Long getDue_this_month();
        Long getOverdue_pregnancies();
    }
}