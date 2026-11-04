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

    // ═════════════════════════════════════════════════════════════════
    // ✅ FAST PAGINATED QUERY (used by the treatment list page)
    // ═════════════════════════════════════════════════════════════════

    @Query(value = """
        SELECT t FROM LivestockTreatment t
        LEFT JOIN FETCH t.livestock l
        LEFT JOIN FETCH l.beneficiary
        LEFT JOIN FETCH l.location
        LEFT JOIN FETCH t.medication
        LEFT JOIN FETCH t.veterinarian
        WHERE (:status IS NULL OR t.treatmentStatus = :status)
          AND (:type IS NULL OR t.treatmentType = :type)
          AND (:livestockId IS NULL OR l.id = :livestockId)
          AND (:fromDate IS NULL OR t.treatmentDate >= :fromDate)
          AND (:toDate IS NULL OR t.treatmentDate <= :toDate)
          AND (:isPaid IS NULL OR t.isPaid = :isPaid)
          AND (:search IS NULL OR :search = ''
               OR LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(t.medication.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(t.diagnosisName) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY t.treatmentDate DESC
        """,
            countQuery = """
        SELECT COUNT(t) FROM LivestockTreatment t
        LEFT JOIN t.livestock l
        WHERE (:status IS NULL OR t.treatmentStatus = :status)
          AND (:type IS NULL OR t.treatmentType = :type)
          AND (:livestockId IS NULL OR l.id = :livestockId)
          AND (:fromDate IS NULL OR t.treatmentDate >= :fromDate)
          AND (:toDate IS NULL OR t.treatmentDate <= :toDate)
          AND (:isPaid IS NULL OR t.isPaid = :isPaid)
          AND (:search IS NULL OR :search = ''
               OR LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(t.medication.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(t.diagnosisName) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<LivestockTreatment> findFilteredPage(
            @Param("status") LivestockTreatment.TreatmentStatus status,
            @Param("type") LivestockTreatment.TreatmentCategory type,
            @Param("livestockId") UUID livestockId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("isPaid") Boolean isPaid,
            @Param("search") String search,
            Pageable pageable);

    // ═════════════════════════════════════════════════════════════════
    // QUICK COUNT QUERIES
    // ═════════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(t) FROM LivestockTreatment t")
    long countAll();

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.treatmentStatus = :status")
    long countByStatus(@Param("status") LivestockTreatment.TreatmentStatus status);

    // ═════════════════════════════════════════════════════════════════
    // BASIC FINDERS (used everywhere)
    // ═════════════════════════════════════════════════════════════════

    @Query("SELECT t FROM LivestockTreatment t ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findAllActive();

    @Query("SELECT t FROM LivestockTreatment t ORDER BY t.treatmentDate DESC")
    Page<LivestockTreatment> findAllActive(Pageable pageable);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.livestock.id = :livestockId ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findByLivestockId(@Param("livestockId") UUID livestockId);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.livestock.id IN :livestockIds ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findByLivestock_IdIn(@Param("livestockIds") List<UUID> livestockIds);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.treatmentStatus = :status ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findByStatus(@Param("status") LivestockTreatment.TreatmentStatus status);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.treatmentDate BETWEEN :fromDate AND :toDate")
    List<LivestockTreatment> findByTreatmentDateBetween(@Param("fromDate") LocalDate fromDate,
                                                        @Param("toDate") LocalDate toDate);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.medication.id = :medicationId " +
            "AND t.treatmentDate BETWEEN :fromDate AND :toDate")
    List<LivestockTreatment> findByMedicationIdAndTreatmentDateBetween(
            @Param("medicationId") UUID medicationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    // ═════════════════════════════════════════════════════════════════
    // NATIVE SEARCH / FILTER
    // ═════════════════════════════════════════════════════════════════

    @Query(value = "SELECT t.* FROM livestock_treatments t " +
            "LEFT JOIN livestock l ON l.id = t.livestock_id " +
            "LEFT JOIN medications m ON m.id = t.medication_id " +
            "WHERE (:status IS NULL OR t.treatment_status = CAST(:status AS text)) " +
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
            "WHERE (LOWER(CAST(l.tag_number AS TEXT)) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "OR LOWER(t.diagnosis_name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) " +
            "ORDER BY t.treatment_date DESC",
            nativeQuery = true)
    List<LivestockTreatment> searchNative(@Param("search") String search);

    // ═════════════════════════════════════════════════════════════════
    // PAYMENT / OUTCOME COUNTS
    // ═════════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.outcomeStatus = :outcome")
    long countByOutcome(@Param("outcome") LivestockTreatment.OutcomeStatus outcome);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isPaid = false")
    long countUnpaid();

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.paymentStatus = 'PARTIALLY_PAID'")
    long countPartiallyPaid();

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isPaid = true")
    long countPaid();

    @Query("SELECT COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t")
    BigDecimal getTotalCost();

    @Query("SELECT COALESCE(SUM(t.amountPaid), 0) FROM LivestockTreatment t")
    BigDecimal getTotalPaid();

    @Query("SELECT COALESCE(SUM(t.treatmentCost - t.amountPaid), 0) FROM LivestockTreatment t WHERE t.treatmentCost IS NOT NULL")
    BigDecimal getTotalOutstanding();

    @Query("SELECT t FROM LivestockTreatment t WHERE t.withdrawalEndDate IS NOT NULL AND t.withdrawalEndDate > CURRENT_DATE")
    List<LivestockTreatment> findActiveWithdrawals();

    @Query("SELECT t FROM LivestockTreatment t WHERE t.followUpDate IS NOT NULL AND t.followUpDate BETWEEN CURRENT_DATE AND :endDate")
    List<LivestockTreatment> findFollowUpBetween(@Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM LivestockTreatment t " +
            "LEFT JOIN FETCH t.livestock l " +
            "LEFT JOIN FETCH l.beneficiary b " +
            "LEFT JOIN FETCH t.medication m " +
            "LEFT JOIN FETCH t.veterinarian v " +
            "ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findAllActiveWithRelationships();

    // ═════════════════════════════════════════════════════════════════
    // ✅ REPORT AGGREGATIONS (used by VeterinarianActivityService)
    // ═════════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.treatmentDate BETWEEN :from AND :to")
    long countInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(DISTINCT t.livestock.id) FROM LivestockTreatment t WHERE t.treatmentDate BETWEEN :from AND :to")
    long countDistinctAnimalsInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t WHERE t.treatmentDate BETWEEN :from AND :to")
    BigDecimal sumCostInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT new rw.animalproduct.animal.production.dto.VeterinarianActivityDtos$Dimension(
            COALESCE(l.livestockCategory.name, 'UNSPECIFIED'), COUNT(t),
            COUNT(DISTINCT t.livestock.id), COALESCE(SUM(t.treatmentCost), 0))
        FROM LivestockTreatment t
        JOIN t.livestock l
        LEFT JOIN l.livestockCategory
        WHERE t.treatmentDate BETWEEN :from AND :to
        GROUP BY l.livestockCategory.name ORDER BY COUNT(t) DESC
        """)
    List<Dimension> aggregateByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT new rw.animalproduct.animal.production.dto.VeterinarianActivityDtos$Dimension(
            COALESCE(t.diagnosisName, 'UNSPECIFIED'), COUNT(t),
            COUNT(DISTINCT t.livestock.id), COALESCE(SUM(t.treatmentCost), 0))
        FROM LivestockTreatment t
        WHERE t.treatmentDate BETWEEN :from AND :to
        GROUP BY COALESCE(t.diagnosisName, 'UNSPECIFIED') ORDER BY COUNT(t) DESC
        """)
    List<Dimension> aggregateByDisease(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT new rw.animalproduct.animal.production.dto.VeterinarianActivityDtos$Dimension(
            COALESCE(loc.name, 'UNSPECIFIED'), COUNT(t),
            COUNT(DISTINCT t.livestock.id), COALESCE(SUM(t.treatmentCost), 0))
        FROM LivestockTreatment t
        JOIN t.livestock l LEFT JOIN l.location loc
        WHERE t.treatmentDate BETWEEN :from AND :to
        GROUP BY COALESCE(loc.name, 'UNSPECIFIED') ORDER BY COUNT(t) DESC
        """)
    List<Dimension> aggregateByLocation(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT t.treatmentDate, COUNT(t) FROM LivestockTreatment t
        WHERE t.treatmentDate BETWEEN :from AND :to
        GROUP BY t.treatmentDate ORDER BY t.treatmentDate
        """)
    List<Object[]> dailyTreatmentCounts(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT t.treatmentDate, COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t
        WHERE t.treatmentDate BETWEEN :from AND :to
        GROUP BY t.treatmentDate ORDER BY t.treatmentDate
        """)
    List<Object[]> dailyCostSums(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT t FROM LivestockTreatment t " +
            "WHERE t.followUpDate IS NOT NULL AND t.followUpDate <= :asOf " +
            "AND (t.treatmentStatus IS NULL OR t.treatmentStatus NOT IN ('COMPLETED','CANCELLED')) " +
            "ORDER BY t.followUpDate ASC")
    List<LivestockTreatment> findFollowUpsDue(@Param("asOf") LocalDate asOf);

    @Query("SELECT t FROM LivestockTreatment t " +
            "WHERE t.followUpDate IS NOT NULL " +
            "AND t.followUpDate >= CURRENT_DATE AND t.followUpDate <= :endDate " +
            "AND (t.treatmentStatus IS NULL OR t.treatmentStatus NOT IN ('COMPLETED','CANCELLED')) " +
            "ORDER BY t.followUpDate ASC")
    List<LivestockTreatment> findFollowUpsComingUp(@Param("endDate") LocalDate endDate);
}