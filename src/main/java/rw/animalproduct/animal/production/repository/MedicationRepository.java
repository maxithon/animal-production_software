package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.Medication;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    List<Medication> findAllByOrderByNameAsc();

    List<Medication> findByIsActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    List<Medication> findByCategoryAndIsActiveTrueOrderByNameAsc(Medication.MedicationCategory category);

    /** Count treatments for a single medication — used for the delete rule. */
    @Query("""
           SELECT COUNT(t) FROM LivestockTreatment t
           WHERE t.medication.id = :medicationId
           """)
    long countActiveTreatmentsByMedicationId(@Param("medicationId") UUID medicationId);

    /** Bulk usage for the whole list page — ONE query instead of N. */
    @Query("""
           SELECT t.medication.id, COUNT(t)
           FROM LivestockTreatment t
           WHERE t.medication IS NOT NULL
           GROUP BY t.medication.id
           """)
    List<Object[]> countAllActiveTreatmentsByMedication();
}