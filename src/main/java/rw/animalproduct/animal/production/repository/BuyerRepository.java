package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.dto.BuyerSummary;
import rw.animalproduct.animal.production.entity.Buyer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuyerRepository extends JpaRepository<Buyer, UUID> {

    // ── Lookups used for duplicate checks (case-insensitive where relevant) ──
    Optional<Buyer> findByPhone(String phone);
    Optional<Buyer> findByNationalId(String nationalId);
    Optional<Buyer> findByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);
    boolean existsByNationalId(String nationalId);
    boolean existsByEmailIgnoreCase(String email);

    // Exclude the buyer's own id, needed when validating an UPDATE
    boolean existsByPhoneAndIdNot(String phone, UUID id);
    boolean existsByNationalIdAndIdNot(String nationalId, UUID id);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    List<Buyer> findByIsActiveTrue();

    List<Buyer> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String first, String last);

    // ── Efficient list/summary queries (single query, no N+1) ────────────
    @Query("SELECT b.id as id, b.firstName as firstName, b.lastName as lastName, " +
            "b.phone as phone, b.address as address, " +
            "b.nationalId as nationalId, b.email as email, " +
            "b.buyerType as buyerType, b.photo as photo, " +
            "b.notes as notes, b.createdAt as createdAt, " +
            "b.isActive as isActive, COUNT(s.id) as totalPurchases " +
            "FROM Buyer b LEFT JOIN b.sales s " +
            "GROUP BY b.id, b.firstName, b.lastName, b.phone, b.address, " +
            "b.nationalId, b.email, b.buyerType, b.photo, b.notes, b.createdAt, b.isActive " +
            "ORDER BY b.firstName, b.lastName")
    List<BuyerSummary> findAllSummaries();

    @Query("SELECT b.id as id, b.firstName as firstName, b.lastName as lastName, " +
            "b.phone as phone, b.address as address, " +
            "b.nationalId as nationalId, b.email as email, " +
            "b.buyerType as buyerType, b.photo as photo, " +
            "b.notes as notes, b.createdAt as createdAt, " +
            "b.isActive as isActive, COUNT(s.id) as totalPurchases " +
            "FROM Buyer b LEFT JOIN b.sales s " +
            "WHERE b.isActive = true AND (" +
            "  LOWER(b.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.phone) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.address) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.nationalId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.notes) LIKE LOWER(CONCAT('%', :search, '%'))" +
            ") " +
            "GROUP BY b.id, b.firstName, b.lastName, b.phone, b.address, " +
            "b.nationalId, b.email, b.buyerType, b.photo, b.notes, b.createdAt, b.isActive " +
            "ORDER BY b.firstName, b.lastName")
    List<BuyerSummary> searchActiveSummaries(@Param("search") String search);

    @Query("SELECT b.id as id, b.firstName as firstName, b.lastName as lastName, " +
            "b.phone as phone, b.address as address, " +
            "b.nationalId as nationalId, b.email as email, " +
            "b.buyerType as buyerType, b.photo as photo, " +
            "b.notes as notes, b.createdAt as createdAt, " +
            "b.isActive as isActive, COUNT(s.id) as totalPurchases " +
            "FROM Buyer b LEFT JOIN b.sales s " +
            "WHERE (" +
            "  LOWER(b.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.phone) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.address) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.nationalId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(b.notes) LIKE LOWER(CONCAT('%', :search, '%'))" +
            ") " +
            "GROUP BY b.id, b.firstName, b.lastName, b.phone, b.address, " +
            "b.nationalId, b.email, b.buyerType, b.photo, b.notes, b.createdAt, b.isActive " +
            "ORDER BY b.firstName, b.lastName")
    List<BuyerSummary> searchAllSummaries(@Param("search") String search);

    @Query("SELECT b.id as id, b.firstName as firstName, b.lastName as lastName, " +
            "b.phone as phone, b.address as address, " +
            "b.nationalId as nationalId, b.email as email, " +
            "b.buyerType as buyerType, b.photo as photo, " +
            "b.notes as notes, b.createdAt as createdAt, " +
            "b.isActive as isActive, COUNT(s.id) as totalPurchases " +
            "FROM Buyer b LEFT JOIN b.sales s " +
            "GROUP BY b.id, b.firstName, b.lastName, b.phone, b.address, " +
            "b.nationalId, b.email, b.buyerType, b.photo, b.notes, b.createdAt, b.isActive " +
            "ORDER BY COUNT(s.id) DESC")
    List<BuyerSummary> findTopBuyerSummaries();

    @Query("SELECT COUNT(b) FROM Buyer b WHERE b.isActive = true")
    long countActiveBuyers();

    @Query("SELECT COUNT(b) FROM Buyer b WHERE b.isActive = false")
    long countInactiveBuyers();
}