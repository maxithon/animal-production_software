package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import rw.animalproduct.animal.production.entity.Location;

/**
 * Read-only view-model for the livestock-deaths list page.
 * Flattens: Livestock → Beneficiary → Location (village→cell→sector→district→province).
 */
public class LivestockDeathView {

    private final LivestockDeath death;

    public LivestockDeathView(LivestockDeath death) { this.death = death; }
    public LivestockDeath getDeath() { return death; }

    // ── Beneficiary ──────────────────────────────────────────
    public String getBeneficiaryFullName() {
        Beneficiary b = beneficiary();
        if (b == null) return "—";
        String first = b.getFirstName() == null ? "" : b.getFirstName().trim();
        String last  = b.getLastName()  == null ? "" : b.getLastName().trim();
        String full  = (first + " " + last).trim();
        return full.isEmpty() ? "—" : full;
    }

    public String getBeneficiaryPhone() {
        Beneficiary b = beneficiary();
        return (b != null && b.getPhone() != null && !b.getPhone().isBlank()) ? b.getPhone() : "—";
    }

    // ── Location hierarchy ───────────────────────────────────
    public String getVillage()  { return locName(0); }
    public String getCell()     { return locName(1); }
    public String getSector()   { return locName(2); }
    public String getDistrict() { return locName(3); }
    public String getProvince() { return locName(4); }

    public String getFullLocation() {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{ getVillage(), getCell(), getSector(), getDistrict(), getProvince() }) {
            if (part != null && !"—".equals(part) && !part.isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(part);
            }
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private Beneficiary beneficiary() {
        Livestock l = death.getLivestock();
        return l != null ? l.getBeneficiary() : null;
    }

    /** level: 0=village,1=cell,2=sector,3=district,4=province */
    private String locName(int level) {
        Beneficiary b = beneficiary();
        if (b == null || b.getLocation() == null) return "—";
        Location cur = b.getLocation();
        for (int i = 0; i < level && cur.getParent() != null; i++) cur = cur.getParent();
        return (cur.getName() != null && !cur.getName().isBlank()) ? cur.getName() : "—";
    }
}