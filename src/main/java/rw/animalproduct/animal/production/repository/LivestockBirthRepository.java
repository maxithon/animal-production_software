package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockBirth;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockBirthRepository extends JpaRepository<LivestockBirth, UUID> {

    // ── Paginated list — only non-deleted ────────────────────────────────────
    Page<LivestockBirth> findByIsDeletedFalseOrderByBirthDateDesc(Pageable pageable);

    // ── All non-deleted ───────────────────────────────────────────────────────
    List<LivestockBirth> findByIsDeletedFalseOrderByBirthDateDesc();

    // ── By mother ─────────────────────────────────────────────────────────────
    List<LivestockBirth> findByLivestockIdAndIsDeletedFalse(UUID livestockId);

    // ── Legacy — kept for backward compat ────────────────────────────────────
    List<LivestockBirth> findByLivestockId(UUID livestockId);

    // ── ✅ ADDED — PERFORMANCE FIX: batched lookup for the livestock list page ──
    // LivestockController.list() used to build its birthMap by calling
    // findByLivestockIdAndIsDeletedFalse() once PER ROW on the current page
    // (an N+1 query problem — a 25-row page cost 25 extra round trips to the
    // database). This single query fetches every matching birth record for a
    // whole page of animal IDs in one round trip; LivestockBirthService then
    // groups the results into a Map in memory. Filters isDeleted = false to
    // match the exact behavior of findByLivestockIdAndIsDeletedFalse, since
    // that's the method the old per-row loop relied on.
    @Query("SELECT b FROM LivestockBirth b WHERE b.livestockId IN :livestockIds AND b.isDeleted = false")
    List<LivestockBirth> findByLivestockIdInAndIsDeletedFalse(@Param("livestockIds") List<UUID> livestockIds);

    // ── Find birth event for a child animal ──────────────────────────────────
    @Query("""
           SELECT b FROM LivestockBirth b
           JOIN b.children o
           WHERE o.childLivestock.id = :animalId
             AND b.isDeleted = false
           """)
    Optional<LivestockBirth> findByChildAnimalId(@Param("animalId") UUID animalId);

    // ── Births in a date range ────────────────────────────────────────────────
    @Query("""
           SELECT b FROM LivestockBirth b
           WHERE b.isDeleted  = false
             AND b.birthDate >= :from
             AND b.birthDate <= :to
           ORDER BY b.birthDate DESC
           """)
    List<LivestockBirth> findByBirthDateBetween(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);

    // ── Births in a date range with isDeleted flag ─────────────────────────────
    List<LivestockBirth> findByBirthDateBetweenAndIsDeletedFalse(
            LocalDate startDate,
            LocalDate endDate);

    // ── Count births for a mother ─────────────────────────────────────────────
    long countByLivestockIdAndIsDeletedFalse(UUID livestockId);

    // ── Births ordered by birth date ascending for calving interval ──────────
    List<LivestockBirth> findByLivestockIdAndIsDeletedFalseOrderByBirthDateAsc(UUID livestockId);

    // ── Births with breeding relationship loaded (for performance) ───────────
    @Query("""
           SELECT b FROM LivestockBirth b
           LEFT JOIN FETCH b.breeding
           WHERE b.livestockId = :livestockId
             AND b.isDeleted = false
           ORDER BY b.birthDate ASC
           """)
    List<LivestockBirth> findByLivestockIdWithBreeding(@Param("livestockId") UUID livestockId);
}
