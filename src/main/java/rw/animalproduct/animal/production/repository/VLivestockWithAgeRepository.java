package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.VLivestockWithAge;

import java.util.List;
import java.util.UUID;

/**
 * Read-only repository over {@code v_livestock_with_age}.
 *
 * <p>No soft-delete column exists on {@code livestock}. "Removed" animals are
 * represented by {@code status} in {@code SOLD | DEAD | TRANSFERRED}.
 * Pass {@code status = "ACTIVE"} to filter to live animals.
 */
public interface VLivestockWithAgeRepository
        extends JpaRepository<VLivestockWithAge, UUID> {

    // ── Simple finders ───────────────────────────────────────────────────
    List<VLivestockWithAge> findByLifecycleStage(String lifecycleStage);
    Page<VLivestockWithAge> findByLifecycleStage(String lifecycleStage, Pageable pageable);

    List<VLivestockWithAge> findByStatus(String status);
    Page<VLivestockWithAge> findByStatus(String status, Pageable pageable);

    List<VLivestockWithAge> findByCategoryName(String categoryName);
    Page<VLivestockWithAge> findByCategoryName(String categoryName, Pageable pageable);

    List<VLivestockWithAge> findByGender(String gender);
    Page<VLivestockWithAge> findByGender(String gender, Pageable pageable);

    Page<VLivestockWithAge> findByLifecycleStageAndGender(
            String lifecycleStage, String gender, Pageable pageable);

    Page<VLivestockWithAge> findByLifecycleStageAndCategoryName(
            String lifecycleStage, String categoryName, Pageable pageable);

    Page<VLivestockWithAge> findByTagNumberContaining(String tagNumber, Pageable pageable);

    // ── Counts ───────────────────────────────────────────────────────────
    @Query("SELECT COUNT(v) FROM VLivestockWithAge v WHERE v.lifecycleStage = :stage")
    long countByLifecycleStage(@Param("stage") String stage);

    @Query("SELECT COUNT(v) FROM VLivestockWithAge v WHERE v.status = :status")
    long countByStatus(@Param("status") String status);

    // ── Group-by stats (used by dashboards) ──────────────────────────────
    @Query("SELECT v.lifecycleStage, COUNT(v) FROM VLivestockWithAge v " +
            "WHERE v.lifecycleStage IS NOT NULL GROUP BY v.lifecycleStage")
    List<Object[]> getLifecycleStageCounts();

    @Query("SELECT v.categoryName, COUNT(v) FROM VLivestockWithAge v " +
            "WHERE v.categoryName IS NOT NULL GROUP BY v.categoryName")
    List<Object[]> getCategoryCounts();

    @Query("SELECT v.status, COUNT(v) FROM VLivestockWithAge v " +
            "WHERE v.status IS NOT NULL GROUP BY v.status")
    List<Object[]> getStatusCounts();

    @Query("SELECT v.healthStatus, COUNT(v) FROM VLivestockWithAge v " +
            "WHERE v.healthStatus IS NOT NULL GROUP BY v.healthStatus")
    List<Object[]> getHealthStatusCounts();

    @Query("SELECT v.reproductiveStatus, COUNT(v) FROM VLivestockWithAge v " +
            "WHERE v.reproductiveStatus IS NOT NULL GROUP BY v.reproductiveStatus")
    List<Object[]> getReproductiveStatusCounts();

    // ── Age range ────────────────────────────────────────────────────────
    @Query("SELECT v FROM VLivestockWithAge v " +
            "WHERE v.ageInMonths BETWEEN :minMonths AND :maxMonths")
    List<VLivestockWithAge> findByAgeRange(@Param("minMonths") Integer minMonths,
                                           @Param("maxMonths") Integer maxMonths);

    // ── Combined dynamic filtering ───────────────────────────────────────
    /**
     * Returns a page of livestock matching the supplied filters.
     * Pass {@code null} or the literal {@code "ALL"} to disable a filter.
     *
     * <p>There is <strong>no soft-delete clause</strong> — if you want only
     * live animals, pass {@code status = "ACTIVE"}.
     */
    @Query("""
        SELECT v FROM VLivestockWithAge v
        WHERE (:stage IS NULL OR :stage = 'ALL' OR v.lifecycleStage = :stage)
          AND (:status IS NULL OR :status = 'ALL' OR v.status = :status)
          AND (:category IS NULL OR :category = 'ALL' OR v.categoryName = :category)
          AND (:gender IS NULL OR :gender = '' OR v.gender = :gender)
          AND (:search IS NULL OR :search = ''
               OR LOWER(v.tagNumber) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<VLivestockWithAge> findFiltered(
            @Param("stage")    String stage,
            @Param("status")   String status,
            @Param("category") String category,
            @Param("gender")   String gender,
            @Param("search")   String search,
            Pageable pageable);

    // ── Convenience: active animals only ─────────────────────────────────
    @Query("""
        SELECT v FROM VLivestockWithAge v
        WHERE v.status = 'ACTIVE'
          AND (:stage IS NULL OR :stage = 'ALL' OR v.lifecycleStage = :stage)
          AND (:category IS NULL OR :category = 'ALL' OR v.categoryName = :category)
          AND (:gender IS NULL OR :gender = '' OR v.gender = :gender)
          AND (:search IS NULL OR :search = ''
               OR LOWER(v.tagNumber) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<VLivestockWithAge> findActive(
            @Param("stage")    String stage,
            @Param("category") String category,
            @Param("gender")   String gender,
            @Param("search")   String search,
            Pageable pageable);
}