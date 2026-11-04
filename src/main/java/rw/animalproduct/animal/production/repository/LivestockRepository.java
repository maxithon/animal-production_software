package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.Livestock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface LivestockRepository extends JpaRepository<Livestock, UUID> {

    // ══════════════════════════════════════════════════════════════════════
    // BASIC LOOKUPS
    // ══════════════════════════════════════════════════════════════════════

    boolean existsByTagNumber(String tagNumber);
    boolean existsByTagNumberAndIdNot(String tagNumber, UUID id);

    Optional<Livestock> findByTagNumber(String tagNumber);

    @EntityGraph(attributePaths = {"livestockCategory", "location", "mother", "father", "beneficiary"})
    @Query("SELECT l FROM Livestock l WHERE l.id = :id")
    Optional<Livestock> findByIdWithDetails(@Param("id") UUID id);

    List<Livestock> findByStatus(String status);
    Page<Livestock> findByStatus(String status, Pageable pageable);

    /** Used by LivestockDeathController.searchAnimalsForDeath(...) */
    @Query("SELECT l FROM Livestock l WHERE l.status NOT IN :statuses ORDER BY l.tagNumber")
    List<Livestock> findByStatusNotIn(@Param("statuses") List<String> statuses);

    List<Livestock> findByLivestockCategoryId(UUID categoryId);
    Page<Livestock> findByLivestockCategoryId(UUID categoryId, Pageable pageable);

    List<Livestock> findByLivestockCategoryIdAndStatus(UUID categoryId, String status);
    Page<Livestock> findByLivestockCategoryIdAndStatus(UUID categoryId, String status, Pageable pageable);

    List<Livestock> findByBeneficiaryId(UUID beneficiaryId);
    Page<Livestock> findByBeneficiaryId(UUID beneficiaryId, Pageable pageable);

    List<Livestock> findByReproductiveStatus(String reproductiveStatus);

    /** Used by LivestockService.findByGenderAndStatusAndReproductiveStatus(...) */
    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = :gender " +
            "AND l.status = :status " +
            "AND l.reproductiveStatus = :reproductiveStatus " +
            "ORDER BY l.tagNumber")
    List<Livestock> findByGenderAndStatusAndReproductiveStatus(
            @Param("gender") String gender,
            @Param("status") String status,
            @Param("reproductiveStatus") String reproductiveStatus);

    // ══════════════════════════════════════════════════════════════════════
    // MOTHER / FATHER
    // ══════════════════════════════════════════════════════════════════════

    List<Livestock> findByMotherId(UUID motherId);
    List<Livestock> findByMotherIdAndStatusIn(UUID motherId, List<String> statuses);
    Page<Livestock> findByMotherId(UUID motherId, Pageable pageable);

    List<Livestock> findByFatherId(UUID fatherId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.mother.id = :motherId")
    long countOffspringByMotherId(@Param("motherId") UUID motherId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.father.id = :fatherId")
    long countOffspringByFatherId(@Param("fatherId") UUID fatherId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.draftBirthEvent.id = :birthId AND l.isDraft = true")
    long countDraftsForBirth(@Param("birthId") UUID birthId);

    // ══════════════════════════════════════════════════════════════════════
    // DRAFTS
    // ══════════════════════════════════════════════════════════════════════

    @Query("SELECT l FROM Livestock l WHERE l.isDraft = true ORDER BY l.createdAt DESC")
    List<Livestock> findAllPendingDrafts();

    @Query("SELECT l FROM Livestock l WHERE l.isDraft = true ORDER BY l.createdAt DESC")
    Page<Livestock> findAllPendingDrafts(Pageable pageable);

    List<Livestock> findByDraftBirthEvent_IdAndIsDraftTrue(UUID draftBirthEventId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.isDraft = true")
    long countPendingDrafts();

    // ══════════════════════════════════════════════════════════════════════
    // COUNTS
    // ══════════════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.healthStatus = :healthStatus")
    long countByHealthStatus(@Param("healthStatus") String healthStatus);

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.status = :status AND l.reproductiveStatus = :reproductiveStatus")
    long countByStatusAndReproductiveStatus(@Param("status") String status,
                                            @Param("reproductiveStatus") String reproductiveStatus);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countByCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countAllByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT l.livestockCategory.id, COUNT(l) FROM Livestock l " +
            "WHERE l.livestockCategory IS NOT NULL GROUP BY l.livestockCategory.id")
    List<Object[]> countGroupedByCategoryAll();

    @Query("SELECT COUNT(DISTINCT l) FROM Livestock l " +
            "WHERE EXISTS (SELECT v FROM LivestockValuation v WHERE v.livestock.id = l.id)")
    long countValued();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE NOT EXISTS (SELECT v FROM LivestockValuation v WHERE v.livestock.id = l.id)")
    long countUnvalued();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.acquisitionMethod = 'BIRTH'")
    long countBornOnFarm();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = 'SOLD'")
    long countSold();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = 'DEAD'")
    long countDead();

    @Query("SELECT COUNT(l) FROM Livestock l " +
            "WHERE l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED')")
    long countLive();

    // ══════════════════════════════════════════════════════════════════════
    // PREGNANCY / DUE DATE
    // ══════════════════════════════════════════════════════════════════════

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND NOT EXISTS (SELECT b FROM LivestockBreeding b " +
            "                WHERE b.livestock = l " +
            "                AND b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT'))")
    List<Livestock> findPregnantWithoutBreedingRecord();

    @Query("SELECT DISTINCT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l AND b.expectedDueDate IS NOT NULL) " +
            "ORDER BY l.tagNumber")
    List<Livestock> findPregnantWithDueDate();

    @Query("SELECT DISTINCT l FROM Livestock l " +
            "WHERE l.reproductiveStatus = 'PREGNANT' " +
            "AND EXISTS (SELECT b FROM LivestockBreeding b " +
            "            WHERE b.livestock = l " +
            "            AND b.expectedDueDate IS NOT NULL " +
            "            AND b.expectedDueDate BETWEEN :startDate AND :endDate) " +
            "ORDER BY l.tagNumber")
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
            "            AND b.expectedDueDate < CURRENT_DATE) " +
            "ORDER BY l.tagNumber")
    List<Livestock> findOverduePregnant();

    // ══════════════════════════════════════════════════════════════════════
    // BREEDING READINESS
    // ══════════════════════════════════════════════════════════════════════

    @Query("""
           SELECT l FROM Livestock l
           WHERE l.gender = 'FEMALE'
             AND l.status = 'ACTIVE'
             AND l.reproductiveStatus = 'OPEN'
             AND l.birthDate IS NOT NULL
             AND (
                 l.livestockCategory IS NULL
                 OR l.livestockCategory.minBreedingAgeMonths IS NULL
                 OR (
                     ((YEAR(CURRENT_DATE) - YEAR(l.birthDate)) * 12)
                     + (MONTH(CURRENT_DATE) - MONTH(l.birthDate))
                     - (CASE WHEN DAY(CURRENT_DATE) < DAY(l.birthDate) THEN 1 ELSE 0 END)
                 ) >= l.livestockCategory.minBreedingAgeMonths
             )
           ORDER BY l.tagNumber
           """)
    List<Livestock> findAllReadyToBreedFemales();

    @Query("""
           SELECT l FROM Livestock l
           WHERE l.gender = 'FEMALE'
             AND l.status = 'ACTIVE'
             AND l.reproductiveStatus = 'OPEN'
             AND l.birthDate IS NOT NULL
             AND (
                 l.livestockCategory IS NULL
                 OR l.livestockCategory.minBreedingAgeMonths IS NULL
                 OR (
                     ((YEAR(CURRENT_DATE) - YEAR(l.birthDate)) * 12)
                     + (MONTH(CURRENT_DATE) - MONTH(l.birthDate))
                     - (CASE WHEN DAY(CURRENT_DATE) < DAY(l.birthDate) THEN 1 ELSE 0 END)
                 ) >= l.livestockCategory.minBreedingAgeMonths
             )
           ORDER BY l.tagNumber
           """)
    Page<Livestock> findAllReadyToBreedFemales(Pageable pageable);

    @Query("""
           SELECT COUNT(l) FROM Livestock l
           WHERE l.gender = 'FEMALE'
             AND l.status = 'ACTIVE'
             AND l.reproductiveStatus = 'OPEN'
             AND l.birthDate IS NOT NULL
             AND (
                 l.livestockCategory IS NULL
                 OR l.livestockCategory.minBreedingAgeMonths IS NULL
                 OR (
                     ((YEAR(CURRENT_DATE) - YEAR(l.birthDate)) * 12)
                     + (MONTH(CURRENT_DATE) - MONTH(l.birthDate))
                     - (CASE WHEN DAY(CURRENT_DATE) < DAY(l.birthDate) THEN 1 ELSE 0 END)
                 ) >= l.livestockCategory.minBreedingAgeMonths
             )
           """)
    long countReadyToBreedFemales();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = 'MALE' AND l.status = 'ACTIVE' " +
            "ORDER BY l.tagNumber")
    List<Livestock> findAllBreedingMales();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.gender = 'MALE' AND l.status = 'ACTIVE' " +
            "ORDER BY l.tagNumber")
    Page<Livestock> findAllBreedingMales(Pageable pageable);

    // ══════════════════════════════════════════════════════════════════════
    // ✅ SERVER-SIDE SEARCH FOR ABORTION PICKER (scale to 1000s)
    //
    // Note: `isPregnant` is @Transient on Livestock — NOT a DB column —
    // so it CANNOT appear in HQL. Pregnancy is fully covered by:
    //   1) reproductiveStatus = 'PREGNANT'  (the column that drives the flag)
    //   2) an active LivestockBreeding record
    // ══════════════════════════════════════════════════════════════════════

    @Query("""
           SELECT l.id, l.tagNumber, c.name
           FROM Livestock l
           LEFT JOIN l.livestockCategory c
           WHERE l.gender = 'FEMALE'
             AND l.isDraft = false
             AND l.status NOT IN ('DEAD', 'SOLD', 'TRANSFERRED')
             AND (
                 l.reproductiveStatus = 'PREGNANT'
                 OR EXISTS (
                     SELECT 1 FROM LivestockBreeding b
                     WHERE b.livestock = l
                       AND b.status IN ('CONFIRMED_PREGNANT', 'PREGNANT')
                 )
             )
             AND (
                 :q = ''
                 OR LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(c.name)      LIKE LOWER(CONCAT('%', :q, '%'))
             )
           ORDER BY l.tagNumber
           """)
    List<Object[]> searchPregnantAnimals(@Param("q") String q, Pageable pageable);

    // ══════════════════════════════════════════════════════════════════════
    // LOCATION
    // ══════════════════════════════════════════════════════════════════════

    @Query("SELECT DISTINCT l.location.id FROM Livestock l WHERE l.location IS NOT NULL")
    List<UUID> findDistinctLocationIdsWithLivestock();

    @Query("SELECT l FROM Livestock l WHERE l.location.id IN :locationIds ORDER BY l.tagNumber")
    List<Livestock> findByLocationIdIn(@Param("locationIds") List<UUID> locationIds);

    // ══════════════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════════════

    @Query("SELECT l FROM Livestock l " +
            "WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY l.tagNumber")
    Page<Livestock> searchByTagNumber(@Param("q") String q, Pageable pageable);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.beneficiary.id = :beneficiaryId AND l.status = :status " +
            "ORDER BY l.tagNumber")
    List<Livestock> findByBeneficiaryIdAndStatus(@Param("beneficiaryId") UUID beneficiaryId,
                                                 @Param("status") String status);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.beneficiary.id = :beneficiaryId AND l.livestockCategory.id = :categoryId " +
            "ORDER BY l.tagNumber")
    List<Livestock> findByBeneficiaryIdAndCategoryId(@Param("beneficiaryId") UUID beneficiaryId,
                                                     @Param("categoryId") UUID categoryId);

    @Query("SELECT l FROM Livestock l " +
            "WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :tag, '%')) " +
            "AND l.healthStatus = :healthStatus " +
            "ORDER BY l.tagNumber ASC")
    List<Livestock> searchByTagAndHealthStatus(@Param("tag") String tag,
                                               @Param("healthStatus") String healthStatus);

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.healthStatus = :healthStatus " +
            "ORDER BY l.updatedAt DESC")
    List<Livestock> findRecentByHealthStatus(@Param("healthStatus") String healthStatus,
                                             Pageable pageable);

    @Query("SELECT l FROM Livestock l WHERE l.healthStatus = :healthStatus ORDER BY l.tagNumber")
    List<Livestock> findByHealthStatus(@Param("healthStatus") String healthStatus);

    // ══════════════════════════════════════════════════════════════════════
    // SALE ELIGIBILITY
    // ══════════════════════════════════════════════════════════════════════

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.status NOT IN ('SOLD', 'DEAD', 'TRANSFERRED') " +
            "ORDER BY l.tagNumber")
    List<Livestock> findEligibleForSale();

    @Query("SELECT l FROM Livestock l " +
            "WHERE l.status NOT IN ('SOLD', 'DEAD', 'TRANSFERRED') " +
            "AND (LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(l.livestockCategory.name) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY l.tagNumber")
    List<Livestock> searchEligibleForSale(@Param("query") String query);

    // ══════════════════════════════════════════════════════════════════════
    // EAGER-FETCH VARIANTS
    // ══════════════════════════════════════════════════════════════════════

    @EntityGraph(attributePaths = {"livestockCategory"})
    @Query("SELECT l FROM Livestock l WHERE l.status = 'ACTIVE' ORDER BY l.tagNumber")
    List<Livestock> findAllActiveWithCategory();

    @EntityGraph(attributePaths = {"livestockCategory"})
    @Query("SELECT l FROM Livestock l WHERE l.id IN :ids")
    List<Livestock> findAllWithCategoryByIdIn(@Param("ids") Set<UUID> ids);

    // ══════════════════════════════════════════════════════════════════════
    // VALUE AGGREGATES
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // DELETE PROTECTION
    // ══════════════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(b) FROM LivestockBreeding b WHERE b.livestock.id = :id")
    long countBreedingsByLivestock(@Param("id") UUID id);

    @Query("SELECT COUNT(bt) FROM LivestockBirth bt WHERE bt.livestock.id = :id")
    long countBirthsAsMother(@Param("id") UUID id);

    @Query("SELECT COUNT(o) FROM LivestockOffspring o " +
            "WHERE o.childLivestock.id = :id AND o.isDeleted = false")
    long countBirthsAsChild(@Param("id") UUID id);

    @Query("SELECT COUNT(s) FROM LivestockSale s WHERE s.livestock.id = :id")
    long countSales(@Param("id") UUID id);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.livestock.id = :id")
    long countTreatments(@Param("id") UUID id);

    @Query("SELECT COUNT(t) FROM LivestockTreatment t WHERE t.sickLivestock.id = :id")
    long countTreatmentsAsSickAnimal(@Param("id") UUID id);

    @Query("SELECT COUNT(sk) FROM LivestockSick sk WHERE sk.livestock.id = :id")
    long countSickRecords(@Param("id") UUID id);

    @Query("SELECT COUNT(d) FROM LivestockDeath d WHERE d.livestock.id = :id")
    long countDeaths(@Param("id") UUID id);

    @Query("SELECT COUNT(w) FROM LivestockWeightHistory w WHERE w.livestock.id = :id")
    long countWeightHistory(@Param("id") UUID id);

    @Query("SELECT COUNT(v) FROM LivestockValuation v WHERE v.livestock.id = :id")
    long countValuations(@Param("id") UUID id);

    @Query("SELECT COUNT(a) FROM LivestockAbortion a WHERE a.livestock.id = :id")
    long countAbortions(@Param("id") UUID id);

    @Query("SELECT COUNT(l2) FROM Livestock l2 WHERE l2.mother.id = :id")
    long countChildrenAsMother(@Param("id") UUID id);

    @Query("SELECT COUNT(l3) FROM Livestock l3 WHERE l3.father.id = :id")
    long countChildrenAsFather(@Param("id") UUID id);

    @Query("SELECT COUNT(l4) FROM Livestock l4 WHERE l4.draftBirthEvent.id = :id")
    long countDraftBirthReferences(@Param("id") UUID id);

    default long countLinkedRecords(UUID id) {
        return countBreedingsByLivestock(id)
                + countBirthsAsMother(id)
                + countBirthsAsChild(id)
                + countSales(id)
                + countTreatments(id)
                + countTreatmentsAsSickAnimal(id)
                + countSickRecords(id)
                + countDeaths(id)
                + countWeightHistory(id)
                + countValuations(id)
                + countAbortions(id)
                + countChildrenAsMother(id)
                + countChildrenAsFather(id)
                + countDraftBirthReferences(id);
    }
}