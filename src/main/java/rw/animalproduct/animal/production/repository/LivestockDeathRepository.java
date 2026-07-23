package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockDeathRepository extends JpaRepository<LivestockDeath, UUID> {

    List<LivestockDeath> findByLivestockId(UUID livestockId);

    // ========== PAGINATION SUPPORT ==========

    /**
     * Get paginated deaths (excludes soft-deleted)
     */
    @Query("SELECT d FROM LivestockDeath d WHERE d.isDeleted = false OR d.isDeleted IS NULL")
    Page<LivestockDeath> findAllActive(Pageable pageable);

    /**
     * Get paginated deaths with search
     */
    @Query("SELECT d FROM LivestockDeath d " +
            "WHERE (d.isDeleted = false OR d.isDeleted IS NULL) " +
            "AND (LOWER(d.livestock.tagNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(d.causeOfDeath) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LivestockDeath> searchDeaths(@Param("search") String search, Pageable pageable);

    // ========== EXISTING METHODS ==========

    /**
     * Get total deaths count (active only)
     */
    @Query("SELECT COUNT(d) FROM LivestockDeath d WHERE d.isDeleted = false OR d.isDeleted IS NULL")
    long countActive();

    /**
     * Get deaths in date range
     */
    @Query("SELECT d FROM LivestockDeath d WHERE d.deathDate BETWEEN :start AND :end AND (d.isDeleted = false OR d.isDeleted IS NULL)")
    List<LivestockDeath> findByDeathDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Count deaths by month/year
     */
    @Query("SELECT COUNT(d) FROM LivestockDeath d " +
            "WHERE YEAR(d.deathDate) = :year AND MONTH(d.deathDate) = :month " +
            "AND (d.isDeleted = false OR d.isDeleted IS NULL)")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Get death causes distribution
     */
    @Query("SELECT d.causeOfDeath, COUNT(d) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != '' " +
            "AND (d.isDeleted = false OR d.isDeleted IS NULL) " +
            "GROUP BY d.causeOfDeath ORDER BY COUNT(d) DESC")
    List<Object[]> getDeathCausesDistribution();

    /**
     * Get most common cause of death
     */
    @Query("SELECT d.causeOfDeath, COUNT(d) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != '' " +
            "AND (d.isDeleted = false OR d.isDeleted IS NULL) " +
            "GROUP BY d.causeOfDeath ORDER BY COUNT(d) DESC")
    List<Object[]> findMostCommonCause();

    /**
     * Count distinct death causes
     */
    @Query("SELECT COUNT(DISTINCT d.causeOfDeath) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != '' " +
            "AND (d.isDeleted = false OR d.isDeleted IS NULL)")
    long countDistinctCauses();

    /**
     * Get mortality rate by category
     */
    @Query("SELECT d.livestock.livestockCategory.name, COUNT(d) " +
            "FROM LivestockDeath d " +
            "WHERE d.livestock.livestockCategory IS NOT NULL " +
            "AND (d.isDeleted = false OR d.isDeleted IS NULL) " +
            "GROUP BY d.livestock.livestockCategory.name")
    List<Object[]> getDeathsByCategory();

    /**
     * Get recent deaths
     */
    @Query("SELECT d FROM LivestockDeath d WHERE (d.isDeleted = false OR d.isDeleted IS NULL) ORDER BY d.deathDate DESC")
    List<LivestockDeath> findTop50ByOrderByDeathDateDesc(Pageable pageable);
}