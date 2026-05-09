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

    // ── By status ─────────────────────────────────────────────────────────────
    List<LivestockBreeding> findByStatusAndIsDeletedFalse(String status);

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

    List<LivestockBreeding> findByLivestockId(UUID livestockId);
}