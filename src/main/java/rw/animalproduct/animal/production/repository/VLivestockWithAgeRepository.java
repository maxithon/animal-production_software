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

    // Find by lifecycle stage
    List<VLivestockWithAge> findByLifecycleStage(String lifecycleStage);
    Page<VLivestockWithAge> findByLifecycleStage(String lifecycleStage, Pageable pageable);

    // Find by status
    List<VLivestockWithAge> findByStatus(String status);
    Page<VLivestockWithAge> findByStatus(String status, Pageable pageable);

    // Find by category
    List<VLivestockWithAge> findByCategoryName(String categoryName);
    Page<VLivestockWithAge> findByCategoryName(String categoryName, Pageable pageable);

    // Find by gender
    List<VLivestockWithAge> findByGender(String gender);
    Page<VLivestockWithAge> findByGender(String gender, Pageable pageable);

    // Combined filters
    Page<VLivestockWithAge> findByLifecycleStageAndGender(String lifecycleStage, String gender, Pageable pageable);
    Page<VLivestockWithAge> findByLifecycleStageAndCategoryName(String lifecycleStage, String categoryName, Pageable pageable);

    // Count by lifecycle stage
    @Query("SELECT COUNT(v) FROM VLivestockWithAge v WHERE v.lifecycleStage = :stage")
    long countByLifecycleStage(@Param("stage") String stage);

    // Count by status
    @Query("SELECT COUNT(v) FROM VLivestockWithAge v WHERE v.status = :status")
    long countByStatus(@Param("status") String status);

    // Summary statistics
    @Query("SELECT v.lifecycleStage, COUNT(v) FROM VLivestockWithAge v GROUP BY v.lifecycleStage")
    List<Object[]> getLifecycleStageCounts();

    @Query("SELECT v.categoryName, COUNT(v) FROM VLivestockWithAge v GROUP BY v.categoryName")
    List<Object[]> getCategoryCounts();

    @Query("SELECT v.status, COUNT(v) FROM VLivestockWithAge v GROUP BY v.status")
    List<Object[]> getStatusCounts();

    // Age range queries
    @Query("SELECT v FROM VLivestockWithAge v WHERE v.ageInMonths BETWEEN :minMonths AND :maxMonths")
    List<VLivestockWithAge> findByAgeRange(@Param("minMonths") Integer minMonths, @Param("maxMonths") Integer maxMonths);

    // Search by tag number
    List<VLivestockWithAge> findByTagNumberContaining(String tagNumber);
    Page<VLivestockWithAge> findByTagNumberContaining(String tagNumber, Pageable pageable);

    // All active livestock with pagination
    Page<VLivestockWithAge> findAll(Pageable pageable);
}