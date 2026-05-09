package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.LivestockWithAgeDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes lifecycle stage from real DB columns:
 *
 *   livestock_births.birth_date – the actual birth date (from livestock_births table)
 *   livestock.date_received     – fallback for animals without birth records
 *   livestock.is_pregnant       – boolean flag
 *   livestock.status            – ACTIVE, SICK, PREGNANT, SOLD, DEAD
 *   livestock.gender            – MALE / FEMALE
 *
 * Lifecycle stages (calculated on the fly):
 *   NEWBORN      –   0–30 days
 *   YOUNG        –  31–364 days
 *   PRE_BREEDING – 180–364 days
 *   READY_TO_BREED – 365+ days, not pregnant, not dead/sold
 *   PREGNANT     – is_pregnant = true OR status = 'PREGNANT'
 *   MATURE       – 365+ days
 *   BREEDING_MALE – MALE, 365+ days, active
 */
@Service
public class LivestockWithAgeService {

    private static final int NEWBORN_MAX_DAYS      = 30;
    private static final int YOUNG_MAX_DAYS        = 364;
    private static final int PRE_BREEDING_MIN_DAYS = 180;
    private static final int MATURE_MIN_DAYS       = 365;

    private final LivestockRepository livestockRepository;
    private final LivestockBirthRepository birthRepository;

    public LivestockWithAgeService(LivestockRepository livestockRepository,
                                   LivestockBirthRepository birthRepository) {
        this.livestockRepository = livestockRepository;
        this.birthRepository = birthRepository;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    public List<LivestockWithAgeDTO> getAll() {
        return livestockRepository.findAll().stream()
                .filter(ls -> !Boolean.TRUE.equals(ls.getIsDeleted()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getYoung() {
        return getAll().stream()
                .filter(dto -> {
                    int days = dto.getAgeInDays() != null ? dto.getAgeInDays() : 0;
                    return days > NEWBORN_MAX_DAYS && days <= YOUNG_MAX_DAYS;
                })
                .filter(dto -> isActive(dto.getStatus()))
                .sorted(Comparator.comparingInt(d -> d.getAgeInDays() != null ? d.getAgeInDays() : 0))
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getMature() {
        return getAll().stream()
                .filter(dto -> {
                    int days = dto.getAgeInDays() != null ? dto.getAgeInDays() : 0;
                    return days >= MATURE_MIN_DAYS;
                })
                .filter(dto -> isActive(dto.getStatus()))
                .sorted(Comparator.comparingInt(d -> d.getAgeInDays() != null ? d.getAgeInDays() : 0))
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getReadyToBreed() {
        return getAll().stream()
                .filter(dto -> "FEMALE".equalsIgnoreCase(dto.getGender()))
                .filter(dto -> {
                    int days = dto.getAgeInDays() != null ? dto.getAgeInDays() : 0;
                    return days >= MATURE_MIN_DAYS;
                })
                .filter(dto -> !Boolean.TRUE.equals(dto.getIsPregnant()))
                .filter(dto -> isActive(dto.getStatus()))
                .sorted(Comparator.comparingInt(d -> d.getAgeInDays() != null ? d.getAgeInDays() : 0))
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getBreedingMales() {
        return getAll().stream()
                .filter(dto -> "MALE".equalsIgnoreCase(dto.getGender()))
                .filter(dto -> {
                    int days = dto.getAgeInDays() != null ? dto.getAgeInDays() : 0;
                    return days >= MATURE_MIN_DAYS;
                })
                .filter(dto -> isActive(dto.getStatus()))
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getPregnant() {
        return getAll().stream()
                .filter(dto -> Boolean.TRUE.equals(dto.getIsPregnant())
                        || "PREGNANT".equalsIgnoreCase(dto.getStatus()))
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getNewborns() {
        return getAll().stream()
                .filter(dto -> {
                    int days = dto.getAgeInDays() != null ? dto.getAgeInDays() : 0;
                    return days <= NEWBORN_MAX_DAYS;
                })
                .filter(dto -> isActive(dto.getStatus()))
                .sorted(Comparator.comparingInt(d -> d.getAgeInDays() != null ? d.getAgeInDays() : 0))
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getByGender(String gender) {
        return getAll().stream()
                .filter(dto -> gender != null && gender.equalsIgnoreCase(dto.getGender()))
                .collect(Collectors.toList());
    }

    public List<LivestockWithAgeDTO> getByLifecycleStage(String stage) {
        return getAll().stream()
                .filter(dto -> stage != null && stage.equalsIgnoreCase(dto.getLifecycleStage()))
                .collect(Collectors.toList());
    }

    public Optional<LivestockWithAgeDTO> getById(UUID id) {
        return livestockRepository.findById(id)
                .filter(ls -> !Boolean.TRUE.equals(ls.getIsDeleted()))
                .map(this::toDTO);
    }

    public Map<String, Long> getLifecycleSummary() {
        return getAll().stream()
                .collect(Collectors.groupingBy(
                        d -> d.getLifecycleStage() != null ? d.getLifecycleStage() : "UNKNOWN",
                        Collectors.counting()));
    }

    public Map<String, Long> getStatusSummary() {
        return getAll().stream()
                .collect(Collectors.groupingBy(
                        d -> d.getStatus() != null ? d.getStatus() : "UNKNOWN",
                        Collectors.counting()));
    }

    public Map<String, Long> getGenderSummary() {
        return getAll().stream()
                .collect(Collectors.groupingBy(
                        d -> d.getGender() != null ? d.getGender() : "UNKNOWN",
                        Collectors.counting()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CORE: entity → DTO conversion with automatic age & stage calculation
    // ══════════════════════════════════════════════════════════════════════════

    private LivestockWithAgeDTO toDTO(Livestock ls) {
        LocalDate today = LocalDate.now();

        // ── 1. Get birth date from livestock_births table ──
        LocalDate birthDate = getBirthDateFromBirthRecord(ls.getId());

        // ── 2. Reference date: birth_date (from livestock_births) OR date_received ──
        LocalDate ref = birthDate != null ? birthDate
                : ls.getBirthDate() != null ? ls.getBirthDate()
                : ls.getDateReceived();

        // ── 3. Age in days and months ─────────────────────────────────────────
        int ageInDays   = 0;
        int ageInMonths = 0;
        if (ref != null) {
            ageInDays   = (int) ChronoUnit.DAYS.between(ref, today);
            ageInMonths = ageInDays / 30;
        }

        // ── 4. Lifecycle stage ────────────────────────────────────────────────
        String stage = computeStage(ls, ageInDays);

        // ── 5. Category name ──────────────────────────────────────────────────
        String categoryName = (ls.getLivestockCategory() != null)
                ? ls.getLivestockCategory().getName()
                : null;

        // ── 6. Days remaining until breeding age ──────────────────────────────
        int daysToBreedingAge = Math.max(0, MATURE_MIN_DAYS - ageInDays);

        // ── 7. Build DTO ──────────────────────────────────────────────────────
        LivestockWithAgeDTO dto = new LivestockWithAgeDTO();
        dto.setId(ls.getId());
        dto.setTagNumber(ls.getTagNumber());
        dto.setGender(ls.getGender());
        dto.setStatus(ls.getStatus());
        dto.setIsPregnant(ls.getIsPregnant());
        dto.setPregnancyStatus(ls.getPregnancyStatus());
        dto.setOffspringCount(ls.getOffspringCount());
        dto.setLastBreedingDate(ls.getLastBreedingDate());
        dto.setFirstBreedingDate(ls.getFirstBreedingDate());
        dto.setConceptionDate(ls.getConceptionDate());
        dto.setExpectedDueDate(ls.getExpectedDueDate());
        dto.setLastBirthDate(ls.getLastBirthDate());
        dto.setDateReceived(ls.getDateReceived());
        dto.setBirthDate(birthDate);  // From livestock_births table
        dto.setCurrentValue(ls.getCurrentValue());
        dto.setCategoryName(categoryName);
        dto.setAgeInDays(ageInDays);
        dto.setAgeInMonths(ageInMonths);
        dto.setLifecycleStage(stage);
        dto.setDaysToBreedingAge(daysToBreedingAge);

        return dto;
    }

    /**
     * Get the actual birth date from livestock_births table.
     * This queries through livestock_offspring to find the birth event for this animal.
     *
     * Returns:
     * - birth_date from livestock_births if the animal has a birth record
     * - null if no birth record exists (means animal was not tracked from birth)
     */
    private LocalDate getBirthDateFromBirthRecord(UUID animalId) {
        Optional<LivestockBirth> birthRecord = birthRepository.findByChildAnimalId(animalId);
        return birthRecord.map(LivestockBirth::getBirthDate).orElse(null);
    }

    private String computeStage(Livestock ls, int ageInDays) {
        // Pregnant always wins
        if (Boolean.TRUE.equals(ls.getIsPregnant())
                || "PREGNANT".equalsIgnoreCase(ls.getStatus())) {
            return "PREGNANT";
        }

        // Dead / sold
        if ("DEAD".equalsIgnoreCase(ls.getStatus()))   return "DEAD";
        if ("SOLD".equalsIgnoreCase(ls.getStatus()))   return "SOLD";

        // Age-based stages
        if (ageInDays <= 0)                return "UNKNOWN";
        if (ageInDays <= NEWBORN_MAX_DAYS) return "NEWBORN";

        if (ageInDays >= MATURE_MIN_DAYS) {
            if ("MALE".equalsIgnoreCase(ls.getGender()))   return "BREEDING_MALE";
            if ("FEMALE".equalsIgnoreCase(ls.getGender())) return "READY_TO_BREED";
            return "MATURE";
        }

        // Everything between 31 and 364 days is YOUNG (removed PRE_BREEDING)
        return "YOUNG";
    }

    private boolean isActive(String status) {
        if (status == null) return false;
        return !"DEAD".equalsIgnoreCase(status) && !"SOLD".equalsIgnoreCase(status);
    }
}