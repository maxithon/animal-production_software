package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.VLivestockWithAge;

import java.util.List;
import java.util.UUID;

public interface VLivestockWithAgeRepository extends JpaRepository<VLivestockWithAge, UUID> {

    // ---- Existing finders (kept) ----
    List<VLivestockWithAge> findByLifecycleStage(String lifecycleStage);
    Page<VLivestockWithAge> findByLifecycleStage(String lifecycleStage, Pageable pageable);

    List<VLivestockWithAge> findByStatus(String status);
    Page<VLivestockWithAge> findByStatus(String status, Pageable pageable);

    List<VLivestockWithAge> findByCategoryName(String categoryName);
    Page<VLivestockWithAge> findByCategoryName(String categoryName, Pageable pageable);

    List<VLivestockWithAge> findByGender(String gender);
    Page<VLivestockWithAge> findByGender(String gender, Pageable pageable);

    Page<VLivestockWithAge> findByLifecycleStageAndGender(String lifecycleStage, String gender, Pageable pageable);
    Page<VLivestockWithAge> findByLifecycleStageAndCategoryName(String lifecycleStage, String categoryName, Pageable pageable);

    // ---- Existing counts ----
    @Query("SELECT COUNT(v) FROM VLivestockWithAge v WHERE v.lifecycleStage = :stage")
    long countByLifecycleStage(@Param("stage") String stage);

    @Query("SELECT COUNT(v) FROM VLivestockWithAge v WHERE v.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT v.lifecycleStage, COUNT(v) FROM VLivestockWithAge v GROUP BY v.lifecycleStage")
    List<Object[]> getLifecycleStageCounts();

    @Query("SELECT v.categoryName, COUNT(v) FROM VLivestockWithAge v GROUP BY v.categoryName")
    List<Object[]> getCategoryCounts();

    @Query("SELECT v.status, COUNT(v) FROM VLivestockWithAge v GROUP BY v.status")
    List<Object[]> getStatusCounts();

    // ---- Age range ----
    @Query("SELECT v FROM VLivestockWithAge v WHERE v.ageInMonths BETWEEN :minMonths AND :maxMonths")
    List<VLivestockWithAge> findByAgeRange(@Param("minMonths") Integer minMonths,
                                           @Param("maxMonths") Integer maxMonths);

    // ---- Search ----
    List<VLivestockWithAge> findByTagNumberContaining(String tagNumber);
    Page<VLivestockWithAge> findByTagNumberContaining(String tagNumber, Pageable pageable);

    // ---- All ----
    Page<VLivestockWithAge> findAll(Pageable pageable);

    // ═══════════════════════════════════════════════════════════════
    // NEW: combined dynamic filtering (all filters optional)
    // Only non-deleted rows are returned.
    // Pass NULL (or 'ALL') for any filter you don't want applied.
    // ═══════════════════════════════════════════════════════════════
    @Query("""
        SELECT v FROM VLivestockWithAge v
        WHERE (v.isDeleted IS NULL OR v.isDeleted = false)
          AND (:stage IS NULL OR :stage = 'ALL' OR v.lifecycleStage = :stage)
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
}