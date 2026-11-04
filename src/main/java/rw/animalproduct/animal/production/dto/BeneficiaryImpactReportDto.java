package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class BeneficiaryImpactReportDto {

    // ── Period ──────────────────────────────────────────────────────────
    private LocalDate fromDate;
    private LocalDate toDate;
    private String periodLabel;

    // ── 1. Program reach ────────────────────────────────────────────────
    private int totalBeneficiaries;
    private int activeBeneficiaries;
    private int inactiveBeneficiaries;
    private int femaleHeadedHouseholds;
    private int maleHeadedHouseholds;
    private int householdsWithNoAnimals;

    // ── 2. Asset transfer ───────────────────────────────────────────────
    private int totalAnimalsDistributed;
    private BigDecimal totalOriginalValue = BigDecimal.ZERO;
    private BigDecimal totalCurrentValue = BigDecimal.ZERO;

    // ── 3. Asset retention ──────────────────────────────────────────────
    private int totalCurrentAnimals;
    private int totalSoldAnimals;
    private int totalDeadAnimals;
    private int totalBornAnimals;

    private double survivalRatePercent;
    private double mortalityRatePercent;
    private double birthRatePercent;
    private double offtakeRatePercent;

    // ── 4. Asset growth ─────────────────────────────────────────────────
    private double assetGrowthRatePercent;
    private int householdsDoubled;

    // ── 5. Income ───────────────────────────────────────────────────────
    private int totalSales;
    private BigDecimal totalSaleRevenue = BigDecimal.ZERO;
    private BigDecimal averageRevenuePerBeneficiary = BigDecimal.ZERO;
    private BigDecimal averageSalePrice = BigDecimal.ZERO;

    // ── 6. Contract compliance ──────────────────────────────────────────
    private int withContract;
    private int withoutContract;
    private double contractCompletionRatePercent;

    // ── 7. Distribution breakdowns ──────────────────────────────────────
    private List<Breakdown> byLocation = new ArrayList<>();
    // ✅ Representative breakdown removed — not used
    private List<Breakdown> byGender = new ArrayList<>();
    private List<Breakdown> byMaritalStatus = new ArrayList<>();

    // ── 8. Per-beneficiary rows ─────────────────────────────────────────
    private List<BeneficiaryRow> rows = new ArrayList<>();

    // ── 9. Exceptions worklist ──────────────────────────────────────────
    private List<ExceptionRow> exceptions = new ArrayList<>();

    public boolean hasExceptions() {
        return exceptions != null && !exceptions.isEmpty();
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ═════════════════════════════════════════════════════════════════════
    //  Nested DTOs
    // ═════════════════════════════════════════════════════════════════════

    @Data
    public static class Breakdown {
        private String label;
        private int count;
        private double percent;

        public Breakdown() {}
        public Breakdown(String label, int count, double percent) {
            this.label = label;
            this.count = count;
            this.percent = percent;
        }
    }

    @Data
    public static class BeneficiaryRow {
        private String beneficiaryId;
        private String fullName;
        private String phone;                 // ✅ NEW — beneficiary phone
        private String locationName;          // leaf name (kept for compatibility)
        private String fullLocation;          // ✅ NEW — Province › District › Sector › Cell › Village
        private String status;                // ✅ Representative field removed

        private int animalsReceived;
        private BigDecimal originalValue = BigDecimal.ZERO;
        private int animalsNow;
        private BigDecimal currentValue = BigDecimal.ZERO;
        private int births;
        private int sold;
        private int deaths;
        private BigDecimal saleRevenue = BigDecimal.ZERO;

        private double assetGrowthRatePercent;
        private double survivalRatePercent;

        public boolean hasDoubled() {
            return assetGrowthRatePercent >= 100.0;
        }
    }

    @Data
    public static class ExceptionRow {
        private String severity;
        private String category;
        private String beneficiaryId;
        private String beneficiaryName;
        private String phone;                 // ✅ NEW
        private String locationName;          // ✅ now full location
        private String message;

        public ExceptionRow() {}
        public ExceptionRow(String severity, String category,
                            String beneficiaryId, String beneficiaryName,
                            String phone, String locationName, String message) {
            this.severity = severity;
            this.category = category;
            this.beneficiaryId = beneficiaryId;
            this.beneficiaryName = beneficiaryName;
            this.phone = phone;
            this.locationName = locationName;
            this.message = message;
        }
    }
}