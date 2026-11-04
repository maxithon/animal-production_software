package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockNotificationLogRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockValuationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LivestockService {

    private final LivestockRepository livestockRepository;
    private final LivestockCategoryRepository livestockCategoryRepository;
    private final LivestockValuationRepository livestockValuationRepository;
    private final LifecycleEmailService emailService;
    private final AuditLogService auditLogService;
    private final LivestockNotificationLogRepository notificationLogRepository;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "tagNumber", "birthDate", "currentValue"
    );
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    // ─── READ ──────────────────────────────────────────────────────────────────

    public List<Livestock> getAll() { return livestockRepository.findAll(); }
    public List<Livestock> getAllIncludingDrafts() { return livestockRepository.findAll(); }

    public Page<Livestock> getPaginated(Pageable pageable) { return livestockRepository.findAll(pageable); }

    public Page<Livestock> getPaged(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD).and(Sort.by(Sort.Direction.DESC, "id"));
        return livestockRepository.findAll(PageRequest.of(page, size, sort));
    }

    public Page<Livestock> getPaged(int page, int size, String sortField, String dir, String status, UUID categoryId) {
        String safeSort = (sortField != null && ALLOWED_SORT_FIELDS.contains(sortField)) ? sortField : DEFAULT_SORT_FIELD;
        Sort.Direction direction = "ASC".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, safeSort).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        boolean hasStatus = status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status);
        boolean hasCategory = categoryId != null;

        if (hasStatus && hasCategory) return livestockRepository.findByLivestockCategoryIdAndStatus(categoryId, status, pageable);
        if (hasStatus) return livestockRepository.findByStatus(status, pageable);
        if (hasCategory) return livestockRepository.findByLivestockCategoryId(categoryId, pageable);
        return livestockRepository.findAll(pageable);
    }

    public Optional<Livestock> getById(UUID id) { return livestockRepository.findById(id); }
    public Optional<Livestock> getByIdIncludingDeleted(UUID id) { return livestockRepository.findById(id); }

    public Livestock findById(UUID id) {
        return livestockRepository.findById(id).orElseThrow(() -> new RuntimeException("Livestock not found: " + id));
    }

    public List<Livestock> getByStatus(String status) { return livestockRepository.findByStatus(status); }
    public List<Livestock> getByCategory(UUID categoryId) { return livestockRepository.findByLivestockCategoryId(categoryId); }
    public List<Livestock> getByCategoryAndStatus(UUID categoryId, String status) { return livestockRepository.findByLivestockCategoryIdAndStatus(categoryId, status); }
    public Page<Livestock> getByStatusPaginated(String status, Pageable pageable) { return livestockRepository.findByStatus(status, pageable); }
    public Page<Livestock> getByStatusPaged(String status, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD).and(Sort.by(Sort.Direction.DESC, "id"));
        return livestockRepository.findByStatus(status, PageRequest.of(page, size, sort));
    }

    // ─── LIVESTOCK VALUATIONS ──────────────────────────────────────────────────

    public Map<UUID, LivestockValuation> getLatestValuationsForIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return livestockValuationRepository.findLatestValuationsForLivestockIds(ids).stream()
                .collect(Collectors.toMap(v -> v.getLivestock().getId(), v -> v, (a, b) -> a));
    }
    public Optional<LivestockValuation> getLatestValuationForLivestock(UUID livestockId) {
        return livestockValuationRepository.findFirstByLivestockIdOrderByValuationDateDesc(livestockId);
    }
    public List<LivestockValuation> getValuationsForLivestock(UUID livestockId) {
        return livestockValuationRepository.findByLivestockIdOrderByValuationDateDesc(livestockId);
    }
    public Double getCurrentValue(UUID livestockId) {
        return livestockValuationRepository.findFirstByLivestockIdOrderByValuationDateDesc(livestockId)
                .map(LivestockValuation::getEstimatedValue).orElse(null);
    }

    // ─── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public Livestock create(Livestock livestock) {
        livestock.setCreatedAt(LocalDateTime.now());
        if (livestock.getStatus() == null) livestock.setStatus(Livestock.STATUS_ACTIVE);
        if ("FEMALE".equalsIgnoreCase(livestock.getGender()) && livestock.getReproductiveStatus() == null)
            livestock.setReproductiveStatus(Livestock.REPRO_OPEN);

        Livestock saved = livestockRepository.save(livestock);

        try {
            auditLogService.logCreate("livestock", saved.getId(), saved, "system");
        } catch (Exception e) {
            log.warn("Audit-log CREATE failed for {}: {}", saved.getTagNumber(), e.getMessage());
        }

        String tag = saved.getTagNumber() != null ? saved.getTagNumber() : "—";
        String category = saved.getLivestockCategory() != null ? saved.getLivestockCategory().getName() : "—";
        String gender = saved.getGender() != null ? saved.getGender() : "—";
        String age = saved.getAgeDisplay() != null ? saved.getAgeDisplay() : "—";
        String status = saved.getStatus() != null ? saved.getStatus() : "ACTIVE";
        String health = saved.getHealthStatus() != null ? saved.getHealthStatus() : "—";

        String bName = getBeneficiaryName(saved);
        String bPhone = getBeneficiaryPhone(saved);
        String loc = getFullLocation(saved);

        // ✅ FIX: Use registerAfterCommit so it runs only AFTER the DB commits
        registerAfterCommit(() -> {
            try {
                emailService.sendAnimalRegisteredNotification(tag, category, gender, age, status, health, bName, bPhone, loc);
            } catch (Exception e) {
                log.error("❌ EMAIL FAILED for {}: {}", tag, e.getMessage(), e);
            }
        });

        return saved;
    }

    @Transactional
    public Livestock addNew(Livestock livestock) { return create(livestock); }

    // ─── UPDATE ────────────────────────────────────────────────────────────────

    @Transactional
    public Livestock update(UUID id, Livestock updated) {
        Livestock existing = findById(id);
        String beforeSnapshot = auditLogService.snapshot(existing);

        existing.setTagNumber(updated.getTagNumber());
        existing.setGender(updated.getGender());
        existing.setStatus(updated.getStatus());
        existing.setHealthStatus(updated.getHealthStatus());
        existing.setReproductiveStatus(updated.getReproductiveStatus());
        existing.setAcquisitionMethod(updated.getAcquisitionMethod());
        existing.setAcquisitionSource(updated.getAcquisitionSource());
        existing.setDateReceived(updated.getDateReceived());
        existing.setBirthDate(updated.getBirthDate());
        existing.setSourceLocation(updated.getSourceLocation());
        existing.setOffspringCount(updated.getOffspringCount());
        existing.setPhoto(updated.getPhoto());

        if (updated.getLivestockCategory() != null) existing.setLivestockCategory(updated.getLivestockCategory());
        if (updated.getBeneficiary() != null) existing.setBeneficiary(updated.getBeneficiary());
        if (updated.getLocation() != null) existing.setLocation(updated.getLocation());
        if (updated.getFather() != null) existing.setFather(updated.getFather());
        if (updated.getMother() != null) existing.setMother(updated.getMother());

        existing.setUpdatedAt(LocalDateTime.now());
        Livestock saved = livestockRepository.save(existing);

        try {
            auditLogService.logUpdate("livestock", saved.getId(), beforeSnapshot, auditLogService.snapshot(saved), "system");
        } catch (Exception e) {
            log.warn("Audit-log UPDATE failed for {}: {}", saved.getTagNumber(), e.getMessage());
        }

        String tag = saved.getTagNumber() != null ? saved.getTagNumber() : "—";
        String cat = saved.getLivestockCategory() != null ? saved.getLivestockCategory().getName() : "—";
        String gender = saved.getGender() != null ? saved.getGender() : "—";
        String age = saved.getAgeDisplay() != null ? saved.getAgeDisplay() : "—";
        String status = saved.getStatus() != null ? saved.getStatus() : "—";
        String health = saved.getHealthStatus() != null ? saved.getHealthStatus() : "—";

        String bName = getBeneficiaryName(saved);
        String bPhone = getBeneficiaryPhone(saved);
        String loc = getFullLocation(saved);

        registerAfterCommit(() -> {
            try {
                emailService.sendAnimalUpdatedNotification(tag, cat, status, "Fields updated", gender, age, health, bName, bPhone, loc);
            } catch (Exception e) {
                log.error("❌ EMAIL FAILED for {}: {}", tag, e.getMessage(), e);
            }
        });

        return saved;
    }

    // ─── DELETE ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id, String deletedBy, String reason) {
        Livestock animal = livestockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Animal not found: " + id));

        long linkedCount = livestockRepository.countLinkedRecords(id);
        if (linkedCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete " + animal.getTagNumber()
                            + " because it is linked to " + linkedCount + " operational record(s). "
                            + "You must remove or reassign those records first.");
        }

        String tag = animal.getTagNumber();
        String category = animal.getLivestockCategory() != null ? animal.getLivestockCategory().getName() : "—";
        String status = animal.getStatus();
        String gender = animal.getGender() != null ? animal.getGender() : "—";
        String age = animal.getAgeDisplay() != null ? animal.getAgeDisplay() : "—";
        String bName = getBeneficiaryName(animal);
        String bPhone = getBeneficiaryPhone(animal);
        String loc = getFullLocation(animal);

        try {
            notificationLogRepository.deleteByLivestockId(id);
        } catch (Exception e) {
            log.warn("Failed to clean up notification logs for {}: {}", tag, e.getMessage());
        }

        auditLogService.log("livestock", animal.getId(), "DELETE", deletedBy, animal, null,
                "Permanently deleted animal " + tag + (reason != null && !reason.isBlank() ? " — Reason: " + reason.trim() : ""));

        livestockRepository.delete(animal);
        log.info("Permanently deleted livestock: {} by {}", tag, deletedBy);

        registerAfterCommit(() -> {
            try {
                emailService.sendAnimalDeletedNotification(tag, category, status, reason, gender, age, bName, bPhone, loc);
            } catch (Exception e) {
                log.error("❌ EMAIL FAILED for {}: {}", tag, e.getMessage(), e);
            }
        });
    }

    // ─── TRANSACTION HOOK ────────────────────────────────────────────────────
    private void registerAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // ─── STATUS UPDATES ───────────────────────────────────────────────────────

    @Transactional
    public void updateStatus(UUID id, String status) {
        Livestock existing = findById(id);
        existing.setStatus(status);
        existing.setUpdatedAt(LocalDateTime.now());
        livestockRepository.save(existing);
    }

    @Transactional
    public void updateReproductiveStatus(UUID id, String reproductiveStatus) {
        Livestock existing = findById(id);
        existing.setReproductiveStatus(reproductiveStatus);
        if (Livestock.REPRO_PREGNANT.equals(reproductiveStatus)) {
            existing.setIsPregnant(true);
        } else if (Livestock.REPRO_OPEN.equals(reproductiveStatus)) {
            existing.setIsPregnant(false);
            existing.setExpectedDueDate(null);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        livestockRepository.save(existing);
    }

    // ─── BULK OPERATIONS ─────────────────────────────────────────────────────

    @Transactional
    public void updateStatusByCategory(UUID categoryId, String status) {
        List<Livestock> animals = livestockRepository.findByLivestockCategoryId(categoryId);
        for (Livestock animal : animals) {
            animal.setStatus(status);
            animal.setUpdatedAt(LocalDateTime.now());
        }
        livestockRepository.saveAll(animals);
        log.info("Updated status to {} for {} animals in category {}", status, animals.size(), categoryId);
    }

    // ─── BREEDING READINESS ──────────────────────────────────────────────────

    public List<Livestock> findAllReadyToBreedFemales() { return livestockRepository.findAllReadyToBreedFemales(); }
    public long countReadyToBreedFemales() { return livestockRepository.countReadyToBreedFemales(); }
    public List<Livestock> findAllBreedingMales() { return livestockRepository.findAllBreedingMales(); }
    public List<Livestock> findByGenderAndStatusAndReproductiveStatus(String g, String s, String r) { return livestockRepository.findByGenderAndStatusAndReproductiveStatus(g, s, r); }

    // ─── DASHBOARD STATS ─────────────────────────────────────────────────────

    public long countActive() { return livestockRepository.countByStatus(Livestock.STATUS_ACTIVE); }
    public long countPregnant() { return livestockRepository.findByStatus(Livestock.STATUS_ACTIVE).stream().filter(l -> Boolean.TRUE.equals(l.getIsPregnant())).count(); }
    public long countByCategory(UUID categoryId) { return livestockRepository.countByCategoryId(categoryId); }
    public long countBornOnFarm() { return livestockRepository.countBornOnFarm(); }
    public long countSold() { return livestockRepository.countSold(); }
    public long countDead() { return livestockRepository.countDead(); }

    // ─── HELPER METHODS FOR EMAIL ──────────────────────────────────────────────

    private String getBeneficiaryName(Livestock animal) {
        if (animal.getBeneficiary() == null) return "—";
        String f = animal.getBeneficiary().getFirstName();
        String l = animal.getBeneficiary().getLastName();
        return ((f != null ? f : "") + " " + (l != null ? l : "")).trim();
    }

    private String getBeneficiaryPhone(Livestock animal) {
        if (animal.getBeneficiary() == null || animal.getBeneficiary().getPhone() == null) return "—";
        return animal.getBeneficiary().getPhone();
    }

    private String getFullLocation(Livestock animal) {
        if (animal.getBeneficiary() == null || animal.getBeneficiary().getLocation() == null) return "—";

        Location loc = animal.getBeneficiary().getLocation();
        String village = loc.getName();
        String cell = "—", sector = "—", district = "—", province = "—";

        if (loc.getParent() != null) {
            cell = loc.getParent().getName();
            if (loc.getParent().getParent() != null) {
                sector = loc.getParent().getParent().getName();
                if (loc.getParent().getParent().getParent() != null) {
                    district = loc.getParent().getParent().getParent().getName();
                    if (loc.getParent().getParent().getParent().getParent() != null) {
                        province = loc.getParent().getParent().getParent().getParent().getName();
                    }
                }
            }
        }

        return String.format("%s, %s, %s, %s, %s", village, cell, sector, district, province);
    }
}