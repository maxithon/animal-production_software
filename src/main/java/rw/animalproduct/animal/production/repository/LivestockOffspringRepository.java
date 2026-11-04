package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockOffspring;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface LivestockOffspringRepository extends JpaRepository<LivestockOffspring, UUID> {

    // ══════════════════════════════════════════════════════════════════════
    // READ — by birth event
    // ══════════════════════════════════════════════════════════════════════

    List<LivestockOffspring> findByBirthEventId(UUID birthEventId);

    @Query("""
           SELECT o FROM LivestockOffspring o
           WHERE o.birthEvent.id = :birthEventId
             AND o.isDeleted = false
           """)
    List<LivestockOffspring> findByBirthEventIdAndIsDeletedFalse(@Param("birthEventId") UUID birthEventId);

    @Query("""
           SELECT o FROM LivestockOffspring o
           JOIN FETCH o.childLivestock c
           WHERE o.birthEvent.id = :birthEventId
             AND o.isDeleted = false
           ORDER BY c.tagNumber
           """)
    List<LivestockOffspring> findActiveByBirthEventIdOrdered(@Param("birthEventId") UUID birthEventId);

    @Query("""
           SELECT o FROM LivestockOffspring o
           JOIN FETCH o.childLivestock c
           LEFT JOIN FETCH c.livestockCategory
           LEFT JOIN FETCH c.mother
           LEFT JOIN FETCH c.father
           WHERE o.birthEvent.id = :birthEventId
             AND o.isDeleted = false
           ORDER BY c.tagNumber
           """)
    List<LivestockOffspring> findActiveWithChildByBirthEventId(@Param("birthEventId") UUID birthEventId);

    // ══════════════════════════════════════════════════════════════════════
    // READ — by child
    //
    // NOTE: `uq_child UNIQUE (child_livestock_id)` on livestock_offspring
    // guarantees at most ONE row per child — so a List return type is
    // defensive; callers can rely on at-most-one non-deleted row.
    // ══════════════════════════════════════════════════════════════════════

    @Query("""
           SELECT o FROM LivestockOffspring o
           WHERE o.childLivestock.id = :childLivestockId
             AND o.isDeleted = false
           """)
    List<LivestockOffspring> findByChildLivestockIdAndIsDeletedFalse(@Param("childLivestockId") UUID childLivestockId);

    @Query("""
           SELECT o FROM LivestockOffspring o
           WHERE o.childLivestock.id = :childLivestockId
             AND o.isDeleted = false
           """)
    Optional<LivestockOffspring> findFirstActiveByChildLivestockId(@Param("childLivestockId") UUID childLivestockId);

    @Query("""
           SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
           FROM LivestockOffspring o
           WHERE o.childLivestock.id = :childLivestockId
             AND o.isDeleted = false
           """)
    boolean existsActiveByChildLivestockId(@Param("childLivestockId") UUID childLivestockId);

    @Query("""
           SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
           FROM LivestockOffspring o
           WHERE o.childLivestock.id = :childLivestockId
             AND o.birthEvent.id    = :birthEventId
             AND o.isDeleted = false
           """)
    boolean existsActiveByChildAndBirth(@Param("childLivestockId") UUID childLivestockId,
                                        @Param("birthEventId") UUID birthEventId);

    /** Every non-deleted link for a child (used by unlink to clean up). */
    @Query("""
           SELECT o FROM LivestockOffspring o
           WHERE o.childLivestock.id = :childLivestockId
           """)
    List<LivestockOffspring> findAllByChildLivestockId(@Param("childLivestockId") UUID childLivestockId);

    // ══════════════════════════════════════════════════════════════════════
    // AGGREGATE LOOKUPS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Every child livestock id currently linked (non-deleted) to any birth.
     * Replaces the old "load all births + all offspring" loop.
     * Because of `uq_child`, each child appears at most once — but
     * findLinkedChildIds is queried against non-deleted rows only, so a
     * soft-deleted link frees the child for re-linking.
     */
    @Query("""
           SELECT o.childLivestock.id FROM LivestockOffspring o
           WHERE o.isDeleted = false
             AND o.childLivestock IS NOT NULL
           """)
    Set<UUID> findLinkedChildIds();

    @Query("""
           SELECT o.childLivestock.id FROM LivestockOffspring o
           WHERE o.isDeleted = false
             AND o.birthEvent.id = :birthEventId
           """)
    Set<UUID> findLinkedChildIdsByBirthEventId(@Param("birthEventId") UUID birthEventId);

    // ══════════════════════════════════════════════════════════════════════
    // COUNTS
    // ══════════════════════════════════════════════════════════════════════

    @Query("""
           SELECT COUNT(o) FROM LivestockOffspring o
           WHERE o.birthEvent.id = :birthEventId
             AND o.isDeleted = false
             AND o.isAlive = true
           """)
    long countLiveOffspringByBirthEventId(@Param("birthEventId") UUID birthEventId);

    @Query("""
           SELECT COUNT(o) FROM LivestockOffspring o
           WHERE o.birthEvent.id = :birthEventId
             AND o.isDeleted = false
             AND o.isAlive = false
           """)
    long countStillbornByBirthEventId(@Param("birthEventId") UUID birthEventId);

    @Query("""
           SELECT COUNT(o) FROM LivestockOffspring o
           WHERE o.birthEvent.id = :birthEventId
             AND o.isDeleted = false
           """)
    long countActiveByBirthEventId(@Param("birthEventId") UUID birthEventId);

    @Query("""
           SELECT COUNT(o) FROM LivestockOffspring o
           JOIN o.childLivestock l
           WHERE o.birthEvent.id = :birthEventId
             AND o.isDeleted = false
             AND l.gender = :gender
           """)
    long countOffspringByGender(@Param("birthEventId") UUID birthEventId,
                                @Param("gender") String gender);

    @Query("""
           SELECT COUNT(o) FROM LivestockOffspring o
           WHERE o.childLivestock.id = :childLivestockId
             AND o.isDeleted = false
           """)
    long countActiveByChildLivestockId(@Param("childLivestockId") UUID childLivestockId);

    // ══════════════════════════════════════════════════════════════════════
    // MAINTENANCE / REPAIR
    // ══════════════════════════════════════════════════════════════════════

    @Query("""
           SELECT o FROM LivestockOffspring o
           JOIN FETCH o.childLivestock c
           JOIN FETCH o.birthEvent b
           WHERE o.isDeleted = false
             AND c.mother IS NULL
             AND b.livestock IS NOT NULL
           """)
    List<LivestockOffspring> findAllMissingMotherOnChild();

    @Query("""
           SELECT o FROM LivestockOffspring o
           JOIN FETCH o.childLivestock c
           JOIN FETCH o.birthEvent b
           WHERE o.isDeleted = false
             AND b.birthDate IS NOT NULL
             AND (c.birthDate IS NULL OR c.birthDate <> b.birthDate)
           """)
    List<LivestockOffspring> findAllWithMismatchedBirthDate();
}