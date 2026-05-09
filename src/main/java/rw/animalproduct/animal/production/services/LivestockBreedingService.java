package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockBreedingService {

    private final LivestockBreedingRepository breedingRepository;
    private final VeterinarianRepository      veterinarianRepository;
    private final LivestockRepository         livestockRepository;
    private final LifecycleEmailService       emailService;

    public LivestockBreedingService(LivestockBreedingRepository breedingRepository,
                                    VeterinarianRepository veterinarianRepository,
                                    LivestockRepository livestockRepository,
                                    LifecycleEmailService emailService) {
        this.breedingRepository     = breedingRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.livestockRepository    = livestockRepository;
        this.emailService           = emailService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    public List<LivestockBreeding> getAll() {
        return breedingRepository.findByIsDeletedFalse(Pageable.unpaged()).getContent();
    }

    public Page<LivestockBreeding> getPaginated(int page, int size) {
        return breedingRepository.findByIsDeletedFalse(PageRequest.of(page, size));
    }

    public Optional<LivestockBreeding> getById(UUID id) {
        return breedingRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public LivestockBreeding addNew(LivestockBreeding breeding) {
        breeding.setIsDeleted(false);
        if (breeding.getStatus() == null || breeding.getStatus().isBlank()) {
            breeding.setStatus(LivestockBreeding.STATUS_PENDING);
        }
        LivestockBreeding saved = breedingRepository.save(breeding);
        emailService.sendBreedingStartedNotification(saved);
        return saved;
    }

    // ── CREATE FOR PURCHASED PREGNANT ANIMAL ─────────────────────────────────

    /**
     * Called automatically when a PURCHASED/DONATED/TRANSFERRED animal is
     * registered with is_pregnant = true.
     *
     * Creates a CONFIRMED_PREGNANT breeding record with method = PURCHASE_PREGNANT
     * so the animal appears in pregnancy tracking, due-date alerts, and all
     * breeding dashboards — even though no on-farm breeding event was recorded.
     *
     * IMPORTANT: The database check constraint on livestock_breeding.breeding_method
     * must include 'PURCHASE_PREGNANT'. Run this migration before deploying:
     *
     *   ALTER TABLE livestock_breeding
     *   DROP CONSTRAINT livestock_breeding_breeding_method_check;
     *
     *   ALTER TABLE livestock_breeding
     *   ADD CONSTRAINT livestock_breeding_breeding_method_check
     *   CHECK (breeding_method IN (
     *       'NATURAL','ARTIFICIAL_INSEMINATION','EMBRYO_TRANSFER','PURCHASE_PREGNANT'
     *   ));
     *
     * @param animal          The newly registered pregnant animal
     * @param conceptionDate  The conception date entered during registration (may be null)
     * @param expectedDueDate The expected due date (may be null — estimated from gestation)
     */
    @Transactional
    public LivestockBreeding createForPurchasedPregnantAnimal(Livestock animal,
                                                              LocalDate conceptionDate,
                                                              LocalDate expectedDueDate) {

        // Guard: only for non-BIRTH, female animals
        if (!isEligibleForPurchasePregnantRecord(animal)) {
            throw new IllegalArgumentException(
                    "createForPurchasedPregnantAnimal() called for ineligible animal: "
                            + animal.getTagNumber()
                            + " (must be female, non-BIRTH, and pregnant)");
        }

        LivestockBreeding breeding = new LivestockBreeding();
        breeding.setLivestock(animal);
        breeding.setIsDeleted(false);
        breeding.setStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);

        // Use conception date as the "breeding date" proxy.
        // If unknown, fall back to: date received → today
        LocalDate breedingDateProxy = conceptionDate;
        if (breedingDateProxy == null) {
            breedingDateProxy = animal.getDateReceived() != null
                    ? animal.getDateReceived()
                    : LocalDate.now();
        }
        breeding.setBreedingDate(breedingDateProxy);

        // Mark as PURCHASE_PREGNANT so it's distinguishable in the UI
        // The DB constraint MUST allow this value — see migration SQL above.
        breeding.setBreedingMethod(LivestockBreeding.METHOD_PURCHASE_PREGNANT);

        // Resolve due date: use provided value, or estimate from category gestation
        LocalDate dueDate = expectedDueDate;
        if (dueDate == null && animal.getLivestockCategory() != null
                && animal.getLivestockCategory().getGestationPeriodMonths() != null) {
            dueDate = breedingDateProxy.plusMonths(
                    animal.getLivestockCategory().getGestationPeriodMonths());
        }
        breeding.setExpectedDueDate(dueDate);

        // Pregnancy check: schedule 7 days from today (vet should verify)
        breeding.setExpectedPregnancyCheckDate(LocalDate.now().plusDays(7));

        breeding.setNotes(
                "Animal purchased/received while already pregnant. "
                        + "Pregnancy tracking started on registration date: "
                        + LocalDate.now() + "."
        );

        LivestockBreeding saved = breedingRepository.save(breeding);

        // ── Sync pregnancy fields back onto the livestock record ──────────────
        animal.setStatus(Livestock.STATUS_PREGNANT);
        animal.setIsPregnant(true);
        animal.setPregnancyStatus("PREGNANT");
        animal.setConceptionDate(conceptionDate);
        animal.setExpectedDueDate(dueDate);
        if (conceptionDate != null) {
            animal.setLastBreedingDate(conceptionDate);
        }
        livestockRepository.save(animal);

        return saved;
    }

    /**
     * Returns true if the animal qualifies for a PURCHASE_PREGNANT breeding record.
     * Must be: female, not born on this farm, and currently pregnant.
     */
    private boolean isEligibleForPurchasePregnantRecord(Livestock animal) {
        return "FEMALE".equalsIgnoreCase(animal.getGender())
                && Boolean.TRUE.equals(animal.getIsPregnant())
                && !Livestock.ACQ_BIRTH.equals(animal.getAcquisitionMethod());
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Transactional
    public LivestockBreeding update(UUID id, LivestockBreeding updated) {
        LivestockBreeding existing = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        String oldStatus = existing.getStatus();

        existing.setLivestock(updated.getLivestock());
        existing.setMaleLivestock(updated.getMaleLivestock());
        existing.setBreedingDate(updated.getBreedingDate());
        existing.setBreedingMethod(updated.getBreedingMethod());
        existing.setStatus(updated.getStatus());
        existing.setExpectedPregnancyCheckDate(updated.getExpectedPregnancyCheckDate());
        existing.setExpectedDueDate(updated.getExpectedDueDate());
        existing.setNotes(updated.getNotes());
        existing.setVeterinarian(updated.getVeterinarian());

        LivestockBreeding saved = breedingRepository.save(existing);

        // When status transitions to CONFIRMED_PREGNANT, update the animal record
        if (!saved.getStatus().equals(oldStatus)
                && LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(saved.getStatus())) {
            confirmPregnancyOnAnimal(saved);
            emailService.sendPregnancyConfirmedNotification(saved);
        }

        // When status transitions to FAILED or COMPLETED, reset pregnancy flags
        if (!saved.getStatus().equals(oldStatus)
                && (LivestockBreeding.STATUS_FAILED.equals(saved.getStatus())
                || LivestockBreeding.STATUS_COMPLETED.equals(saved.getStatus()))) {
            resetPregnancyOnAnimal(saved.getLivestock());
        }

        return saved;
    }

    // ── DELETE (soft) ─────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
        b.setIsDeleted(true);
        breedingRepository.save(b);
    }

    // ── CONFIRM PREGNANCY ─────────────────────────────────────────────────────

    @Transactional
    public LivestockBreeding confirmPregnancy(UUID id, LocalDate expectedDueDate) {
        LivestockBreeding breeding = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        breeding.setStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        breeding.setExpectedDueDate(expectedDueDate);
        LivestockBreeding saved = breedingRepository.save(breeding);

        confirmPregnancyOnAnimal(saved);
        emailService.sendPregnancyConfirmedNotification(saved);

        return saved;
    }

    // ── AUTO-COMPLETE ON BIRTH ────────────────────────────────────────────────

    /**
     * Called automatically by LivestockBirthService when a birth is recorded.
     *
     * Finds the most recent CONFIRMED_PREGNANT or PENDING breeding record
     * for the mother, marks it COMPLETED, and resets the animal's pregnancy flags.
     */
    @Transactional
    public void completeBreedingOnBirth(UUID femaleLivestockId, LocalDate birthDate) {

        Optional<LivestockBreeding> activeOpt =
                breedingRepository.findMostRecentActiveBreeding(femaleLivestockId);

        activeOpt.ifPresent(breeding -> {
            breeding.setStatus(LivestockBreeding.STATUS_COMPLETED);
            String autoNote = "[Auto-completed] Birth recorded on: " + birthDate;
            breeding.setNotes(breeding.getNotes() == null || breeding.getNotes().isBlank()
                    ? autoNote
                    : breeding.getNotes() + "\n" + autoNote);
            breedingRepository.save(breeding);
        });

        // Reset animal pregnancy flags regardless of whether a breeding was found
        livestockRepository.findById(femaleLivestockId).ifPresent(animal -> {
            animal.setIsPregnant(false);
            animal.setPregnancyStatus("NOT_PREGNANT");
            animal.setStatus(Livestock.STATUS_ACTIVE);
            animal.setLastBirthDate(birthDate);
            animal.setExpectedDueDate(null);
            livestockRepository.save(animal);
        });
    }

    // ── DASHBOARD / REPORT HELPERS ────────────────────────────────────────────

    public List<LivestockBreeding> getDueForPregnancyCheck() {
        return breedingRepository.findOverduePregnancyChecks(LocalDate.now());
    }

    public List<LivestockBreeding> getApproachingDueDate() {
        LocalDate today = LocalDate.now();
        return breedingRepository.findApproachingDueDate(today, today.plusDays(30));
    }

    public List<LivestockBreeding> getOverduePregnancies() {
        return breedingRepository.findOverduePregnancies(LocalDate.now());
    }

    public List<LivestockBreeding> getAllActivePregnancies() {
        return breedingRepository.findAllActivePregnancies();
    }

    public List<LivestockBreeding> getRecentBreedings() {
        return breedingRepository.findRecentBreedings(10);
    }

    public double getSuccessRate() {
        long total = breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_PENDING)
                + breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_CONFIRMED_PREGNANT)
                + breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_FAILED)
                + breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_COMPLETED);
        if (total == 0) return 0.0;
        long confirmed = breedingRepository
                .countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        return (confirmed * 100.0) / total;
    }

    public long countByStatus(String status) {
        return breedingRepository.countByStatusAndIsDeletedFalse(status);
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private void confirmPregnancyOnAnimal(LivestockBreeding saved) {
        if (saved.getLivestock() == null) return;
        Livestock animal = saved.getLivestock();
        animal.setStatus(Livestock.STATUS_PREGNANT);
        animal.setIsPregnant(true);
        animal.setPregnancyStatus("PREGNANT");
        animal.setExpectedDueDate(saved.getExpectedDueDate());
        livestockRepository.save(animal);
    }

    private void resetPregnancyOnAnimal(Livestock animal) {
        if (animal == null) return;
        animal.setIsPregnant(false);
        animal.setPregnancyStatus("NOT_PREGNANT");
        if (Livestock.STATUS_PREGNANT.equals(animal.getStatus())) {
            animal.setStatus(Livestock.STATUS_ACTIVE);
        }
        animal.setExpectedDueDate(null);
        livestockRepository.save(animal);
    }
}