package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Improvement #10: append-only audit trail for a sale — who did what, when.
 * One row is written by the service for every state-changing action
 * (create, confirm, cancel, refund, payment, edit).
 */
@Entity
@Table(name = "sale_audit_logs")
public class SaleAuditLog {

    public static final String ACTION_CREATED = "CREATED";
    public static final String ACTION_UPDATED = "UPDATED";
    public static final String ACTION_CONFIRMED = "CONFIRMED";
    public static final String ACTION_CANCELLED = "CANCELLED";
    public static final String ACTION_REFUNDED = "REFUNDED";
    public static final String ACTION_PAYMENT_RECORDED = "PAYMENT_RECORDED";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", referencedColumnName = "id", nullable = false)
    private LivestockSale sale;

    @Column(name = "action", length = 40, nullable = false)
    private String action;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @ManyToOne
    @JoinColumn(name = "performed_by", referencedColumnName = "user_id")
    private Users performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        if (performedAt == null) performedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LivestockSale getSale() { return sale; }
    public void setSale(LivestockSale sale) { this.sale = sale; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Users getPerformedBy() { return performedBy; }
    public void setPerformedBy(Users performedBy) { this.performedBy = performedBy; }

    public LocalDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(LocalDateTime performedAt) { this.performedAt = performedAt; }
}