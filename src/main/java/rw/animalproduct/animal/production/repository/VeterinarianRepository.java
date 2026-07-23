package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Veterinarian;

import java.util.List;
import java.util.UUID;

public interface VeterinarianRepository extends JpaRepository<Veterinarian, UUID> {

    /**
     * Active vets only (for dropdowns).
     */
    List<Veterinarian> findByIsDeletedFalseAndIsActiveTrue();

    /**
     * Live search: matches first name, last name, or license number.
     * Case-insensitive, non-deleted, active records only.
     */
    @Query("""
        SELECT v FROM Veterinarian v
        WHERE v.isDeleted = false
          AND v.isActive  = true
          AND (
              LOWER(v.firstName)     LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(v.lastName)      LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(CONCAT(v.firstName, ' ', v.lastName)) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(v.licenseNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY v.firstName, v.lastName
    """)
    List<Veterinarian> searchActive(@Param("q") String query);

    /**
     * All active (no filter) — used when query is blank.
     */
    @Query("""
        SELECT v FROM Veterinarian v
        WHERE v.isDeleted = false AND v.isActive = true
        ORDER BY v.firstName, v.lastName
    """)
    List<Veterinarian> findAllActive();

    // ── NEW: real, non-deleted count (vets are soft-deleted, so plain count() overcounts) ──
    long countByIsDeletedFalse();
}