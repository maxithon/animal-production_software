package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "livestock_valuation_history")
public class LivestockValuation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id", nullable = false)
    private Livestock livestock;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "value", nullable = false)
    private Double estimatedValue;

    @Column(name = "valuation_method", nullable = false, length = 30)
    private String valuationMethod;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "recorded_by", length = 100)
    private String valuedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ── Transient helpers — NOT persisted, just carry extra state in memory ──
    @Transient private LocalDateTime updatedAt;
    @Transient private Boolean       isDeleted = false;
    @Transient private LocalDateTime deletedAt;
    @Transient private String        deletedBy;

    // ── Convenience constructors ─────────────────────────────────────────────

    public LivestockValuation(Livestock livestock, LocalDate valuationDate,
                              Double estimatedValue, String valuationMethod,
                              String notes, String valuedBy) {
        this.livestock = livestock;
        this.valuationDate = valuationDate != null ? valuationDate : LocalDate.now();
        this.estimatedValue = estimatedValue;
        this.valuationMethod = valuationMethod;
        this.notes = notes;
        this.valuedBy = valuedBy;
        this.isDeleted = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public LivestockValuation(Livestock livestock, LocalDate valuationDate,
                              BigDecimal estimatedValue, String valuationMethod,
                              String notes, String valuedBy) {
        this(livestock, valuationDate,
                estimatedValue != null ? estimatedValue.doubleValue() : null,
                valuationMethod, notes, valuedBy);
    }

    // ── Lifecycle hooks ─────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (isDeleted == null) isDeleted = false;
        if (valuationDate == null) valuationDate = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Soft-delete helpers (in-memory only — not persisted) ────────────────

    public void softDelete(String deletedBy) {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(isDeleted);
    }

    // ── Template helpers ────────────────────────────────────────────────────

    public Double getValue() {
        return estimatedValue;
    }

    public String methodLabel() {
        if (valuationMethod == null) return "—";
        return switch (valuationMethod) {
            case "MARKET_REVALUATION" -> "Market Revaluation";
            case "GROWTH_ADJUSTMENT"  -> "Growth Adjustment";
            case "APPRAISAL"          -> "Appraisal";
            case "MANUAL_ADJUSTMENT"  -> "Manual Adjustment";
            default -> valuationMethod;
        };
    }

    public String getRecordedBy() {
        return valuedBy;
    }

    // ✅ REMOVED: the broken public Beneficiary getLivestock() { } method.
    //            Lombok @Data already generates getLivestock() returning Livestock.
}