package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockBreedingRepository extends JpaRepository<LivestockBreeding, UUID> {

    // ─────────────────────────────────────────────────────────────────────
    // BASIC
    // ─────────────────────────────────────────────────────────────────────

    List<LivestockBreeding> findByLivestockId(UUID livestockId);
    Page<LivestockBreeding> findByLivestockId(UUID livestockId, Pageable pageable);
    List<LivestockBreeding> findByLivestockIdOrderByFirstBreedingDateDesc(UUID livestockId);

    // ─────────────────────────────────────────────────────────────────────
    // STATUS
    // ─────────────────────────────────────────────────────────────────────

    Page<LivestockBreeding> findByPregnancyStatus(String status, Pageable pageable);
    Page<LivestockBreeding> findByLivestockIdAndPregnancyStatus(UUID livestockId, String status, Pageable pageable);
    List<LivestockBreeding> findByPregnancyStatus(String status);
    long countByPregnancyStatus(String status);

    List<LivestockBreeding> findByStatus(String status);
    Page<LivestockBreeding> findByStatus(String status, Pageable pageable);
    Page<LivestockBreeding> findByLivestockIdAndStatus(UUID livestockId, String status, Pageable pageable);
    long countByStatus(String status);
    List<LivestockBreeding> findByStatusIn(List<String> statuses);
    List<LivestockBreeding> findByLivestockIdAndStatus(UUID livestockId, String status);

    List<LivestockBreeding> findByStatusAndExpectedDueDateBetween(String status,
                                                                  LocalDate startDate,
                                                                  LocalDate endDate);
    List<LivestockBreeding> findByBreedingDateBetween(LocalDate startDate, LocalDate endDate);

    // ─────────────────────────────────────────────────────────────────────
    // LEGACY COMPATIBILITY ALIASES
    // ─────────────────────────────────────────────────────────────────────

    default List<LivestockBreeding> findByStatusInAndIsDeletedFalse(List<String> statuses) {
        return findByStatusIn(statuses);
    }

    default Page<LivestockBreeding> findByIsDeletedFalse(Pageable pageable) {
        return findAll(pageable);
    }

    default List<LivestockBreeding> findByIsDeletedFalse() {
        return findAll();
    }

    default List<LivestockBreeding> findByLivestockIdAndIsDeletedFalse(UUID livestockId) {
        return findByLivestockId(livestockId);
    }

    default Page<LivestockBreeding> findByLivestockIdAndIsDeletedFalse(UUID livestockId, Pageable pageable) {
        return findByLivestockId(livestockId, pageable);
    }

    default Optional<LivestockBreeding> findByIdAndIsDeletedFalse(UUID id) {
        return findById(id);
    }

    default Page<LivestockBreeding> findByStatusAndIsDeletedFalse(String status, Pageable pageable) {
        return findByStatus(status, pageable);
    }

    default List<LivestockBreeding> findByStatusAndIsDeletedFalse(String status) {
        return findByStatus(status);
    }

    default Page<LivestockBreeding> findByLivestockIdAndStatusAndIsDeletedFalse(
            UUID livestockId, String status, Pageable pageable) {
        return findByLivestockIdAndStatus(livestockId, status, pageable);
    }

    default List<LivestockBreeding> findByLivestockIdAndStatusAndIsDeletedFalse(
            UUID livestockId, String status) {
        return findByLivestockIdAndStatus(livestockId, status);
    }

    default long countByStatusAndIsDeletedFalse(String status) {
        return countByStatus(status);
    }

    default long countByIsDeletedFalse() {
        return count();
    }

    default long countByPregnancyStatusAndIsDeletedFalse(String status) {
        return countByPregnancyStatus(status);
    }

    default List<LivestockBreeding> findByLivestockIdAndIsDeletedFalseOrderByFirstBreedingDateDesc(
            UUID livestockId) {
        return findByLivestockIdOrderByFirstBreedingDateDesc(livestockId);
    }

    default Optional<LivestockBreeding> findFirstByLivestockIdAndIsDeletedFalseOrderByBreedingDateDesc(
            UUID livestockId) {
        return findFirstByLivestockIdOrderByBreedingDateDesc(livestockId);
    }

    default List<LivestockBreeding> findByStatusAndExpectedDueDateBetweenAndIsDeletedFalse(
            String status, LocalDate startDate, LocalDate endDate) {
        return findByStatusAndExpectedDueDateBetween(status, startDate, endDate);
    }

    default List<LivestockBreeding> findByBreedingDateBetweenAndIsDeletedFalse(
            LocalDate startDate, LocalDate endDate) {
        return findByBreedingDateBetween(startDate, endDate);
    }

    // ─────────────────────────────────────────────────────────────────────
    // CANONICAL ACTIVE PREGNANCY
    // ─────────────────────────────────────────────────────────────────────

    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           LEFT JOIN FETCH l.livestockCategory
           WHERE b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
             AND b.expectedDueDate IS NOT NULL
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findCanonicalActivePregnancies();

    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           LEFT JOIN FETCH l.livestockCategory
           WHERE b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
             AND b.expectedDueDate IS NOT NULL
             AND b.expectedDueDate < :today
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findCanonicalOverduePregnancies(@Param("today") LocalDate today);

    // ─────────────────────────────────────────────────────────────────────
    // ACTIVE PREGNANCY HELPERS
    // ─────────────────────────────────────────────────────────────────────

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
           ORDER BY b.expectedDueDate ASC NULLS LAST
           """)
    List<LivestockBreeding> findActivePregnancies();

    @Query("SELECT COUNT(b) FROM LivestockBreeding b WHERE b.status IN ('CONFIRMED_PREGNANT','PREGNANT')")
    long countActivePregnancies();

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status IN ('CONFIRMED_PREGNANT','PREGNANT')
             AND b.expectedDueDate IS NOT NULL
             AND b.expectedDueDate BETWEEN :startDate AND :endDate
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findDueWithinDateRange(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate")   LocalDate endDate);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status IN ('CONFIRMED_PREGNANT','PREGNANT')
             AND b.expectedDueDate IS NOT NULL
             AND b.expectedDueDate < :today
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findOverduePregnancies(@Param("today") LocalDate today);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.pregnancyStatus = 'NOT_PREGNANT'
             AND b.conceptionDate IS NOT NULL
             AND b.conceptionDate < :cutoffDate
           ORDER BY b.conceptionDate ASC
           """)
    List<LivestockBreeding> findOverdueForCheck(@Param("cutoffDate") LocalDate cutoffDate);

    default List<LivestockBreeding> findOverdueForCheck() {
        return findOverdueForCheck(LocalDate.now().minusWeeks(8));
    }

    @Query("SELECT AVG(b.breedingAttempt) FROM LivestockBreeding b WHERE b.pregnancyStatus = 'CONFIRMED'")
    Double averageAttemptsToConception();

    @Query("SELECT b.failureReason, COUNT(b) FROM LivestockBreeding b " +
            "WHERE b.pregnancyStatus = 'FAILED' AND b.failureReason IS NOT NULL GROUP BY b.failureReason")
    List<Object[]> countFailuresByReason();

    @Query("""
           SELECT COUNT(b) FROM LivestockBreeding b
           WHERE b.status IN ('CONFIRMED_PREGNANT','PREGNANT')
             AND b.expectedDueDate IS NOT NULL
             AND b.expectedDueDate BETWEEN :startDate AND :endDate
           """)
    long countDueWithinRange(@Param("startDate") LocalDate startDate,
                             @Param("endDate")   LocalDate endDate);

    @Query("SELECT b FROM LivestockBreeding b ORDER BY b.createdAt DESC")
    List<LivestockBreeding> findRecentBreedings(Pageable pageable);

    default List<LivestockBreeding> findRecentBreedings(int limit) {
        return findRecentBreedings(Pageable.ofSize(Math.max(1, limit)));
    }

    @Query("SELECT b FROM LivestockBreeding b WHERE b.male.id = :maleId")
    List<LivestockBreeding> findByMaleLivestockId(@Param("maleId") UUID maleId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.livestock.id = :livestockId
           ORDER BY b.lastBreedingDate DESC NULLS LAST, b.createdAt DESC
           """)
    List<LivestockBreeding> findMostRecentByLivestockIdList(@Param("livestockId") UUID livestockId);

    default Optional<LivestockBreeding> findMostRecentByLivestockId(UUID livestockId) {
        List<LivestockBreeding> all = findMostRecentByLivestockIdList(livestockId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    Optional<LivestockBreeding> findFirstByLivestockIdOrderByBreedingDateDesc(UUID livestockId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status = :status
             AND b.expectedPregnancyCheckDate IS NOT NULL
             AND b.expectedPregnancyCheckDate < :date
           """)
    List<LivestockBreeding> findOverduePregnancyChecks(@Param("status") String status,
                                                       @Param("date") LocalDate date);

    default List<LivestockBreeding> findOverduePregnancyChecks(LocalDate date) {
        return findOverduePregnancyChecks(LivestockBreeding.STATUS_PENDING, date);
    }

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status IN (:statuses)
             AND b.livestock.id = :livestockId
           ORDER BY b.createdAt DESC
           """)
    List<LivestockBreeding> findByLivestockIdAndStatusIn(@Param("livestockId") UUID livestockId,
                                                         @Param("statuses") List<String> statuses);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status IN (:statuses)
             AND b.livestock.id = :livestockId
           """)
    List<LivestockBreeding> findActiveByLivestockId(@Param("livestockId") UUID livestockId,
                                                    @Param("statuses") List<String> statuses);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status = 'CONFIRMED_PREGNANT'
             AND b.livestock.id = :livestockId
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findActivePregnanciesByLivestockId(@Param("livestockId") UUID livestockId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status = 'CONFIRMED_PREGNANT'
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findAllActivePregnancies();

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.status = 'CONFIRMED_PREGNANT'
             AND b.expectedDueDate BETWEEN :startDate AND :endDate
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findApproachingDueDate(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate")   LocalDate endDate);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.livestock.id = :livestockId
             AND b.status IN ('PENDING','BRED','CONFIRMED_PREGNANT')
           ORDER BY b.createdAt DESC
           """)
    Optional<LivestockBreeding> findMostRecentActiveBreeding(@Param("livestockId") UUID livestockId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE (b.status = :status OR b.pregnancyStatus = :pregnancyStatus)
           """)
    List<LivestockBreeding> findFailedOrNotPregnant(@Param("status") String status,
                                                    @Param("pregnancyStatus") String pregnancyStatus);

    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           JOIN FETCH l.livestockCategory
           WHERE b.status IN ('CONFIRMED_PREGNANT','PREGNANT')
             AND b.expectedDueDate IS NOT NULL
           """)
    List<LivestockBreeding> findActivePregnanciesWithDueDate();

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.livestock.id = :livestockId
           ORDER BY b.breedingDate DESC NULLS LAST, b.createdAt DESC
           """)
    List<LivestockBreeding> findAllByLivestockIdOrderByDateDesc(@Param("livestockId") UUID livestockId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           WHERE b.livestock.id = :livestockId
             AND b.expectedDueDate IS NOT NULL
             AND (b.status = 'CONFIRMED_PREGNANT' OR b.pregnancyStatus = 'CONFIRMED')
           ORDER BY b.expectedDueDate DESC
           """)
    List<LivestockBreeding> findConfirmedPregnanciesByLivestockId(@Param("livestockId") UUID livestockId);

    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           WHERE b.status = 'PENDING'
             AND b.breedingDate IS NOT NULL
             AND b.breedingDate < :cutoff
             AND (b.pregnancyStatus IS NULL OR b.pregnancyStatus <> 'CONFIRMED')
           ORDER BY b.breedingDate ASC
           """)
    List<LivestockBreeding> findAwaitingPregnancyConfirmation(@Param("cutoff") LocalDate cutoff);

    default List<LivestockBreeding> findAwaitingPregnancyConfirmation() {
        return findAwaitingPregnancyConfirmation(LocalDate.now().minusDays(21));
    }

    // ─────────────────────────────────────────────────────────────────────
    // ✅ BULK AGGREGATE for the abortion picker
    //
    // Given a list of animal IDs (typically ≤ 20), returns:
    //   [0] UUID livestockId
    //   [1] LocalDate minExpectedDueDate   (nullable)
    //   [2] Long      completedCount       (0 if none)
    //
    // ONE round trip. No N+1. No entity hydration.
    // ─────────────────────────────────────────────────────────────────────

    @Query("""
           SELECT b.livestock.id,
                  MIN(CASE WHEN b.expectedDueDate IS NOT NULL
                           AND (b.status = 'CONFIRMED_PREGNANT' OR b.pregnancyStatus = 'CONFIRMED')
                           THEN b.expectedDueDate END),
                  SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END)
           FROM LivestockBreeding b
           WHERE b.livestock.id IN :ids
           GROUP BY b.livestock.id
           """)
    List<Object[]> findDueAndCompletedForAnimals(@Param("ids") Collection<UUID> ids);
}