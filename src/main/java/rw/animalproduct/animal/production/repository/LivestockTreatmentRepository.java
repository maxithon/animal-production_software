package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.dto.VeterinarianActivityDtos.Dimension;
import rw.animalproduct.animal.production.entity.LivestockTreatment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LivestockTreatmentRepository extends JpaRepository<LivestockTreatment, UUID> {

    /* ============================================================
     *  EXISTING METHODS — unchanged
     * ============================================================ */

    @Query("SELECT t FROM LivestockTreatment t WHERE t.isDeleted = false ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findAllActive();

    @Query("SELECT t FROM LivestockTreatment t WHERE t.isDeleted = false ORDER BY t.treatmentDate DESC")
    Page<LivestockTreatment> findAllActive(Pageable pageable);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.livestock.id = :livestockId AND t.isDeleted = false ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findByLivestockId(@Param("livestockId") UUID livestockId);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.livestock.id IN :livestockIds AND t.isDeleted = false ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findByLivestock_IdIn(@Param("livestockIds") List<UUID> livestockIds);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.treatmentStatus = :status AND t.isDeleted = false ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findByStatus(@Param("status") LivestockTreatment.TreatmentStatus status);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.treatmentDate BETWEEN :fromDate AND :toDate AND t.isDeleted = false")
    List<LivestockTreatment> findByTreatmentDateBetween(@Param("fromDate") LocalDate fromDate,
                                                        @Param("toDate") LocalDate toDate);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.medication.id = :medicationId AND t.treatmentDate BETWEEN :fromDate AND :toDate AND t.isDeleted = false")
    List<LivestockTreatment> findByMedicationIdAndTreatmentDateBetweenAndIsDeletedFalse(
            @Param("medicationId") UUID medicationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(value = "SELECT t.* FROM livestock_treatments t " +
            "LEFT JOIN livestock l ON l.id = t.livestock_id " +
            "LEFT JOIN medications m ON m.id = t.medication_id " +
            "WHERE t.is_deleted = false " +
            "AND (:status IS NULL OR t.treatment_status = CAST(:status AS text)) " +
            "AND (:type IS NULL OR t.treatment_type = CAST(:type AS text)) " +
            "AND (:livestockId IS NULL OR l.id = CAST(:livestockId AS uuid)) " +
            "AND (:fromDate IS NULL OR t.treatment_date >= CAST(:fromDate AS date)) " +
            "AND (:toDate IS NULL OR t.treatment_date <= CAST(:toDate AS date)) " +
            "AND (:isPaid IS NULL OR t.is_paid = CAST(:isPaid AS boolean)) " +
            "AND (:search IS NULL OR " +
            "     LOWER(CAST(l.tag_number AS TEXT)) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "     OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "     OR LOWER(t.diagnosis_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) " +
            "ORDER BY t.treatment_date DESC",
            nativeQuery = true)
    List<LivestockTreatment> findFilteredNative(
            @Param("status") String status,
            @Param("type") String type,
            @Param("livestockId") UUID livestockId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("isPaid") Boolean isPaid,
            @Param("search") String search);

    @Query(value = "SELECT t.* FROM livestock_treatments t " +
            "LEFT JOIN livestock l ON l.id = t.livestock_id " +
            "LEFT JOIN medications m ON m.id = t.medication_id " +
            "WHERE t.is_deleted = false " +
            "AND (LOWER(CAST(l.tag_number AS TEXT)) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "OR LOWER(t.diagnosis_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) " +
            "ORDER BY t.treatment_date DESC",
            nativeQuery = true)
    List<LivestockTreatment> searchNative(@Param("search") String search);

    @Query(value = "SELECT COUNT(*) FROM livestock_treatments t " +
            "LEFT JOIN livestock l ON l.id = t.livestock_id " +
            "LEFT JOIN medications m ON m.id = t.medication_id " +
            "WHERE t.is_deleted = false " +
            "AND (:status IS NULL OR t.treatment_status = CAST(:status AS text)) " +
            "AND (:type IS NULL OR t.treatment_type = CAST(:type AS text)) " +
            "AND (:livestockId IS NULL OR l.id = CAST(:livestockId AS uuid)) " +
            "AND (:fromDate IS NULL OR t.treatment_date >= CAST(:fromDate AS date)) " +
            "AND (:toDate IS NULL OR t.treatment_date <= CAST(:toDate AS date)) " +
            "AND (:isPaid IS NULL OR t.is_paid = CAST(:isPaid AS boolean)) " +
            "AND (:search IS NULL OR " +
            "     LOWER(CAST(l.tag_number AS TEXT)) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "     OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "     OR LOWER(t.diagnosis_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))",
            nativeQuery = true)
    long countFilteredNative(
            @Param("status") String status,
            @Param("type") String type,
            @Param("livestockId") UUID livestockId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("isPaid") Boolean isPaid,
            @Param("search") String search);

    @Query(value = "SELECT COUNT(*) FROM livestock_treatments t " +
            "LEFT JOIN livestock l ON l.id = t.livestock_id " +
            "LEFT JOIN medications m ON m.id = t.medication_id " +
            "WHERE t.is_deleted = false " +
            "AND (LOWER(CAST(l.tag_number AS TEXT)) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "OR LOWER(t.diagnosis_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))",
            nativeQuery = true)
    long countSearchNative(@Param("search") String search);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.treatmentStatus = :status AND t.isDeleted = false")
    long countByStatus(@Param("status") LivestockTreatment.TreatmentStatus status);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.outcomeStatus = :outcome AND t.isDeleted = false")
    long countByOutcome(@Param("outcome") LivestockTreatment.OutcomeStatus outcome);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isPaid = false AND t.isDeleted = false")
    long countUnpaid();

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.paymentStatus = 'PARTIALLY_PAID' AND t.isDeleted = false")
    long countPartiallyPaid();

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isPaid = true AND t.isDeleted = false")
    long countPaid();

    @Query("SELECT COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t WHERE t.isDeleted = false")
    BigDecimal getTotalCost();

    @Query("SELECT COALESCE(SUM(t.amountPaid), 0) FROM LivestockTreatment t WHERE t.isDeleted = false")
    BigDecimal getTotalPaid();

    @Query("SELECT COALESCE(SUM(t.treatmentCost - t.amountPaid), 0) FROM LivestockTreatment t WHERE t.isDeleted = false AND t.treatmentCost IS NOT NULL")
    BigDecimal getTotalOutstanding();

    @Query("SELECT t FROM LivestockTreatment t WHERE t.withdrawalEndDate IS NOT NULL AND t.withdrawalEndDate > CURRENT_DATE AND t.isDeleted = false")
    List<LivestockTreatment> findActiveWithdrawals();

    @Query("SELECT t FROM LivestockTreatment t WHERE t.followUpDate IS NOT NULL AND t.followUpDate BETWEEN CURRENT_DATE AND :endDate AND t.isDeleted = false")
    List<LivestockTreatment> findFollowUpBetween(@Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM LivestockTreatment t " +
            "LEFT JOIN FETCH t.livestock l " +
            "LEFT JOIN FETCH l.beneficiary b " +
            "LEFT JOIN FETCH t.medication m " +
            "LEFT JOIN FETCH t.veterinarian v " +
            "WHERE t.isDeleted = false " +
            "ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findAllActiveWithRelationships();

    /* ============================================================
     *  REPORT AGGREGATION QUERIES
     * ============================================================ */

    // ----- Summary counters -----

    @Query("SELECT COUNT(t) FROM LivestockTreatment t " +
            "WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false")
    long countInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(DISTINCT t.livestock.id) FROM LivestockTreatment t " +
            "WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false")
    long countDistinctAnimalsInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t " +
            "WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false")
    BigDecimal sumCostInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ----- Analysis: by livestock category -----
    // FIXED: Livestock has `livestockCategory` (ManyToOne), not `category`.
    // LivestockCategory has `name` and `code` — we group by `name` so the
    // label reads "Goat", "Cow", "Pig" instead of "GOA"/"C01".

    @Query("""
        SELECT new rw.animalproduct.animal.production.dto.VeterinarianActivityDtos$Dimension(
            COALESCE(l.livestockCategory.name, 'UNSPECIFIED'),
            COUNT(t),
            COUNT(DISTINCT t.livestock.id),
            COALESCE(SUM(t.treatmentCost), 0))
        FROM LivestockTreatment t
        JOIN t.livestock l
        LEFT JOIN l.livestockCategory
        WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false
        GROUP BY l.livestockCategory.name
        ORDER BY COUNT(t) DESC
        """)
    List<Dimension> aggregateByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ----- Analysis: by disease / condition -----

    @Query("""
        SELECT new rw.animalproduct.animal.production.dto.VeterinarianActivityDtos$Dimension(
            COALESCE(t.diagnosisName, 'UNSPECIFIED'),
            COUNT(t),
            COUNT(DISTINCT t.livestock.id),
            COALESCE(SUM(t.treatmentCost), 0))
        FROM LivestockTreatment t
        WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false
        GROUP BY COALESCE(t.diagnosisName, 'UNSPECIFIED')
        ORDER BY COUNT(t) DESC
        """)
    List<Dimension> aggregateByDisease(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ----- Analysis: by location -----
    // Livestock.location is a @ManyToOne to Location (has `name`).

    @Query("""
        SELECT new rw.animalproduct.animal.production.dto.VeterinarianActivityDtos$Dimension(
            COALESCE(loc.name, 'UNSPECIFIED'),
            COUNT(t),
            COUNT(DISTINCT t.livestock.id),
            COALESCE(SUM(t.treatmentCost), 0))
        FROM LivestockTreatment t
        JOIN t.livestock l
        LEFT JOIN l.location loc
        WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false
        GROUP BY COALESCE(loc.name, 'UNSPECIFIED')
        ORDER BY COUNT(t) DESC
        """)
    List<Dimension> aggregateByLocation(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ----- Trends -----

    @Query("""
        SELECT t.treatmentDate, COUNT(t)
        FROM LivestockTreatment t
        WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false
        GROUP BY t.treatmentDate
        ORDER BY t.treatmentDate
        """)
    List<Object[]> dailyTreatmentCounts(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT t.treatmentDate, COALESCE(SUM(t.treatmentCost), 0)
        FROM LivestockTreatment t
        WHERE t.treatmentDate BETWEEN :from AND :to AND t.isDeleted = false
        GROUP BY t.treatmentDate
        ORDER BY t.treatmentDate
        """)
    List<Object[]> dailyCostSums(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ----- Follow-ups (treatment-level — followUpDate lives here) -----

    @Query("SELECT t FROM LivestockTreatment t " +
            "WHERE t.followUpDate IS NOT NULL " +
            "  AND t.followUpDate <= :asOf " +
            "  AND t.isDeleted = false " +
            "  AND (t.treatmentStatus IS NULL OR t.treatmentStatus NOT IN ('COMPLETED','CANCELLED')) " +
            "ORDER BY t.followUpDate ASC")
    List<LivestockTreatment> findFollowUpsDue(@Param("asOf") LocalDate asOf);

    @Query("SELECT t FROM LivestockTreatment t " +
            "WHERE t.followUpDate IS NOT NULL " +
            "  AND t.followUpDate >= CURRENT_DATE " +
            "  AND t.followUpDate <= :endDate " +
            "  AND t.isDeleted = false " +
            "  AND (t.treatmentStatus IS NULL OR t.treatmentStatus NOT IN ('COMPLETED','CANCELLED')) " +
            "ORDER BY t.followUpDate ASC")
    List<LivestockTreatment> findFollowUpsComingUp(@Param("endDate") LocalDate endDate);
}