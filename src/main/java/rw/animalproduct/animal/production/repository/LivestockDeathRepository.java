package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockDeath;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockDeathRepository extends JpaRepository<LivestockDeath, UUID> {

    List<LivestockDeath> findByLivestockId(UUID livestockId);

    // ── PAGINATED WITH FETCH (needed for Beneficiary + Location columns) ──

    @Query(value = """
        SELECT d FROM LivestockDeath d
          JOIN FETCH d.livestock l
          LEFT JOIN FETCH l.beneficiary b
          LEFT JOIN FETCH b.location loc
        """,
            countQuery = "SELECT COUNT(d) FROM LivestockDeath d")
    Page<LivestockDeath> findAllWithOwner(Pageable pageable);

    @Query(value = """
        SELECT d FROM LivestockDeath d
          JOIN FETCH d.livestock l
          LEFT JOIN FETCH l.beneficiary b
          LEFT JOIN FETCH b.location loc
        WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(d.causeOfDeath) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(b.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(b.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(b.phone)     LIKE LOWER(CONCAT('%', :search, '%'))
        """,
            countQuery = """
        SELECT COUNT(d) FROM LivestockDeath d
          JOIN d.livestock l
          LEFT JOIN l.beneficiary b
        WHERE LOWER(l.tagNumber) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(d.causeOfDeath) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(b.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(b.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(b.phone)     LIKE LOWER(CONCAT('%', :search, '%'))
        """)
    Page<LivestockDeath> searchDeaths(@Param("search") String search, Pageable pageable);

    // ── AGGREGATIONS ────────────────────────────────────────

    @Query("SELECT COUNT(d) FROM LivestockDeath d")
    long countActive();

    @Query("SELECT d FROM LivestockDeath d WHERE d.deathDate BETWEEN :start AND :end")
    List<LivestockDeath> findByDeathDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(d) FROM LivestockDeath d " +
            "WHERE YEAR(d.deathDate) = :year AND MONTH(d.deathDate) = :month")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT d.causeOfDeath, COUNT(d) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != '' " +
            "GROUP BY d.causeOfDeath ORDER BY COUNT(d) DESC")
    List<Object[]> getDeathCausesDistribution();

    @Query("SELECT d.causeOfDeath, COUNT(d) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != '' " +
            "GROUP BY d.causeOfDeath ORDER BY COUNT(d) DESC")
    List<Object[]> findMostCommonCause();

    @Query("SELECT COUNT(DISTINCT d.causeOfDeath) FROM LivestockDeath d " +
            "WHERE d.causeOfDeath IS NOT NULL AND d.causeOfDeath != ''")
    long countDistinctCauses();

    @Query("SELECT d.livestock.livestockCategory.name, COUNT(d) " +
            "FROM LivestockDeath d " +
            "WHERE d.livestock.livestockCategory IS NOT NULL " +
            "GROUP BY d.livestock.livestockCategory.name")
    List<Object[]> getDeathsByCategory();

    @Query("SELECT d FROM LivestockDeath d ORDER BY d.deathDate DESC")
    List<LivestockDeath> findTop50ByOrderByDeathDateDesc(Pageable pageable);
}