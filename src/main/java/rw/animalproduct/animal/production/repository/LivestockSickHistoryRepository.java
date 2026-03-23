package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.entity.LivestockSickHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LivestockSickHistoryRepository extends JpaRepository<LivestockSickHistory, UUID> {

    // ── Per sick episode ─────────────────────────────────────────────

    /** Full timeline for one sick episode, oldest first. */
    List<LivestockSickHistory> findByLivestockSickIdOrderByChangedAtAsc(UUID sickId);

    // ── Per animal ───────────────────────────────────────────────────

    /** All history rows for a specific livestock animal across all sick episodes. */
    @Query("SELECT h FROM LivestockSickHistory h " +
           "WHERE h.livestockSick.livestock.id = :livestockId " +
           "ORDER BY h.changedAt DESC")
    List<LivestockSickHistory> findByLivestockId(@Param("livestockId") UUID livestockId);

    // ── Date-range queries (for reports) ─────────────────────────────

    /** All history rows where the change happened within a date range. */
    @Query("SELECT h FROM LivestockSickHistory h " +
           "WHERE h.changedAt BETWEEN :from AND :to " +
           "ORDER BY h.changedAt DESC")
    List<LivestockSickHistory> findByDateRange(@Param("from") LocalDateTime from,
                                               @Param("to")   LocalDateTime to);

    /**
     * All SICK initial records reported within a date range.
     * Use this for "how many animals got sick in January 2025".
     */
    @Query("SELECT h FROM LivestockSickHistory h " +
           "WHERE h.status = 'SICK' " +
           "AND h.changedAt BETWEEN :from AND :to " +
           "ORDER BY h.changedAt DESC")
    List<LivestockSickHistory> findNewSickCasesByDateRange(@Param("from") LocalDateTime from,
                                                           @Param("to")   LocalDateTime to);

    /**
     * All CRITICAL status changes within a date range.
     * Use this for "which animals went critical in 2025".
     */
    @Query("SELECT h FROM LivestockSickHistory h " +
           "WHERE h.status = 'CRITICAL' " +
           "AND h.changedAt BETWEEN :from AND :to " +
           "ORDER BY h.changedAt DESC")
    List<LivestockSickHistory> findCriticalCasesByDateRange(@Param("from") LocalDateTime from,
                                                            @Param("to")   LocalDateTime to);

    /**
     * All RECOVERED status changes within a date range.
     * Use this for "how many animals recovered in March 2025".
     */
    @Query("SELECT h FROM LivestockSickHistory h " +
           "WHERE h.status = 'RECOVERED' " +
           "AND h.changedAt BETWEEN :from AND :to " +
           "ORDER BY h.changedAt DESC")
    List<LivestockSickHistory> findRecoveredCasesByDateRange(@Param("from") LocalDateTime from,
                                                             @Param("to")   LocalDateTime to);

    // ── Count queries ────────────────────────────────────────────────

    /** Count of CRITICAL events in a specific year. */
    @Query("SELECT COUNT(h) FROM LivestockSickHistory h " +
           "WHERE h.status = 'CRITICAL' " +
           "AND YEAR(h.changedAt) = :year")
    long countCriticalByYear(@Param("year") int year);

    /** Count of RECOVERED events in a specific year. */
    @Query("SELECT COUNT(h) FROM LivestockSickHistory h " +
           "WHERE h.status = 'RECOVERED' " +
           "AND YEAR(h.changedAt) = :year")
    long countRecoveredByYear(@Param("year") int year);

    /** Count of initial SICK events in a specific year. */
    @Query("SELECT COUNT(h) FROM LivestockSickHistory h " +
           "WHERE h.status = 'SICK' " +
           "AND YEAR(h.changedAt) = :year")
    long countSickByYear(@Param("year") int year);

    // ── By status ────────────────────────────────────────────────────

    List<LivestockSickHistory> findByStatusOrderByChangedAtDesc(LivestockSick.SickStatus status);
}
