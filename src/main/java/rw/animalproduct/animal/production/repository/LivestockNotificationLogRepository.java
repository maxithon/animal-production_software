package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.LivestockNotificationLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LivestockNotificationLogRepository extends JpaRepository<LivestockNotificationLog, UUID> {

    boolean existsByNotificationTypeAndDedupeKey(String notificationType, String dedupeKey);

    List<LivestockNotificationLog> findByLivestockId(UUID livestockId);

    @Modifying
    @Transactional
    @Query("DELETE FROM LivestockNotificationLog l WHERE l.livestockId = :livestockId")
    void deleteByLivestockId(@Param("livestockId") UUID livestockId);

    List<LivestockNotificationLog> findByNotificationType(String notificationType);

    @Query("SELECT l FROM LivestockNotificationLog l WHERE l.sentAt >= :from AND l.sentAt < :to ORDER BY l.sentAt DESC")
    List<LivestockNotificationLog> findBySentDateBetween(@Param("from") LocalDateTime fromDateTime, @Param("to") LocalDateTime toDateTime);

    default List<LivestockNotificationLog> findBySentDateBetween(LocalDate fromDate, LocalDate toDate) {
        return findBySentDateBetween(fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());
    }

    long countByNotificationType(String notificationType);

    @Query("SELECT l FROM LivestockNotificationLog l WHERE l.livestockId = :livestockId AND l.notificationType = :type ORDER BY l.sentAt DESC")
    List<LivestockNotificationLog> findLatestByLivestockAndType(@Param("livestockId") UUID livestockId, @Param("type") String notificationType);
}