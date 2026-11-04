package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.dto.BuyerSummary;
import rw.animalproduct.animal.production.entity.Buyer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuyerRepository extends JpaRepository<Buyer, UUID> {

    // ─── Basic Queries ──────────────────────────────────────────────

    Optional<Buyer> findByPhone(String phone);
    Optional<Buyer> findByNationalId(String nationalId);
    Optional<Buyer> findByEmailIgnoreCase(String email);

    List<Buyer> findByIsActiveTrue();

    // ─── Existence Checks ───────────────────────────────────────────

    boolean existsByPhone(String phone);
    boolean existsByNationalId(String nationalId);
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhoneAndIdNot(String phone, UUID id);
    boolean existsByNationalIdAndIdNot(String nationalId, UUID id);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    // ─── Counts ─────────────────────────────────────────────────────

    @Query("SELECT COUNT(b) FROM Buyer b WHERE b.isActive = true AND (b.isDeleted = false OR b.isDeleted IS NULL)")
    long countActiveBuyers();

    @Query("SELECT COUNT(b) FROM Buyer b WHERE b.isActive = false AND (b.isDeleted = false OR b.isDeleted IS NULL)")
    long countInactiveBuyers();

    // ─── Summaries - Using Raw Query (No DTO Constructor) ──────────
    // Column order: id, firstName, lastName, phone, address, nationalId,
    // email, buyerType, notes, isActive, photo, createdAt, totalPurchases, totalSpent
    // MUST match the BuyerSummary constructor and BuyerService#mapToBuyerSummaries.

    @Query("""
        SELECT 
            b.id, b.firstName, b.lastName, b.phone, b.address, 
            b.nationalId, b.email, b.buyerType, b.notes, b.isActive, b.photo, b.createdAt,
            SIZE(b.sales), COALESCE(SUM(s.salePrice), 0)
        FROM Buyer b
        LEFT JOIN b.sales s
        WHERE b.isDeleted = false OR b.isDeleted IS NULL
        GROUP BY b.id
        ORDER BY b.lastName ASC, b.firstName ASC
    """)
    List<Object[]> findAllSummariesRaw();

    @Query("""
        SELECT 
            b.id, b.firstName, b.lastName, b.phone, b.address, 
            b.nationalId, b.email, b.buyerType, b.notes, b.isActive, b.photo, b.createdAt,
            SIZE(b.sales), COALESCE(SUM(s.salePrice), 0)
        FROM Buyer b
        LEFT JOIN b.sales s
        WHERE (b.isDeleted = false OR b.isDeleted IS NULL)
          AND (LOWER(b.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(b.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR b.phone LIKE CONCAT('%', :search, '%')
               OR b.nationalId LIKE CONCAT('%', :search, '%')
               OR LOWER(b.email) LIKE LOWER(CONCAT('%', :search, '%')))
        GROUP BY b.id
        ORDER BY b.lastName ASC
    """)
    List<Object[]> searchAllSummariesRaw(@Param("search") String search);

    @Query("""
        SELECT 
            b.id, b.firstName, b.lastName, b.phone, b.address, 
            b.nationalId, b.email, b.buyerType, b.notes, b.isActive, b.photo, b.createdAt,
            SIZE(b.sales), COALESCE(SUM(s.salePrice), 0)
        FROM Buyer b
        LEFT JOIN b.sales s
        WHERE b.isActive = true 
          AND (b.isDeleted = false OR b.isDeleted IS NULL)
        GROUP BY b.id
        ORDER BY COALESCE(SUM(s.salePrice), 0) DESC
    """)
    List<Object[]> findTopBuyerSummariesRaw();
}