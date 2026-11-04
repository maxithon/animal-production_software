package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.AuditLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByChangedAtDesc(String entityType, UUID entityId);

    Page<AuditLog> findByIsDeletedFalseOrderByChangedAtDesc(Pageable pageable);

    Page<AuditLog> findByEntityTypeAndIsDeletedFalseOrderByChangedAtDesc(String entityType, Pageable pageable);

    long countByIsDeletedFalse();
    long countByActionAndIsDeletedFalse(String action);
    long countByEntityTypeAndIsDeletedFalse(String entityType);
    long countByEntityTypeAndActionAndIsDeletedFalse(String entityType, String action);

    @Query("SELECT COUNT(a) FROM AuditLog a " +
            "WHERE a.isDeleted = false AND a.entityType = :entityType " +
            "  AND (a.action = 'DELETE' OR a.action = 'SOFT_DELETE')")
    long countDeletesForEntityType(@Param("entityType") String entityType);

    @Query("SELECT COUNT(a) FROM AuditLog a " +
            "WHERE a.isDeleted = false " +
            "  AND (a.action = 'DELETE' OR a.action = 'SOFT_DELETE')")
    long countAllDeletes();

    @Query("SELECT a.entityType, COUNT(a) FROM AuditLog a " +
            "WHERE a.isDeleted = false GROUP BY a.entityType")
    List<Object[]> countAllGroupedByEntityType();

    @Query("SELECT a.entityType, COUNT(a) FROM AuditLog a " +
            "WHERE a.isDeleted = false AND a.action = :action GROUP BY a.entityType")
    List<Object[]> countGroupedByEntityTypeAndAction(@Param("action") String action);

    @Query("SELECT a.entityType, COUNT(a) FROM AuditLog a " +
            "WHERE a.isDeleted = false " +
            "  AND (a.action = 'DELETE' OR a.action = 'SOFT_DELETE') " +
            "GROUP BY a.entityType")
    List<Object[]> countDeletesGroupedByEntityType();
}