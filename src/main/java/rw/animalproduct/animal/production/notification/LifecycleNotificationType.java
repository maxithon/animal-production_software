package rw.animalproduct.animal.production.notification;

public enum LifecycleNotificationType {

    // ─── LIFECYCLE STAGE NOTIFICATIONS ──────────────────────────────────────
    NEWBORN("Newborn", "🐄", "#0f766e"),
    YOUNG_ANIMAL("Young Animal", "🐄", "#0d9488"),
    PRE_BREEDING("Pre-Breeding", "📋", "#1d4ed8"),
    READY_TO_BREED("Ready to Breed", "🐂", "#16a34a"),

    // ─── PREGNANCY NOTIFICATIONS ────────────────────────────────────────────
    UPCOMING_BIRTH("Upcoming Birth", "🤰", "#9333ea"),
    BIRTH_PREPARATION("Birth Preparation", "🤰", "#7c3aed"),
    BIRTH_DUE_SOON("Birth Due Soon", "🤰", "#6d28d9"),
    OVERDUE_PREGNANCY("⚠️ Overdue Pregnancy", "⚠️", "#dc2626"),

    // ─── CLICK-TRIGGERED NOTIFICATIONS ──────────────────────────────────────
    ANIMAL_REGISTERED("Animal Registered", "📋", "#1a5f7a"),
    STAGE_CHANGED("Stage Changed", "📝", "#0369a1"),
    BREEDING_RECORDED("Breeding Recorded", "📋", "#6b21a8"),
    BREEDING_FAILED("Breeding Failed", "❌", "#b91c1c"),
    PREGNANCY_CONFIRMED("Pregnancy Confirmed", "✅", "#059669"),
    BIRTH_RECORDED("Birth Recorded", "🐣", "#059669"),
    ANIMAL_DEATH("Animal Death", "💔", "#dc2626"),

    // ─── SALE NOTIFICATIONS ──────────────────────────────────────────────────
    SALE_CREATED("Sale Created", "💰", "#2563eb"),
    SALE_CONFIRMED("Sale Confirmed", "✅", "#059669"),
    SALE_PAYMENT_RECORDED("Payment Recorded", "💰", "#059669"),
    SALE_CANCELLED("Sale Cancelled", "❌", "#dc2626"),
    SALE_REFUNDED("Sale Refunded", "↩️", "#d97706"),

    // ─── TREATMENT NOTIFICATIONS ────────────────────────────────────────────
    TREATMENT_RECORDED("Treatment Recorded", "🔬", "#7c3aed"),
    TREATMENT_UPDATED("Treatment Updated", "📝", "#6d28d9"),
    TREATMENT_PAYMENT_RECORDED("Treatment Payment", "💰", "#059669"),
    TREATMENT_COMPLETED("Treatment Completed", "✅", "#059669"),

    // ─── ✅ NEW: BULK IMPORT ─────────────────────────────────────────────────
    BULK_IMPORT_COMPLETED("Bulk Import Completed", "📦", "#0ea5e9");

    private final String label;
    private final String emoji;
    private final String colorHex;

    LifecycleNotificationType(String label, String emoji, String colorHex) {
        this.label = label;
        this.emoji = emoji;
        this.colorHex = colorHex;
    }

    public String getLabel() { return label; }
    public String getEmoji() { return emoji; }
    public String getColorHex() { return colorHex; }
}