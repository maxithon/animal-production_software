package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FAO-standard append-only valuation record for a livestock animal.
 *
 * Design intent:
 *  - An animal's value is a TIME SERIES, not a single mutable field.
 *  - Every valuation event (initial registration, market revaluation,
 *    growth adjustment, appraisal, sale price, manual correction) creates
 *    a NEW row here — nothing is ever overwritten.
 *  - Livestock.currentValue is a denormalized cache of the most recent
 *    row in this table, refreshed exclusively by LivestockValuationService.
 */
@Entity
@Table(name = "livestock_valuation_history")
public class LivestockValuation {

    // ── Valuation method constants ────────────────────────────────────────────
    public static final String METHOD_INITIAL            = "INITIAL";
    public static final String METHOD_MARKET_REVALUATION = "MARKET_REVALUATION";
    public static final String METHOD_GROWTH_ADJUSTMENT  = "GROWTH_ADJUSTMENT";
    public static final String METHOD_APPRAISAL          = "APPRAISAL";
    public static final String METHOD_SALE_PRICE         = "SALE_PRICE";
    public static final String METHOD_MANUAL_ADJUSTMENT  = "MANUAL_ADJUSTMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id", nullable = false)
    private Livestock livestock;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "value", precision = 12, scale = 2, nullable = false)
    private BigDecimal value;

    @Column(name = "valuation_method", length = 30, nullable = false)
    private String valuationMethod;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (valuationDate == null) valuationDate = LocalDate.now();
    }

    public LivestockValuation() {}

    public LivestockValuation(Livestock livestock, LocalDate valuationDate, BigDecimal value,
                              String valuationMethod, String notes, String recordedBy) {
        this.livestock = livestock;
        this.valuationDate = valuationDate;
        this.value = value;
        this.valuationMethod = valuationMethod;
        this.notes = notes;
        this.recordedBy = recordedBy;
    }

    // ── Display helper ─────────────────────────────────────────────────────────
    public String methodLabel() {
        if (valuationMethod == null) return "Unknown";
        return switch (valuationMethod) {
            case METHOD_INITIAL -> "Initial Valuation";
            case METHOD_MARKET_REVALUATION -> "Market Revaluation";
            case METHOD_GROWTH_ADJUSTMENT -> "Growth Adjustment";
            case METHOD_APPRAISAL -> "Appraisal";
            case METHOD_SALE_PRICE -> "Sale Price";
            case METHOD_MANUAL_ADJUSTMENT -> "Manual Adjustment";
            default -> valuationMethod;
        };
    }

    // ── Getters and Setters ───────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public LocalDate getValuationDate() { return valuationDate; }
    public void setValuationDate(LocalDate valuationDate) { this.valuationDate = valuationDate; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getValuationMethod() { return valuationMethod; }
    public void setValuationMethod(String valuationMethod) { this.valuationMethod = valuationMethod; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
