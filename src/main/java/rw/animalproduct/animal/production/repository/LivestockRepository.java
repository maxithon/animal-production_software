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

    boolean existsByTagNumber(String tagNumber);

    List<Livestock> findByStatus(String status);
    List<Livestock> findByLivestockCategoryIdAndStatus(UUID categoryId, String status);
    Page<Livestock> findByStatus(String status, Pageable pageable);
    List<Livestock> findByLivestockCategoryId(UUID categoryId);
    Page<Livestock> findByLivestockCategoryId(UUID categoryId, Pageable pageable);
    Page<Livestock> findByLivestockCategoryIdAndStatus(UUID categoryId, String status, Pageable pageable);
    List<Livestock> findByBeneficiaryId(UUID beneficiaryId);
    Page<Livestock> findByBeneficiaryId(UUID beneficiaryId, Pageable pageable);
    List<Livestock> findByMotherId(UUID motherId);

    @Query("SELECT l FROM Livestock l WHERE l.status NOT IN :statuses")
    List<Livestock> findByStatusNotIn(@Param("statuses") List<String> statuses);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = :gender " +
            "AND l.status = :status " +
            "AND l.reproductiveStatus = :reproductiveStatus")
    List<Livestock> findByGenderAndStatusAndReproductiveStatus(
            @Param("gender") String gender,
            @Param("status") String status,
            @Param("reproductiveStatus") String reproductiveStatus);

    List<Livestock> findByReproductiveStatus(String reproductiveStatus);

    @Query("SELECT l FROM Livestock l WHERE l.isDraft = true")
    List<Livestock> findAllPendingDrafts();

    List<Livestock> findByDraftBirthEventIdAndIsDraftTrue(UUID draftBirthEventId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.healthStatus = :healthStatus")
    long countByHealthStatus(@Param("healthStatus") String healthStatus);

    @Query("SELECT COUNT(DISTINCT l) FROM Livestock l " +
            "WHERE EXISTS (SELECT v FROM LivestockValuation v WHERE v.livestock.id = l.id)")
    long countValued();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE NOT EXISTS (SELECT v FROM LivestockValuation v WHERE v.livestock.id = l.id)")
    long countUnvalued();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.status = :status " +
            "AND l.reproductiveStatus = :reproductiveStatus")
    long countByStatusAndReproductiveStatus(@Param("status") String status,
                                            @Param("reproductiveStatus") String reproductiveStatus);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countByCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND l.id NOT IN (SELECT b.livestock.id FROM LivestockBreeding b " +
            "                 WHERE b.status IN ('CONFIRMED_PREGNANT', 'PENDING'))")
    List<Livestock> findPregnantWithoutBreedingRecord();

    @Query("SELECT DISTINCT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l " +
            "            AND b.expectedDueDate IS NOT NULL)")
    List<Livestock> findPregnantWithDueDate();

    @Query("SELECT DISTINCT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l " +
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
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l " +
            "            AND b.expectedDueDate IS NOT NULL " +
            "            AND b.expectedDueDate < CURRENT_DATE)")
    List<Livestock> findOverduePregnant();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = 'FEMALE' " +
            "AND l.status = 'ACTIVE' " +
            "AND l.reproductiveStatus = 'OPEN' " +
            "AND l.birthDate IS NOT NULL " +
            "AND (l.livestockCategory.minBreedingAgeMonths IS NULL " +
            "     OR ((EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM l.birthDate)) * 12 " +
            "         + (EXTRACT(MONTH FROM CURRENT_DATE) - EXTRACT(MONTH FROM l.birthDate)) " +
            "         >= l.livestockCategory.minBreedingAgeMonths))")
    List<Livestock> findAllReadyToBreedFemales();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.gender = 'FEMALE' " +
            "AND l.status = 'ACTIVE' " +
            "AND l.reproductiveStatus = 'OPEN'")
    long countReadyToBreedFemales();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = 'MALE' " +
            "AND l.status = 'ACTIVE'")
    List<Livestock> findAllBreedingMales();

    @Query("SELECT DISTINCT l.location.id FROM Livestock l " +
            "WHERE l.location IS NOT NULL")
    List<UUID> findDistinctLocationIdsWithLivestock();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.location.id IN :locationIds")
    List<Livestock> findByLocationIdIn(@Param("locationIds") List<UUID> locationIds);

    @Query("SELECT l FROM Livestock l " +
            "WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY l.tagNumber")
    List<Livestock> searchByTagNumber(@Param("q") String q, Pageable pageable);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.beneficiary.id = :beneficiaryId " +
            "AND l.status = :status")
    List<Livestock> findByBeneficiaryIdAndStatus(@Param("beneficiaryId") UUID beneficiaryId,
                                                 @Param("status") String status);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.beneficiary.id = :beneficiaryId " +
            "AND l.livestockCategory.id = :categoryId")
    List<Livestock> findByBeneficiaryIdAndCategoryId(@Param("beneficiaryId") UUID beneficiaryId,
                                                     @Param("categoryId") UUID categoryId);

    @Query("select l from Livestock l join fetch l.livestockCategory c " +
            "where l.status = 'ACTIVE'")
    List<Livestock> findAllActiveWithCategory();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.status != 'SOLD' " +
            "AND l.status != 'DEAD' " +
            "AND l.status != 'TRANSFERRED' " +
            "ORDER BY l.tagNumber")
    List<Livestock> findEligibleForSale();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.status != 'SOLD' " +
            "AND l.status != 'DEAD' " +
            "AND l.status != 'TRANSFERRED' " +
            "AND (LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(l.livestockCategory.name) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY l.tagNumber")
    List<Livestock> searchEligibleForSale(@Param("query") String query);

    @Query("SELECT l FROM Livestock l " +
            "WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :tag, '%')) " +
            "AND l.healthStatus = :healthStatus " +
            "ORDER BY l.tagNumber ASC")
    List<Livestock> findByTagNumberContainingIgnoreCaseAndHealthStatus(
            @Param("tag") String tag,
            @Param("healthStatus") String healthStatus);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.healthStatus = :healthStatus " +
            "ORDER BY l.updatedAt DESC")
    List<Livestock> findTop10ByHealthStatusOrderByUpdatedAtDesc(
            @Param("healthStatus") String healthStatus);

    @Query("SELECT l FROM Livestock l WHERE l.healthStatus = :healthStatus")
    List<Livestock> findByHealthStatus(@Param("healthStatus") String healthStatus);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.acquisitionMethod = 'BIRTH'")
    long countBornOnFarm();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = 'SOLD'")
    long countSold();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = 'DEAD'")
    long countDead();

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED')")
    BigDecimal sumCurrentValueForLive();

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED') " +
            "AND UPPER(l.acquisitionMethod) = UPPER(:method)")
    BigDecimal sumCurrentValueForLiveByAcquisitionMethod(@Param("method") String method);

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE UPPER(l.acquisitionMethod) = UPPER(:method)")
    BigDecimal sumCurrentValueByAcquisitionMethod(@Param("method") String method);

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l " +
            "WHERE l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED') " +
            "AND l.beneficiary.id = :beneficiaryId")
    BigDecimal sumCurrentValueForLiveByBeneficiary(@Param("beneficiaryId") UUID beneficiaryId);

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED')")
    long countLive();

    @Query("""
           SELECT COUNT(l)
           FROM Livestock l
           WHERE l.livestockCategory.id = :categoryId
           """)
    long countAllByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("""
           SELECT l.livestockCategory.id, COUNT(l)
           FROM Livestock l
           WHERE l.livestockCategory IS NOT NULL
           GROUP BY l.livestockCategory.id
           """)
    List<Object[]> countGroupedByCategoryAll();

    /**
     * Counts the number of rows in every child table that reference this
     * livestock record — used by LivestockService.delete() to refuse deletion
     * of animals that still have historical records.
     */
    @Query("""
           SELECT (
               (SELECT COUNT(b)  FROM LivestockBreeding        b  WHERE b.livestock.id     = :id)
             + (SELECT COUNT(bt) FROM LivestockBirth           bt WHERE bt.livestock.id    = :id)
             + (SELECT COUNT(s)  FROM LivestockSale            s  WHERE s.livestock.id     = :id)
             + (SELECT COUNT(t)  FROM LivestockTreatment       t  WHERE t.livestock.id     = :id)
             + (SELECT COUNT(t)  FROM LivestockTreatment       t  WHERE t.sickLivestock.id = :id)
             + (SELECT COUNT(sk) FROM LivestockSick            sk WHERE sk.livestock.id    = :id)
             + (SELECT COUNT(d)  FROM LivestockDeath           d  WHERE d.livestock.id     = :id)
             + (SELECT COUNT(w)  FROM LivestockWeightHistory   w  WHERE w.livestock.id     = :id)
             + (SELECT COUNT(v)  FROM LivestockValuation       v  WHERE v.livestock.id     = :id)
             + (SELECT COUNT(a)  FROM LivestockAbortion        a  WHERE a.livestock.id     = :id)
             + (SELECT COUNT(l2) FROM Livestock                l2 WHERE l2.mother.id        = :id)
             + (SELECT COUNT(l3) FROM Livestock                l3 WHERE l3.father.id        = :id)
           )
           """)
    long countLinkedRecords(@Param("id") UUID id);
}