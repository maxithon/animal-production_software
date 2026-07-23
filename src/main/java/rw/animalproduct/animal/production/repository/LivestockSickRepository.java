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

    // ========== BASIC FINDERS ==========

    /**
     * Find all sick records for a specific livestock animal
     */
    List<LivestockSick> findByLivestockId(UUID livestockId);

    /**
     * Find all sick records for a list of livestock IDs
     */
    @Query("SELECT s FROM LivestockSick s WHERE s.livestock.id IN :ids AND s.isDeleted = false")
    List<LivestockSick> findByLivestockIds(@Param("ids") List<UUID> ids);

    // ========== DATE RANGE QUERIES ==========

    /**
     * Get sick records reported between two dates (standard Spring Data JPA)
     */
    List<LivestockSick> findByReportedDateBetween(LocalDate start, LocalDate end);

    /**
     * Get sick records reported between two dates with ordering
     */
    List<LivestockSick> findByReportedDateBetweenOrderByReportedDateDesc(LocalDate start, LocalDate end);

    /**
     * Get sick records reported between two dates with isDeleted filter
     */
    List<LivestockSick> findByReportedDateBetweenAndIsDeletedFalse(LocalDate start, LocalDate end);

    /**
     * Get sick records with their history/offspring data between dates
     * Uses JOIN FETCH to avoid N+1 queries
     */
    @Query("SELECT DISTINCT s FROM LivestockSick s " +
            "LEFT JOIN FETCH s.statusHistory " +
            "WHERE s.reportedDate BETWEEN :start AND :end " +
            "ORDER BY s.reportedDate DESC")
    List<LivestockSick> findByReportedDateBetweenWithHistory(@Param("start") LocalDate start,
                                                             @Param("end") LocalDate end);

    // ========== DASHBOARD QUERIES ==========

    /**
     * Get all non-deleted sick records ordered by reported date
     */
    List<LivestockSick> findByIsDeletedFalseOrderByReportedDateDesc();

    /**
     * ✅ ADDED: Pageable version of the query above.
     *
     * WHY: findByIsDeletedFalseOrderByReportedDateDesc() (no arguments) pulls
     * EVERY non-deleted sick record into memory in one shot — fine at a few
     * hundred rows, but it's the thing that will eventually make the Sick
     * Livestock page slow to load as your data grows, the same way the old
     * Animal(Tag) <select> was slow. This overload lets the controller ask
     * for one page at a time instead.
     *
     * Example controller usage:
     *   Pageable pageable = PageRequest.of(page, size, Sort.by("reportedDate").descending());
     *   Page<LivestockSick> result = sickRepository.findByIsDeletedFalse(pageable);
     *   model.addAttribute("sickRecords", result.getContent());
     *   model.addAttribute("totalPages", result.getTotalPages());
     *   model.addAttribute("currentPage", page);
     */
    Page<LivestockSick> findByIsDeletedFalse(Pageable pageable);

    /**
     * ✅ ADDED: One query that covers the list page's three filters
     * (status, severity, animal-tag search) AND pagination together, run in
     * the database instead of in the browser. Pass null for any filter you
     * don't want applied — e.g. searchActive(null, null, "GOA", pageable)
     * searches only by tag.
     *
     * This is what you'd swap the controller over to if/when the client-side
     * JS filtering + pagination on the list page starts to feel slow with a
     * large herd — it currently still loads the full active list into the
     * page and paginates in JS, which is fine for a few hundred rows but not
     * indefinitely.
     */
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

    /**
     * Count sick animals by status
     */
    long countByStatusAndIsDeletedFalse(LivestockSick.SickStatus status);

    /**
     * Count sick reports by month/year
     */
    @Query("SELECT COUNT(s) FROM LivestockSick s " +
            "WHERE YEAR(s.reportedDate) = :year AND MONTH(s.reportedDate) = :month " +
            "AND s.isDeleted = false")
    long countByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Find most frequently sick animal
     */
    @Query("SELECT s.livestock.tagNumber, COUNT(s) FROM LivestockSick s " +
            "WHERE s.livestock IS NOT NULL AND s.isDeleted = false " +
            "GROUP BY s.livestock.tagNumber ORDER BY COUNT(s) DESC")
    List<Object[]> findMostSickAnimal();

    /**
     * Get severity level distribution
     */
    @Query("SELECT s.severityLevel, COUNT(s) FROM LivestockSick s " +
            "WHERE s.severityLevel IS NOT NULL AND s.isDeleted = false " +
            "GROUP BY s.severityLevel")
    List<Object[]> getSeverityDistribution();

    /**
     * Get recovery statistics for a time period
     */
    @Query("SELECT COUNT(s) FROM LivestockSick s " +
            "WHERE s.status = 'RECOVERED' " +
            "AND s.recoveryDate BETWEEN :start AND :end " +
            "AND s.isDeleted = false")
    long countRecoveredInDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Get currently sick animals (not recovered)
     */
    @Query("SELECT s FROM LivestockSick s " +
            "WHERE s.status IN ('SICK', 'CRITICAL', 'RECOVERING') " +
            "AND s.isDeleted = false " +
            "ORDER BY s.severityLevel DESC, s.reportedDate DESC")
    List<LivestockSick> findActiveSickAnimals();

    /**
     * Get critical cases requiring immediate attention
     */
    @Query("SELECT s FROM LivestockSick s " +
            "WHERE s.status = 'CRITICAL' AND s.isDeleted = false " +
            "ORDER BY s.reportedDate DESC")
    List<LivestockSick> findCriticalCases();

    /**
     * Calculate average recovery time in days
     * ✅ FIXED: Using native PostgreSQL query for DATEDIFF calculation
     */
    @Query(value = "SELECT AVG(EXTRACT(DAY FROM (recovery_date - reported_date))) " +
            "FROM livestock_sick " +
            "WHERE status = 'RECOVERED' AND recovery_date IS NOT NULL " +
            "AND is_deleted = false",
            nativeQuery = true)
    Double getAverageRecoveryDays();
}
