package rw.animalproduct.animal.production.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Buyer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuyerRepository extends JpaRepository<Buyer, UUID> {

    Optional<Buyer> findByBuyerNationalId(String nationalId);
    Optional<Buyer> findByBuyerPhone(String phone);
    List<Buyer> findByIsActiveTrue();
    List<Buyer> findByBuyerNameContainingIgnoreCase(String name);

    @Query("SELECT b FROM Buyer b WHERE b.buyerPhone = ?1 OR b.buyerNationalId = ?2")
    Optional<Buyer> findByPhoneOrNationalId(String phone, String nationalId);

    @Query("SELECT b FROM Buyer b ORDER BY SIZE(b.sales) DESC")
    List<Buyer> findTopBuyers();

    @Query("SELECT b FROM Buyer b WHERE b.isActive = true AND " +
            "(LOWER(b.buyerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(b.buyerPhone) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(b.buyerNationalId) LIKE LOWER(CONCAT('%', :search, '%'))) ")
    List<Buyer> searchActiveBuyers(@Param("search") String search);

    @Query("SELECT COUNT(b) FROM Buyer b WHERE b.isActive = true")
    long countActiveBuyers();

    @Query("SELECT COUNT(b) FROM Buyer b WHERE b.isActive = false")
    long countInactiveBuyers();
}