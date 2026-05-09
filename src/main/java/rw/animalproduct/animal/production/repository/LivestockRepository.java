package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Livestock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivestockRepository extends JpaRepository<Livestock, UUID> {

    // ── Basic finders ─────────────────────────────────────────────────────────

    Optional<Livestock> findByTagNumber(String tagNumber);

    List<Livestock> findByLivestockCategoryId(UUID categoryId);

    List<Livestock> findByBeneficiaryId(UUID beneficiaryId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countByCategory(@Param("categoryId") UUID categoryId);

    long countByStatus(String status);

    // ── Gender-based queries ──────────────────────────────────────────────────

    List<Livestock> findByGenderIgnoreCase(String gender);

    List<Livestock> findByGender(String gender);

    List<Livestock> findByGenderIgnoreCaseAndStatus(String gender, String status);

    long countByGenderIgnoreCase(String gender);

    long countByGenderIgnoreCaseAndStatus(String gender, String status);

    // ── Mother-child relationships ────────────────────────────────────────────

    List<Livestock> findByMotherId(UUID motherId);

    boolean existsByMotherId(UUID motherId);

    // ── Draft animal queries ──────────────────────────────────────────────────

    /**
     * Find all draft animals for a specific birth event.
     * Used on the "complete children" screen after recording a birth.
     */
    List<Livestock> findByDraftBirthEventIdAndIsDraftTrue(UUID birthEventId);

    /**
     * Count how many drafts are pending for a birth event.
     */
    long countByDraftBirthEventIdAndIsDraftTrue(UUID birthEventId);

    /**
     * Find all incomplete draft animals across all birth events.
     * Useful for an admin "incomplete registrations" warning page.
     */
    @Query("SELECT l FROM Livestock l WHERE l.isDraft = true AND l.isDeleted = false ORDER BY l.createdAt DESC")
    List<Livestock> findAllPendingDrafts();

    // ── Dashboard queries ─────────────────────────────────────────────────────

    List<Livestock> findByStatus(String status);

    @Query("SELECT l FROM Livestock l WHERE l.status NOT IN :statuses")
    List<Livestock> findByStatusNotIn(@Param("statuses") List<String> statuses);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.gender = 'MALE'")
    long countMales();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.gender = 'FEMALE'")
    long countFemales();

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l WHERE l.status = 'ACTIVE'")
    BigDecimal sumActiveValues();

    @Query("SELECT l.livestockCategory.name, COUNT(l) FROM Livestock l " +
            "WHERE l.livestockCategory IS NOT NULL " +
            "GROUP BY l.livestockCategory.name")
    List<Object[]> getCountByCategory();

    @Query("SELECT l.status, COUNT(l) FROM Livestock l GROUP BY l.status")
    List<Object[]> getCountByStatus();

    List<Livestock> findTop20ByOrderByCreatedAtDesc();

    @Query("SELECT l FROM Livestock l WHERE l.status = 'PREGNANT'")
    List<Livestock> findPregnantLivestock();

    @Query("SELECT l FROM Livestock l " +
            "WHERE UPPER(l.gender) = 'FEMALE' " +
            "AND l.status NOT IN ('DEAD', 'SOLD') " +
            "AND l.isDeleted = false " +
            "ORDER BY l.tagNumber")
    List<Livestock> findAllActiveFemales();

    @Query("SELECT l FROM Livestock l " +
            "WHERE UPPER(l.gender) = 'MALE' " +
            "AND l.status NOT IN ('DEAD', 'SOLD') " +
            "AND l.isDeleted = false " +
            "ORDER BY l.tagNumber")
    List<Livestock> findAllActiveMales();

    @Query("SELECT l FROM Livestock l WHERE l.gender = 'FEMALE' " +
            "AND (l.isDeleted = false OR l.isDeleted IS NULL) " +
            "AND l.status != 'SOLD' " +
            "AND l.status != 'DEAD' " +
            "AND (" +
            "  (l.birthDate IS NOT NULL AND l.birthDate <= :cutoffDate) " +
            "  OR (l.birthDate IS NULL AND l.acquisitionMethod = 'PURCHASE') " +
            ")")
    List<Livestock> findEligibleMothers(@Param("cutoffDate") LocalDate cutoffDate);

    // ── Pregnancy tracking — purchased / external animals ─────────────────────

    /**
     * Finds all female animals where {@code is_pregnant = true} but they have
     * NO confirmed-pregnant or pending breeding record linked to them.
     *
     * <p>These are typically purchased, donated, or transferred animals whose
     * pregnancy was flagged at intake via the livestock register form (the
     * "Currently Pregnant?" toggle), but were never linked to a
     * {@code LivestockBreeding} record through the breeding module.</p>
     *
     * <p>The pregnancy-tracking controller calls this and merges the results
     * with the standard {@code CONFIRMED_PREGNANT} breeding records so that
     * <em>all</em> pregnant animals appear on the tracking dashboard — not
     * just those bred on the farm.</p>
     *
     * <p>Exclusion logic: any livestock ID that already appears in a
     * {@code LivestockBreeding} row with status {@code CONFIRMED_PREGNANT}
     * or {@code PENDING} is omitted, preventing double-counting.</p>
     */
    @Query("SELECT l FROM Livestock l " +
            "WHERE l.isPregnant = true " +
            "AND UPPER(l.gender) = 'FEMALE' " +
            "AND (l.isDeleted = false OR l.isDeleted IS NULL) " +
            "AND l.status NOT IN ('DEAD', 'SOLD') " +
            "AND l.id NOT IN (" +
            "    SELECT b.livestock.id FROM LivestockBreeding b " +
            "    WHERE b.livestock IS NOT NULL " +
            "      AND b.status IN ('CONFIRMED_PREGNANT', 'PENDING')" +
            ") " +
            "ORDER BY l.tagNumber")
    List<Livestock> findPregnantWithoutBreedingRecord();
}