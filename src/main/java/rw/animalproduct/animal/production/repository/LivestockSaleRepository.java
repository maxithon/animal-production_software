package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockSale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockSaleRepository extends JpaRepository<LivestockSale, UUID>,
        JpaSpecificationExecutor<LivestockSale> {

    // ─── Basic Queries ────────────────────────────────────────────

    List<LivestockSale> findByLivestockId(UUID livestockId);

    @Query("SELECT s FROM LivestockSale s WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    Page<LivestockSale> findAllActive(Pageable pageable);

    // FIXED: Use CONCAT(s.buyer.firstName, ' ', s.buyer.lastName) instead of s.buyer.fullName
    // because fullName is @Transient and not available in JPQL
    @Query("SELECT s FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND (LOWER(s.livestock.tagNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(s.saleLocation) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(s.saleReason) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(s.saleReference) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(CONCAT(s.buyer.firstName, ' ', s.buyer.lastName)) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(s.buyer.phone) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LivestockSale> searchSales(@Param("search") String search, Pageable pageable);

    @Query("SELECT s FROM LivestockSale s WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    List<LivestockSale> findAllActive();

    // ─── Sale Status Counts ──────────────────────────────────────

    @Query("SELECT COUNT(s) FROM LivestockSale s " +
            "WHERE s.saleStatus = :status AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    long countBySaleStatus(@Param("status") String status);

    @Query("SELECT s FROM LivestockSale s " +
            "WHERE s.saleStatus = :status AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    Page<LivestockSale> findBySaleStatusPaged(@Param("status") String status, Pageable pageable);

    // ─── Payment Status Counts ───────────────────────────────────

    @Query("SELECT COUNT(s) FROM LivestockSale s " +
            "WHERE s.paymentStatus = :status AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    long countByPaymentStatus(@Param("status") String status);

    @Query("SELECT s FROM LivestockSale s " +
            "WHERE s.paymentStatus = :status AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    Page<LivestockSale> findByPaymentStatusPaged(@Param("status") String status, Pageable pageable);

    @Query("SELECT s FROM LivestockSale s " +
            "WHERE s.paymentStatus = :status AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    List<LivestockSale> findByPaymentStatus(@Param("status") String status);

    // ─── Financial Summary ────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(s.salePrice), 0) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED'")
    BigDecimal getTotalRevenue();

    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED'")
    BigDecimal getTotalAmountPaid();

    @Query("SELECT COALESCE(SUM(s.salePrice - s.amountPaid), 0) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED'")
    BigDecimal getTotalOutstanding();

    @Query("SELECT COALESCE(SUM(s.salePrice - s.acquisitionCost), 0) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED'")
    BigDecimal getTotalGrossProfit();

    @Query("SELECT COALESCE(SUM(s.acquisitionCost), 0) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED'")
    BigDecimal getTotalCost();

    @Query("SELECT COALESCE(SUM(s.salePrice), 0) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus IN ('CONFIRMED', 'CANCELLED', 'REFUNDED')")
    BigDecimal getGrossSales();

    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED' " +
            "AND s.paymentStatus = 'PAID'")
    BigDecimal getCollectedAmount();

    @Query("SELECT MAX(s.salePrice) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED'")
    BigDecimal getHighestSalePrice();

    @Query("SELECT MIN(s.salePrice) FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED'")
    BigDecimal getLowestSalePrice();

    @Query("SELECT COUNT(s) FROM LivestockSale s WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    long countActive();

    // ─── Reference ─────────────────────────────────────────────────

    @Query("SELECT s FROM LivestockSale s WHERE s.saleReference = :reference")
    LivestockSale findBySaleReference(@Param("reference") String reference);

    @Query("SELECT COUNT(s) FROM LivestockSale s WHERE s.saleReference LIKE CONCAT(:prefix, '%')")
    long countBySaleReferencePrefix(@Param("prefix") String prefix);

    // ─── Analytics - Raw Queries ─────────────────────────────────

    // ✅ FIXED: removed `AND (l.isDeleted = false OR l.isDeleted IS NULL)`.
    //    That clause referenced Livestock.isDeleted, which no longer exists —
    //    the column was dropped along with all other soft-delete fields on Livestock.
    //    Deleted animals are now physically removed from the table, so a join to
    //    s.livestock can only ever match live animals. The `s.isDeleted` guard
    //    on LivestockSale stays, because that entity still has its own soft-delete flag.
    @Query("SELECT " +
            "l.livestockCategory.name, " +
            "COUNT(s), " +
            "COALESCE(SUM(s.salePrice), 0), " +
            "COALESCE(AVG(s.salePrice), 0), " +
            "COALESCE(SUM(s.salePrice - s.acquisitionCost), 0) " +
            "FROM LivestockSale s JOIN s.livestock l " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED' " +
            "GROUP BY l.livestockCategory.name")
    List<Object[]> getSalesSummaryByCategoryRaw();

    @Query("SELECT s.buyer, COUNT(s), COALESCE(SUM(s.salePrice), 0), COALESCE(AVG(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED' " +
            "AND s.buyer IS NOT NULL " +
            "GROUP BY s.buyer ORDER BY SUM(s.salePrice) DESC")
    List<Object[]> getBuyerPurchaseSummary();

    @Query("SELECT MONTH(s.saleDate), COUNT(s), COALESCE(SUM(s.salePrice), 0) " +
            "FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND s.saleStatus = 'CONFIRMED' " +
            "AND YEAR(s.saleDate) = :year " +
            "GROUP BY MONTH(s.saleDate) ORDER BY MONTH(s.saleDate)")
    List<Object[]> getMonthlySalesTrend(@Param("year") int year);

    // ─── Advanced Search / Filter ─────────────────────────────────

    @Query("SELECT s FROM LivestockSale s " +
            "WHERE (s.isDeleted = false OR s.isDeleted IS NULL) " +
            "AND (:fromDate IS NULL OR s.saleDate >= :fromDate) " +
            "AND (:toDate IS NULL OR s.saleDate <= :toDate) " +
            "AND (:categoryId IS NULL OR s.livestock.livestockCategory.id = :categoryId) " +
            "AND (:saleStatus IS NULL OR s.saleStatus = :saleStatus) " +
            "AND (:paymentStatus IS NULL OR s.paymentStatus = :paymentStatus) " +
            "AND (:buyerId IS NULL OR s.buyer.id = :buyerId) " +
            "AND (:minPrice IS NULL OR s.salePrice >= :minPrice) " +
            "AND (:maxPrice IS NULL OR s.salePrice <= :maxPrice)")
    Page<LivestockSale> findWithFilters(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("categoryId") UUID categoryId,
            @Param("saleStatus") String saleStatus,
            @Param("paymentStatus") String paymentStatus,
            @Param("buyerId") UUID buyerId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);
}