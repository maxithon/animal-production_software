package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockTreatment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * NOTE: If you already have a LivestockTreatmentRepository in your project,
 * do NOT add this as a second file — merge the query methods below into your
 * existing repository instead (same issue we hit with LivestockBreedingRepository).
 *
 * Assumes LivestockTreatment has: treatmentDate (LocalDate), medication
 * (Medication), livestock (Livestock), treatmentCost (BigDecimal), isDeleted (Boolean).
 * Adjust field names below if your entity differs.
 */
@Repository
public interface LivestockTreatmentRepository extends JpaRepository<LivestockTreatment, UUID> {

    @Query("SELECT t FROM LivestockTreatment t " +
            "JOIN FETCH t.medication m " +
            "LEFT JOIN FETCH t.livestock l " +
            "WHERE t.treatmentDate BETWEEN :fromDate AND :toDate " +
            "AND t.isDeleted = false")
    List<LivestockTreatment> findByTreatmentDateBetweenAndIsDeletedFalse(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT t FROM LivestockTreatment t " +
            "JOIN FETCH t.medication m " +
            "LEFT JOIN FETCH t.livestock l " +
            "WHERE t.medication.id = :medicationId " +
            "AND t.treatmentDate BETWEEN :fromDate AND :toDate " +
            "AND t.isDeleted = false")
    List<LivestockTreatment> findByMedicationIdAndTreatmentDateBetweenAndIsDeletedFalse(
            @Param("medicationId") UUID medicationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    // Used by SupervisorReportController to pull treatments for a set of animals
    List<LivestockTreatment> findByLivestock_IdIn(List<UUID> livestockIds);
}