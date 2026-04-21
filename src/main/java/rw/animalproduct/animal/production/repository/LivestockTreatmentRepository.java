package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockTreatmentRepository extends JpaRepository<LivestockTreatment, UUID> {

    // Existing methods
    List<LivestockTreatment> findByLivestock_Id(UUID livestockId);
    List<LivestockTreatment> findByLivestock_IdIn(List<UUID> ids);

    @Query("""
        SELECT t.livestock.id,
               COUNT(t),
               SUM(COALESCE(t.treatmentCost, 0))
        FROM LivestockTreatment t
        WHERE t.livestock.id IN :animalIds
        GROUP BY t.livestock.id
    """)
    List<Object[]> treatmentStatsByAnimalIds(@Param("animalIds") List<UUID> animalIds);

    @Query("""
        SELECT COALESCE(SUM(t.treatmentCost), 0)
        FROM LivestockTreatment t
        WHERE t.livestock.id IN :animalIds
    """)
    BigDecimal totalTreatmentCostByAnimalIds(@Param("animalIds") List<UUID> animalIds);

    List<LivestockTreatment> findBySickLivestockIdIn(List<UUID> sickIds);
    List<LivestockTreatment> findBySickLivestockId(UUID sickId);

    // ========== NEW ENHANCEMENTS FOR DASHBOARD ==========

    /**
     * Get total treatment cost across ALL treatments
     */
    @Query("SELECT COALESCE(SUM(t.treatmentCost), 0) FROM LivestockTreatment t WHERE t.isDeleted = false")
    BigDecimal getTotalTreatmentCost();

    /**
     * Get total number of treatments
     */
    long countByIsDeletedFalse();

    /**
     * Get treatments in a specific date range
     */
    List<LivestockTreatment> findByTreatmentDateBetweenAndIsDeletedFalse(LocalDate start, LocalDate end);

    /**
     * Count treatments by month and year
     */
    @Query("SELECT COUNT(t) FROM LivestockTreatment t " +
            "WHERE YEAR(t.treatmentDate) = :year AND MONTH(t.treatmentDate) = :month " +
            "AND t.isDeleted = false")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Find most treated animal (by tag number)
     */
    @Query("SELECT t.livestock.tagNumber, COUNT(t) FROM LivestockTreatment t " +
            "WHERE t.livestock IS NOT NULL AND t.isDeleted = false " +
            "GROUP BY t.livestock.tagNumber ORDER BY COUNT(t) DESC")
    List<Object[]> findMostTreatedAnimal();

    /**
     * Get recent treatments for dashboard (last 100)
     */
    @Query("SELECT t FROM LivestockTreatment t WHERE t.isDeleted = false ORDER BY t.treatmentDate DESC")
    List<LivestockTreatment> findTop100ByOrderByTreatmentDateDesc(Pageable pageable);

    /**
     * Get upcoming/overdue treatments
     */
    @Query("SELECT t FROM LivestockTreatment t " +
            "WHERE t.nextTreatmentDate IS NOT NULL " +
            "AND t.nextTreatmentDate <= :date " +
            "AND t.treatmentStatus != 'COMPLETED' " +
            "AND t.isDeleted = false " +
            "ORDER BY t.nextTreatmentDate ASC")
    List<LivestockTreatment> findUpcomingTreatments(@Param("date") LocalDate date);

    /**
     * Get treatment cost grouped by category
     */
    @Query("SELECT t.treatmentType, COALESCE(SUM(t.treatmentCost), 0), COUNT(t) " +
            "FROM LivestockTreatment t " +
            "WHERE t.isDeleted = false " +
            "GROUP BY t.treatmentType")
    List<Object[]> getTreatmentCostByCategory();
}