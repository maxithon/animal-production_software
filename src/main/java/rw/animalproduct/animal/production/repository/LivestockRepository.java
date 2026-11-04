package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.Livestock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockRepository extends JpaRepository<Livestock, UUID> {

    // ✅ NEW: used by bulk import auto-tag
    boolean existsByTagNumber(String tagNumber);

    List<Livestock> findByStatusAndIsDeletedFalse(String status);
    List<Livestock> findByLivestockCategoryIdAndStatusAndIsDeletedFalse(UUID categoryId, String status);
    Page<Livestock> findByStatusAndIsDeletedFalse(String status, Pageable pageable);
    Optional<Livestock> findByIdAndIsDeletedFalse(UUID id);
    List<Livestock> findByIsDeletedFalse();
    List<Livestock> findByLivestockCategoryIdAndIsDeletedFalse(UUID categoryId);
    List<Livestock> findByLivestockCategoryId(UUID categoryId);
    Page<Livestock> findByLivestockCategoryIdAndIsDeletedFalse(UUID categoryId, Pageable pageable);
    Page<Livestock> findByLivestockCategoryIdAndStatusAndIsDeletedFalse(UUID categoryId, String status, Pageable pageable);
    List<Livestock> findByBeneficiaryIdAndIsDeletedFalse(UUID beneficiaryId);
    List<Livestock> findByBeneficiaryId(UUID beneficiaryId);
    Page<Livestock> findByBeneficiaryIdAndIsDeletedFalse(UUID beneficiaryId, Pageable pageable);
    List<Livestock> findByMotherIdAndIsDeletedFalse(UUID motherId);
    List<Livestock> findByMotherId(UUID motherId);

    @Query("SELECT l FROM Livestock l WHERE l.status NOT IN :statuses AND l.isDeleted = false")
    List<Livestock> findByStatusNotIn(@Param("statuses") List<String> statuses);

    @Query("SELECT l FROM Livestock l WHERE l.isDeleted = false")
    Page<Livestock> findAllNonDeleted(Pageable pageable);

    Page<Livestock> findByIsDeletedFalse(Pageable pageable);

    @Query("SELECT l FROM Livestock l WHERE l.isDeleted = true")
    List<Livestock> findAllSoftDeleted();

    @Query("SELECT l FROM Livestock l WHERE l.isDeleted = true")
    Page<Livestock> findAllSoftDeleted(Pageable pageable);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = :gender " +
            "AND l.status = :status " +
            "AND l.reproductiveStatus = :reproductiveStatus " +
            "AND l.isDeleted = false")
    List<Livestock> findByGenderAndStatusAndReproductiveStatusAndIsDeletedFalse(
            @Param("gender") String gender,
            @Param("status") String status,
            @Param("reproductiveStatus") String reproductiveStatus);

    List<Livestock> findByReproductiveStatus(String reproductiveStatus);

    @Query("SELECT l FROM Livestock l WHERE l.isDraft = true AND l.isDeleted = false")
    List<Livestock> findAllPendingDrafts();

    List<Livestock> findByDraftBirthEventIdAndIsDraftTrue(UUID draftBirthEventId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = :status AND l.isDeleted = false")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.healthStatus = :healthStatus AND l.isDeleted = false")
    long countByHealthStatus(@Param("healthStatus") String healthStatus);

    @Query("SELECT COUNT(DISTINCT l) FROM Livestock l " +
            "WHERE l.isDeleted = false " +
            "AND EXISTS (SELECT v FROM LivestockValuation v WHERE v.livestock.id = l.id)")
    long countValued();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.isDeleted = false " +
            "AND NOT EXISTS (SELECT v FROM LivestockValuation v WHERE v.livestock.id = l.id)")
    long countUnvalued();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.isDeleted = true")
    long countSoftDeleted();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.status = :status " +
            "AND l.reproductiveStatus = :reproductiveStatus " +
            "AND l.isDeleted = false")
    long countByStatusAndReproductiveStatus(@Param("status") String status,
                                            @Param("reproductiveStatus") String reproductiveStatus);

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.livestockCategory.id = :categoryId " +
            "AND l.isDeleted = false")
    long countByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.livestockCategory.id = :categoryId " +
            "AND l.isDeleted = false")
    long countByCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND l.isDeleted = false " +
            "AND l.id NOT IN (SELECT b.livestock.id FROM LivestockBreeding b " +
            "                 WHERE b.status IN ('CONFIRMED_PREGNANT', 'PENDING') " +
            "                 AND b.isDeleted = false)")
    List<Livestock> findPregnantWithoutBreedingRecord();

    @Query("SELECT DISTINCT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND l.isDeleted = false " +
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l " +
            "            AND b.isDeleted = false " +
            "            AND b.expectedDueDate IS NOT NULL)")
    List<Livestock> findPregnantWithDueDate();

    @Query("SELECT DISTINCT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND l.isDeleted = false " +
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l " +
            "            AND b.isDeleted = false " +
            "            AND b.expectedDueDate IS NOT NULL " +
            "            AND b.expectedDueDate BETWEEN :startDate AND :endDate)")
    List<Livestock> findPregnantDueWithinRange(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    default List<Livestock> findPregnantDueWithin(int days) {
        LocalDate today = LocalDate.now();
        return findPregnantDueWithinRange(today, today.plusDays(days));
    }

    @Query("SELECT DISTINCT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND l.isDeleted = false " +
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l " +
            "            AND b.isDeleted = false " +
            "            AND b.expectedDueDate IS NOT NULL " +
            "            AND b.expectedDueDate < CURRENT_DATE)")
    List<Livestock> findOverduePregnant();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = 'FEMALE' " +
            "AND l.status = 'ACTIVE' " +
            "AND l.reproductiveStatus = 'OPEN' " +
            "AND l.isDeleted = false " +
            "AND l.birthDate IS NOT NULL " +
            "AND (l.livestockCategory.minBreedingAgeMonths IS NULL " +
            "     OR ((EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM l.birthDate)) * 12 " +
            "         + (EXTRACT(MONTH FROM CURRENT_DATE) - EXTRACT(MONTH FROM l.birthDate)) " +
            "         >= l.livestockCategory.minBreedingAgeMonths))")
    List<Livestock> findAllReadyToBreedFemales();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.gender = 'FEMALE' " +
            "AND l.status = 'ACTIVE' " +
            "AND l.reproductiveStatus = 'OPEN' " +
            "AND l.isDeleted = false")
    long countReadyToBreedFemales();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = 'MALE' " +
            "AND l.status = 'ACTIVE' " +
            "AND l.isDeleted = false")
    List<Livestock> findAllBreedingMales();

    @Query("SELECT DISTINCT l.location.id FROM Livestock l " +
            "WHERE l.location IS NOT NULL " +
            "AND l.isDeleted = false")
    List<UUID> findDistinctLocationIdsWithLivestock();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.location.id IN :locationIds " +
            "AND l.isDeleted = false")
    List<Livestock> findByLocationIdIn(@Param("locationIds") List<UUID> locationIds);

    @Query("SELECT l FROM Livestock l " +
            "WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "AND l.isDeleted = false " +
            "ORDER BY l.tagNumber")
    List<Livestock> searchByTagNumber(@Param("q") String q, Pageable pageable);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.beneficiary.id = :beneficiaryId " +
            "AND l.status = :status " +
            "AND l.isDeleted = false")
    List<Livestock> findByBeneficiaryIdAndStatus(@Param("beneficiaryId") UUID beneficiaryId,
                                                 @Param("status") String status);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.beneficiary.id = :beneficiaryId " +
            "AND l.livestockCategory.id = :categoryId " +
            "AND l.isDeleted = false")
    List<Livestock> findByBeneficiaryIdAndCategoryId(@Param("beneficiaryId") UUID beneficiaryId,
                                                     @Param("categoryId") UUID categoryId);

    @Query("select l from Livestock l join fetch l.livestockCategory c " +
            "where l.status = 'ACTIVE' and l.isDeleted = false and c.isDeleted = false")
    List<Livestock> findAllActiveWithCategory();

    @Query("SELECT l FROM Livestock l " +
            "WHERE (l.isDeleted = false OR l.isDeleted IS NULL) " +
            "AND l.status != 'SOLD' " +
            "AND l.status != 'DEAD' " +
            "AND l.status != 'TRANSFERRED' " +
            "ORDER BY l.tagNumber")
    List<Livestock> findEligibleForSale();

    @Query("SELECT l FROM Livestock l " +
            "WHERE (l.isDeleted = false OR l.isDeleted IS NULL) " +
            "AND l.status != 'SOLD' " +
            "AND l.status != 'DEAD' " +
            "AND l.status != 'TRANSFERRED' " +
            "AND (LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(l.livestockCategory.name) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY l.tagNumber")
    List<Livestock> searchEligibleForSale(@Param("query") String query);

    @Query("SELECT l FROM Livestock l " +
            "WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :tag, '%')) " +
            "AND l.healthStatus = :healthStatus " +
            "AND l.isDeleted = false " +
            "ORDER BY l.tagNumber ASC")
    List<Livestock> findByTagNumberContainingIgnoreCaseAndHealthStatusAndIsDeletedFalse(
            @Param("tag") String tag,
            @Param("healthStatus") String healthStatus);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.healthStatus = :healthStatus " +
            "AND l.isDeleted = false " +
            "ORDER BY l.updatedAt DESC")
    List<Livestock> findTop10ByHealthStatusAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("healthStatus") String healthStatus);

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.healthStatus = :healthStatus " +
            "AND l.isDeleted = false")
    long countByHealthStatusAndIsDeletedFalse(@Param("healthStatus") String healthStatus);

    @Query("SELECT l FROM Livestock l WHERE l.healthStatus = :healthStatus AND l.isDeleted = false")
    List<Livestock> findByHealthStatusAndIsDeletedFalse(@Param("healthStatus") String healthStatus);

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.isDeleted = false AND l.acquisitionMethod = 'BIRTH'")
    long countBornOnFarm();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.isDeleted = false AND l.status = 'SOLD'")
    long countSold();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.isDeleted = false AND l.status = 'DEAD'")
    long countDead();

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE l.isDeleted = false " +
            "AND l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED')")
    BigDecimal sumCurrentValueForLive();

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE l.isDeleted = false " +
            "AND l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED') " +
            "AND UPPER(l.acquisitionMethod) = UPPER(:method)")
    BigDecimal sumCurrentValueForLiveByAcquisitionMethod(@Param("method") String method);

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE l.isDeleted = false " +
            "AND UPPER(l.acquisitionMethod) = UPPER(:method)")
    BigDecimal sumCurrentValueByAcquisitionMethod(@Param("method") String method);

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE l.isDeleted = false " +
            "AND l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED') " +
            "AND l.beneficiary.id = :beneficiaryId")
    BigDecimal sumCurrentValueForLiveByBeneficiary(@Param("beneficiaryId") UUID beneficiaryId);

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.isDeleted = false " +
            "AND l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED')")
    long countLive();
}