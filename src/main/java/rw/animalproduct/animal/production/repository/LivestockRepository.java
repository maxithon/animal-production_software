package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Livestock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivestockRepository extends JpaRepository<Livestock, UUID> {

    // ========== BASIC FINDERS ==========

    /**
     * Find livestock by tag number
     */
    Optional<Livestock> findByTagNumber(String tagNumber);

    /**
     * Find livestock by category ID
     */
    List<Livestock> findByLivestockCategoryId(UUID categoryId);

    /**
     * Find livestock by beneficiary ID
     */
    List<Livestock> findByBeneficiaryId(UUID beneficiaryId);

    /**
     * Count livestock by category
     */
    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countByCategory(@Param("categoryId") UUID categoryId);

    /**
     * Count livestock by status
     */
    long countByStatus(String status);

    // ========== GENDER-BASED QUERIES (ADD THESE!) ==========

    /**
     * Find all livestock by gender (case insensitive)
     * Example: findByGenderIgnoreCase("FEMALE") or findByGenderIgnoreCase("MALE")
     */
    List<Livestock> findByGenderIgnoreCase(String gender);

    /**
     * Find all livestock by gender (case sensitive)
     */
    List<Livestock> findByGender(String gender);

    /**
     * Find livestock by gender and status
     */
    List<Livestock> findByGenderIgnoreCaseAndStatus(String gender, String status);

    /**
     * Count livestock by gender
     */
    long countByGenderIgnoreCase(String gender);

    /**
     * Count livestock by gender and status
     */
    long countByGenderIgnoreCaseAndStatus(String gender, String status);

    // ========== MOTHER-CHILD RELATIONSHIPS ==========

    /**
     * Find all children of a specific mother
     */
    List<Livestock> findByMotherId(UUID motherId);

    /**
     * Check if a mother has any children
     */
    boolean existsByMotherId(UUID motherId);

    // ========== QUERIES FOR DASHBOARD ==========

    /**
     * Get livestock by status
     */
    List<Livestock> findByStatus(String status);

    /**
     * Get livestock not in specified statuses (for dropdowns)
     */
    @Query("SELECT l FROM Livestock l WHERE l.status NOT IN :statuses")
    List<Livestock> findByStatusNotIn(@Param("statuses") List<String> statuses);

    /**
     * Count male livestock
     */
    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.gender = 'MALE'")
    long countMales();

    /**
     * Count female livestock
     */
    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.gender = 'FEMALE'")
    long countFemales();

    /**
     * Sum of current values for active livestock
     */
    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l WHERE l.status = 'ACTIVE'")
    BigDecimal sumActiveValues();

    /**
     * Get livestock count by category
     */
    @Query("SELECT l.livestockCategory.name, COUNT(l) FROM Livestock l " +
            "WHERE l.livestockCategory IS NOT NULL " +
            "GROUP BY l.livestockCategory.name")
    List<Object[]> getCountByCategory();

    /**
     * Get livestock count by status
     */
    @Query("SELECT l.status, COUNT(l) FROM Livestock l GROUP BY l.status")
    List<Object[]> getCountByStatus();

    /**
     * Get recently added livestock
     */
    List<Livestock> findTop20ByOrderByCreatedAtDesc();

    /**
     * Get pregnant livestock
     */
    @Query("SELECT l FROM Livestock l WHERE l.status = 'PREGNANT'")
    List<Livestock> findPregnantLivestock();

    /**
     * Get all female livestock that are not dead or sold (for breeding selection)
     */
    @Query("SELECT l FROM Livestock l " +
            "WHERE UPPER(l.gender) = 'FEMALE' " +
            "AND l.status NOT IN ('DEAD', 'SOLD') " +
            "AND l.isDeleted = false " +
            "ORDER BY l.tagNumber")
    List<Livestock> findAllActiveFemales();

    /**
     * Get all male livestock that are not dead or sold (for breeding selection)
     */
    @Query("SELECT l FROM Livestock l " +
            "WHERE UPPER(l.gender) = 'MALE' " +
            "AND l.status NOT IN ('DEAD', 'SOLD') " +
            "AND l.isDeleted = false " +
            "ORDER BY l.tagNumber")
    List<Livestock> findAllActiveMales();
}