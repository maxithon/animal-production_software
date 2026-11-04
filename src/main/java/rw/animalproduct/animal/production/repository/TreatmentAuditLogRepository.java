package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.TreatmentAuditLog;

import java.util.List;
import java.util.UUID;

public interface TreatmentAuditLogRepository extends JpaRepository<TreatmentAuditLog, UUID> {

    List<TreatmentAuditLog> findByTreatmentIdOrderByPerformedAtDesc(UUID treatmentId);
}