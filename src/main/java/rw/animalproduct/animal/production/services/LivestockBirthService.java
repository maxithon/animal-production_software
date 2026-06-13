package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LivestockBirthService {

    private final LivestockBirthRepository     birthRepository;
    private final LivestockRepository          livestockRepository;
    private final LivestockOffspringRepository offspringRepository;
    private final LivestockBreedingRepository  breedingRepository;
    private final LivestockBreedingService     breedingService;
    private final AuditLogService              auditLogService;

    public LivestockBirthService(LivestockBirthRepository birthRepository,
                                 LivestockRepository livestockRepository,
                                 LivestockOffspringRepository offspringRepository,
                                 LivestockBreedingRepository breedingRepository,
                                 LivestockBreedingService breedingService,
                                 AuditLogService auditLogService) {
        this.birthRepository     = birthRepository;
        this.livestockRepository = livestockRepository;
        this.offspringRepository = offspringRepository;
        this.breedingRepository  = breedingRepository;
        this.breedingService     = breedingService;
        this.auditLogService     = auditLogService;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public List<LivestockBirth> getAll() {
        return birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();
    }

    public Page<LivestockBirth> getPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "birthDate");
        return birthRepository.findByIsDeletedFalseOrderByBirthDateDesc(pageable);
    }

    public Optional<LivestockBirth> getById(UUID id) {
        return birthRepository.findById(id);
    }

    public List<LivestockBirth> getByLivestockId(UUID livestockId) {
        return birthRepository.findByLivestockId(livestockId);
    }

    public List<Livestock> getDraftChildrenForBirth(UUID birthId) {
        Optional<LivestockBirth> birthOpt = birthRepository.findById(birthId);
        if (birthOpt.isEmpty()) return new ArrayList<>();
        LivestockBirth birth = birthOpt.get();
        if (birth.getChildren() == null || birth.getChildren().isEmpty()) return new ArrayList<>();
        return birth.getChildren().stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .filter(child -> Boolean.TRUE.equals(child.getIsDraft()))
                .collect(Collectors.toList());
    }

    public List<LivestockOffspring> getOffspringLinksForBirth(UUID birthId) {
        return offspringRepository.findByBirthEventId(birthId);
    }

    public long countPendingDraftsForBirth(UUID birthId) {
        return livestockRepository.countByDraftBirthEventIdAndIsDraftTrue(birthId);
    }

    @Transactional
    public void completeDraft(UUID draftId, String tagNumber, String gender,
                              String categoryId, String beneficiaryId,
                              BigDecimal currentValue, UUID locationId,
                              String sourceLocation,
                              BeneficiaryRepository beneficiaryRepository,
                              LivestockCategoryRepository categoryRepository,
                              LocationRepository locationRepository) {

        Livestock draft = livestockRepository.findById(draftId)
                .orElseThrow(() -> new RuntimeException("Draft animal not found"));

        if (!Boolean.TRUE.equals(draft.getIsDraft())) {
            throw new RuntimeException("This animal is not a draft");
        }

        draft.setTagNumber(tagNumber);
        draft.setGender(gender);
        draft.setIsDraft(false);
        draft.setStatus(Livestock.STATUS_ACTIVE);
        draft.setAcquisitionMethod(Livestock.ACQ_BIRTH);
        draft.setDateReceived(LocalDate.now());

        if (currentValue != null) draft.setCurrentValue(currentValue);

        if (draft.getMother() == null && draft.getDraftBirthEvent() != null) {
            Livestock motherFromBirth = draft.getDraftBirthEvent().getLivestock();
            if (motherFromBirth != null) draft.setMother(motherFromBirth);
        }

        if (draft.getAcquisitionSource() == null || draft.getAcquisitionSource().isBlank()) {
            draft.setAcquisitionSource(draft.getMother() != null
                    ? "Born on this farm — Mother: " + draft.getMother().getTagNumber()
                    : "Born on this farm");
        }

        if (categoryId != null && !categoryId.isEmpty()) {
            UUID catId = UUID.fromString(categoryId);
            LivestockCategory category = categoryRepository.findById(catId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            draft.setLivestockCategory(category);
        }

        if (beneficiaryId != null && !beneficiaryId.isEmpty()) {
            UUID benId = UUID.fromString(beneficiaryId);
            Beneficiary beneficiary = beneficiaryRepository.findById(benId)
                    .orElseThrow(() -> new RuntimeException("Beneficiary not found"));
            draft.setBeneficiary(beneficiary);
        }

        if (locationId != null) {
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Location not found"));
            draft.setLocation(location);
        }

        if (sourceLocation != null && !sourceLocation.isEmpty()) {
            draft.setAcquisitionSource(sourceLocation);
        }

        livestockRepository.save(draft);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // addNew — OVERLOADED
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockBirth addNew(LivestockBirth birth) {
        return addNew(birth, null);
    }

    @Transactional
    public LivestockBirth addNew(LivestockBirth birth, List<UUID> linkedChildIds) {

        birth.setIsExternalBirth(false);
        resolveAndSetLivestock(birth);

        Livestock mother = birth.getLivestock();
        if (mother == null) {
            throw new RuntimeException(
                    "Mother animal is required — please select the mother from the list.");
        }

        boolean isPurchasedPregnant =
                !Livestock.ACQ_BIRTH.equals(mother.getAcquisitionMethod());

        if (isPurchasedPregnant) {
            validateBirthInterval(mother, birth.getBirthDate());
        } else {
            validateGestationComplete(mother, birth.getBirthDate());
            validateBirthInterval(mother, birth.getBirthDate());
            validateDueDateWindow(mother, birth.getBirthDate());
        }

        mother.setLastBirthDate(birth.getBirthDate());
        mother.setIsPregnant(false);
        mother.setPregnancyStatus("NOT_PREGNANT");
        mother.setStatus(Livestock.STATUS_ACTIVE);
        mother.setExpectedDueDate(null);
        mother.setConceptionDate(null);
        mother.setLastBreedingDate(null);

        if (birth.getOffspringCount() != null) {
            int current = mother.getOffspringCount() == null ? 0 : mother.getOffspringCount();
            mother.setOffspringCount(current + birth.getOffspringCount());
        }
        livestockRepository.save(mother);

        breedingService.completeBreedingOnBirth(mother.getId(), birth.getBirthDate());

        LivestockBirth saved = birthRepository.save(birth);

        // ── Audit: CREATE ─────────────────────────────────────────────────────
        auditLogService.log(
                "livestock_birth",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                "Birth recorded for mother: " + mother.getTagNumber()
                        + " | Date: " + birth.getBirthDate()
                        + " | Offspring count: " + birth.getOffspringCount(),
                "New birth event created"
        );

        // ── Link already-registered children ─────────────────────────────────
        int alreadyLinkedCount = 0;
        if (linkedChildIds != null && !linkedChildIds.isEmpty()) {
            for (UUID childId : linkedChildIds) {
                try {
                    linkChild(saved.getId(), childId);
                    alreadyLinkedCount++;
                } catch (Exception ex) {
                    System.err.println("Warning: could not link child "
                            + childId + ": " + ex.getMessage());
                }
            }
        }

        // ── Create draft placeholders for remaining unregistered offspring ────
        int offspringCount = birth.getOffspringCount() != null
                ? birth.getOffspringCount() : 0;
        int draftsNeeded = Math.max(0, offspringCount - alreadyLinkedCount);

        for (int i = 0; i < draftsNeeded; i++) {
            Livestock draft = new Livestock();
            draft.setIsDraft(true);
            draft.setDraftBirthEvent(saved);
            draft.setStatus(Livestock.STATUS_ACTIVE);
            draft.setAcquisitionMethod(Livestock.ACQ_BIRTH);
            draft.setGender("UNKNOWN");
            draft.setBirthDate(birth.getBirthDate());
            draft.setMother(mother);
            draft.setTagNumber("DRAFT-"
                    + saved.getId().toString().substring(0, 8).toUpperCase()
                    + "-" + (i + 1));
            draft.setPregnancyStatus("NOT_PREGNANT");
            draft.setIsPregnant(false);
            draft.setOffspringCount(0);
            draft.setIsDeleted(false);
            draft.setAcquisitionSource(
                    "Born on this farm — Mother: " + mother.getTagNumber());

            if (saved.getLivestock() != null
                    && saved.getLivestock().getLivestockCategory() != null) {
                draft.setLivestockCategory(saved.getLivestock().getLivestockCategory());
            }

            Livestock savedDraft = livestockRepository.save(draft);
            LivestockOffspring link = new LivestockOffspring(saved, savedDraft, 1);
            offspringRepository.save(link);
        }

        return saved;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION 1 — Gestation period completeness
    // ═════════════════════════════════════════════════════════════════════════

    private void validateGestationComplete(Livestock mother, LocalDate birthDate) {
        if (mother.getLivestockCategory() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() <= 0) return;

        int gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        LocalDate effectiveBirthDate = (birthDate != null) ? birthDate : LocalDate.now();

        Optional<LivestockBreeding> activeBreedingOpt = breedingRepository
                .findByLivestockId(mother.getId()).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate));

        if (activeBreedingOpt.isEmpty()) {
            if (mother.getLastBreedingDate() == null) return;
            LocalDate expectedBirthDate =
                    mother.getLastBreedingDate().plusMonths(gestationMonths);
            if (effectiveBirthDate.isBefore(expectedBirthDate)) {
                throw new RuntimeException("Animal " + mother.getTagNumber()
                        + " is not ready to give birth yet. Earliest birth date: "
                        + expectedBirthDate);
            }
            return;
        }

        LocalDate breedingDate = activeBreedingOpt.get().getBreedingDate();
        LocalDate expectedBirthDate = breedingDate.plusMonths(gestationMonths);

        if (effectiveBirthDate.isBefore(expectedBirthDate)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " is not ready to give birth yet. Earliest birth date: "
                    + expectedBirthDate);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION 2 — Minimum interval between consecutive births
    // ═════════════════════════════════════════════════════════════════════════

    private void validateBirthInterval(Livestock mother, LocalDate newBirthDate) {
        if (mother.getLastBirthDate() == null) return;
        if (mother.getLivestockCategory() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() <= 0) return;

        boolean hasPreExistingConfirmedPregnancy = breedingRepository
                .findByLivestockId(mother.getId()).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getBreedingDate() != null)
                .anyMatch(b -> !b.getBreedingDate().isAfter(mother.getLastBirthDate()));

        if (hasPreExistingConfirmedPregnancy) return;

        int gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        LocalDate effectiveBirthDate = (newBirthDate != null) ? newBirthDate : LocalDate.now();
        LocalDate earliestNextBirth =
                mother.getLastBirthDate().plusMonths(gestationMonths);

        if (effectiveBirthDate.isBefore(earliestNextBirth)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " gave birth too recently. Earliest next birth date: "
                    + earliestNextBirth);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION 3 — Birth date within due-date window
    // ═════════════════════════════════════════════════════════════════════════

    private void validateDueDateWindow(Livestock mother, LocalDate birthDate) {
        if (mother.getLivestockCategory() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() <= 0) return;

        final int DAYS_BEFORE_TOLERANCE = 14;
        final int DAYS_AFTER_TOLERANCE  = 14;
        LocalDate effectiveBirthDate = (birthDate != null) ? birthDate : LocalDate.now();

        Optional<LivestockBreeding> activeBreedingOpt = breedingRepository
                .findByLivestockId(mother.getId()).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getExpectedDueDate() != null)
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate));

        if (activeBreedingOpt.isEmpty()) return;

        LocalDate expectedDueDate = activeBreedingOpt.get().getExpectedDueDate();
        LocalDate earliestAllowedBirth =
                expectedDueDate.minusDays(DAYS_BEFORE_TOLERANCE);
        LocalDate latestAllowedBirth =
                expectedDueDate.plusDays(DAYS_AFTER_TOLERANCE);

        if (effectiveBirthDate.isBefore(earliestAllowedBirth)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " birth date is too early. Earliest allowed: "
                    + earliestAllowedBirth);
        }
        if (effectiveBirthDate.isAfter(latestAllowedBirth)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " birth date is too late. Latest allowed: "
                    + latestAllowedBirth);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═════════════════════════════════════════════════════════════════════════
    @Transactional
    public LivestockBirth update(UUID id, LivestockBirth updated) {
        Optional<LivestockBirth> existingOpt = birthRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockBirth existing = existingOpt.get();

        // ── CORRECT: old snapshot captured BEFORE any setters ────────────────
        String oldSnapshot = "Mother: "
                + (existing.getLivestock() != null
                ? existing.getLivestock().getTagNumber() : "unknown")
                + " | Birth Date: " + existing.getBirthDate()
                + " | Offspring: "  + existing.getOffspringCount()
                + " | Notes: "      + existing.getNotes();

        // ── Now apply changes ─────────────────────────────────────────────────
        existing.setBirthDate(updated.getBirthDate());
        existing.setOffspringCount(updated.getOffspringCount());
        existing.setOffspringGender(updated.getOffspringGender());
        existing.setWeaningDate(updated.getWeaningDate());
        existing.setNextBreedingDate(updated.getNextBreedingDate());
        existing.setNotes(updated.getNotes());
        existing.setIsExternalBirth(false);
        existing.setSourceLocation(updated.getSourceLocation());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        LivestockBirth saved = birthRepository.save(existing);

        // ── New snapshot captured AFTER save ──────────────────────────────────
        String newSnapshot = "Mother: "
                + (saved.getLivestock() != null
                ? saved.getLivestock().getTagNumber() : "unknown")
                + " | Birth Date: " + saved.getBirthDate()
                + " | Offspring: "  + saved.getOffspringCount()
                + " | Notes: "      + saved.getNotes();

        auditLogService.log(
                "livestock_birth", id, "UPDATE",
                getCurrentUsername(), oldSnapshot, newSnapshot,
                "Birth record updated"
        );

        return saved;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DELETE — SOFT DELETE (keeps history, just marks is_deleted = true)
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(UUID id) {
        LivestockBirth birth = birthRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Birth record not found: " + id));

        // Build a readable snapshot before soft-deleting
        String snapshot = "Birth ID: " + id
                + " | Mother: " + (birth.getLivestock() != null
                ? birth.getLivestock().getTagNumber() : "unknown")
                + " | Birth Date: " + birth.getBirthDate()
                + " | Offspring Count: " + birth.getOffspringCount()
                + " | Notes: " + birth.getNotes();

        // Soft delete — set flag only, do NOT remove from DB
        birth.setIsDeleted(true);
        birthRepository.save(birth);

        // Write audit log so you can always see what was deleted and when
        auditLogService.log(
                "livestock_birth",
                id,
                "SOFT_DELETE",
                getCurrentUsername(),
                snapshot,
                null,
                "Birth record soft-deleted. Record is still in the database with is_deleted=true."
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CHILD LINKING
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockOffspring linkChild(UUID birthId, UUID childLivestockId) {
        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        Livestock child = livestockRepository.findById(childLivestockId)
                .orElseThrow(() -> new RuntimeException("Child livestock not found"));

        boolean alreadyLinked = birth.getChildren() != null
                && birth.getChildren().stream()
                .anyMatch(o -> o.getChildLivestock() != null
                        && o.getChildLivestock().getId().equals(childLivestockId));
        if (alreadyLinked) {
            throw new RuntimeException("Animal " + child.getTagNumber()
                    + " is already linked to this birth.");
        }

        Livestock mother = birth.getLivestock();

        if (child.getMother() != null && mother != null
                && !child.getMother().getId().equals(mother.getId())) {
            System.err.println("WARNING: Animal " + child.getTagNumber()
                    + " had mother " + child.getMother().getTagNumber()
                    + " but is being reassigned to " + mother.getTagNumber());
        }

        if (mother != null) {
            Optional<LivestockOffspring> existingLink =
                    offspringRepository.findByChildLivestockId(childLivestockId);
            if (existingLink.isPresent()
                    && !existingLink.get().getBirthEvent().getId().equals(birthId)) {
                offspringRepository.delete(existingLink.get());
                System.err.println("Removed existing birth link for animal "
                        + child.getTagNumber());
            }

            child.setMother(mother);

            // FIX: If child has no birth date, copy it from the birth event
            if (child.getBirthDate() == null && birth.getBirthDate() != null) {
                child.setBirthDate(birth.getBirthDate());
            }

            if (child.getAcquisitionSource() == null
                    || child.getAcquisitionSource().isBlank()
                    || child.getAcquisitionSource().startsWith("Born on this farm")) {
                child.setAcquisitionSource(
                        "Born on this farm — Mother: " + mother.getTagNumber());
            }
            livestockRepository.save(child);
        }

        int generation = (mother != null) ? calculateGeneration(mother) + 1 : 0;
        LivestockOffspring link = new LivestockOffspring(birth, child, generation);
        return offspringRepository.save(link);
    }

    @Transactional
    public void unlinkChild(UUID childLivestockId) {
        offspringRepository.findByChildLivestockId(childLivestockId).ifPresent(link -> {
            Livestock child = link.getChildLivestock();
            if (child != null) {
                child.setMother(null);
                livestockRepository.save(child);
            }
            offspringRepository.delete(link);
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FAMILY QUERIES
    // ═════════════════════════════════════════════════════════════════════════

    public List<Livestock> getDirectChildren(UUID livestockId) {
        return livestockRepository.findByMotherId(livestockId);
    }

    public boolean hasChildren(UUID livestockId) {
        return livestockRepository.existsByMotherId(livestockId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // REPAIR HELPER
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public int repairMissingMotherLinks() {
        List<Livestock> broken = livestockRepository.findAll().stream()
                .filter(l -> l.getMother() == null)
                .filter(l -> l.getDraftBirthEvent() != null)
                .filter(l -> l.getDraftBirthEvent().getLivestock() != null)
                .collect(Collectors.toList());

        for (Livestock animal : broken) {
            Livestock mother = animal.getDraftBirthEvent().getLivestock();
            animal.setMother(mother);
            if (animal.getAcquisitionSource() == null
                    || animal.getAcquisitionSource().isBlank()) {
                animal.setAcquisitionSource(
                        "Born on this farm — Mother: " + mother.getTagNumber());
            }
            livestockRepository.save(animal);
        }
        return broken.size();
    }

    public List<Livestock> getEligibleChildrenForBirth(UUID birthId) {
        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        Livestock mother = birth.getLivestock();
        if (mother == null) return new ArrayList<>();

        UUID categoryId = mother.getLivestockCategory() != null
                ? mother.getLivestockCategory().getId() : null;

        Set<UUID> linkedIds = birth.getChildren().stream()
                .map(o -> o.getChildLivestock().getId())
                .collect(Collectors.toSet());

        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> !l.getId().equals(mother.getId()))
                .filter(l -> categoryId == null
                        || (l.getLivestockCategory() != null
                        && categoryId.equals(l.getLivestockCategory().getId())))
                .filter(l -> !linkedIds.contains(l.getId()))
                .collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private void resolveAndSetLivestock(LivestockBirth birth) {
        String idStr = birth.getLivestockIdValue();
        if (idStr != null && !idStr.trim().isEmpty()) {
            UUID id = UUID.fromString(idStr.trim());
            Livestock ls = livestockRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException(
                            "Livestock not found: " + idStr));
            birth.setLivestock(ls);
            birth.setLivestockId(id);
        }
    }

    private int calculateGeneration(Livestock animal) {
        int gen = 0;
        Livestock current = animal;
        while (current.getMother() != null && gen < 50) {
            gen++;
            current = livestockRepository
                    .findById(current.getMother().getId()).orElse(null);
            if (current == null) break;
        }
        return gen;
    }

    /**
     * Get the current username for audit logging.
     * This method can be enhanced later when Spring Security is added.
     *
     * @return the current username or "system" as fallback
     */
    private String getCurrentUsername() {
        // For now, return a default value
        // When you add Spring Security, you can uncomment the code below:

        // try {
        //     Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //     if (auth != null && auth.isAuthenticated() &&
        //         !(auth.getPrincipal() instanceof String && auth.getPrincipal().equals("anonymousUser"))) {
        //         return auth.getName();
        //     }
        // } catch (Exception e) {
        //     // Ignore - return default
        // }

        return "system";
    }
}