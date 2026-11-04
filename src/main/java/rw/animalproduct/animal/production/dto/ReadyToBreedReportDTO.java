package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Getter
@Builder
public class ReadyToBreedReportDTO {
    // --- Identity ---
    private UUID livestockId;
    private String tagNumber;
    private String categoryName;
    private String gender; // MALE / FEMALE
    // --- Age ---
    private Integer ageMonths;
    private String ageDisplay;
    private Integer minBreedingAgeMonths;
    private Integer maxBreedingAgeMonths;
    // --- Breeding / birth history ---
    private long births;
    private LocalDate lastBreedingDate;
    private LocalDate lastBirthDate;
    private Long daysSinceLastBirth;
    /** "Recovered" / "Recovering (Xd left)" / "No births yet — N/A" */
    private String postpartumRecoveryStatus;
    // --- Readiness ---
    private String reproductiveStatus; // raw: READY_TO_BREED, OPEN, PREGNANT, NOT_APPLICABLE
    /** READY (4/4 reasons met) or ALMOST_READY (some pending). */
    private String readinessStatus;
    private String readinessLabel; // "🟢 Ready to Breed" / "🟡 Almost Ready"
    // Reason checklist ("Reason Ready to Breed")
    private boolean reasonReachedBreedingAge;
    private boolean reasonRecoveryCompleted;
    private boolean reasonNotPregnant;
    private boolean reasonNoBreedingRestriction;
    private boolean reasonNoActiveBreeding;
    private int reasonsMetCount;

    /**
     * The date the animal became (or will become) eligible under category
     * rules. Can be in the past (already eligible) or the future (still
     * recovering / not yet of breeding age).
     */
    private LocalDate readySince;

    /**
     * The actionable date: today if the animal is fully READY, otherwise the
     * same as readySince (the projected date it will become ready).
     */
    private LocalDate recommendedBreedingDate;

    /** Days since readySince, floored at 0. Only meaningful when READY. */
    private Long daysReadyForBreeding;

    /** Days until readySince, floored at 0. Only meaningful when ALMOST_READY. */
    private Long daysUntilReady;

    // --- Beneficiary & location context ---
    private String beneficiaryName;
    private String beneficiaryPhone;
    private String beneficiaryLocationName; // beneficiary's registered address (a_location.name)

    private String animalLocationProvince;
    private String animalLocationDistrict;
    private String animalLocationSector;
    private String animalLocationCell;
    private String animalLocationVillage;

    // --- Notification tracking ---
    private LocalDateTime notifiedAt;
    private boolean notified;
    private String notificationStatus;      // NOTIFIED / AWAITING / OVERDUE
    private String notificationStatusLabel; // "🟢 Notified" etc.


    /** All known location levels, broad → specific, skipping any missing ones. */
    private List<String> locationLevelsBroadToSpecific() {
        List<String> levels = new ArrayList<>(5);
        if (animalLocationProvince != null) levels.add(animalLocationProvince);
        if (animalLocationDistrict != null) levels.add(animalLocationDistrict);
        if (animalLocationSector != null) levels.add(animalLocationSector);
        if (animalLocationCell != null) levels.add(animalLocationCell);
        if (animalLocationVillage != null) levels.add(animalLocationVillage);
        return levels;
    }

    /** "Province > District > Sector > Cell > Village", skipping any missing levels. */
    public String getAnimalLocationBreadcrumb() {
        List<String> parts = locationLevelsBroadToSpecific();
        return parts.isEmpty() ? "—" : String.join(" › ", parts);
    }

    /**
     * The single most specific place known for this animal — Village if
     * present, else Cell, else Sector, else District, else Province. This is
     * the headline shown in the table; "—" if no location is set at all.
     */
    public String getAnimalLocationPrimary() {
        if (animalLocationVillage != null) return animalLocationVillage;
        if (animalLocationCell != null) return animalLocationCell;
        if (animalLocationSector != null) return animalLocationSector;
        if (animalLocationDistrict != null) return animalLocationDistrict;
        if (animalLocationProvince != null) return animalLocationProvince;
        return "—";
    }

    /**
     * The parent chain above the primary place (e.g. "Kigali › Gasabo › Kimironko"
     * when the primary is a Village) — the supporting detail line under the
     * headline. Empty string when there is nothing broader than the primary
     * (e.g. only a Province is known).
     */
    public String getAnimalLocationSecondary() {
        List<String> parts = locationLevelsBroadToSpecific();
        if (parts.size() <= 1) return "";
        parts.remove(parts.size() - 1); // drop the most-specific one — that's the primary line
        return String.join(" › ", parts);
    }

    /** Ordered checklist items with a human label, for rendering without duplicating logic in the view. */
    public List<ReasonItem> getReasonChecklist() {
        List<ReasonItem> items = new ArrayList<>(4);
        items.add(new ReasonItem("Minimum breeding age reached", reasonReachedBreedingAge));
        items.add(new ReasonItem("Postpartum recovery completed", reasonRecoveryCompleted));
        items.add(new ReasonItem("Not currently pregnant", reasonNotPregnant));
        items.add(new ReasonItem("No active breeding restriction", reasonNoBreedingRestriction));
        items.add(new ReasonItem("Not already in an active breeding process", reasonNoActiveBreeding));
        return items;
    }

    @Getter
    public static class ReasonItem {
        private final String label;
        private final boolean met;

        public ReasonItem(String label, boolean met) {
            this.label = label;
            this.met = met;
        }
    }
}
