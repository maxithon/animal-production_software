package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.LivestockWithAgeDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes lifecycle stage from real DB columns:
 *
 *   livestock_births.birth_date – the actual birth date (from livestock_births table)
 *   livestock.date_received     – fallback for animals without birth records
 *   livestock.reproductive_status – PREGNANT, OPEN, READY_TO_BREED, etc.
 *   livestock.status            – ACTIVE, SOLD, DEAD, TRANSFERRED
 *   livestock.gender            – MALE / FEMALE
 *
 * Lifecycle stages (calculated on the fly):
 *   NEWBORN          – 0–30 days
 *   YOUNG            – 31–364 days
 *   READY_TO_BREED   – 365+ days, female, not pregnant, active
 *   BREEDING_MALE    – 365+ days, male, active
 *   PREGNANT         – reproductive_status = 'PREGNANT'
 *   MATURE           – 365+ days, not in other categories
 *   DEAD             – status = 'DEAD'
 *   SOLD             – status = 'SOLD'
 */
@Service
public class LivestockWithAgeService {

    private static final int NEWBORN_MAX_DAYS      = 30;
    private static final int YOUNG_MAX_DAYS        = 364;
    private static final int MATURE_MIN_DAYS       = 365;

    private final LivestockRepository livestockRepository;
    private final LivestockBirthRepository birthRepository;
    // NEW — needed to resolve conceptionDate now that Livestock no longer
    // holds that field. conception_date lives only on livestock_breeding
    // (see Livestock.java / LivestockBreeding.java). We read it from the
    // animal's active pregnancy record instead.
    private final LivestockBreedingRepository breedingRepository;

    public LivestockWithAgeService(LivestockRepository livestockRepository,
                                   LivestockBirthRepository birthRepository,
                                   LivestockBreedingRepository breedingRepository) {
        this.livestockRepository = livestockRepository;
        this.birthRepository = birthRepository;
        this.breedingRepository = breedingRepository;
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
                .filter(dto -> !"FEMALE".equalsIgnoreCase(dto.getGender())
                        || !Boolean.TRUE.equals(dto.getIsPregnant()))
                .filter(dto -> !"READY_TO_BREED".equals(dto.getLifecycleStage()))
                .filter(dto -> !"BREEDING_MALE".equals(dto.getLifecycleStage()))
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
                        || Livestock.REPRO_PREGNANT.equalsIgnoreCase(dto.getReproductiveStatus()))
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
        int ageInDays = 0;
        int ageInMonths = 0;
        if (ref != null) {
            ageInDays = (int) ChronoUnit.DAYS.between(ref, today);
            ageInMonths = ageInDays / 30;
        }

        // ── 4. Determine pregnancy status ─────────────────────────────────────
        // Check BOTH the transient isPregnant flag AND the reproductiveStatus column
        boolean isPregnant = Boolean.TRUE.equals(ls.getIsPregnant())
                || Livestock.REPRO_PREGNANT.equalsIgnoreCase(ls.getReproductiveStatus());

        // ── 5. Lifecycle stage ────────────────────────────────────────────────
        String stage = computeStage(ls, ageInDays, isPregnant);

        // ── 6. Category name ──────────────────────────────────────────────────
        String categoryName = (ls.getLivestockCategory() != null)
                ? ls.getLivestockCategory().getName()
                : null;

        // ── 7. Days remaining until breeding age ──────────────────────────────
        int daysToBreedingAge = Math.max(0, MATURE_MIN_DAYS - ageInDays);

        // ── 8. Conception date, resolved from LivestockBreeding ────────────────
        // Livestock no longer stores conceptionDate (removed — see
        // Livestock.java). If this animal is currently pregnant, pull the
        // conception date from her active LivestockBreeding record instead.
        LocalDate conceptionDate = isPregnant ? resolveActiveConceptionDate(ls.getId()) : null;

        // ── 9. Build DTO ──────────────────────────────────────────────────────
        LivestockWithAgeDTO dto = new LivestockWithAgeDTO();
        dto.setId(ls.getId());
        dto.setTagNumber(ls.getTagNumber());
        dto.setGender(ls.getGender());
        dto.setStatus(ls.getStatus());
        dto.setReproductiveStatus(ls.getReproductiveStatus());
        dto.setHealthStatus(ls.getHealthStatus());
        dto.setIsPregnant(isPregnant);
        dto.setPregnancyStatus(isPregnant ? "PREGNANT" : "NOT_PREGNANT");
        dto.setOffspringCount(ls.getOffspringCount());
        // REMOVED: lastBreedingDate and firstBreedingDate - they belong to LivestockSale or Breeding module
        // dto.setLastBreedingDate(ls.getLastBreedingDate());  // <- REMOVED - field doesn't exist in Livestock
        // dto.setFirstBreedingDate(ls.getFirstBreedingDate()); // <- REMOVED - field doesn't exist in Livestock
        dto.setConceptionDate(conceptionDate);
        dto.setExpectedDueDate(ls.getExpectedDueDate());
        dto.setLastBirthDate(ls.getLastBirthDate());
        dto.setDateReceived(ls.getDateReceived());
        dto.setBirthDate(birthDate);
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
     */
    private LocalDate getBirthDateFromBirthRecord(UUID animalId) {
        Optional<LivestockBirth> birthRecord = birthRepository.findByChildAnimalId(animalId);
        return birthRecord.map(LivestockBirth::getBirthDate).orElse(null);
    }

    /**
     * NEW — resolves the presumed conception date for a currently-pregnant
     * animal by looking at her active LivestockBreeding record(s), rather
     * than a field on Livestock (which no longer exists). If she has more
     * than one active pregnancy record (shouldn't normally happen), the one
     * with the earliest expected due date is used, matching the ordering
     * findActivePregnanciesByLivestockId() already returns.
     */
    private LocalDate resolveActiveConceptionDate(UUID livestockId) {
        List<LivestockBreeding> active = breedingRepository.findActivePregnanciesByLivestockId(livestockId);
        if (active == null || active.isEmpty()) return null;
        return active.get(0).getConceptionDate();
    }

    /**
     * Compute the lifecycle stage based on the animal's attributes.
     * Uses the reproductiveStatus column directly, not getPregnancyStatus().
     */
    private String computeStage(Livestock ls, int ageInDays, boolean isPregnant) {
        // Dead / sold always win
        if (Livestock.STATUS_DEAD.equalsIgnoreCase(ls.getStatus())) return "DEAD";
        if (Livestock.STATUS_SOLD.equalsIgnoreCase(ls.getStatus())) return "SOLD";

        // Check reproductiveStatus directly for pregnancy
        if (isPregnant || Livestock.REPRO_PREGNANT.equalsIgnoreCase(ls.getReproductiveStatus())) {
            return "PREGNANT";
        }

        // Age-based stages
        if (ageInDays <= 0) return "UNKNOWN";
        if (ageInDays <= NEWBORN_MAX_DAYS) return "NEWBORN";

        if (ageInDays >= MATURE_MIN_DAYS) {
            if ("MALE".equalsIgnoreCase(ls.getGender())) {
                return "BREEDING_MALE";
            }
            if ("FEMALE".equalsIgnoreCase(ls.getGender())) {
                return "READY_TO_BREED";
            }
            return "MATURE";
        }

        // Everything between 31 and 364 days is YOUNG
        return "YOUNG";
    }

    private boolean isActive(String status) {
        if (status == null) return false;
        return !Livestock.STATUS_DEAD.equalsIgnoreCase(status)
                && !Livestock.STATUS_SOLD.equalsIgnoreCase(status);
    }

    /**
     * Helper method to check if a livestock entity is pregnant based on its fields.
     * This avoids calling getPregnancyStatus() on the Livestock entity.
     */
    private boolean isLivestockPregnant(Livestock ls) {
        if (ls == null) return false;
        // Check the transient isPregnant flag (set by breeding service)
        if (Boolean.TRUE.equals(ls.getIsPregnant())) return true;
        // Check the reproductiveStatus column
        if (Livestock.REPRO_PREGNANT.equalsIgnoreCase(ls.getReproductiveStatus())) return true;
        return false;
    }


}
