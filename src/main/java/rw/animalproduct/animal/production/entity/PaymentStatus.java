
package rw.animalproduct.animal.production.entity;

/**
 * Purely financial state: how much of the sale price has been collected.
 * NEVER set directly by a controller/form — always derived by
 * {@code LivestockSaleService.recalculatePaymentStatus()} from the sum
 * of the sale's {@link SalePayment} rows vs. salePrice.
 */
public enum PaymentStatus {
    UNPAID,
    PARTIAL,
    PAID;

    public String label() {
        return switch (this) {
            case UNPAID -> "Unpaid";
            case PARTIAL -> "Partial";
            case PAID -> "Paid";
        };
    }

    public String badgeClass() {
        return switch (this) {
            case UNPAID -> "badge-unpaid";
            case PARTIAL -> "badge-partial";
            case PAID -> "badge-paid";
        };
    }

    public String icon() {
        return switch (this) {
            case UNPAID -> "🟡";
            case PARTIAL -> "🟠";
            case PAID -> "🟢";
        };
    }
}