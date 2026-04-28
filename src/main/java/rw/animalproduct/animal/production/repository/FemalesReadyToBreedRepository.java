package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.Livestock;

import java.util.List;
import java.util.UUID;

@Repository
public interface FemalesReadyToBreedRepository extends JpaRepository<Livestock, UUID> {

    @Query(value = """
            SELECT
                id,
                tag_number,
                category_id,
                category_name,
                category_code,
                gestation_period_months,
                age_months,
                offspring_count,
                last_breeding_date,
                first_breeding_date,
                last_birth_date,
                is_pregnant,
                pregnancy_status,
                conception_date,
                expected_due_date,
                total_breedings,
                successful_breedings,
                date_received,
                birth_date,
                status,
                gender,
                current_value
            FROM v_females_ready_to_breed
            ORDER BY date_received
            """, nativeQuery = true)
    List<Object[]> findAllReadyToBreedRaw();

    @Query(value = """
            SELECT
                id,
                tag_number,
                category_id,
                category_name,
                category_code,
                gestation_period_months,
                age_months,
                offspring_count,
                last_breeding_date,
                first_breeding_date,
                last_birth_date,
                is_pregnant,
                pregnancy_status,
                conception_date,
                expected_due_date,
                total_breedings,
                successful_breedings,
                date_received,
                birth_date,
                status,
                gender,
                current_value
            FROM v_females_ready_to_breed
            WHERE LOWER(tag_number)    LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(category_name) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY date_received
            """, nativeQuery = true)
    List<Object[]> searchReadyToBreedRaw(@Param("search") String search);
}