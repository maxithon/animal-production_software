package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // ── Basic finders (EXCLUDING soft-deleted by default) ─────────────────────

    @Override
    @Query("SELECT l FROM Livestock l WHERE l.isDeleted = false OR l.isDeleted IS NULL")
    List<Livestock> findAll();

    @Query("SELECT l FROM Livestock l WHERE l.isDeleted = false OR l.isDeleted IS NULL")
    Page<Livestock> findAll(Pageable pageable);

    @Query("SELECT l FROM Livestock l WHERE l.id = :id AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    Optional<Livestock> findByIdNotDeleted(@Param("id") UUID id);

    @Query("SELECT l FROM Livestock l WHERE l.tagNumber = :tagNumber AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    Optional<Livestock> findByTagNumberNotDeleted(@Param("tagNumber") String tagNumber);

    // Include deleted records (for admin/recovery)
    @Query("SELECT l FROM Livestock l")
    List<Livestock> findAllIncludingDeleted();

    @Query("SELECT l FROM Livestock l WHERE l.isDeleted = true")
    List<Livestock> findAllSoftDeleted();

    Optional<Livestock> findByTagNumber(String tagNumber);

    // ── Category and Beneficiary queries ──────────────────────────────────────

    @Query("SELECT l FROM Livestock l WHERE l.livestockCategory.id = :categoryId AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByLivestockCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT l FROM Livestock l WHERE l.beneficiary.id = :beneficiaryId AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByBeneficiaryId(@Param("beneficiaryId") UUID beneficiaryId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    long countByCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = :status AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    long countByStatus(@Param("status") String status);

    // ── Gender-based queries ──────────────────────────────────────────────────

    @Query("SELECT l FROM Livestock l WHERE UPPER(l.gender) = UPPER(:gender) AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByGenderIgnoreCase(@Param("gender") String gender);

    @Query("SELECT l FROM Livestock l WHERE l.gender = :gender AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByGender(@Param("gender") String gender);

    @Query("SELECT l FROM Livestock l WHERE UPPER(l.gender) = UPPER(:gender) AND l.status = :status AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByGenderIgnoreCaseAndStatus(@Param("gender") String gender, @Param("status") String status);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE UPPER(l.gender) = UPPER(:gender) AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    long countByGenderIgnoreCase(@Param("gender") String gender);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE UPPER(l.gender) = UPPER(:gender) AND l.status = :status AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    long countByGenderIgnoreCaseAndStatus(@Param("gender") String gender, @Param("status") String status);

    // ── Mother-child relationships ────────────────────────────────────────────

    @Query("SELECT l FROM Livestock l WHERE l.mother.id = :motherId AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByMotherId(@Param("motherId") UUID motherId);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM Livestock l WHERE l.mother.id = :motherId AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    boolean existsByMotherId(@Param("motherId") UUID motherId);

    // ── Draft animal queries ──────────────────────────────────────────────────

    @Query("SELECT l FROM Livestock l WHERE l.draftBirthEvent.id = :birthEventId AND l.isDraft = true AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByDraftBirthEventIdAndIsDraftTrue(@Param("birthEventId") UUID birthEventId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.draftBirthEvent.id = :birthEventId AND l.isDraft = true AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    long countByDraftBirthEventIdAndIsDraftTrue(@Param("birthEventId") UUID birthEventId);

    @Query("SELECT l FROM Livestock l WHERE l.isDraft = true AND (l.isDeleted = false OR l.isDeleted IS NULL) ORDER BY l.createdAt DESC")
    List<Livestock> findAllPendingDrafts();

    // ── Status queries ────────────────────────────────────────────────────────

    @Query("SELECT l FROM Livestock l WHERE l.status = :status AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByStatus(@Param("status") String status);

    @Query("SELECT l FROM Livestock l WHERE l.status NOT IN :statuses AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findByStatusNotIn(@Param("statuses") List<String> statuses);

    // ── Dashboard stats ───────────────────────────────────────────────────────

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.gender = 'MALE' AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    long countMales();

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.gender = 'FEMALE' AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    long countFemales();

    @Query("SELECT COALESCE(SUM(l.currentValue), 0) FROM Livestock l WHERE l.status = 'ACTIVE' AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    BigDecimal sumActiveValues();

    @Query("SELECT l.livestockCategory.name, COUNT(l) FROM Livestock l " +
            "WHERE l.livestockCategory IS NOT NULL " +
            "AND (l.isDeleted = false OR l.isDeleted IS NULL) " +
            "GROUP BY l.livestockCategory.name")
    List<Object[]> getCountByCategory();

    @Query("SELECT l.status, COUNT(l) FROM Livestock l " +
            "WHERE (l.isDeleted = false OR l.isDeleted IS NULL) " +
            "GROUP BY l.status")
    List<Object[]> getCountByStatus();

    @Query("SELECT l FROM Livestock l WHERE (l.isDeleted = false OR l.isDeleted IS NULL) ORDER BY l.createdAt DESC")
    List<Livestock> findTop20ByOrderByCreatedAtDesc();

    @Query("SELECT l FROM Livestock l WHERE l.status = 'PREGNANT' AND (l.isDeleted = false OR l.isDeleted IS NULL)")
    List<Livestock> findPregnantLivestock();

    // ── Active animals for breeding ──────────────────────────────────────────

    @Query("SELECT l FROM Livestock l " +
            "WHERE UPPER(l.gender) = 'FEMALE' " +
            "AND l.status NOT IN ('DEAD', 'SOLD') " +
            "AND (l.isDeleted = false OR l.isDeleted IS NULL) " +
            "ORDER BY l.tagNumber")
    List<Livestock> findAllActiveFemales();

    @Query("SELECT l FROM Livestock l " +
            "WHERE UPPER(l.gender) = 'MALE' " +
            "AND l.status NOT IN ('DEAD', 'SOLD') " +
            "AND (l.isDeleted = false OR l.isDeleted IS NULL) " +
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

    // ── Pregnancy tracking without breeding record ───────────────────────────

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