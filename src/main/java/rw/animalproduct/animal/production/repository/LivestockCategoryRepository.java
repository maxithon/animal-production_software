        package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.LivestockCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockCategoryRepository
        extends JpaRepository<LivestockCategory, UUID> {

    // ---------------------------------------------------------
    // Basic lookups
    // ---------------------------------------------------------

    Optional<LivestockCategory> findByCode(String code);

    Optional<LivestockCategory> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    // ---------------------------------------------------------
    // Active categories
    // ---------------------------------------------------------

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.isDeleted = false
               OR c.isDeleted IS NULL
            """)
    List<LivestockCategory> findAllByIsDeletedFalse();

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.isDeleted = false
               OR c.isDeleted IS NULL
            ORDER BY c.name ASC
            """)
    List<LivestockCategory> findAllActiveOrderByName();

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.isDeleted = false
               OR c.isDeleted IS NULL
            ORDER BY c.code ASC
            """)
    List<LivestockCategory> findAllActiveOrderByCode();

    // ---------------------------------------------------------
    // Duplicate checks
    // ---------------------------------------------------------

    @Query("""
            SELECT COUNT(c) > 0
            FROM LivestockCategory c
            WHERE c.code = :code
              AND c.id != :id
              AND (c.isDeleted = false OR c.isDeleted IS NULL)
            """)
    boolean existsByCodeAndIdNot(
            @Param("code") String code,
            @Param("id") UUID id
    );

    @Query("""
            SELECT COUNT(c) > 0
            FROM LivestockCategory c
            WHERE c.name = :name
              AND c.id != :id
              AND (c.isDeleted = false OR c.isDeleted IS NULL)
            """)
    boolean existsByNameAndIdNot(
            @Param("name") String name,
            @Param("id") UUID id
    );

    // ---------------------------------------------------------
    // Search
    // ---------------------------------------------------------

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE (c.isDeleted = false OR c.isDeleted IS NULL)
              AND (
                    LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                    OR
                    LOWER(c.code) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                  )
            ORDER BY c.name ASC
            """)
    List<LivestockCategory> searchActiveCategories(
            @Param("searchTerm") String searchTerm
    );

    // ---------------------------------------------------------
    // Count
    // ---------------------------------------------------------

    @Query("""
            SELECT COUNT(c)
            FROM LivestockCategory c
            WHERE c.isDeleted = false
               OR c.isDeleted IS NULL
            """)
    long countActiveCategories();

    // ---------------------------------------------------------
    // Breeding
    // ---------------------------------------------------------

    /**
     * Returns active categories that can be used
     * in breeding workflows.
     *
     * There is no can_breed column in the database.
     * Therefore, all active livestock categories are
     * considered available for breeding workflows.
     */
    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.isDeleted = false
               OR c.isDeleted IS NULL
            ORDER BY c.name ASC
            """)
    List<LivestockCategory> findActiveBreedingEligible();

    /**
     * Find active categories that have a gestation period.
     */
    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE (c.isDeleted = false OR c.isDeleted IS NULL)
              AND c.gestationPeriodMonths IS NOT NULL
            ORDER BY c.name ASC
            """)
    List<LivestockCategory> findActiveWithGestationPeriod();

    // ---------------------------------------------------------
    // Gestation / breeding helpers
    // ---------------------------------------------------------

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.isDeleted = false
               OR c.isDeleted IS NULL
            ORDER BY c.gestationPeriodDays ASC
            """)
    List<LivestockCategory> findActiveOrderByGestationPeriod();

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE (c.isDeleted = false OR c.isDeleted IS NULL)
              AND c.minBreedingAgeMonths IS NOT NULL
            ORDER BY c.minBreedingAgeMonths ASC
            """)
    List<LivestockCategory> findActiveWithBreedingAge();

    // ---------------------------------------------------------
    // Code / name searches
    // ---------------------------------------------------------

    @Query("""
            SELECT COUNT(c) > 0
            FROM LivestockCategory c
            WHERE c.code = :code
              AND (c.isDeleted = false OR c.isDeleted IS NULL)
            """)
    boolean existsActiveByCode(
            @Param("code") String code
    );

    @Query("""
            SELECT COUNT(c) > 0
            FROM LivestockCategory c
            WHERE c.name = :name
              AND (c.isDeleted = false OR c.isDeleted IS NULL)
            """)
    boolean existsActiveByName(
            @Param("name") String name
    );

    // ---------------------------------------------------------
    // Including deleted records
    // ---------------------------------------------------------

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.code = :code
            """)
    Optional<LivestockCategory> findByCodeIncludingDeleted(
            @Param("code") String code
    );

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.name = :name
            """)
    Optional<LivestockCategory> findByNameIncludingDeleted(
            @Param("name") String name
    );

    @Query("""
            SELECT c
            FROM LivestockCategory c
            ORDER BY c.name ASC
            """)
    List<LivestockCategory> findAllIncludingDeleted();

    @Query("""
            SELECT c
            FROM LivestockCategory c
            WHERE c.isDeleted = true
            ORDER BY c.name ASC
            """)
    List<LivestockCategory> findDeletedCategories();

    // ---------------------------------------------------------
    // Restore
    // ---------------------------------------------------------

    @Modifying
    @Transactional
    @Query("""
            UPDATE LivestockCategory c
            SET c.isDeleted = false
            WHERE c.id = :id
            """)
    int restoreCategory(
            @Param("id") UUID id
    );

    // ---------------------------------------------------------
    // Soft delete
    // ---------------------------------------------------------

    @Modifying
    @Transactional
    @Query("""
            UPDATE LivestockCategory c
            SET c.isDeleted = true
            WHERE c.id = :id
            """)
    int softDeleteCategory(
            @Param("id") UUID id
    );
}