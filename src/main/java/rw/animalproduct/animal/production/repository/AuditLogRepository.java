package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.AuditLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByChangedAtDesc(
            String entityType, UUID entityId);

    Page<AuditLog> findByIsDeletedFalseOrderByChangedAtDesc(Pageable pageable);

    Page<AuditLog> findByEntityTypeAndIsDeletedFalseOrderByChangedAtDesc(
            String entityType, Pageable pageable);
}