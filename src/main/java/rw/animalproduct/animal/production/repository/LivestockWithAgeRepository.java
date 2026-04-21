package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Livestock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the v_livestock_with_age view.
 *
 * Uses native SQL queries against the view, mapping results into
 * LivestockWithAgeDTO via a constructor-based JPQL approach, or
 * via native queries with SqlResultSetMapping.
 *
 * NOTE: We still extend JpaRepository<Livestock,UUID> so Spring
 * can manage the entity; the view queries are added as @Query methods.
 */
public interface LivestockWithAgeRepository extends JpaRepository<Livestock, UUID> {

    // ─────────────────────────────────────────────────────────────
    // Full list from view
    // ─────────────────────────────────────────────────────────────

    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name, ' ', a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        ORDER BY v.created_at DESC
        """,
            countQuery = "SELECT COUNT(*) FROM v_livestock_with_age",
            nativeQuery = true)
    Page<Object[]> findAllFromView(Pageable pageable);

    /**
     * Single animal by ID from the view.
     */
    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name, ' ', a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE v.id = :id
        """, nativeQuery = true)
    Optional<Object[]> findByIdFromView(@Param("id") UUID id);

    // ─────────────────────────────────────────────────────────────
    // Lifecycle-stage filters
    // ─────────────────────────────────────────────────────────────

    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name,' ',a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE v.lifecycle_stage = :stage
        ORDER BY v.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findByLifecycleStage(@Param("stage") String stage);

    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name,' ',a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE v.lifecycle_stage = :stage
        ORDER BY v.created_at DESC
        """,
            countQuery = "SELECT COUNT(*) FROM v_livestock_with_age WHERE lifecycle_stage = :stage",
            nativeQuery = true)
    Page<Object[]> findByLifecycleStage(@Param("stage") String stage, Pageable pageable);

    // ─────────────────────────────────────────────────────────────
    // Status filters
    // ─────────────────────────────────────────────────────────────

    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name,' ',a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE v.status = :status
        ORDER BY v.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findByStatusFromView(@Param("status") String status);

    // Alias method for controller compatibility
    default List<Object[]> findByStatus(String status) {
        return findByStatusFromView(status);
    }

    // ─────────────────────────────────────────────────────────────
    // Category filter
    // ─────────────────────────────────────────────────────────────

    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name,' ',a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE v.livestock_category_id = :categoryId
        ORDER BY v.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findByCategoryIdFromView(@Param("categoryId") UUID categoryId);

    // ─────────────────────────────────────────────────────────────
    // Gender filter
    // ─────────────────────────────────────────────────────────────

    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name,' ',a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE v.gender = :gender
        ORDER BY v.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findByGenderFromView(@Param("gender") String gender);

    // Alias method for controller compatibility
    default List<Object[]> findByGender(String gender) {
        return findByGenderFromView(gender);
    }

    // ─────────────────────────────────────────────────────────────
    // Search by tag number
    // ─────────────────────────────────────────────────────────────

    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name,' ',a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE LOWER(v.tag_number) LIKE LOWER(CONCAT('%', :tag, '%'))
        ORDER BY v.created_at DESC
        """, nativeQuery = true)
    List<Object[]> searchByTagFromView(@Param("tag") String tag);

    // ─────────────────────────────────────────────────────────────
    // Summary counts from view
    // ─────────────────────────────────────────────────────────────

    @Query(value = "SELECT COUNT(*) FROM v_livestock_with_age WHERE lifecycle_stage = :stage",
            nativeQuery = true)
    long countByLifecycleStage(@Param("stage") String stage);

    @Query(value = "SELECT COUNT(*) FROM v_livestock_with_age WHERE status = :status",
            nativeQuery = true)
    long countByStatusFromView(@Param("status") String status);

    // Method name expected by the service (without "FromView" suffix)
    default long countByStatus(String status) {
        return countByStatusFromView(status);
    }

    @Query(value = "SELECT COUNT(*) FROM v_livestock_with_age WHERE gender = :gender",
            nativeQuery = true)
    long countByGenderFromView(@Param("gender") String gender);

    // Method name expected by the service (without "FromView" suffix)
    default long countByGender(String gender) {
        return countByGenderFromView(gender);
    }

    // Ready-to-breed animals due within N days of expected due date
    @Query(value = """
        SELECT
            v.id,
            v.tag_number,
            v.gender,
            v.photo,
            v.date_received,
            v.last_birth_date,
            v.offspring_count,
            v.current_value,
            v.acquisition_method,
            v.sold_price,
            v.status,
            v.conception_date,
            v.last_breeding_date,
            v.pregnancy_status,
            v.first_breeding_date,
            v.expected_due_date,
            v.is_pregnant,
            v.created_at,
            v.age_in_days,
            v.age_in_months,
            v.category_name,
            v.category_code,
            v.gestation_period_months,
            v.lifecycle_stage,
            CONCAT(a.first_name,' ',a.last_name) AS beneficiary_name,
            loc.name AS location_name
        FROM v_livestock_with_age v
        LEFT JOIN abaragizwa_amatungo a ON a.id = v.abaragizwa_amatungo_id
        LEFT JOIN a_location loc ON loc.id = v.location_id
        WHERE v.expected_due_date IS NOT NULL
          AND v.expected_due_date BETWEEN CURRENT_DATE AND (CURRENT_DATE + INTERVAL ':days days')
        ORDER BY v.expected_due_date ASC
        """, nativeQuery = true)
    List<Object[]> findDueSoon(@Param("days") int days);
}