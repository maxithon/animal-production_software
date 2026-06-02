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

    public LivestockBirthService(LivestockBirthRepository birthRepository,
                                 LivestockRepository livestockRepository,
                                 LivestockOffspringRepository offspringRepository,
                                 LivestockBreedingRepository breedingRepository,
                                 LivestockBreedingService breedingService) {
        this.birthRepository     = birthRepository;
        this.livestockRepository = livestockRepository;
        this.offspringRepository = offspringRepository;
        this.breedingRepository  = breedingRepository;
        this.breedingService     = breedingService;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public List<LivestockBirth> getAll() {
        return birthRepository.findAll();
    }

    public Page<LivestockBirth> getPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "birthDate");
        return birthRepository.findAll(pageable);
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

    /**
     * Complete a draft animal by giving it a real tag number and details.
     *
     * FIX: The mother relationship is now explicitly preserved from the draft
     * birth event so that GOA-016 (and any other completed draft) correctly
     * shows its mother (e.g. GOA-014) after completion.
     */
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

        // ── FIX: explicitly re-set mother from the draft birth event ──────────
        // When a draft is created in addNew(), draft.setMother(mother) is called,
        // but JPA lazy-loading can drop this reference.  We re-resolve it here
        // from the draft birth event to guarantee it is persisted on completion.
        if (draft.getMother() == null && draft.getDraftBirthEvent() != null) {
            Livestock motherFromBirth = draft.getDraftBirthEvent().getLivestock();
            if (motherFromBirth != null) {
                draft.setMother(motherFromBirth);
            }
        }

        // Build a descriptive acquisition source if not already set
        if (draft.getAcquisitionSource() == null || draft.getAcquisitionSource().isBlank()) {
            if (draft.getMother() != null) {
                draft.setAcquisitionSource("Born on this farm — Mother: " + draft.getMother().getTagNumber());
            } else {
                draft.setAcquisitionSource("Born on this farm");
            }
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

    /**
     * Save a new birth record.
     *
     * KEY BEHAVIOUR for purchased/donated/transferred animals that arrived
     * pregnant (acquisitionMethod != BIRTH):
     *   • Gestation + due-date window validations are SKIPPED — breeding
     *     happened before the animal arrived on this farm.
     *   • Birth-interval validation is still applied.
     *   • After birth pregnancy flags and breeding record are cleared normally.
     */
    @Transactional
    public LivestockBirth addNew(LivestockBirth birth) {

        birth.setIsExternalBirth(false);

        resolveAndSetLivestock(birth);

        Livestock mother = birth.getLivestock();
        if (mother == null) {
            throw new RuntimeException("Mother animal is required — please select the mother from the list.");
        }

        boolean isPurchasedPregnant = !Livestock.ACQ_BIRTH.equals(mother.getAcquisitionMethod());

        if (isPurchasedPregnant) {
            validateBirthInterval(mother, birth.getBirthDate());
        } else {
            validateGestationComplete(mother, birth.getBirthDate());
            validateBirthInterval(mother, birth.getBirthDate());
            validateDueDateWindow(mother, birth.getBirthDate());
        }

        // Clear pregnancy state on the mother
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

        // Create draft children for each expected offspring
        Integer offspringCount = birth.getOffspringCount();
        if (offspringCount != null && offspringCount > 0) {
            for (int i = 0; i < offspringCount; i++) {
                Livestock draft = new Livestock();
                draft.setIsDraft(true);
                draft.setDraftBirthEvent(saved);
                draft.setStatus(Livestock.STATUS_ACTIVE);
                draft.setAcquisitionMethod(Livestock.ACQ_BIRTH);
                draft.setGender("UNKNOWN");
                draft.setBirthDate(birth.getBirthDate());
                // FIX: mother reference is set here and must survive through completeDraft()
                draft.setMother(mother);
                draft.setTagNumber("DRAFT-" + saved.getId().toString().substring(0, 8).toUpperCase() + "-" + (i + 1));
                draft.setPregnancyStatus("NOT_PREGNANT");
                draft.setIsPregnant(false);
                draft.setOffspringCount(0);
                draft.setIsDeleted(false);
                // Pre-fill acquisition source so it is correct from the start
                draft.setAcquisitionSource("Born on this farm — Mother: " + mother.getTagNumber());

                if (saved.getLivestock() != null && saved.getLivestock().getLivestockCategory() != null) {
                    draft.setLivestockCategory(saved.getLivestock().getLivestockCategory());
                }

                livestockRepository.save(draft);

                LivestockOffspring link = new LivestockOffspring(saved, draft, 1);
                offspringRepository.save(link);
            }
        }

        return saved;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION 1 — Gestation period completeness
    // ═════════════════════════════════════════════════════════════════════════

    private void validateGestationComplete(Livestock mother, LocalDate birthDate) {
        if (mother.getLivestockCategory() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() <= 0) {
            return;
        }

        int gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        LocalDate effectiveBirthDate = (birthDate != null) ? birthDate : LocalDate.now();

        Optional<LivestockBreeding> activeBreedingOpt = breedingRepository
                .findByLivestockId(mother.getId())
                .stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate));

        if (activeBreedingOpt.isEmpty()) {
            if (mother.getLastBreedingDate() == null) return;
            LocalDate breedingDate = mother.getLastBreedingDate();
            LocalDate expectedBirthDate = breedingDate.plusMonths(gestationMonths);
            if (effectiveBirthDate.isBefore(expectedBirthDate)) {
                throw new RuntimeException("Animal " + mother.getTagNumber()
                        + " is not ready to give birth yet. Earliest birth date: " + expectedBirthDate);
            }
            return;
        }

        LivestockBreeding activeBreeding = activeBreedingOpt.get();
        LocalDate breedingDate = activeBreeding.getBreedingDate();
        LocalDate expectedBirthDate = breedingDate.plusMonths(gestationMonths);

        if (effectiveBirthDate.isBefore(expectedBirthDate)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " is not ready to give birth yet. Earliest birth date: " + expectedBirthDate);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION 2 — Minimum interval between consecutive births
    // ═════════════════════════════════════════════════════════════════════════

    private void validateBirthInterval(Livestock mother, LocalDate newBirthDate) {
        if (mother.getLastBirthDate() == null) return;

        if (mother.getLivestockCategory() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() <= 0) {
            return;
        }

        boolean hasPreExistingConfirmedPregnancy = breedingRepository
                .findByLivestockId(mother.getId())
                .stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getBreedingDate() != null)
                .anyMatch(b -> !b.getBreedingDate().isAfter(mother.getLastBirthDate()));

        if (hasPreExistingConfirmedPregnancy) return;

        int gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        LocalDate effectiveBirthDate = (newBirthDate != null) ? newBirthDate : LocalDate.now();
        LocalDate earliestNextBirth  = mother.getLastBirthDate().plusMonths(gestationMonths);

        if (effectiveBirthDate.isBefore(earliestNextBirth)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " gave birth too recently. Earliest next birth date: " + earliestNextBirth);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION 3 — Birth date must be within the due date window
    // ═════════════════════════════════════════════════════════════════════════

    private void validateDueDateWindow(Livestock mother, LocalDate birthDate) {
        if (mother.getLivestockCategory() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() == null
                || mother.getLivestockCategory().getGestationPeriodMonths() <= 0) {
            return;
        }

        final int DAYS_BEFORE_TOLERANCE = 14;
        final int DAYS_AFTER_TOLERANCE  = 14;

        LocalDate effectiveBirthDate = (birthDate != null) ? birthDate : LocalDate.now();

        Optional<LivestockBreeding> activeBreedingOpt = breedingRepository
                .findByLivestockId(mother.getId())
                .stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .filter(b -> b.getExpectedDueDate() != null)
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate));

        if (activeBreedingOpt.isEmpty()) return;

        LivestockBreeding activeBreeding = activeBreedingOpt.get();
        LocalDate expectedDueDate = activeBreeding.getExpectedDueDate();
        LocalDate earliestAllowedBirth = expectedDueDate.minusDays(DAYS_BEFORE_TOLERANCE);
        LocalDate latestAllowedBirth   = expectedDueDate.plusDays(DAYS_AFTER_TOLERANCE);

        if (effectiveBirthDate.isBefore(earliestAllowedBirth)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " birth date is too early. Earliest allowed: " + earliestAllowedBirth);
        }
        if (effectiveBirthDate.isAfter(latestAllowedBirth)) {
            throw new RuntimeException("Animal " + mother.getTagNumber()
                    + " birth date is too late. Latest allowed: " + latestAllowedBirth);
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

        return birthRepository.save(existing);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DELETE
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(UUID id) {
        birthRepository.deleteById(id);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CHILD LINKING
    //
    // FIX: The available-animals filter in LivestockBirthController previously
    // excluded any animal where getMother() != null.  This blocked draft animals
    // from being linked because they already have their mother set.  The fix is
    // to move the mother-null check ONLY to the controller view (for display
    // purposes) and here in linkChild() we always update/overwrite the mother.
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockOffspring linkChild(UUID birthId, UUID childLivestockId) {
        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        Livestock child = livestockRepository.findById(childLivestockId)
                .orElseThrow(() -> new RuntimeException("Child livestock not found"));

        Livestock mother = birth.getLivestock();
        if (mother != null) {
            child.setMother(mother);
            // Update acquisition source to reflect the correct mother
            if (child.getAcquisitionSource() == null || child.getAcquisitionSource().isBlank()
                    || child.getAcquisitionSource().startsWith("Born on this farm")) {
                child.setAcquisitionSource("Born on this farm — Mother: " + mother.getTagNumber());
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
    // DATABASE FIX HELPER
    // Call this once to repair any existing animals whose mother_id is NULL
    // but whose draft_birth_id points to a birth event with a known mother.
    // You can expose this as an admin endpoint or run it at startup once.
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
            if (animal.getAcquisitionSource() == null || animal.getAcquisitionSource().isBlank()) {
                animal.setAcquisitionSource("Born on this farm — Mother: " + mother.getTagNumber());
            }
            livestockRepository.save(animal);
        }
        return broken.size();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private void resolveAndSetLivestock(LivestockBirth birth) {
        String idStr = birth.getLivestockIdValue();
        if (idStr != null && !idStr.trim().isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found: " + idStr));
            birth.setLivestock(ls);
        }
    }

    private int calculateGeneration(Livestock animal) {
        int gen = 0;
        Livestock current = animal;
        while (current.getMother() != null && gen < 50) {
            gen++;
            current = livestockRepository.findById(current.getMother().getId()).orElse(null);
            if (current == null) break;
        }
        return gen;
    }
}
