package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockSick;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockSickRepository extends JpaRepository<LivestockSick, UUID> {

    /* ============================================================
     *  EXISTING METHODS
     * ============================================================ */

    List<LivestockSick> findByLivestockId(UUID livestockId);

    @Query("SELECT s FROM LivestockSick s WHERE s.livestock.id IN :ids AND s.isDeleted = false")
    List<LivestockSick> findByLivestockIds(@Param("ids") List<UUID> ids);

    List<LivestockSick> findByReportedDateBetween(LocalDate start, LocalDate end);

    List<LivestockSick> findByReportedDateBetweenOrderByReportedDateDesc(LocalDate start, LocalDate end);

    List<LivestockSick> findByReportedDateBetweenAndIsDeletedFalse(LocalDate start, LocalDate end);

    @Query("SELECT DISTINCT s FROM LivestockSick s " +
            "LEFT JOIN FETCH s.statusHistory " +
            "WHERE s.reportedDate BETWEEN :start AND :end " +
            "ORDER BY s.reportedDate DESC")
    List<LivestockSick> findByReportedDateBetweenWithHistory(@Param("start") LocalDate start,
                                                             @Param("end") LocalDate end);

    List<LivestockSick> findByIsDeletedFalseOrderByReportedDateDesc();

    Page<LivestockSick> findByIsDeletedFalse(Pageable pageable);

    @Query("SELECT s FROM LivestockSick s " +
            "WHERE s.isDeleted = false " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND (:severity IS NULL OR s.severityLevel = :severity) " +
            "AND (:tagQuery IS NULL OR LOWER(s.livestock.tagNumber) LIKE LOWER(CONCAT('%', :tagQuery, '%'))) " +
            "ORDER BY s.reportedDate DESC")
    Page<LivestockSick> searchActive(@Param("status") LivestockSick.SickStatus status,
                                     @Param("severity") LivestockSick.SeverityLevel severity,
                                     @Param("tagQuery") String tagQuery,
                                     Pageable pageable);

    long countByStatusAndIsDeletedFalse(LivestockSick.SickStatus status);

    @Query("SELECT COUNT(s) FROM LivestockSick s " +
            "WHERE YEAR(s.reportedDate) = :year AND MONTH(s.reportedDate) = :month " +
            "AND s.isDeleted = false")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT s.livestock.tagNumber, COUNT(s) FROM LivestockSick s " +
            "WHERE s.livestock IS NOT NULL AND s.isDeleted = false " +
            "GROUP BY s.livestock.tagNumber ORDER BY COUNT(s) DESC")
    List<Object[]> findMostSickAnimal();

    @Query("SELECT s.severityLevel, COUNT(s) FROM LivestockSick s " +
            "WHERE s.severityLevel IS NOT NULL AND s.isDeleted = false " +
            "GROUP BY s.severityLevel")
    List<Object[]> getSeverityDistribution();

    @Query("SELECT COUNT(s) FROM LivestockSick s " +
            "WHERE s.status = 'RECOVERED' " +
            "AND s.recoveryDate BETWEEN :start AND :end " +
            "AND s.isDeleted = false")
    long countRecoveredInDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT s FROM LivestockSick s " +
            "WHERE s.status IN ('SICK', 'CRITICAL', 'RECOVERING') " +
            "AND s.isDeleted = false " +
            "ORDER BY s.severityLevel DESC, s.reportedDate DESC")
    List<LivestockSick> findActiveSickAnimals();

    @Query("SELECT s FROM LivestockSick s " +
            "WHERE s.status = 'CRITICAL' AND s.isDeleted = false " +
            "ORDER BY s.reportedDate DESC")
    List<LivestockSick> findCriticalCases();

    /**
     * ✅ FIXED: In PostgreSQL, `date - date` already returns an integer
     * (number of days), so `EXTRACT(DAY FROM ...)` was invalid — it only
     * works on interval/date/timestamp, not on an integer.
     *
     * Old (broken):
     *   SELECT AVG(EXTRACT(DAY FROM (recovery_date - reported_date)))
     *   → "function pg_catalog.extract(unknown, integer) does not exist"
     *
     * New (correct):
     *   SELECT AVG(recovery_date - reported_date)
     */
    @Query(value = "SELECT AVG(recovery_date - reported_date) " +
            "FROM livestock_sick " +
            "WHERE status = 'RECOVERED' AND recovery_date IS NOT NULL " +
            "AND is_deleted = false",
            nativeQuery = true)
    Double getAverageRecoveryDays();

    /* ============================================================
     *  REPORT AGGREGATION QUERIES
     * ============================================================ */

    @Query("SELECT COUNT(s) FROM LivestockSick s " +
            "WHERE s.reportedDate BETWEEN :from AND :to AND s.isDeleted = false")
    long countInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT s.status, COUNT(s)
        FROM LivestockSick s
        WHERE s.reportedDate BETWEEN :from AND :to AND s.isDeleted = false
        GROUP BY s.status
        """)
    List<Object[]> countByStatusInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT s FROM LivestockSick s " +
            "WHERE s.isDeleted = false " +
            "  AND s.status IN ('SICK', 'CRITICAL', 'RECOVERING') " +
            "ORDER BY CASE s.status WHEN 'CRITICAL' THEN 1 " +
            "                      WHEN 'SICK' THEN 2 " +
            "                      WHEN 'RECOVERING' THEN 3 ELSE 4 END, " +
            "         s.reportedDate ASC")
    List<LivestockSick> findCasesRequiringAttention();

    @Query("""
        SELECT s.reportedDate, COUNT(s)
        FROM LivestockSick s
        WHERE s.reportedDate BETWEEN :from AND :to AND s.isDeleted = false
        GROUP BY s.reportedDate
        ORDER BY s.reportedDate
        """)
    List<Object[]> dailySickCounts(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT s.recoveryDate,
               SUM(CASE WHEN s.status = 'RECOVERED' THEN 1 ELSE 0 END),
               COUNT(s)
        FROM LivestockSick s
        WHERE s.recoveryDate BETWEEN :from AND :to
          AND s.isDeleted = false
          AND s.recoveryDate IS NOT NULL
        GROUP BY s.recoveryDate
        ORDER BY s.recoveryDate
        """)
    List<Object[]> dailyRecovery(@Param("from") LocalDate from, @Param("to") LocalDate to);
}