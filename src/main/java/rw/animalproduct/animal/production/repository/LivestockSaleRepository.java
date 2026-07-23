package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockSale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockSaleRepository extends JpaRepository<LivestockSale, UUID> {

    List<LivestockSale> findByLivestockId(UUID livestockId);

    // ========== PAGINATION SUPPORT ==========

    /**
     * Get paginated sales (excludes soft-deleted)
     */
    @Query("SELECT s FROM LivestockSale s WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    Page<LivestockSale> findAllActive(Pageable pageable);

    /**
     * Get paginated sales with search
     */
    @Query("SELECT s FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND (LOWER(s.livestock.tagNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(s.saleLocation) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(s.saleReason) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LivestockSale> searchSales(@Param("search") String search, Pageable pageable);

    // ========== EXISTING METHODS ==========

    /**
     * Get total revenue from all sales
     */
    @Query("SELECT COALESCE(SUM(s.salePrice), 0) FROM LivestockSale s WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    BigDecimal getTotalRevenue();

    /**
     * Get total number of sales (active only)
     */
    @Query("SELECT COUNT(s) FROM LivestockSale s WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    long countActive();

    /**
     * Get sales in date range
     */
    @Query("SELECT s FROM LivestockSale s WHERE s.saleDate BETWEEN :start AND :end AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    List<LivestockSale> findBySaleDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Count sales by month/year
     */
    @Query("SELECT COUNT(s) FROM LivestockSale s " +
            "WHERE YEAR(s.saleDate) = :year AND MONTH(s.saleDate) = :month " +
            "AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Get revenue by month/year
     */
    @Query("SELECT COALESCE(SUM(s.salePrice), 0) FROM LivestockSale s " +
            "WHERE YEAR(s.saleDate) = :year AND MONTH(s.saleDate) = :month " +
            "AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    BigDecimal getRevenueByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Find most sold animal
     */
    @Query("SELECT s.livestock.tagNumber, COUNT(s), COALESCE(SUM(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "WHERE s.livestock IS NOT NULL " +
            "AND (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "GROUP BY s.livestock.tagNumber ORDER BY COUNT(s) DESC")
    List<Object[]> findMostSoldAnimal();

    /**
     * Get sales grouped by reason
     */
    @Query("SELECT s.saleReason, COUNT(s), COALESCE(SUM(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "GROUP BY s.saleReason")
    List<Object[]> getSalesByReason();

    /**
     * Get recent sales for dashboard
     */
    @Query("SELECT s FROM LivestockSale s WHERE (s.isDeleted = false OR s.isDeleted IS NULL) ORDER BY s.saleDate DESC")
    List<LivestockSale> findTop50ByOrderBySaleDateDesc(Pageable pageable);

    /**
     * Get monthly sales trend for current year
     */
    @Query("SELECT MONTH(s.saleDate), COUNT(s), COALESCE(SUM(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "WHERE YEAR(s.saleDate) = :year " +
            "AND (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "GROUP BY MONTH(s.saleDate) ORDER BY MONTH(s.saleDate)")
    List<Object[]> getMonthlySalesTrend(@Param("year") int year);
}