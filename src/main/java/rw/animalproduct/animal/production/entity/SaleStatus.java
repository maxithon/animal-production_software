package rw.animalproduct.animal.production.entity;

/**
 * Lifecycle status of a sale transaction itself — independent from
 * whether/how much has been paid (see {@link PaymentStatus}).
 *
 * DRAFT      -> being prepared, animal NOT yet marked SOLD, freely editable.
 * CONFIRMED  -> locked in, animal marked SOLD, payments can be recorded.
 * CANCELLED  -> voided before/without payment; animal restored to ACTIVE.
 * REFUNDED   -> reversed after payment was collected; animal restored to ACTIVE.
 *
 * Terminal states: CANCELLED, REFUNDED (no further transitions allowed).
 */
public enum SaleStatus {
    DRAFT,
    CONFIRMED,
    CANCELLED,
    REFUNDED;

    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED;
    }

    public String label() {
        return switch (this) {
            case DRAFT -> "Draft";
            case CONFIRMED -> "Confirmed";
            case CANCELLED -> "Cancelled";
            case REFUNDED -> "Refunded";
        };
    }

    public String badgeClass() {
        return switch (this) {
            case DRAFT -> "badge-draft";
            case CONFIRMED -> "badge-confirmed";
            case CANCELLED -> "badge-cancelled";
            case REFUNDED -> "badge-refunded";
        };
    }

    public String icon() {
        return switch (this) {
            case DRAFT -> "📝";
            case CONFIRMED -> "✅";
            case CANCELLED -> "🔴";
            case REFUNDED -> "🔵";
        };
    }
}