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

    /**
     * Friendly display labels for entity types, shared by controllers and templates.
     */
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

    /**
     * NEW: one Bootstrap Icon class per entity type, shared by controllers and templates
     * so the audit-log views (tabs, live-count cards, filtered-page hero) don't need to
     * hardcode a ternary chain per entity type. Add an entry here whenever a new
     * auditable module is introduced and its icon will automatically show up everywhere.
     */
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

    /**
     * Serialize an entity to JSON right now. Use this to capture a "before" snapshot
     * BEFORE calling a service.update(...) method — Spring's open-in-view session means
     * an entity fetched earlier in the same request can be the same managed instance
     * that update() mutates in place, so it must be turned into a String immediately,
     * not passed as a live object to be serialized later.
     */
    public String snapshot(Object entity) {
        if (entity == null) return null;
        try {
            return objectMapper.writeValueAsString(entity);
        } catch (Exception e) {
            return entity.toString();
        }
    }

    /**
     * Save an audit entry.
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

    // ── KPI totals ───────────────────────────────────────────────────────────

    public long getTotalCount() {
        return auditLogRepository.countByIsDeletedFalse();
    }

    public long getCountByAction(String action) {
        return auditLogRepository.countByActionAndIsDeletedFalse(action);
    }

    public long getCountByEntityType(String entityType) {
        return auditLogRepository.countByEntityTypeAndIsDeletedFalse(entityType);
    }

    public long getCountByEntityTypeAndAction(String entityType, String action) {
        return auditLogRepository.countByEntityTypeAndActionAndIsDeletedFalse(entityType, action);
    }

    /** DELETE + SOFT_DELETE combined, since Buyer soft-deletes instead of hard-deleting. */
    public long getTotalDeleteCount() {
        return getCountByAction("DELETE") + getCountByAction("SOFT_DELETE");
    }

    public long getDeleteCountForEntityType(String entityType) {
        return getCountByEntityTypeAndAction(entityType, "DELETE")
                + getCountByEntityTypeAndAction(entityType, "SOFT_DELETE");
    }

    // ── KPI breakdowns (category -> count), used on the "All Logs" view ────

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], (Long) row[1]);
        }
        return map;
    }

    public Map<String, Long> getBreakdownByEntityType() {
        return toMap(auditLogRepository.countAllGroupedByEntityType());
    }

    public Map<String, Long> getBreakdownByAction(String action) {
        return toMap(auditLogRepository.countGroupedByEntityTypeAndAction(action));
    }

    public Map<String, Long> getDeleteBreakdown() {
        Map<String, Long> merged = new LinkedHashMap<>(getBreakdownByAction("DELETE"));
        getBreakdownByAction("SOFT_DELETE").forEach((k, v) -> merged.merge(k, v, Long::sum));
        return merged;
    }
}