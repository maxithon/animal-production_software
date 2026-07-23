package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockTreatment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    // ── Simple paginated + fetch-joined query (no filters) ────────────────────
    @Query(
            value = "SELECT t FROM LivestockTreatment t " +
                    "LEFT JOIN FETCH t.livestock l " +
                    "LEFT JOIN FETCH t.medication m " +
                    "LEFT JOIN FETCH t.veterinarian v " +
                    "WHERE t.isDeleted = false " +
                    "ORDER BY t.treatmentDate DESC",
            countQuery = "SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isDeleted = false"
    )
    Page<LivestockTreatment> findAllActive(Pageable pageable);

    // ── Paginated + filtered query used by the treatments list page ───────────
    // Every filter is optional: pass null for any parameter you don't want applied.
    // The (:param IS NULL OR field = :param) pattern lets one query serve every
    // filter combination without building dynamic SQL/Specifications by hand.
    //
    // NOTE: a fetch-join + Pageable together throws
    // "firstResult/maxResults specified with collection fetch" if there's no
    // separate countQuery, and can also produce incorrect paging with
    // *-to-many fetches. All the joins below are *-to-one (livestock, medication,
    // veterinarian), so this is safe.
    //
    // IMPORTANT: :search is explicitly CAST to string (varchar) below.
    // Without the cast, Postgres' JDBC driver cannot infer the parameter type
    // for a bind variable that appears both compared to NULL and concatenated
    // inside CONCAT(...), and silently falls back to typing it as `bytea`.
    // That then blows up with:
    //   ERROR: function lower(bytea) does not exist
    // as soon as LOWER(...) is applied on the other side of the OR. Casting
    // fixes the parameter's type up front and avoids the issue entirely.
    @Query(
            value = "SELECT t FROM LivestockTreatment t " +
                    "LEFT JOIN FETCH t.livestock l " +
                    "LEFT JOIN FETCH t.medication m " +
                    "LEFT JOIN FETCH t.veterinarian v " +
                    "WHERE t.isDeleted = false " +
                    "AND (:status IS NULL OR t.treatmentStatus = :status) " +
                    "AND (:type IS NULL OR t.treatmentType = :type) " +
                    "AND (:livestockId IS NULL OR l.id = :livestockId) " +
                    "AND (:fromDate IS NULL OR t.treatmentDate >= :fromDate) " +
                    "AND (:toDate IS NULL OR t.treatmentDate <= :toDate) " +
                    "AND (:isPaid IS NULL OR t.isPaid = :isPaid) " +
                    "AND (:search IS NULL OR LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
                    "     OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
                    "     OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))",
            countQuery = "SELECT COUNT(t) FROM LivestockTreatment t " +
                    "LEFT JOIN t.livestock l " +
                    "LEFT JOIN t.medication m " +
                    "WHERE t.isDeleted = false " +
                    "AND (:status IS NULL OR t.treatmentStatus = :status) " +
                    "AND (:type IS NULL OR t.treatmentType = :type) " +
                    "AND (:livestockId IS NULL OR l.id = :livestockId) " +
                    "AND (:fromDate IS NULL OR t.treatmentDate >= :fromDate) " +
                    "AND (:toDate IS NULL OR t.treatmentDate <= :toDate) " +
                    "AND (:isPaid IS NULL OR t.isPaid = :isPaid) " +
                    "AND (:search IS NULL OR LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
                    "     OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
                    "     OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))"
    )
    Page<LivestockTreatment> findFiltered(
            @Param("status") LivestockTreatment.TreatmentStatus status,
            @Param("type") LivestockTreatment.TreatmentCategory type,
            @Param("livestockId") UUID livestockId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("isPaid") Boolean isPaid,
            @Param("search") String search,
            Pageable pageable
    );

    // ── Lightweight aggregate counts for the dashboard cards on the list page ──
    // Kept as separate scalar queries (rather than pulling all rows into Java)
    // so the summary cards stay O(1) round trips regardless of table size.
    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isDeleted = false " +
            "AND t.treatmentStatus = :status")
    long countByStatus(@Param("status") LivestockTreatment.TreatmentStatus status);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isDeleted = false " +
            "AND t.nextTreatmentDate IS NOT NULL AND t.nextTreatmentDate <= :cutoff")
    long countDueForFollowUp(@Param("cutoff") LocalDate cutoff);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.isDeleted = false " +
            "AND (t.isPaid = false OR t.isPaid IS NULL)")
    long countUnpaid();

    @Query("SELECT COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t " +
            "WHERE t.isDeleted = false AND t.treatmentDate >= :fromDate")
    java.math.BigDecimal sumCostSince(@Param("fromDate") LocalDate fromDate);
}