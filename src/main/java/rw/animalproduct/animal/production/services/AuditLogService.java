package rw.animalproduct.animal.production.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.AuditLog;
import rw.animalproduct.animal.production.repository.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public static final Map<String, String> ENTITY_LABELS = Map.ofEntries(
            Map.entry("representative",       "Representatives"),
            Map.entry("beneficiary",          "Beneficiaries"),
            Map.entry("buyer",                "Buyers"),
            Map.entry("veterinarian",         "Veterinarians"),
            Map.entry("livestock_birth",      "Births"),
            Map.entry("livestock",            "Livestock"),
            Map.entry("livestock_sick",       "Sick Animals"),
            Map.entry("livestock_breeding",   "Breeding"),
            Map.entry("livestock_sale",       "Sales"),
            Map.entry("livestock_death",      "Deaths"),
            Map.entry("livestock_abortion",   "Abortions"),
            Map.entry("livestock_treatment",  "Treatments")
    );

    public static final Map<String, String> ENTITY_ICONS = Map.ofEntries(
            Map.entry("representative",       "bi bi-person-badge"),
            Map.entry("beneficiary",          "bi bi-people"),
            Map.entry("buyer",                "bi bi-cart-check"),
            Map.entry("veterinarian",         "bi bi-person-vcard"),
            Map.entry("livestock_birth",      "bi bi-egg-fried"),
            Map.entry("livestock",            "bi bi-clipboard-data"),
            Map.entry("livestock_sick",       "bi bi-thermometer-half"),
            Map.entry("livestock_breeding",   "bi bi-heart-pulse"),
            Map.entry("livestock_sale",       "bi bi-cash-coin"),
            Map.entry("livestock_death",      "bi bi-emoji-frown"),
            Map.entry("livestock_abortion",   "bi bi-exclamation-diamond"),
            Map.entry("livestock_treatment",  "bi bi-capsule")
    );

    public String getEntityLabel(String entityType) {
        if (entityType == null) return "Unknown";
        return ENTITY_LABELS.getOrDefault(entityType, entityType);
    }

    public String getEntityIcon(String entityType) {
        if (entityType == null) return "bi bi-folder2";
        return ENTITY_ICONS.getOrDefault(entityType, "bi bi-folder2");
    }

    public String snapshot(Object entity) {
        if (entity == null) return null;
        try {
            return objectMapper.writeValueAsString(entity);
        } catch (Exception e) {
            return entity.toString();
        }
    }

    /**
     * Writes an audit row in a NEW transaction so the entry survives
     * even if the caller's business transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String entityType, UUID entityId, String action,
                    String changedBy, Object oldData, Object newData, String notes) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setChangedBy(changedBy != null ? changedBy : "system");
        log.setNotes(notes);
        log.setChangedAt(LocalDateTime.now());

        try {
            if (oldData != null) {
                log.setOldData(oldData instanceof String ? (String) oldData
                        : objectMapper.writeValueAsString(oldData));
            }
            if (newData != null) {
                log.setNewData(newData instanceof String ? (String) newData
                        : objectMapper.writeValueAsString(newData));
            }
        } catch (Exception e) {
            log.setOldData(oldData != null ? oldData.toString() : null);
            log.setNewData(newData != null ? newData.toString() : null);
        }

        auditLogRepository.save(log);
    }

    public void logCreate(String entityType, UUID entityId, Object newSnapshot, String changedBy) {
        log(entityType, entityId, AuditLog.ACTION_CREATE, changedBy, null, newSnapshot, "Created");
    }

    public void logUpdate(String entityType, UUID entityId,
                          Object oldSnapshot, Object newSnapshot, String changedBy) {
        log(entityType, entityId, AuditLog.ACTION_UPDATE, changedBy, oldSnapshot, newSnapshot, "Updated");
    }

    public void logDelete(String entityType, UUID entityId, Object snapshot, String changedBy) {
        log(entityType, entityId, AuditLog.ACTION_DELETE, changedBy, snapshot, null, "Deleted");
    }

    public void logSoftDelete(String entityType, UUID entityId, Object snapshot, String changedBy) {
        log(entityType, entityId, AuditLog.ACTION_SOFT_DELETE, changedBy, snapshot, null, "Soft-deleted");
    }

    public List<AuditLog> getLogsForEntity(String entityType, UUID entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType, entityId);
    }

    public Page<AuditLog> getAllPaged(int page, int size) {
        return auditLogRepository.findByIsDeletedFalseOrderByChangedAtDesc(
                PageRequest.of(page, size, Sort.Direction.DESC, "changedAt"));
    }

    public Page<AuditLog> getByEntityTypePaged(String entityType, int page, int size) {
        return auditLogRepository.findByEntityTypeAndIsDeletedFalseOrderByChangedAtDesc(
                entityType, PageRequest.of(page, size, Sort.Direction.DESC, "changedAt"));
    }

    public long getTotalCount() { return auditLogRepository.countByIsDeletedFalse(); }
    public long getCountByAction(String action) { return auditLogRepository.countByActionAndIsDeletedFalse(action); }
    public long getCountByEntityType(String entityType) { return auditLogRepository.countByEntityTypeAndIsDeletedFalse(entityType); }
    public long getCountByEntityTypeAndAction(String entityType, String action) {
        return auditLogRepository.countByEntityTypeAndActionAndIsDeletedFalse(entityType, action);
    }
    public long getTotalDeleteCount() { return auditLogRepository.countAllDeletes(); }
    public long getDeleteCountForEntityType(String entityType) { return auditLogRepository.countDeletesForEntityType(entityType); }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) map.put((String) row[0], (Long) row[1]);
        return map;
    }

    public Map<String, Long> getBreakdownByEntityType() {
        return toMap(auditLogRepository.countAllGroupedByEntityType());
    }
    public Map<String, Long> getBreakdownByAction(String action) {
        return toMap(auditLogRepository.countGroupedByEntityTypeAndAction(action));
    }
    public Map<String, Long> getDeleteBreakdown() {
        return toMap(auditLogRepository.countDeletesGroupedByEntityType());
    }
}