package rw.animalproduct.animal.production.repository;

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

    // ========== NEW ENHANCEMENTS FOR DASHBOARD ==========

    /**
     * Get total revenue from all sales
     */
    @Query("SELECT COALESCE(SUM(s.salePrice), 0) FROM LivestockSale s")
    BigDecimal getTotalRevenue();

    /**
     * Get total number of sales
     */
    long count();

    /**
     * Get sales in date range
     */
    List<LivestockSale> findBySaleDateBetween(LocalDate start, LocalDate end);

    /**
     * Count sales by month/year
     */
    @Query("SELECT COUNT(s) FROM LivestockSale s " +
            "WHERE YEAR(s.saleDate) = :year AND MONTH(s.saleDate) = :month")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Get revenue by month/year
     */
    @Query("SELECT COALESCE(SUM(s.salePrice), 0) FROM LivestockSale s " +
            "WHERE YEAR(s.saleDate) = :year AND MONTH(s.saleDate) = :month")
    BigDecimal getRevenueByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Find most sold animal
     */
    @Query("SELECT s.livestock.tagNumber, COUNT(s), COALESCE(SUM(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "WHERE s.livestock IS NOT NULL " +
            "GROUP BY s.livestock.tagNumber ORDER BY COUNT(s) DESC")
    List<Object[]> findMostSoldAnimal();

    /**
     * Get sales grouped by reason
     */
    @Query("SELECT s.saleReason, COUNT(s), COALESCE(SUM(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "GROUP BY s.saleReason")
    List<Object[]> getSalesByReason();

    /**
     * Get recent sales for dashboard
     */
    List<LivestockSale> findTop50ByOrderBySaleDateDesc();

    /**
     * Get monthly sales trend for current year
     */
    @Query("SELECT MONTH(s.saleDate), COUNT(s), COALESCE(SUM(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "WHERE YEAR(s.saleDate) = :year " +
            "GROUP BY MONTH(s.saleDate) ORDER BY MONTH(s.saleDate)")
    List<Object[]> getMonthlySalesTrend(@Param("year") int year);
}