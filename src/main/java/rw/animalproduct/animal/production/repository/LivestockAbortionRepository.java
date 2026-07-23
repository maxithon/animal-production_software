package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockAbortion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LivestockAbortionRepository extends JpaRepository<LivestockAbortion, UUID> {

    // ── All non-deleted abortions ───────────────────────────────────────
    @Query("SELECT a FROM LivestockAbortion a WHERE a.isDeleted = false OR a.isDeleted IS NULL ORDER BY a.abortionDate DESC")
    List<LivestockAbortion> findByIsDeletedFalseOrderByAbortionDateDesc();

    // ── By livestock ────────────────────────────────────────────────────
    @Query("SELECT a FROM LivestockAbortion a WHERE a.livestock.id = :livestockId AND (a.isDeleted = false OR a.isDeleted IS NULL) ORDER BY a.abortionDate DESC")
    List<LivestockAbortion> findByLivestockIdAndIsDeletedFalseOrderByAbortionDateDesc(@Param("livestockId") UUID livestockId);

    // ── Count by livestock ──────────────────────────────────────────────
    @Query("SELECT COUNT(a) FROM LivestockAbortion a WHERE a.livestock.id = :livestockId AND (a.isDeleted = false OR a.isDeleted IS NULL)")
    long countByLivestockIdAndIsDeletedFalse(@Param("livestockId") UUID livestockId);

    // ── Abortions in a date range ──────────────────────────────────────
    @Query("""
           SELECT a FROM LivestockAbortion a
           WHERE (a.isDeleted = false OR a.isDeleted IS NULL)
             AND a.abortionDate >= :from
             AND a.abortionDate <= :to
           ORDER BY a.abortionDate DESC
           """)
    List<LivestockAbortion> findByAbortionDateBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // ── Count abortions this month ─────────────────────────────────────
    @Query("""
           SELECT COUNT(a) FROM LivestockAbortion a
           WHERE (a.isDeleted = false OR a.isDeleted IS NULL)
             AND YEAR(a.abortionDate) = YEAR(CURRENT_DATE)
             AND MONTH(a.abortionDate) = MONTH(CURRENT_DATE)
           """)
    long countAbortionsThisMonth();

    // ── Find by stage of pregnancy ──────────────────────────────────────
    @Query("SELECT a FROM LivestockAbortion a WHERE a.stageOfPregnancy = :stage AND (a.isDeleted = false OR a.isDeleted IS NULL)")
    List<LivestockAbortion> findByStageOfPregnancyAndIsDeletedFalse(@Param("stage") String stage);

    // ── Find by reason containing text ──────────────────────────────────
    @Query("""
           SELECT a FROM LivestockAbortion a
           WHERE (a.isDeleted = false OR a.isDeleted IS NULL)
             AND LOWER(a.abortionReason) LIKE LOWER(CONCAT('%', :keyword, '%'))
           """)
    List<LivestockAbortion> findByReasonContaining(@Param("keyword") String keyword);
}