package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockBreedingRepository extends JpaRepository<LivestockBreeding, UUID> {

    // ── Paginated list — only non-deleted ────────────────────────────────────
    Page<LivestockBreeding> findByIsDeletedFalse(Pageable pageable);

    // ── By livestock ID ───────────────────────────────────────────────────────
    List<LivestockBreeding> findByLivestockIdAndIsDeletedFalse(UUID livestockId);

    // ── By livestock ID and status ────────────────────────────────────────────
    // FIXES THE ERROR: This method was missing
    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.livestock.id = :livestockId " +
            "AND b.status = :status " +
            "AND b.isDeleted = false " +
            "ORDER BY b.breedingDate DESC")
    List<LivestockBreeding> findByLivestockIdAndStatusAndIsDeletedFalse(
            @Param("livestockId") UUID livestockId,
            @Param("status") String status);

    // ── By livestock ID and status (without ordering) ────────────────────────
    List<LivestockBreeding> findByLivestockIdAndStatusAndIsDeletedFalseOrderByBreedingDateDesc(
            UUID livestockId,
            String status);

    // ── By status ─────────────────────────────────────────────────────────────
    List<LivestockBreeding> findByStatusAndIsDeletedFalse(String status);

    // ── By status and date range ─────────────────────────────────────────────
    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.status = :status " +
            "AND b.isDeleted = false " +
            "AND b.expectedDueDate BETWEEN :startDate AND :endDate " +
            "ORDER BY b.expectedDueDate ASC")
    List<LivestockBreeding> findByStatusAndExpectedDueDateBetweenAndIsDeletedFalse(
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    long countByStatusAndIsDeletedFalse(String status);

    // ── Active pregnancy for a specific animal ────────────────────────────────
    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.livestock.id   = :livestockId
             AND b.isDeleted      = false
             AND b.status         IN ('CONFIRMED_PREGNANT', 'PENDING')
             AND b.breedingDate   IS NOT NULL
           ORDER BY b.breedingDate DESC
           LIMIT 1
           """)
    Optional<LivestockBreeding> findMostRecentActiveBreeding(
            @Param("livestockId") UUID livestockId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.livestock.id    = :livestockId
             AND b.isDeleted       = false
             AND b.status          IN ('CONFIRMED_PREGNANT', 'PENDING')
             AND b.expectedDueDate IS NOT NULL
             AND b.breedingDate    IS NOT NULL
           ORDER BY b.breedingDate DESC
           LIMIT 1
           """)
    Optional<LivestockBreeding> findMostRecentActiveBreedingWithDueDate(
            @Param("livestockId") UUID livestockId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.isDeleted = false
             AND b.status    = 'PENDING'
             AND b.expectedPregnancyCheckDate < :today
           ORDER BY b.expectedPregnancyCheckDate ASC
           """)
    List<LivestockBreeding> findOverduePregnancyChecks(@Param("today") LocalDate today);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.isDeleted        = false
             AND b.status           = 'CONFIRMED_PREGNANT'
             AND b.expectedDueDate >= :from
             AND b.expectedDueDate <= :to
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findApproachingDueDate(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.isDeleted        = false
             AND b.status           = 'CONFIRMED_PREGNANT'
             AND b.expectedDueDate < :today
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findOverduePregnancies(@Param("today") LocalDate today);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.isDeleted = false
           ORDER BY b.breedingDate DESC
           LIMIT :limit
           """)
    List<LivestockBreeding> findRecentBreedings(@Param("limit") int limit);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.isDeleted = false
             AND b.status    = 'CONFIRMED_PREGNANT'
           ORDER BY b.expectedDueDate ASC NULLS LAST
           """)
    List<LivestockBreeding> findAllActivePregnancies();

    // ── Legacy methods ──────────────────────────────────────────────────────
    List<LivestockBreeding> findByLivestockId(UUID livestockId);

    // ── For BreedingPerformanceService - find by livestock ID and status list ──
    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.livestock.id = :livestockId " +
            "AND b.status IN :statuses " +
            "AND b.isDeleted = false " +
            "ORDER BY b.breedingDate DESC")
    List<LivestockBreeding> findByLivestockIdAndStatusInAndIsDeletedFalse(
            @Param("livestockId") UUID livestockId,
            @Param("statuses") List<String> statuses);

    // ── Added for BreedingPerformanceReportController — pulls records within a
    //    breeding-date range for the /livestock/breeding-performance-report page ──
    List<LivestockBreeding> findByBreedingDateBetweenAndIsDeletedFalse(
            LocalDate startDate,
            LocalDate endDate);
}