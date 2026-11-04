package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@Table(name = "livestock_weight_history")
public class LivestockWeightHistory {

    public static final String METHOD_SCALE       = "SCALE";
    public static final String METHOD_ESTIMATED   = "ESTIMATED";
    public static final String METHOD_TAPE        = "TAPE";
    public static final String METHOD_HEART_GIRTH = "HEART_GIRTH";

    public static final String CONDITION_POOR     = "POOR";
    public static final String CONDITION_MODERATE = "MODERATE";
    public static final String CONDITION_GOOD     = "GOOD";
    public static final String CONDITION_FAT      = "FAT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id", nullable = false)
    private Livestock livestock;

    @Column(name = "weighed_on", nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate weighedOn;

    @Column(name = "weight_kg", nullable = false, precision = 7, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "weight_method", nullable = false, length = 30)
    private String weightMethod = METHOD_SCALE;

    @Column(name = "head_count", nullable = false)
    private Integer headCount = 1;

    @Column(name = "body_condition", length = 20)
    private String bodyCondition;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (isDeleted == null) isDeleted = false;
        if (weighedOn == null) weighedOn = LocalDate.now();
        if (headCount == null || headCount < 1) headCount = 1;
        if (weightMethod == null) weightMethod = METHOD_SCALE;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public LivestockWeightHistory(Livestock livestock, LocalDate weighedOn,
                                  BigDecimal weightKg, String method,
                                  Integer headCount, String recordedBy) {
        this.livestock = livestock;
        this.weighedOn = weighedOn != null ? weighedOn : LocalDate.now();
        this.weightKg = weightKg;
        this.weightMethod = method != null ? method : METHOD_SCALE;
        this.headCount = headCount != null && headCount > 0 ? headCount : 1;
        this.recordedBy = recordedBy;
        this.isDeleted = false;
    }

    public BigDecimal getTotalWeightKg() {
        if (weightKg == null || headCount == null) return BigDecimal.ZERO;
        return weightKg.multiply(BigDecimal.valueOf(headCount));
    }

    public String getMethodLabel() {
        if (weightMethod == null) return "—";
        return switch (weightMethod) {
            case METHOD_SCALE       -> "Scale";
            case METHOD_ESTIMATED   -> "Estimated";
            case METHOD_TAPE        -> "Tape measure";
            case METHOD_HEART_GIRTH -> "Heart girth";
            default -> weightMethod;
        };
    }

    public String getBodyConditionLabel() {
        if (bodyCondition == null) return "—";
        return switch (bodyCondition) {
            case CONDITION_POOR     -> "Poor";
            case CONDITION_MODERATE -> "Moderate";
            case CONDITION_GOOD     -> "Good";
            case CONDITION_FAT      -> "Fat";
            default -> bodyCondition;
        };
    }

    public String getBodyConditionBadgeClass() {
        if (bodyCondition == null) return "bc-unknown";
        return switch (bodyCondition) {
            case CONDITION_POOR     -> "bc-poor";
            case CONDITION_MODERATE -> "bc-moderate";
            case CONDITION_GOOD     -> "bc-good";
            case CONDITION_FAT      -> "bc-fat";
            default -> "bc-unknown";
        };
    }

    public String getWeightFormatted() {
        if (weightKg == null) return "—";
        return weightKg.setScale(2, RoundingMode.HALF_UP).toPlainString() + " kg";
    }
}