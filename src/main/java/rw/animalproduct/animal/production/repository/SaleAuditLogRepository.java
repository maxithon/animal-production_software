package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.SaleAuditLog;

import java.util.List;
import java.util.UUID;

public interface SaleAuditLogRepository extends JpaRepository<SaleAuditLog, UUID> {

    List<SaleAuditLog> findBySaleIdOrderByPerformedAtDesc(UUID saleId);
}