package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockDeathRepository extends JpaRepository<LivestockDeath, UUID> {

    List<LivestockDeath> findByLivestockId(UUID livestockId);

    // ========== NEW ENHANCEMENTS FOR DASHBOARD ==========

    /**
     * Get total deaths count
     */
    long count();

    /**
     * Get deaths in date range
     */
    List<LivestockDeath> findByDeathDateBetween(LocalDate start, LocalDate end);

    /**
     * Count deaths by month/year
     */
    @Query("SELECT COUNT(d) FROM LivestockDeath d " +
            "WHERE YEAR(d.deathDate) = :year AND MONTH(d.deathDate) = :month")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Get death causes distribution
     */
    @Query("SELECT d.causeOfDeath, COUNT(d) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != '' " +
            "GROUP BY d.causeOfDeath ORDER BY COUNT(d) DESC")
    List<Object[]> getDeathCausesDistribution();

    /**
     * Get most common cause of death
     */
    @Query("SELECT d.causeOfDeath, COUNT(d) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != '' " +
            "GROUP BY d.causeOfDeath ORDER BY COUNT(d) DESC")
    List<Object[]> findMostCommonCause();

    /**
     * Count distinct death causes
     */
    @Query("SELECT COUNT(DISTINCT d.causeOfDeath) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != ''")
    long countDistinctCauses();

    /**
     * Get mortality rate by category
     */
    @Query("SELECT d.livestock.livestockCategory.name, COUNT(d) " +
            "FROM LivestockDeath d " +
            "WHERE d.livestock.livestockCategory IS NOT NULL " +
            "GROUP BY d.livestock.livestockCategory.name")
    List<Object[]> getDeathsByCategory();

    /**
     * Get recent deaths
     */
    List<LivestockDeath> findTop50ByOrderByDeathDateDesc();
}
