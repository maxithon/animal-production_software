package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.Medication;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    /** Only active medications, sorted alphabetically — used for dropdowns */
    List<Medication> findByIsActiveTrueOrderByNameAsc();

    /** All medications sorted by name */
    List<Medication> findAllByOrderByNameAsc();

    /** Check if a name already exists (case-insensitive) */
    boolean existsByNameIgnoreCase(String name);

    /** Find by category */
    List<Medication> findByCategoryOrderByNameAsc(Medication.MedicationCategory category);
}
