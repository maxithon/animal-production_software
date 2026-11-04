package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.ActionRequiredItemDTO;
import rw.animalproduct.animal.production.dto.ActiveBreedingRowDTO;
import rw.animalproduct.animal.production.dto.BreedingManagementKpiDTO;
import rw.animalproduct.animal.production.dto.IneligibleFemaleDTO;
import rw.animalproduct.animal.production.dto.ReadyToBreedReportDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LivestockBreedingService {

    private static final Logger log = LoggerFactory.getLogger(LivestockBreedingService.class);

    private static final int MIN_PREGNANCY_CHECK_DAYS = 21;
    private static final double PREGNANCY_CHECK_FRACTION_OF_GESTATION = 0.16;
    private static final int FALLBACK_PREGNANCY_CHECK_DAYS = 30;
    private static final double AVG_DAYS_PER_MONTH = 30.44;
    private static final int FALLBACK_POSTPARTUM_RECOVERY_DAYS = 45;
    private static final int CONSECUTIVE_FAILURES_FLAG_THRESHOLD = 2;

    private static final String HEALTH_HEALTHY = "HEALTHY";
    private static final String HEALTH_SICK = "SICK";
    private static final String HEALTH_UNDER_TREATMENT = "UNDER_TREATMENT";

    private static final String FORM_METHOD_NATURAL = "NATURAL";
    private static final String FORM_METHOD_AI = "ARTIFICIAL_INSEMINATION";
    private static final String FORM_METHOD_EMBRYO_TRANSFER = "EMBRYO_TRANSFER";

    private final LivestockBreedingRepository breedingRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockBirthRepository birthRepository;
    private final LifecycleEmailService emailService;
    private final AuditLogService auditLogService;
    private final ReadyToBreedReportService readyToBreedReportService;
    private final FemalesReadyToBreedService femalesReadyToBreedService;

    public LivestockBreedingService(LivestockBreedingRepository breedingRepository,
                                    VeterinarianRepository veterinarianRepository,
                                    LivestockRepository livestockRepository,
                                    LivestockBirthRepository birthRepository,
                                    LifecycleEmailService emailService,
                                    AuditLogService auditLogService,
                                    ReadyToBreedReportService readyToBreedReportService,
                                    FemalesReadyToBreedService femalesReadyToBreedService) {
        this.breedingRepository = breedingRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.livestockRepository = livestockRepository;
        this.birthRepository = birthRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.readyToBreedReportService = readyToBreedReportService;
        this.femalesReadyToBreedService = femalesReadyToBreedService;
    }

    // ════════════════════════════════════════════════════════════════════
    // READ
    // ════════════════════════════════════════════════════════════════════

    public List<LivestockBreeding> getAll() { return breedingRepository.findAll(); }

    public Optional<LivestockBreeding> getById(UUID id) { return breedingRepository.findById(id); }

    public LivestockBreeding findById(UUID id) {
        return breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
    }

    public Page<LivestockBreeding> findAll(Pageable pageable, String status, UUID livestockId) {
        if (status != null && livestockId != null)
            return breedingRepository.findByLivestockIdAndStatus(livestockId, status, pageable);
        if (status != null)      return breedingRepository.findByStatus(status, pageable);
        if (livestockId != null) return breedingRepository.findByLivestockId(livestockId, pageable);
        return breedingRepository.findAll(pageable);
    }

    public List<LivestockBreeding> findRecentBreedings(int limit) {
        return breedingRepository.findRecentBreedings(limit);
    }

    public List<LivestockBreeding> getAllActivePregnancies() {
        return breedingRepository.findAllActivePregnancies();
    }
    public List<LivestockBreeding> findActivePregnancies() { return getAllActivePregnancies(); }

    public List<LivestockBreeding> findOverduePregnancyChecks() {
        return breedingRepository.findOverduePregnancyChecks(LocalDate.now());
    }

    // ════════════════════════════════════════════════════════════════════
    // MANAGEMENT DASHBOARD SUPPORT
    // ════════════════════════════════════════════════════════════════════

    public BreedingManagementKpiDTO getManagementKpis() {
        long activeBreedings = breedingRepository.countByStatus(LivestockBreeding.STATUS_PENDING);
        long femalesReady    = readyToBreedReportService.getAllReady(null, null).size();
        long pregnantAnimals = breedingRepository.countActivePregnancies();
        long pendingChecks   = breedingRepository
                .findOverduePregnancyChecks(LivestockBreeding.STATUS_PENDING, LocalDate.now()).size();
        double successRate   = getSuccessRate();

        return BreedingManagementKpiDTO.builder()
                .activeBreedings(activeBreedings)
                .femalesReady(femalesReady)
                .pregnantAnimals(pregnantAnimals)
                .pendingPregnancyChecks(pendingChecks)
                .breedingSuccessRate(Math.round(successRate * 10.0) / 10.0)
                .build();
    }

    public List<ActionRequiredItemDTO> getActionsRequired() {
        LocalDate today = LocalDate.now();
        List<ActionRequiredItemDTO> items = new ArrayList<>();

        for (LivestockBreeding b : breedingRepository.findOverduePregnancyChecks(
                LivestockBreeding.STATUS_PENDING, today)) {
            if (b.getLivestock() == null) continue;
            long daysOverdue = ChronoUnit.DAYS.between(b.getExpectedPregnancyCheckDate(), today);
            items.add(ActionRequiredItemDTO.builder()
                    .type(ActionRequiredItemDTO.Type.OVERDUE_PREGNANCY_CHECK)
                    .severity(ActionRequiredItemDTO.Severity.RED)
                    .icon("🔴")
                    .title("Overdue pregnancy check")
                    .message(b.getLivestock().getTagNumber() + " — pregnancy check was due "
                            + daysOverdue + " day(s) ago")
                    .livestockId(b.getLivestock().getId())
                    .tagNumber(b.getLivestock().getTagNumber())
                    .relevantDate(b.getExpectedPregnancyCheckDate())
                    .daysValue(daysOverdue)
                    .actionUrl("/livestock/breeding/view/" + b.getId())
                    .build());
        }

        for (LivestockBreeding b : breedingRepository.findOverduePregnancies(today)) {
            if (b.getLivestock() == null) continue;
            long daysOverdue = ChronoUnit.DAYS.between(b.getExpectedDueDate(), today);
            items.add(ActionRequiredItemDTO.builder()
                    .type(ActionRequiredItemDTO.Type.OVERDUE_DUE_DATE)
                    .severity(ActionRequiredItemDTO.Severity.RED)
                    .icon("🔴")
                    .title("Overdue due date")
                    .message(b.getLivestock().getTagNumber() + " — expected due date passed "
                            + daysOverdue + " day(s) ago")
                    .livestockId(b.getLivestock().getId())
                    .tagNumber(b.getLivestock().getTagNumber())
                    .relevantDate(b.getExpectedDueDate())
                    .daysValue(daysOverdue)
                    .actionUrl("/livestock/breeding/view/" + b.getId())
                    .build());
        }

        for (ReadyToBreedReportDTO f : readyToBreedReportService.getAllNotReady(null, null)) {
            if (f.getDaysUntilReady() != null && f.getDaysUntilReady() > 0 && f.getDaysUntilReady() <= 14) {
                items.add(ActionRequiredItemDTO.builder()
                        .type(ActionRequiredItemDTO.Type.BECOMING_READY_SOON)
                        .severity(ActionRequiredItemDTO.Severity.YELLOW)
                        .icon("🟡")
                        .title("Becoming ready soon")
                        .message(f.getTagNumber() + " — ready to breed in "
                                + f.getDaysUntilReady() + " day(s)")
                        .livestockId(f.getLivestockId())
                        .tagNumber(f.getTagNumber())
                        .relevantDate(f.getRecommendedBreedingDate())
                        .daysValue(f.getDaysUntilReady())
                        .actionUrl("/livestock/view/" + f.getLivestockId())
                        .build());
            }
        }

        for (IneligibleFemaleDTO f : femalesReadyToBreedService.getNotYetEligible()) {
            items.add(ActionRequiredItemDTO.builder()
                    .type(ActionRequiredItemDTO.Type.NOT_READY)
                    .severity(ActionRequiredItemDTO.Severity.YELLOW)
                    .icon("🟡")
                    .title("Not ready for breeding")
                    .message(f.getTagNumber() + " — " + f.getReason())
                    .livestockId(f.getId())
                    .tagNumber(f.getTagNumber())
                    .relevantDate(f.getNextEligibleDate())
                    .actionUrl("/livestock/view/" + f.getId())
                    .build());
        }

        items.sort(Comparator
                .comparing((ActionRequiredItemDTO i) ->
                        i.getSeverity() == ActionRequiredItemDTO.Severity.RED ? 0 : 1)
                .thenComparing(i -> i.getDaysValue() == null ? 0L : -i.getDaysValue()));

        return items;
    }

    public List<ActiveBreedingRowDTO> getActiveBreedingRows() {
        LocalDate today = LocalDate.now();
        return breedingRepository.findByStatus(LivestockBreeding.STATUS_PENDING).stream()
                .map(b -> {
                    boolean overdue = b.getExpectedPregnancyCheckDate() != null
                            && b.getExpectedPregnancyCheckDate().isBefore(today);
                    return ActiveBreedingRowDTO.builder()
                            .breedingId(b.getId())
                            .livestockId(b.getLivestock() != null ? b.getLivestock().getId() : null)
                            .femaleTag(b.getLivestock() != null ? b.getLivestock().getTagNumber() : "N/A")
                            .maleId(b.getMale() != null ? b.getMale().getId() : null)
                            .maleTag(b.getMale() != null ? b.getMale().getTagNumber() : null)
                            .categoryName(b.getLivestock() != null
                                    && b.getLivestock().getLivestockCategory() != null
                                    ? b.getLivestock().getLivestockCategory().getName()
                                    : "Uncategorized")
                            .breedingMethod(b.getBreedingMethod())
                            .breedingDate(b.getBreedingDate())
                            .expectedPregnancyCheckDate(b.getExpectedPregnancyCheckDate())
                            .daysSinceBreeding(b.getBreedingDate() != null
                                    ? ChronoUnit.DAYS.between(b.getBreedingDate(), today) : null)
                            .status(b.getStatus())
                            .overdue(overdue)
                            .build();
                })
                .sorted(Comparator.comparing(ActiveBreedingRowDTO::isOverdue).reversed()
                        .thenComparing(r -> r.getDaysSinceBreeding() == null
                                ? 0 : -r.getDaysSinceBreeding()))
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════════
    // BREEDING CONTEXT — used by the breeding form readiness card and
    // by GET /livestock/breeding/api/context
    // ════════════════════════════════════════════════════════════════════

    /**
     * Readiness / decision-support snapshot for one female. Returns a map
     * with the keys the UI's "Breeding Readiness" card reads. Does not throw;
     * errors are returned under the "error" key so the AJAX endpoint returns
     * a well-formed JSON response.
     */
    public Map<String, Object> getBreedingContext(UUID livestockId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (livestockId == null) {
            result.put("error", "Select a female animal to see her breeding readiness.");
            return result;
        }

        Livestock female = livestockRepository.findById(livestockId).orElse(null);
        if (female == null) {
            result.put("error", "Selected animal could not be found.");
            return result;
        }

        LocalDate today = LocalDate.now();

        result.put("tagNumber",  female.getTagNumber());
        result.put("ageDisplay", female.getAgeDisplay());
        result.put("ageMonths",  female.getAgeInMonths());
        result.put("categoryId",
                female.getLivestockCategory() != null ? female.getLivestockCategory().getId() : null);
        result.put("categoryName",
                female.getLivestockCategory() != null ? female.getLivestockCategory().getName() : null);

        result.put("healthStatus",       female.getHealthStatus());
        result.put("healthyForBreeding", isHealthyForBreeding(female));

        Integer minBreedingAge = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMinBreedingAgeMonths() : null;
        Integer maxBreedingAge = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMaxBreedingAgeMonths() : null;
        result.put("minBreedingAgeMonths", minBreedingAge);
        result.put("maxBreedingAgeMonths", maxBreedingAge);

        Optional<LocalDate> postpartumReadyDate = getPostpartumReadyDate(female);
        boolean inRecovery = isInPostpartumRecovery(female);
        result.put("postpartumRecoveryDays",
                female.getLivestockCategory() != null
                        ? female.getLivestockCategory().getPostpartumRecoveryDays() : null);
        result.put("postpartumReadyDate", postpartumReadyDate.orElse(null));
        result.put("inPostpartumRecovery", inRecovery);
        result.put("daysUntilPostpartumReady", postpartumReadyDate
                .map(d -> Math.max(0, ChronoUnit.DAYS.between(today, d)))
                .orElse(null));

        String ineligibilityReason = getIneligibilityReason(female);
        boolean readyToBreed = ineligibilityReason == null;
        String readyReason = readyToBreed
                ? buildReadyReason(female, minBreedingAge)
                : ineligibilityReason;
        result.put("readyToBreed",       readyToBreed);
        result.put("readyToBreedReason", readyReason);

        LocalDate lastBirthDate = getLastBirthDate(female);
        result.put("lastBirthDate", lastBirthDate);
        result.put("daysSinceLastBirth", lastBirthDate != null
                ? ChronoUnit.DAYS.between(lastBirthDate, today) : null);

        List<LivestockBreeding> allBreedings = breedingRepository.findByLivestockId(livestockId);
        result.put("previousBreedingsCount", allBreedings.size());

        LivestockBreeding last = breedingRepository
                .findFirstByLivestockIdOrderByBreedingDateDesc(livestockId)
                .orElse(null);

        if (last != null) {
            Map<String, Object> prev = new LinkedHashMap<>();
            prev.put("date",        last.getBreedingDate());
            prev.put("status",      last.getStatus());
            prev.put("statusLabel", last.getStatusLabel());
            result.put("previousBreeding", prev);

            boolean isRetry = LivestockBreeding.STATUS_FAILED.equals(last.getStatus())
                    || LivestockBreeding.STATUS_ABORTED.equals(last.getStatus());
            int nextAttempt = isRetry
                    ? (last.getBreedingAttempt() != null ? last.getBreedingAttempt() : 1) + 1
                    : 1;
            result.put("nextBreedingAttempt", nextAttempt);
            result.put("isRetryAttempt",      isRetry);
        } else {
            result.put("previousBreeding",    null);
            result.put("nextBreedingAttempt", 1);
            result.put("isRetryAttempt",      false);
        }

        result.put("historyAdvisory",  getBreedingHistoryAdvisory(livestockId));
        result.put("currentHeatStatus", "NOT_TRACKED");

        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // DASHBOARD STATS
    // ════════════════════════════════════════════════════════════════════

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBreedings",      breedingRepository.count());
        stats.put("pending",             breedingRepository.countByStatus(LivestockBreeding.STATUS_PENDING));
        stats.put("confirmedPregnant",   breedingRepository.countByStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT));
        stats.put("completed",           breedingRepository.countByStatus(LivestockBreeding.STATUS_COMPLETED));
        stats.put("failed",              breedingRepository.countByStatus(LivestockBreeding.STATUS_FAILED));
        stats.put("activePregnancies",   breedingRepository.findAllActivePregnancies().size());
        stats.put("overdueChecks",       breedingRepository.findOverduePregnancyChecks(LocalDate.now()).size());
        stats.put("overduePregnancies",  breedingRepository.findOverduePregnancies(LocalDate.now()).size());
        stats.put("successRate",         getSuccessRate());
        return stats;
    }

    public double getSuccessRate() {
        long total = breedingRepository.countByStatus(LivestockBreeding.STATUS_PENDING)
                + breedingRepository.countByStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT)
                + breedingRepository.countByStatus(LivestockBreeding.STATUS_FAILED)
                + breedingRepository.countByStatus(LivestockBreeding.STATUS_COMPLETED);
        if (total == 0) return 0.0;
        long confirmed = breedingRepository.countByStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        return (confirmed * 100.0) / total;
    }

    public long countByStatus(String status) {
        return breedingRepository.countByStatus(status);
    }

    // ════════════════════════════════════════════════════════════════════
    // CREATE
    // ════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockBreeding addNew(LivestockBreeding breeding) {
        Livestock female = breeding.getLivestock();
        if (female == null && breeding.getLivestockIdValue() != null)
            female = livestockRepository.findById(breeding.getLivestockIdValue()).orElse(null);

        String reason = getIneligibilityReason(female);
        if (reason != null)
            throw new IllegalStateException(
                    (female != null ? female.getTagNumber() : "Selected animal")
                            + " is not eligible for breeding: " + reason);

        Livestock male = breeding.getMale();
        if (male == null && breeding.getMaleLivestockIdValue() != null)
            male = livestockRepository.findById(breeding.getMaleLivestockIdValue()).orElse(null);

        String methodReason = getBreedingMethodCompatibilityReason(breeding.getBreedingMethod(), male);
        if (methodReason != null) throw new IllegalStateException(methodReason);

        String maleReason = getMaleIneligibilityReason(male, female);
        if (maleReason != null)
            throw new IllegalStateException(
                    (male != null ? male.getTagNumber() : "Selected male")
                            + " is not eligible: " + maleReason);

        applyCalculatedFields(breeding);
        LivestockBreeding saved = breedingRepository.save(breeding);
        auditLogService.logCreate("livestock_breeding", saved.getId(), saved, "system");
        emailService.sendBreedingStartedNotification(saved);
        return saved;
    }

    public LivestockBreeding createBreeding(LivestockBreeding b) { return addNew(b); }

    // ════════════════════════════════════════════════════════════════════
    // UPDATE
    // ════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockBreeding updateBreeding(UUID id, LivestockBreeding updated) {
        return update(id, updated);
    }

    @Transactional
    public LivestockBreeding update(UUID id, LivestockBreeding updated) {
        LivestockBreeding existing = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        String oldStatus = existing.getStatus();
        String beforeSnapshot = auditLogService.snapshot(existing);

        existing.setLivestock(updated.getLivestock());
        existing.setMale(updated.getMale());
        existing.setBreedingDate(updated.getBreedingDate());
        existing.setBreedingMethod(updated.getBreedingMethod());
        existing.setStatus(updated.getStatus());
        if (updated.getExpectedPregnancyCheckDate() != null)
            existing.setExpectedPregnancyCheckDate(updated.getExpectedPregnancyCheckDate());
        if (updated.getExpectedDueDate() != null)
            existing.setExpectedDueDate(updated.getExpectedDueDate());
        if (updated.getPregnancyConfirmedDate() != null)
            existing.setPregnancyConfirmedDate(updated.getPregnancyConfirmedDate());
        existing.setNotes(updated.getNotes());
        existing.setVeterinarian(updated.getVeterinarian());

        LivestockBreeding saved = breedingRepository.save(existing);

        if (!Objects.equals(saved.getStatus(), oldStatus)) {
            if (LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(saved.getStatus())) {
                if (saved.getPregnancyConfirmedDate() == null) {
                    saved.setPregnancyConfirmedDate(LocalDate.now());
                    saved = breedingRepository.save(saved);
                }
                confirmPregnancyOnAnimal(saved);
                emailService.sendPregnancyConfirmedNotification(saved);
            } else if (LivestockBreeding.STATUS_FAILED.equals(saved.getStatus())
                    || LivestockBreeding.STATUS_COMPLETED.equals(saved.getStatus())) {
                resetPregnancyOnAnimal(saved.getLivestock());
            }
        }

        String afterSnapshot = auditLogService.snapshot(saved);
        auditLogService.logUpdate("livestock_breeding", saved.getId(), beforeSnapshot, afterSnapshot, "system");
        emailService.sendBreedingUpdatedNotification(saved, "Fields updated");
        return saved;
    }

    // ════════════════════════════════════════════════════════════════════
    // DELETE
    // ════════════════════════════════════════════════════════════════════

    @Transactional
    public void delete(UUID id) { softDeleteBreeding(id, "system"); }

    @Transactional
    public void softDeleteBreeding(UUID id, String deletedBy) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        if (birthRepository.existsByBreedingId(id)) {
            throw new IllegalStateException(
                    "This breeding record has a birth attached to it. "
                            + "Delete or amend the birth first, then delete this breeding.");
        }

        String snapshot = auditLogService.snapshot(b);
        auditLogService.logDelete("livestock_breeding", id, snapshot, deletedBy);
        breedingRepository.delete(b);
        emailService.sendBreedingDeletedNotification(b);
    }

    // ════════════════════════════════════════════════════════════════════
    // PREGNANCY OUTCOME
    // ════════════════════════════════════════════════════════════════════

    @Transactional
    public LivestockBreeding confirmPregnancy(UUID id, LocalDate dueDate, LocalDate confirmedDate) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
        if (!LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
            throw new IllegalStateException("Already resolved (status: " + b.getStatus() + ").");

        LocalDate cd = confirmedDate != null ? confirmedDate : LocalDate.now();
        if (cd.isAfter(LocalDate.now())) throw new IllegalStateException("Confirm date cannot be in the future.");
        if (b.getBreedingDate() != null && cd.isBefore(b.getBreedingDate()))
            throw new IllegalStateException("Confirm date before breeding date.");
        if (dueDate != null && dueDate.isBefore(cd))
            throw new IllegalStateException("Due date before confirmation date.");

        b.setStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        b.setPregnancyStatus("CONFIRMED");
        b.setPregnancyConfirmedDate(cd);
        if (dueDate != null) b.setExpectedDueDate(dueDate);
        LivestockBreeding saved = breedingRepository.save(b);

        confirmPregnancyOnAnimal(saved);
        emailService.sendPregnancyConfirmedNotification(saved);
        return saved;
    }

    @Transactional
    public LivestockBreeding confirmPregnancy(UUID id, LocalDate dueDate) {
        return confirmPregnancy(id, dueDate, null);
    }

    @Transactional
    public LivestockBreeding markFailed(UUID id, String reason, LocalDate checkDate) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
        if (!LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
            throw new IllegalStateException("Already resolved.");

        LocalDate cd = checkDate != null ? checkDate : LocalDate.now();
        if (cd.isAfter(LocalDate.now())) throw new IllegalStateException("Check date cannot be in the future.");
        if (b.getBreedingDate() != null && cd.isBefore(b.getBreedingDate()))
            throw new IllegalStateException("Check date before breeding date.");

        b.setStatus(LivestockBreeding.STATUS_FAILED);
        b.setPregnancyStatus("FAILED");
        b.setFailureReason(reason);
        String note = "[Marked failed] Checked on: " + cd
                + (reason != null && !reason.isBlank() ? " — reason: " + reason : "");
        b.setNotes(b.getNotes() == null || b.getNotes().isBlank() ? note : b.getNotes() + "\n" + note);
        LivestockBreeding saved = breedingRepository.save(b);

        resetPregnancyOnAnimal(saved.getLivestock());
        emailService.sendBreedingFailedNotification(saved);
        return saved;
    }

    @Transactional
    public LivestockBreeding markFailed(UUID id, String reason) {
        return markFailed(id, reason, null);
    }

    // ════════════════════════════════════════════════════════════════════
    // AUTO-COMPLETE ON BIRTH
    // ════════════════════════════════════════════════════════════════════

    @Transactional
    public void completeBreedingOnBirth(UUID femaleId, LocalDate birthDate) {
        breedingRepository.findMostRecentActiveBreeding(femaleId).ifPresent(b -> {
            b.setStatus(LivestockBreeding.STATUS_COMPLETED);
            String note = "[Auto-completed] Birth recorded on: " + birthDate;
            b.setNotes(b.getNotes() == null || b.getNotes().isBlank() ? note : b.getNotes() + "\n" + note);
            breedingRepository.save(b);
        });
        livestockRepository.findById(femaleId).ifPresent(animal -> {
            animal.setIsPregnant(false);
            animal.setReproductiveStatus(Livestock.REPRO_OPEN);
            animal.setStatus(Livestock.STATUS_ACTIVE);
            animal.setLastBirthDate(birthDate);
            animal.setExpectedDueDate(null);
            animal.setBreedingReadyNotifiedAt(null);
            livestockRepository.save(animal);
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // POSTPARTUM / BIRTH HELPERS (used by getBreedingContext)
    // ════════════════════════════════════════════════════════════════════

    public LocalDate getLastBirthDate(Livestock female) {
        if (female == null || female.getId() == null) return null;
        return birthRepository
                .findFirstByLivestockIdOrderByBirthDateDesc(female.getId())
                .map(LivestockBirth::getBirthDate)
                .orElse(female.getLastBirthDate());
    }

    public Optional<LocalDate> getPostpartumReadyDate(Livestock female) {
        if (female == null) return Optional.empty();
        LocalDate lastBirth = getLastBirthDate(female);
        if (lastBirth == null) return Optional.empty();

        Integer recoveryDays = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getPostpartumRecoveryDays() : null;
        if (recoveryDays == null) recoveryDays = FALLBACK_POSTPARTUM_RECOVERY_DAYS;

        return Optional.of(lastBirth.plusDays(recoveryDays));
    }

    public boolean isInPostpartumRecovery(Livestock female) {
        return getPostpartumReadyDate(female)
                .map(readyDate -> LocalDate.now().isBefore(readyDate))
                .orElse(false);
    }

    public boolean isHealthyForBreeding(Livestock animal) {
        return animal != null && HEALTH_HEALTHY.equalsIgnoreCase(animal.getHealthStatus());
    }

    private String buildReadyReason(Livestock female, Integer minBreedingAge) {
        if (minBreedingAge == null) {
            return "No minimum breeding age configured for her category — assumed ready.";
        }
        return "Meets the minimum breeding age (" + minBreedingAge + " months) for her category, "
                + "is healthy, not pregnant, and not in postpartum recovery.";
    }

    // ════════════════════════════════════════════════════════════════════
    // ELIGIBILITY CHECKS
    // ════════════════════════════════════════════════════════════════════

    public boolean isEligibleForBreeding(Livestock f) {
        return getIneligibilityReason(f) == null;
    }

    public String getIneligibilityReason(Livestock female) {
        if (female == null) return "Selected animal could not be found.";
        if (!"FEMALE".equalsIgnoreCase(female.getGender())) return "Not female.";
        if (!Livestock.STATUS_ACTIVE.equals(female.getStatus())) return "Status is " + female.getStatus();
        String h = getHealthIneligibilityReason(female);
        if (h != null) return h;
        String repro = female.getReproductiveStatus();
        if (repro != null) {
            String n = repro.toUpperCase();
            if (Livestock.REPRO_BRED.equals(n) || Livestock.REPRO_PREGNANT.equals(n))
                return "Reproductive status is " + female.reproductiveStatusLabel();
        }
        Long age = female.getAgeInMonths();
        if (age == null) return "Birth date not recorded.";
        Integer min = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMinBreedingAgeMonths() : null;
        if (min != null && age < min) return "Below min breeding age (" + min + "mo).";
        Integer max = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMaxBreedingAgeMonths() : null;
        if (max != null && age > max) return "Above max breeding age (" + max + "mo).";
        return null;
    }

    public String getMaleIneligibilityReason(Livestock male) {
        if (male == null) return null;
        if (!"MALE".equalsIgnoreCase(male.getGender())) return "Not male.";
        if (!Livestock.STATUS_ACTIVE.equals(male.getStatus())) return "Status is " + male.getStatus();
        String h = getHealthIneligibilityReason(male);
        if (h != null) return h;
        Long age = male.getAgeInMonths();
        if (age == null) return "Birth date not recorded.";
        if (male.getLivestockCategory() != null) {
            Integer min = male.getLivestockCategory().getMinBreedingAgeMonths();
            Integer max = male.getLivestockCategory().getMaxBreedingAgeMonths();
            if (min != null && age < min) return "Below min breeding age (" + min + "mo).";
            if (max != null && age > max) return "Above max breeding age (" + max + "mo).";
        }
        return null;
    }

    public String getMaleIneligibilityReason(Livestock male, Livestock female) {
        String base = getMaleIneligibilityReason(male);
        if (base != null) return base;
        if (male == null || female == null) return null;
        UUID fCat = female.getLivestockCategory() != null ? female.getLivestockCategory().getId() : null;
        if (fCat == null) return null;
        UUID mCat = male.getLivestockCategory() != null ? male.getLivestockCategory().getId() : null;
        if (mCat == null) return "Male has no category.";
        if (!mCat.equals(fCat)) return "Male category differs from female.";
        return null;
    }

    public String getBreedingMethodCompatibilityReason(String method, Livestock male) {
        if (method == null || method.isBlank()) return "Please select a breeding method.";
        boolean natural = FORM_METHOD_NATURAL.equals(method);
        if (natural && male == null) return "Natural breeding requires a male.";
        if (!natural && male != null) return "Male should not be selected for " + method + ".";
        return null;
    }

    private String getHealthIneligibilityReason(Livestock a) {
        String h = a.getHealthStatus();
        if (h == null) return "Health status not recorded.";
        if (HEALTH_HEALTHY.equalsIgnoreCase(h)) return null;
        if (HEALTH_SICK.equalsIgnoreCase(h)) return "Animal is SICK.";
        if (HEALTH_UNDER_TREATMENT.equalsIgnoreCase(h)) return "Animal is UNDER_TREATMENT.";
        return "Health status is " + h;
    }

    public List<Livestock> getEligibleMales() {
        return livestockRepository.findAll().stream()
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus())
                        && !Livestock.STATUS_SOLD.equals(l.getStatus())
                        && !Livestock.STATUS_TRANSFERRED.equals(l.getStatus()))
                .filter(l -> getMaleIneligibilityReason(l) == null)
                .collect(Collectors.toList());
    }

    public List<Livestock> getEligibleMalesForFemale(UUID femaleId) {
        if (femaleId == null) return getEligibleMales();
        Livestock female = livestockRepository.findById(femaleId).orElse(null);
        UUID fCat = female != null && female.getLivestockCategory() != null
                ? female.getLivestockCategory().getId() : null;
        return getEligibleMales().stream()
                .filter(m -> fCat == null || (m.getLivestockCategory() != null
                        && fCat.equals(m.getLivestockCategory().getId())))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getBreedingHistoryAdvisory(UUID livestockId) {
        Map<String, Object> advisory = new LinkedHashMap<>();
        if (livestockId == null) {
            advisory.put("level", "none");
            advisory.put("message", null);
            return advisory;
        }
        List<LivestockBreeding> history = breedingRepository
                .findByLivestockIdOrderByFirstBreedingDateDesc(livestockId);
        int total = history.size();
        long completed = history.stream().filter(LivestockBreeding::isSuccessful).count();
        long failed = history.stream().filter(LivestockBreeding::isFailed).count();
        advisory.put("totalAttempts", total);
        advisory.put("completedCount", completed);
        advisory.put("failedCount", failed);
        if (total == 0) {
            advisory.put("level", "info");
            advisory.put("message", "First breeding attempt — no prior history.");
            return advisory;
        }
        int consecutive = 0;
        for (LivestockBreeding b : history) {
            if (b.isFailed()) consecutive++; else break;
        }
        if (consecutive >= CONSECUTIVE_FAILURES_FLAG_THRESHOLD) {
            advisory.put("level", "warning");
            advisory.put("message", consecutive + " consecutive failed attempts — vet check advised.");
        } else if (failed > 0) {
            advisory.put("level", "info");
            advisory.put("message", failed + " of " + total + " past attempts failed.");
        } else {
            advisory.put("level", "good");
            advisory.put("message", "Clean breeding history.");
        }
        return advisory;
    }

    // ════════════════════════════════════════════════════════════════════
    // CALCULATED FIELDS
    // ════════════════════════════════════════════════════════════════════

    public Map<String, Object> calculatePreview(UUID livestockId, String method, LocalDate breedingDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (livestockId == null || breedingDate == null) {
            result.put("error", "Select animal and date.");
            return result;
        }
        Livestock female = livestockRepository.findById(livestockId).orElse(null);
        if (female == null) { result.put("error", "Animal not found."); return result; }
        if (breedingDate.isAfter(LocalDate.now())) {
            result.put("error", "Breeding date cannot be in the future.");
            return result;
        }
        String reason = getIneligibilityReason(female);
        if (reason != null) {
            result.put("error", female.getTagNumber() + " not eligible: " + reason);
            return result;
        }

        LivestockBreeding prev = breedingRepository
                .findFirstByLivestockIdOrderByBreedingDateDesc(livestockId).orElse(null);
        LocalDate firstDate; int attempt; boolean retry = false;
        if (prev == null) { firstDate = breedingDate; attempt = 1; }
        else {
            int prevNo = prev.getBreedingAttempt() != null ? prev.getBreedingAttempt() : 1;
            String ps = prev.getStatus();
            boolean failed = LivestockBreeding.STATUS_FAILED.equals(ps)
                    || LivestockBreeding.STATUS_ABORTED.equals(ps);
            if (failed) {
                firstDate = prev.getFirstBreedingDate() != null
                        ? prev.getFirstBreedingDate() : prev.getBreedingDate();
                attempt = prevNo + 1;
                retry = true;
            } else { firstDate = breedingDate; attempt = prevNo + 1; }
        }

        Integer gestation = null;
        if (female.getLivestockCategory() != null) {
            gestation = female.getLivestockCategory().getGestationPeriodDays();
            if (gestation == null && female.getLivestockCategory().getGestationPeriodMonths() != null)
                gestation = (int) Math.round(female.getLivestockCategory().getGestationPeriodMonths()
                        * AVG_DAYS_PER_MONTH);
        }
        LocalDate due = gestation != null ? breedingDate.plusDays(gestation) : null;
        LocalDate check = gestation != null
                ? breedingDate.plusDays(Math.max(MIN_PREGNANCY_CHECK_DAYS,
                Math.round(gestation * PREGNANCY_CHECK_FRACTION_OF_GESTATION)))
                : breedingDate.plusDays(FALLBACK_PREGNANCY_CHECK_DAYS);

        result.put("firstBreedingDate", firstDate);
        result.put("lastBreedingDate", breedingDate);
        result.put("breedingAttempt", attempt);
        result.put("isRetryAttempt", retry);
        result.put("conceptionDate", breedingDate);
        result.put("expectedDueDate", due);
        result.put("expectedPregnancyCheckDate", check);
        result.put("inseminationMethod", deriveInseminationMethod(method));
        result.put("status", LivestockBreeding.STATUS_PENDING);
        result.put("pregnancyStatus", "NOT_PREGNANT");
        result.put("gestationDaysUsed", gestation);
        if (female.getLivestockCategory() != null)
            result.put("categoryName", female.getLivestockCategory().getName());
        return result;
    }

    @SuppressWarnings("unchecked")
    public void applyCalculatedFields(LivestockBreeding b) {
        UUID id = b.getLivestock() != null ? b.getLivestock().getId() : b.getLivestockIdValue();
        Map<String, Object> calc = calculatePreview(id, b.getBreedingMethod(), b.getBreedingDate());
        if (calc.containsKey("error")) return;
        b.setFirstBreedingDate((LocalDate) calc.get("firstBreedingDate"));
        b.setLastBreedingDate((LocalDate) calc.get("lastBreedingDate"));
        b.setBreedingAttempt((Integer) calc.get("breedingAttempt"));
        b.setConceptionDate((LocalDate) calc.get("conceptionDate"));
        b.setExpectedDueDate((LocalDate) calc.get("expectedDueDate"));
        b.setExpectedPregnancyCheckDate((LocalDate) calc.get("expectedPregnancyCheckDate"));
        b.setInseminationMethod((String) calc.get("inseminationMethod"));
        b.setStatus((String) calc.get("status"));
        b.setPregnancyStatus((String) calc.get("pregnancyStatus"));
    }

    private String deriveInseminationMethod(String method) {
        if (method == null || method.isBlank()) return null;
        return switch (method) {
            case "NATURAL"                 -> LivestockBreeding.METHOD_NATURAL;
            case "ARTIFICIAL_INSEMINATION" -> LivestockBreeding.METHOD_ARTIFICIAL;
            case "EMBRYO_TRANSFER"         -> LivestockBreeding.METHOD_EMBRYO_TRANSFER;
            case "PURCHASE_PREGNANT"       -> LivestockBreeding.METHOD_PURCHASE_PREGNANT;
            default -> null;
        };
    }

    private void confirmPregnancyOnAnimal(LivestockBreeding saved) {
        if (saved.getLivestock() == null) return;
        Livestock a = saved.getLivestock();
        a.setReproductiveStatus(Livestock.REPRO_PREGNANT);
        a.setIsPregnant(true);
        a.setExpectedDueDate(saved.getExpectedDueDate());
        livestockRepository.save(a);
    }

    private void resetPregnancyOnAnimal(Livestock a) {
        if (a == null) return;
        a.setIsPregnant(false);
        a.setReproductiveStatus(Livestock.REPRO_OPEN);
        a.setExpectedDueDate(null);
        a.setBreedingReadyNotifiedAt(null);
        livestockRepository.save(a);
    }
}