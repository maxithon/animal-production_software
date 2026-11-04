package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockNotificationLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LivestockNotificationLogRepository extends JpaRepository<LivestockNotificationLog, UUID> {

    /**
     * Check if a notification with the given type and dedupe key already exists.
     * This is the main deduplication check used by the scheduler.
     */
    boolean existsByNotificationTypeAndDedupeKey(String notificationType, String dedupeKey);

    /**
     * Get all notifications for a specific animal.
     */
    List<LivestockNotificationLog> findByLivestockId(UUID livestockId);

    /**
     * Get all notifications of a specific type.
     */
    List<LivestockNotificationLog> findByNotificationType(String notificationType);

    /**
     * Get notifications sent in a date range.
     *
     * NOTE: uses sentAt (the real DB column) instead of the old sentDate field.
     * The method name is kept as findBySentDateBetween for backward compatibility
     * with any callers, and we provide an explicit @Query so Spring Data doesn't
     * try to auto-derive a query from the field name.
     */
    @Query("SELECT l FROM LivestockNotificationLog l " +
            "WHERE l.sentAt >= :from AND l.sentAt < :to " +
            "ORDER BY l.sentAt DESC")
    List<LivestockNotificationLog> findBySentDateBetween(
            @Param("from") LocalDateTime fromDateTime,
            @Param("to") LocalDateTime toDateTime);

    /**
     * Convenience overload — accepts LocalDate and converts to a half-open
     * LocalDateTime range [from 00:00, to+1day 00:00).
     */
    default List<LivestockNotificationLog> findBySentDateBetween(LocalDate fromDate, LocalDate toDate) {
        return findBySentDateBetween(
                fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay());
    }

    /**
     * Count notifications by type.
     */
    long countByNotificationType(String notificationType);

    /**
     * Find the latest notifications for a specific animal and type.
     * NOTE: orders by sentAt (real DB column) — the old createdAt field no
     * longer exists in the entity, and referenceDate is a natural tie-breaker.
     */
    @Query("SELECT l FROM LivestockNotificationLog l " +
            "WHERE l.livestockId = :livestockId AND l.notificationType = :type " +
            "ORDER BY l.sentAt DESC")
    List<LivestockNotificationLog> findLatestByLivestockAndType(
            @Param("livestockId") UUID livestockId,
            @Param("type") String notificationType);
}