package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.dto.VeterinarianActivityDtos.VetAggregate;
import rw.animalproduct.animal.production.entity.Veterinarian;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface VeterinarianRepository extends JpaRepository<Veterinarian, UUID> {

    @Query("""
           SELECT v FROM Veterinarian v
           WHERE v.isActive = true AND v.isDeleted = false
           ORDER BY v.firstName, v.lastName
           """)
    List<Veterinarian> findAllActive();

    List<Veterinarian> findByIsDeletedFalseAndIsActiveTrue();

    long countByIsDeletedFalse();

    List<Veterinarian> findByIsDeletedFalse();

    @Query("""
           SELECT v FROM Veterinarian v
           LEFT JOIN v.location l
           WHERE v.isActive = true AND v.isDeleted = false
             AND ( LOWER(v.firstName)     LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(v.lastName)      LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(v.licenseNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(v.clinicName)    LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(v.email)         LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(l.name)          LIKE LOWER(CONCAT('%', :q, '%'))
                OR v.phone LIKE CONCAT('%', :q, '%') )
           ORDER BY v.firstName, v.lastName
           """)
    List<Veterinarian> searchActive(@Param("q") String query);

    @Query("""
           SELECT v FROM Veterinarian v
           WHERE v.isDeleted = false
             AND LOWER(v.firstName) = LOWER(:firstName)
             AND LOWER(v.lastName)  = LOWER(:lastName)
           """)
    List<Veterinarian> findPossibleDuplicates(@Param("firstName") String firstName,
                                              @Param("lastName") String lastName);

    /* ============================================================
     *  ONE QUERY — all per-vet aggregation done in the DB.
     *
     *  ✅ FIXED:
     *    - removed every `AND t.isDeleted = false` from the
     *      LivestockTreatment subqueries — that field no longer
     *      exists on LivestockTreatment (deletion is tracked in
     *      audit_log).
     *    - `v.isDeleted = false` on Veterinarian is KEPT because
     *      Veterinarian still has its own soft-delete column.
     * ============================================================ */
    @Query("""
        SELECT new rw.animalproduct.animal.production.dto.VeterinarianActivityDtos$VetAggregate(
            v.id, v.firstName, v.lastName, v.licenseNumber, v.clinicName, v.specialization, v.isActive,

            (SELECT COUNT(t) FROM LivestockTreatment t
                WHERE t.veterinarian = v
                  AND t.treatmentDate BETWEEN :from AND :to),

            (SELECT COUNT(DISTINCT t.livestock.id) FROM LivestockTreatment t
                WHERE t.veterinarian = v
                  AND t.treatmentDate BETWEEN :from AND :to),

            (SELECT COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t
                WHERE t.veterinarian = v
                  AND t.treatmentDate BETWEEN :from AND :to),

            (SELECT COUNT(s) FROM LivestockSick s
                WHERE s.veterinarian = v
                  AND s.reportedDate BETWEEN :from AND :to),

            (SELECT COUNT(s) FROM LivestockSick s
                WHERE s.veterinarian = v
                  AND s.reportedDate BETWEEN :from AND :to
                  AND s.status = 'RECOVERED'),

            (SELECT COUNT(s) FROM LivestockSick s
                WHERE s.veterinarian = v
                  AND s.reportedDate BETWEEN :from AND :to
                  AND s.status = 'RECOVERING'),

            (SELECT COUNT(s) FROM LivestockSick s
                WHERE s.veterinarian = v
                  AND s.reportedDate BETWEEN :from AND :to
                  AND s.status = 'SICK'),

            (SELECT COUNT(s) FROM LivestockSick s
                WHERE s.veterinarian = v
                  AND s.reportedDate BETWEEN :from AND :to
                  AND s.status = 'CRITICAL'),

            (SELECT COUNT(s) FROM LivestockSick s
                WHERE s.veterinarian = v
                  AND s.reportedDate BETWEEN :from AND :to
                  AND s.status = 'DEAD'),

            (SELECT COUNT(t) FROM LivestockTreatment t
                WHERE t.veterinarian = v
                  AND t.followUpDate IS NOT NULL
                  AND t.followUpDate BETWEEN :from AND :to),

            (SELECT COUNT(t) FROM LivestockTreatment t
                WHERE t.veterinarian = v
                  AND t.followUpDate IS NOT NULL
                  AND t.followUpDate < :from
                  AND (t.treatmentStatus IS NULL
                       OR t.treatmentStatus NOT IN ('COMPLETED','CANCELLED')))
        )
        FROM Veterinarian v
        WHERE v.isDeleted = false
        ORDER BY v.firstName, v.lastName
        """)
    List<VetAggregate> findActivitySummary(@Param("from") LocalDate from,
                                           @Param("to")   LocalDate to);
}