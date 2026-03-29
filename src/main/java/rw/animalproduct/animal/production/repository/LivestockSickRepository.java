package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import rw.animalproduct.animal.production.entity.LivestockSick;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockSickRepository extends JpaRepository<LivestockSick, UUID> {

    // =========================================
    // Sick records by one livestock
    // =========================================
    List<LivestockSick> findByLivestockId(UUID livestockId);

    // =========================================
    // Sick records by multiple livestock IDs
    // =========================================
    @Query("""
        SELECT s
        FROM LivestockSick s
        WHERE s.livestock.id IN :animalIds
    """)
    List<LivestockSick> findByLivestockIds(@Param("animalIds") List<UUID> animalIds);

    // =========================================
    // Sick statistics grouped by animal
    // =========================================
    @Query("""
        SELECT s.livestock.id,
               COUNT(s),
               SUM(CASE WHEN s.status = 'CRITICAL' THEN 1 ELSE 0 END),
               SUM(CASE WHEN s.status = 'RECOVERED' THEN 1 ELSE 0 END),
               COALESCE(SUM(s.treatmentCost), 0)
        FROM LivestockSick s
        WHERE s.livestock.id IN :animalIds
        GROUP BY s.livestock.id
    """)
    List<Object[]> sickStatsByAnimalIds(@Param("animalIds") List<UUID> animalIds);

    // =========================================
    // Total treatment cost for selected animals
    // =========================================
    @Query("""
        SELECT COALESCE(SUM(s.treatmentCost), 0)
        FROM LivestockSick s
        WHERE s.livestock.id IN :animalIds
    """)
    BigDecimal totalSickCostByAnimalIds(@Param("animalIds") List<UUID> animalIds);

    // =========================================
    // Report by date range
    // =========================================
    @Query("""
        SELECT DISTINCT s
        FROM LivestockSick s
        LEFT JOIN FETCH s.statusHistory
        LEFT JOIN FETCH s.livestock l
        LEFT JOIN FETCH l.livestockCategory
        WHERE s.reportedDate BETWEEN :from AND :to
        ORDER BY s.reportedDate DESC
    """)
    List<LivestockSick> findByReportedDateBetweenWithHistory(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}