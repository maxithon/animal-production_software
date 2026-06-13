package rw.animalproduct.animal.production.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.AuditLog;
import rw.animalproduct.animal.production.repository.AuditLogRepository;

import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Save an audit entry.
     *
     * @param entityType  e.g. "livestock_birth", "livestock_sick", "livestock_sale"
     * @param entityId    UUID of the record being acted on
     * @param action      "CREATE", "UPDATE", "SOFT_DELETE"
     * @param changedBy   username or email of person who made the change
     * @param oldData     object snapshot BEFORE the change (can be a String or any object)
     * @param newData     object snapshot AFTER the change (can be null for deletes)
     * @param notes       any extra human-readable comment
     */
    public void log(String entityType, UUID entityId, String action,
                    String changedBy, Object oldData, Object newData, String notes) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setChangedBy(changedBy != null ? changedBy : "system");
        log.setNotes(notes);

        try {
            if (oldData != null) {
                log.setOldData(oldData instanceof String
                        ? (String) oldData
                        : objectMapper.writeValueAsString(oldData));
            }
            if (newData != null) {
                log.setNewData(newData instanceof String
                        ? (String) newData
                        : objectMapper.writeValueAsString(newData));
            }
        } catch (Exception e) {
            log.setOldData(oldData != null ? oldData.toString() : null);
            log.setNewData(newData != null ? newData.toString() : null);
        }

        auditLogRepository.save(log);
    }

    public List<AuditLog> getLogsForEntity(String entityType, UUID entityId) {
        return auditLogRepository
                .findByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType, entityId);
    }

    public Page<AuditLog> getAllPaged(int page, int size) {
        return auditLogRepository.findByIsDeletedFalseOrderByChangedAtDesc(
                PageRequest.of(page, size, Sort.Direction.DESC, "changedAt"));
    }

    public Page<AuditLog> getByEntityTypePaged(String entityType, int page, int size) {
        return auditLogRepository.findByEntityTypeAndIsDeletedFalseOrderByChangedAtDesc(
                entityType, PageRequest.of(page, size, Sort.Direction.DESC, "changedAt"));
    }
}