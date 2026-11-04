package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Livestock;

import java.time.LocalDate;
import java.util.UUID;

public interface BreedingEligibilityReportRepository extends JpaRepository<Livestock, UUID> {

    String ELIGIBILITY_BASE_CTE =
            " WITH RECURSIVE base AS ( " +
                    "   SELECT l.id AS livestock_id, l.tag_number, l.gender, l.status AS animal_status, " +
                    "          l.health_status, l.reproductive_status, l.offspring_count, l.birth_date, " +
                    "          l.beneficiary_id, l.location_id, " +
                    "          COALESCE(c.name, 'Uncategorized') AS category_name, " +
                    "          c.min_breeding_age_months, c.max_breeding_age_months, " +
                    "          (EXTRACT(YEAR FROM AGE(CURRENT_DATE, l.birth_date)) * 12 " +
                    "             + EXTRACT(MONTH FROM AGE(CURRENT_DATE, l.birth_date)))::int AS age_months, " +
                    "          GREATEST(3, ROUND(COALESCE(c.max_breeding_age_months, 0) * 0.10))::int AS near_max_buffer_months " +
                    "   FROM livestock l " +
                    "   LEFT JOIN livestock_categories c ON c.id = l.livestock_category_id " +
                    "   WHERE l.is_deleted = false " +
                    "     AND l.gender = 'FEMALE' " +
                    "     AND l.birth_date IS NOT NULL " +
                    "     AND (:categoryId IS NULL OR l.livestock_category_id = :categoryId) " +
                    "     AND (:search IS NULL OR l.tag_number ILIKE CONCAT('%', CAST(:search AS text), '%')) " +
                    " ), " +
                    " loc_chain AS ( " +
                    "   SELECT a.id, a.name, a.location_type, a.parent_id, a.id AS leaf_id " +
                    "   FROM a_location a " +
                    "   WHERE a.id IN (SELECT DISTINCT location_id FROM base WHERE location_id IS NOT NULL) " +
                    "   UNION ALL " +
                    "   SELECT p.id, p.name, p.location_type, p.parent_id, lc.leaf_id " +
                    "   FROM a_location p " +
                    "   JOIN loc_chain lc ON p.id = lc.parent_id " +
                    " ), " +
                    " loc_pivot AS ( " +
                    "   SELECT leaf_id, " +
                    "     MAX(CASE WHEN location_type = 'PROVINCE' THEN name END) AS province, " +
                    "     MAX(CASE WHEN location_type = 'DISTRICT' THEN name END) AS district, " +
                    "     MAX(CASE WHEN location_type = 'SECTOR'   THEN name END) AS sector, " +
                    "     MAX(CASE WHEN location_type = 'CELL'     THEN name END) AS cell, " +
                    "     MAX(CASE WHEN location_type = 'VILLAGE'  THEN name END) AS village " +
                    "   FROM loc_chain " +
                    "   GROUP BY leaf_id " +
                    " ), " +
                    " scored AS ( " +
                    "   SELECT b.*, " +
                    "     ben.first_name AS beneficiary_first_name, " +
                    "     ben.last_name  AS beneficiary_last_name, " +
                    "     ben.phone      AS beneficiary_phone, " +
                    "     lp.province, lp.district, lp.sector, lp.cell, lp.village, " +
                    "     CASE " +
                    "       WHEN b.min_breeding_age_months IS NULL OR b.max_breeding_age_months IS NULL THEN 'NOT_CONFIGURED' " +
                    "       WHEN b.age_months < b.min_breeding_age_months THEN 'TOO_YOUNG' " +
                    "       WHEN b.age_months >= b.max_breeding_age_months THEN 'EXCEEDED' " +
                    "       WHEN b.age_months >= (b.max_breeding_age_months - b.near_max_buffer_months) THEN 'NEAR_MAXIMUM' " +
                    "       ELSE 'WITHIN_RANGE' " +
                    "     END AS age_status, " +
                    "     (b.max_breeding_age_months - b.age_months) AS remaining_months, " +
                    "     CASE " +
                    "       WHEN b.reproductive_status = 'PREGNANT' THEN 'PREGNANT' " +
                    "       WHEN b.animal_status <> 'ACTIVE' THEN 'NOT_AVAILABLE' " +
                    "       WHEN b.health_status IS NOT NULL AND b.health_status <> 'HEALTHY' THEN 'NOT_AVAILABLE' " +
                    "       WHEN b.min_breeding_age_months IS NULL OR b.max_breeding_age_months IS NULL THEN 'NOT_CONFIGURED' " +
                    "       WHEN b.age_months < b.min_breeding_age_months THEN 'TOO_YOUNG' " +
                    "       WHEN b.age_months >= b.max_breeding_age_months THEN 'EXCEEDED' " +
                    "       WHEN b.age_months >= (b.max_breeding_age_months - b.near_max_buffer_months) THEN 'APPROACHING' " +
                    "       ELSE 'ELIGIBLE' " +
                    "     END AS breeding_status, " +
                    "     CASE " +
                    "       WHEN b.reproductive_status = 'PREGNANT' THEN NULL " +
                    "       WHEN b.animal_status = 'DEAD' THEN 'Animal is deceased' " +
                    "       WHEN b.animal_status = 'SOLD' THEN 'Animal is sold' " +
                    "       WHEN b.animal_status = 'TRANSFERRED' THEN 'Animal is transferred' " +
                    "       WHEN b.animal_status IS NOT NULL AND b.animal_status <> 'ACTIVE' THEN 'Animal no longer active in herd' " +
                    "       WHEN b.health_status IS NOT NULL AND b.health_status <> 'HEALTHY' THEN 'Reproductive/health restriction' " +
                    "       WHEN b.min_breeding_age_months IS NULL OR b.max_breeding_age_months IS NULL THEN 'Breeding age not configured for category' " +
                    "       WHEN b.age_months < b.min_breeding_age_months THEN 'Too young for breeding' " +
                    "       WHEN b.age_months >= b.max_breeding_age_months THEN 'Maximum breeding age exceeded' " +
                    "       ELSE NULL " +
                    "     END AS ineligibility_reason, " +
                    "     CASE " +
                    "       WHEN b.min_breeding_age_months IS NULL OR b.max_breeding_age_months IS NULL THEN 'NOT_APPLICABLE' " +
                    "       WHEN b.age_months >= b.max_breeding_age_months AND b.animal_status = 'ACTIVE' THEN 'RETIREMENT_RECOMMENDED' " +
                    "       WHEN b.age_months >= b.max_breeding_age_months AND b.animal_status <> 'ACTIVE' THEN 'RETIRED' " +
                    "       WHEN b.age_months >= (b.max_breeding_age_months - b.near_max_buffer_months) THEN 'APPROACHING_RETIREMENT' " +
                    "       ELSE 'NOT_APPLICABLE' " +
                    "     END AS retirement_status " +
                    "   FROM base b " +
                    "   LEFT JOIN beneficiaries ben ON ben.id = b.beneficiary_id " +
                    "   LEFT JOIN loc_pivot lp ON lp.leaf_id = b.location_id " +
                    " ), " +
                    " filtered AS ( " +
                    "   SELECT * FROM scored s " +
                    "   WHERE (:status IS NULL OR s.breeding_status = :status) " +
                    "     AND (:ageStatus IS NULL OR s.age_status = :ageStatus) " +
                    "     AND (:retirementStatus IS NULL OR s.retirement_status = :retirementStatus) " +
                    " ), " +
                    " brd AS ( " +
                    "   SELECT livestock_id, " +
                    "     COUNT(*) FILTER (WHERE status = 'COMPLETED')                                  AS successful_pregnancies, " +
                    "     COUNT(*) FILTER (WHERE pregnancy_status = 'NOT_PREGNANT' OR status = 'FAILED') AS failed_pregnancies, " +
                    "     MAX(breeding_date) FILTER (WHERE status = 'COMPLETED')                         AS last_breeding_completed " +
                    "   FROM livestock_breeding WHERE is_deleted = false GROUP BY livestock_id " +
                    " ) ";

    /**
     * Sort is fixed inside this native query — pass an unsorted Pageable, same
     * convention as every other native-query report in this codebase. Rows are
     * ordered so the most actionable statuses (exceeded, approaching) surface first.
     */
    @Query(value =
            ELIGIBILITY_BASE_CTE +
                    " SELECT f.livestock_id, f.tag_number, f.category_name, f.gender, f.animal_status, " +
                    "   f.health_status, f.reproductive_status, f.age_months, " +
                    "   f.min_breeding_age_months, f.max_breeding_age_months, " +
                    "   f.remaining_months, " +
                    "   COALESCE(f.offspring_count, 0) AS births, " +
                    "   COALESCE(brd.successful_pregnancies, 0) AS successful_pregnancies, " +
                    "   COALESCE(brd.failed_pregnancies, 0) AS failed_pregnancies, " +
                    "   0 AS abortions, " +
                    "   brd.last_breeding_completed AS last_birth_date, " +
                    "   (CURRENT_DATE - brd.last_breeding_completed) AS days_since_last_birth, " +
                    "   f.age_status AS age_status, " +
                    "   f.breeding_status AS breeding_status, " +
                    "   f.ineligibility_reason AS ineligibility_reason, " +
                    "   f.retirement_status AS retirement_status, " +
                    "   f.beneficiary_first_name AS beneficiary_first_name, " +
                    "   f.beneficiary_last_name AS beneficiary_last_name, " +
                    "   f.beneficiary_phone AS beneficiary_phone, " +
                    "   f.province AS province, " +
                    "   f.district AS district, " +
                    "   f.sector AS sector, " +
                    "   f.cell AS cell, " +
                    "   f.village AS village " +
                    " FROM filtered f LEFT JOIN brd ON brd.livestock_id = f.livestock_id " +
                    " ORDER BY " +
                    "   CASE f.age_status WHEN 'EXCEEDED' THEN 0 WHEN 'NEAR_MAXIMUM' THEN 1 " +
                    "     WHEN 'WITHIN_RANGE' THEN 2 WHEN 'TOO_YOUNG' THEN 3 ELSE 4 END, " +
                    "   CASE f.breeding_status WHEN 'NOT_CONFIGURED' THEN 0 WHEN 'EXCEEDED' THEN 1 WHEN 'APPROACHING' THEN 2 " +
                    "     WHEN 'ELIGIBLE' THEN 3 WHEN 'PREGNANT' THEN 4 WHEN 'TOO_YOUNG' THEN 5 ELSE 6 END, " +
                    "   f.tag_number ASC ",
            countQuery = ELIGIBILITY_BASE_CTE + " SELECT COUNT(*) FROM filtered f ",
            nativeQuery = true)
    Page<EligibilityRow> findEligibility(@Param("categoryId") UUID categoryId,
                                         @Param("search") String search,
                                         @Param("status") String status,
                                         @Param("ageStatus") String ageStatus,
                                         @Param("retirementStatus") String retirementStatus,
                                         Pageable pageable);

    /**
     * KPIs intentionally read from the unfiltered `scored` CTE (only category +
     * search narrow it) so the cards always show the full breakdown regardless
     * of which status filters are currently applied on the table below them.
     */
    @Query(value =
            ELIGIBILITY_BASE_CTE +
                    " SELECT " +
                    "   COUNT(*) AS total_animals, " +
                    "   COUNT(*) FILTER (WHERE breeding_status = 'ELIGIBLE')              AS eligible_for_breeding, " +
                    "   COUNT(*) FILTER (WHERE age_status = 'TOO_YOUNG')                  AS too_young, " +
                    "   COUNT(*) FILTER (WHERE age_status = 'NEAR_MAXIMUM')               AS near_maximum_age, " +
                    "   COUNT(*) FILTER (WHERE age_status = 'EXCEEDED')                   AS maximum_age_exceeded, " +
                    "   COUNT(*) FILTER (WHERE retirement_status = 'RETIREMENT_RECOMMENDED') AS retirement_recommended, " +
                    "   COUNT(*) FILTER (WHERE breeding_status = 'PREGNANT')              AS currently_pregnant, " +
                    "   COUNT(*) FILTER (WHERE breeding_status = 'NOT_AVAILABLE')         AS not_available, " +
                    "   COUNT(*) FILTER (WHERE retirement_status = 'RETIRED')             AS retired " +
                    " FROM scored ",
            nativeQuery = true)
    KpiRow findKpis(@Param("categoryId") UUID categoryId,
                    @Param("search") String search,
                    @Param("status") String status,
                    @Param("ageStatus") String ageStatus,
                    @Param("retirementStatus") String retirementStatus);

    interface EligibilityRow {
        UUID getLivestock_id();
        String getTag_number();
        String getCategory_name();
        String getGender();
        String getAnimal_status();
        String getHealth_status();
        String getReproductive_status();
        Integer getAge_months();
        Integer getMin_breeding_age_months();
        Integer getMax_breeding_age_months();
        Integer getRemaining_months();
        Long getBirths();
        Long getSuccessful_pregnancies();
        Long getFailed_pregnancies();
        Long getAbortions();
        LocalDate getLast_birth_date();
        Long getDays_since_last_birth();
        String getAge_status();
        String getBreeding_status();
        String getIneligibility_reason();
        String getRetirement_status();
        String getBeneficiary_first_name();
        String getBeneficiary_last_name();
        String getBeneficiary_phone();
        String getProvince();
        String getDistrict();
        String getSector();
        String getCell();
        String getVillage();
    }

    interface KpiRow {
        Long getTotal_animals();
        Long getEligible_for_breeding();
        Long getToo_young();
        Long getNear_maximum_age();
        Long getMaximum_age_exceeded();
        Long getRetirement_recommended();
        Long getCurrently_pregnant();
        Long getNot_available();
        Long getRetired();
    }
}
