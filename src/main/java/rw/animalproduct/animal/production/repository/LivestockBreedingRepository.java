package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.dto.FemaleCandidateProjection;
import rw.animalproduct.animal.production.dto.MaleCandidateProjection;
import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockBreedingRepository extends JpaRepository<LivestockBreeding, UUID> {

    // ════════════════════════════════════════════════════════════════════════
    // BASIC
    // ════════════════════════════════════════════════════════════════════════

    List<LivestockBreeding> findByLivestockId(UUID livestockId);

    Page<LivestockBreeding> findByIsDeletedFalse(Pageable pageable);

    List<LivestockBreeding> findByIsDeletedFalse();

    List<LivestockBreeding> findByLivestockIdAndIsDeletedFalse(UUID livestockId);

    Page<LivestockBreeding> findByLivestockIdAndIsDeletedFalse(UUID livestockId, Pageable pageable);

    List<LivestockBreeding> findByLivestockIdAndIsDeletedFalseOrderByFirstBreedingDateDesc(UUID livestockId);

    Optional<LivestockBreeding> findByIdAndIsDeletedFalse(UUID id);

    // ════════════════════════════════════════════════════════════════════════
    // STATUS
    // ════════════════════════════════════════════════════════════════════════

    Page<LivestockBreeding> findByPregnancyStatusAndIsDeletedFalse(String status, Pageable pageable);

    Page<LivestockBreeding> findByLivestockIdAndPregnancyStatusAndIsDeletedFalse(
            UUID livestockId, String status, Pageable pageable);

    List<LivestockBreeding> findByPregnancyStatusAndIsDeletedFalse(String status);

    long countByPregnancyStatusAndIsDeletedFalse(String status);

    long countByIsDeletedFalse();

    List<LivestockBreeding> findByStatusAndIsDeletedFalse(String status);

    Page<LivestockBreeding> findByStatusAndIsDeletedFalse(String status, Pageable pageable);

    long countByStatusAndIsDeletedFalse(String status);

    List<LivestockBreeding> findByStatusInAndIsDeletedFalse(List<String> statuses);

    List<LivestockBreeding> findByLivestockIdAndStatusAndIsDeletedFalse(UUID livestockId, String status);

    Page<LivestockBreeding> findByLivestockIdAndStatusAndIsDeletedFalse(
            UUID livestockId, String status, Pageable pageable);

    List<LivestockBreeding> findByStatusAndExpectedDueDateBetweenAndIsDeletedFalse(
            String status, LocalDate startDate, LocalDate endDate);

    List<LivestockBreeding> findByBreedingDateBetweenAndIsDeletedFalse(
            LocalDate startDate, LocalDate endDate);

    List<LivestockBreeding> findByFirstBreedingDateBetweenAndIsDeletedFalse(
            LocalDate startDate, LocalDate endDate);

    // ════════════════════════════════════════════════════════════════════════
    // ✅ CANONICAL ACTIVE-PREGNANCY QUERIES
    //    Use these from the dashboard, pregnancy-tracking, and alerts.
    //    They return only currently active pregnancies with a real due date,
    //    JOIN FETCH livestock + category so nothing throws LazyInitException.
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ✅ THE canonical query for "which animals are currently pregnant".
     *
     * Rules:
     *   - status = 'CONFIRMED_PREGNANT' or 'PREGNANT'
     *   - real expected_due_date on file
     *   - JOIN FETCH livestock + category
     *   - ordered by expected_due_date ascending (most urgent first)
     */
    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           LEFT JOIN FETCH l.livestockCategory
           WHERE b.isDeleted = false
             AND b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
             AND b.expectedDueDate IS NOT NULL
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findCanonicalActivePregnancies();

    /**
     * Same as findCanonicalActivePregnancies() but scoped to one animal.
     */
    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           LEFT JOIN FETCH l.livestockCategory
           WHERE b.isDeleted = false
             AND b.livestock.id = :livestockId
             AND b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
             AND b.expectedDueDate IS NOT NULL
           ORDER BY b.expectedDueDate DESC
           """)
    List<LivestockBreeding> findCanonicalActivePregnanciesByLivestockId(
            @Param("livestockId") UUID livestockId);

    /**
     * Overdue pregnancies only — active, due date strictly before today.
     */
    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           LEFT JOIN FETCH l.livestockCategory
           WHERE b.isDeleted = false
             AND b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
             AND b.expectedDueDate IS NOT NULL
             AND b.expectedDueDate < :today
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findCanonicalOverduePregnancies(@Param("today") LocalDate today);

    /**
     * Active pregnancies whose due date falls inside the given window.
     */
    @Query("""
           SELECT b FROM LivestockBreeding b
           JOIN FETCH b.livestock l
           LEFT JOIN FETCH l.livestockCategory
           WHERE b.isDeleted = false
             AND b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
             AND b.expectedDueDate IS NOT NULL
             AND b.expectedDueDate BETWEEN :startDate AND :endDate
           ORDER BY b.expectedDueDate ASC
           """)
    List<LivestockBreeding> findCanonicalPregnanciesDueBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // ════════════════════════════════════════════════════════════════════════
    // PREGNANCY — general queries (kept for backward compatibility)
    // ════════════════════════════════════════════════════════════════════════

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND (b.status = 'CONFIRMED_PREGNANT' OR b.status = 'PREGNANT') " +
            "ORDER BY b.expectedDueDate ASC NULLS LAST")
    List<LivestockBreeding> findActivePregnancies();

    @Query("SELECT COUNT(b) FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND (b.status = 'CONFIRMED_PREGNANT' OR b.status = 'PREGNANT')")
    long countActivePregnancies();

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.expectedDueDate IS NOT NULL " +
            "AND b.expectedDueDate BETWEEN :startDate AND :endDate " +
            "ORDER BY b.expectedDueDate ASC")
    List<LivestockBreeding> findDueWithinDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND (b.status = 'CONFIRMED_PREGNANT' OR b.status = 'PREGNANT') " +
            "AND b.expectedDueDate IS NOT NULL " +
            "AND b.expectedDueDate < :today " +
            "ORDER BY b.expectedDueDate ASC")
    List<LivestockBreeding> findOverduePregnancies(@Param("today") LocalDate today);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.pregnancyStatus = 'NOT_PREGNANT' " +
            "AND b.conceptionDate IS NOT NULL " +
            "AND b.conceptionDate < :cutoffDate " +
            "ORDER BY b.conceptionDate ASC")
    List<LivestockBreeding> findOverdueForCheck(@Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT COUNT(b) FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.pregnancyStatus IN ('NOT_PREGNANT', 'PREGNANT') " +
            "AND b.conceptionDate IS NOT NULL " +
            "AND b.conceptionDate < :cutoffDate")
    long countPendingChecks(@Param("cutoffDate") LocalDate cutoffDate);

    default long countPendingChecks() {
        return countPendingChecks(LocalDate.now().minusWeeks(8));
    }

    default List<LivestockBreeding> findOverdueForCheck() {
        return findOverdueForCheck(LocalDate.now().minusWeeks(8));
    }

    @Query("SELECT AVG(b.breedingAttempt) FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.pregnancyStatus = 'CONFIRMED'")
    Double averageAttemptsToConception();

    @Query("SELECT b.failureReason, COUNT(b) FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.pregnancyStatus = 'FAILED' " +
            "AND b.failureReason IS NOT NULL " +
            "GROUP BY b.failureReason")
    List<Object[]> countFailuresByReason();

    @Query("SELECT COUNT(b) FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND (b.status = 'CONFIRMED_PREGNANT' OR b.status = 'PREGNANT') " +
            "AND b.expectedDueDate IS NOT NULL " +
            "AND b.expectedDueDate BETWEEN :startDate AND :endDate")
    long countDueWithinRange(@Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate);

    // ✅ LIMIT inside JPQL is illegal — replaced with Pageable
    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "ORDER BY b.createdAt DESC")
    List<LivestockBreeding> findRecentBreedings(Pageable pageable);

    default List<LivestockBreeding> findRecentBreedings(int limit) {
        return findRecentBreedings(Pageable.ofSize(Math.max(1, limit)));
    }

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.male.id = :maleId")
    List<LivestockBreeding> findByMaleLivestockId(@Param("maleId") UUID maleId);

    // ✅ LIMIT 1 removed from JPQL — default method wraps a list-returning query
    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.livestock.id = :livestockId " +
            "AND b.isDeleted = false " +
            "ORDER BY b.lastBreedingDate DESC NULLS LAST, b.createdAt DESC")
    List<LivestockBreeding> findMostRecentByLivestockIdList(@Param("livestockId") UUID livestockId);

    default Optional<LivestockBreeding> findMostRecentByLivestockId(UUID livestockId) {
        List<LivestockBreeding> all = findMostRecentByLivestockIdList(livestockId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    Optional<LivestockBreeding> findFirstByLivestockIdAndIsDeletedFalseOrderByBreedingDateDesc(UUID livestockId);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.status = :status " +
            "AND b.expectedPregnancyCheckDate IS NOT NULL " +
            "AND b.expectedPregnancyCheckDate < :date")
    List<LivestockBreeding> findOverduePregnancyChecks(
            @Param("status") String status, @Param("date") LocalDate date);

    default List<LivestockBreeding> findOverduePregnancyChecks(LocalDate date) {
        return findOverduePregnancyChecks(LivestockBreeding.STATUS_PENDING, date);
    }

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.status IN (:statuses) " +
            "AND b.livestock.id = :livestockId " +
            "ORDER BY b.createdAt DESC")
    List<LivestockBreeding> findByLivestockIdAndStatusIn(
            @Param("livestockId") UUID livestockId,
            @Param("statuses") List<String> statuses);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.status IN (:statuses) " +
            "AND b.livestock.id = :livestockId")
    List<LivestockBreeding> findActiveByLivestockId(
            @Param("livestockId") UUID livestockId,
            @Param("statuses") List<String> statuses);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.status = :status " +
            "AND b.livestock.id = :livestockId")
    List<LivestockBreeding> findByLivestockIdAndStatus(
            @Param("livestockId") UUID livestockId,
            @Param("status") String status);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.status = 'CONFIRMED_PREGNANT' " +
            "AND b.livestock.id = :livestockId " +
            "ORDER BY b.expectedDueDate ASC")
    List<LivestockBreeding> findActivePregnanciesByLivestockId(@Param("livestockId") UUID livestockId);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.livestock.id = :livestockId " +
            "ORDER BY b.createdAt DESC")
    List<LivestockBreeding> findRecentByLivestockId(@Param("livestockId") UUID livestockId);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.livestock.id = :livestockId " +
            "AND b.expectedDueDate IS NOT NULL " +
            "AND b.expectedDueDate < :date " +
            "AND b.status = 'CONFIRMED_PREGNANT'")
    List<LivestockBreeding> findOverduePregnanciesByLivestockId(
            @Param("livestockId") UUID livestockId,
            @Param("date") LocalDate date);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.livestock.id = :livestockId " +
            "AND b.expectedDueDate IS NOT NULL " +
            "AND b.expectedDueDate BETWEEN :startDate AND :endDate " +
            "AND b.status = 'CONFIRMED_PREGNANT'")
    List<LivestockBreeding> findDueSoonByLivestockId(
            @Param("livestockId") UUID livestockId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.status = 'CONFIRMED_PREGNANT' " +
            "ORDER BY b.expectedDueDate ASC")
    List<LivestockBreeding> findAllActivePregnancies();

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.status = 'CONFIRMED_PREGNANT' " +
            "AND b.expectedDueDate BETWEEN :startDate AND :endDate " +
            "ORDER BY b.expectedDueDate ASC")
    List<LivestockBreeding> findApproachingDueDate(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND b.livestock.id = :livestockId " +
            "AND b.status IN ('PENDING', 'BRED', 'CONFIRMED_PREGNANT') " +
            "ORDER BY b.createdAt DESC")
    Optional<LivestockBreeding> findMostRecentActiveBreeding(@Param("livestockId") UUID livestockId);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.isDeleted = false " +
            "AND (b.status = :status OR b.pregnancyStatus = :pregnancyStatus)")
    List<LivestockBreeding> findFailedOrNotPregnant(
            @Param("status") String status,
            @Param("pregnancyStatus") String pregnancyStatus);

    // ✅ Removed the `AND b.isActive = true` filter — that column isn't reliably
    //    set on every row, and the status/isDeleted check is enough.
    @Query("SELECT b FROM LivestockBreeding b " +
            "JOIN FETCH b.livestock l " +
            "JOIN FETCH l.livestockCategory " +
            "WHERE b.status IN ('CONFIRMED_PREGNANT','PREGNANT') " +
            "AND b.expectedDueDate IS NOT NULL " +
            "AND b.isDeleted = false")
    List<LivestockBreeding> findActivePregnanciesWithDueDate();

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.livestock.id = :livestockId " +
            "AND (b.isDeleted = false OR b.isDeleted IS NULL) " +
            "ORDER BY b.breedingDate DESC NULLS LAST, b.createdAt DESC")
    List<LivestockBreeding> findAllByLivestockIdOrderByDateDesc(@Param("livestockId") UUID livestockId);

    @Query("SELECT b FROM LivestockBreeding b " +
            "WHERE b.livestock.id = :livestockId " +
            "AND (b.isDeleted = false OR b.isDeleted IS NULL) " +
            "AND b.expectedDueDate IS NOT NULL " +
            "AND (b.status = 'CONFIRMED_PREGNANT' OR b.pregnancyStatus = 'CONFIRMED') " +
            "ORDER BY b.expectedDueDate DESC")
    List<LivestockBreeding> findConfirmedPregnanciesByLivestockId(@Param("livestockId") UUID livestockId);

    // ════════════════════════════════════════════════════════════════════════
    // PREGNANCY CHECK WORKLIST
    // ════════════════════════════════════════════════════════════════════════

    @Query("SELECT b FROM LivestockBreeding b " +
            "JOIN FETCH b.livestock l " +
            "WHERE b.isDeleted = false " +
            "AND b.status = 'PENDING' " +
            "AND b.breedingDate IS NOT NULL " +
            "AND b.breedingDate < :cutoff " +
            "AND (b.pregnancyStatus IS NULL OR b.pregnancyStatus <> 'CONFIRMED') " +
            "ORDER BY b.breedingDate ASC")
    List<LivestockBreeding> findAwaitingPregnancyConfirmation(@Param("cutoff") LocalDate cutoff);

    default List<LivestockBreeding> findAwaitingPregnancyConfirmation() {
        return findAwaitingPregnancyConfirmation(LocalDate.now().minusDays(21));
    }

    // ════════════════════════════════════════════════════════════════════════
    // CANDIDATE PICKERS (native SQL)
    // ════════════════════════════════════════════════════════════════════════

    // ✅ Changed CAST(:categoryId AS uuid) → livestock_category_id::text = :categoryId
    //    and added `:categoryId = ''` guard — avoids Postgres crash on empty string.
    @Query(value = """
           SELECT
             l.id AS id,
             l.tag_number AS tagNumber,
             CAST(EXTRACT(YEAR FROM AGE(CURRENT_DATE, l.birth_date)) * 12
                  + EXTRACT(MONTH FROM AGE(CURRENT_DATE, l.birth_date)) AS INTEGER) AS ageMonths,
             l.health_status AS healthStatus,
             l.reproductive_status AS reproductiveStatus,
             (SELECT COUNT(*) FROM livestock_breeding b
                WHERE b.livestock_id = l.id AND b.is_deleted = false) AS totalBreedings,
             (SELECT COUNT(*) FROM livestock_breeding b
                WHERE b.livestock_id = l.id AND b.is_deleted = false
                  AND b.status = 'CONFIRMED_PREGNANT') AS successfulBreedings,
             (SELECT MAX(lb.birth_date) FROM livestock_births lb
                WHERE lb.livestock_id = l.id AND lb.is_deleted = false) AS lastBirthDate,
             l.livestock_category_id AS categoryId,
             lc.name AS categoryName,
             l.gender AS gender
           FROM livestock l
           LEFT JOIN livestock_categories lc ON lc.id = l.livestock_category_id
           WHERE l.gender = 'FEMALE'
             AND l.status = 'ACTIVE'
             AND (l.is_deleted = false OR l.is_deleted IS NULL)
             AND l.is_draft = false
             AND (l.reproductive_status IS NULL
                  OR l.reproductive_status NOT IN ('BRED', 'PREGNANT'))
             AND (:categoryId IS NULL OR :categoryId = ''
                  OR l.livestock_category_id::text = :categoryId)
             AND (:q IS NULL OR :q = ''
                  OR LOWER(l.tag_number) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY l.tag_number ASC
           """,
            countQuery = """
           SELECT COUNT(*)
           FROM livestock l
           WHERE l.gender = 'FEMALE'
             AND l.status = 'ACTIVE'
             AND (l.is_deleted = false OR l.is_deleted IS NULL)
             AND l.is_draft = false
             AND (l.reproductive_status IS NULL
                  OR l.reproductive_status NOT IN ('BRED', 'PREGNANT'))
             AND (:categoryId IS NULL OR :categoryId = ''
                  OR l.livestock_category_id::text = :categoryId)
             AND (:q IS NULL OR :q = ''
                  OR LOWER(l.tag_number) LIKE LOWER(CONCAT('%', :q, '%')))
           """,
            nativeQuery = true)
    Page<FemaleCandidateProjection> findFemaleCandidates(
            @Param("q") String q,
            @Param("categoryId") String categoryId,
            Pageable pageable);

    @Query(value = """
           SELECT
             l.id AS id,
             l.tag_number AS tagNumber,
             CAST(EXTRACT(YEAR FROM AGE(CURRENT_DATE, l.birth_date)) * 12
                  + EXTRACT(MONTH FROM AGE(CURRENT_DATE, l.birth_date)) AS INTEGER) AS ageMonths,
             l.health_status AS healthStatus,
             (SELECT COUNT(*) FROM livestock_breeding b
                WHERE b.male_id = l.id AND b.is_deleted = false) AS totalBreedings,
             (SELECT COUNT(*) FROM livestock_breeding b
                WHERE b.male_id = l.id AND b.is_deleted = false
                  AND b.status = 'CONFIRMED_PREGNANT') AS successfulBreedings,
             l.livestock_category_id AS categoryId,
             lc.name AS categoryName,
             l.gender AS gender
           FROM livestock l
           LEFT JOIN livestock_categories lc ON lc.id = l.livestock_category_id
           WHERE l.gender = 'MALE'
             AND l.status = 'ACTIVE'
             AND l.health_status = 'HEALTHY'
             AND (l.is_deleted = false OR l.is_deleted IS NULL)
             AND l.is_draft = false
             AND (:categoryId IS NULL OR :categoryId = ''
                  OR l.livestock_category_id::text = :categoryId)
             AND (:q IS NULL OR :q = ''
                  OR LOWER(l.tag_number) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY l.tag_number ASC
           """,
            countQuery = """
           SELECT COUNT(*)
           FROM livestock l
           WHERE l.gender = 'MALE'
             AND l.status = 'ACTIVE'
             AND l.health_status = 'HEALTHY'
             AND (l.is_deleted = false OR l.is_deleted IS NULL)
             AND l.is_draft = false
             AND (:categoryId IS NULL OR :categoryId = ''
                  OR l.livestock_category_id::text = :categoryId)
             AND (:q IS NULL OR :q = ''
                  OR LOWER(l.tag_number) LIKE LOWER(CONCAT('%', :q, '%')))
           """,
            nativeQuery = true)
    Page<MaleCandidateProjection> findMaleCandidates(
            @Param("q") String q,
            @Param("categoryId") String categoryId,
            Pageable pageable);
}