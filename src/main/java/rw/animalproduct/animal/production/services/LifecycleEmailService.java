package rw.animalproduct.animal.production.services;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.LivestockValuation;
import rw.animalproduct.animal.production.notification.LifecycleNotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class LifecycleEmailService {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleEmailService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    private volatile String lastSendResult = "No notification has been attempted yet.";
    private volatile LocalDate lastSendDate = null;

    public LifecycleEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        logger.info("✅ LifecycleEmailService initialized");
    }

    @PostConstruct
    void validateConfigOnStartup() {
        logger.info("=================================================================");
        logger.info(" Email Notification Configuration Check");
        logger.info("=================================================================");
        logger.info(" notifications.enabled : {}", notificationsEnabled);
        logger.info(" notifications.from    : {}", fromAddress);

        List<String> recipients = recipients();
        if (!notificationsEnabled) {
            logger.warn(" ⚠️ Notifications are DISABLED (app.notifications.enabled=false).");
        } else if (recipients.isEmpty()) {
            logger.warn(" *** NO RECIPIENTS CONFIGURED ***");
            logger.warn(" app.notifications.recipients is empty or missing in application.properties.");
        } else {
            logger.info(" notifications.recipients ({}): {}", recipients.size(), recipients);
        }
        logger.info("=================================================================");
    }

    // ============ ZONE 1: CLICK-TRIGGERED ============

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendAnimalRegisteredNotification(Livestock animal) {
        if (animal == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        rows.add(new DetailRow("Category", animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "—"));
        rows.add(new DetailRow("Gender", animal.getGender()));
        rows.add(new DetailRow("Status", animal.getStatus()));
        rows.add(new DetailRow("Health Status", animal.healthStatusLabel()));
        rows.add(new DetailRow("Reproductive Status", animal.reproductiveStatusLabel()));
        rows.add(new DetailRow("Acquisition Method", animal.getAcquisitionMethod()));
        rows.add(new DetailRow("Birth Date", format(animal.getBirthDate())));
        rows.add(new DetailRow("Beneficiary", animal.getBeneficiary() != null
                ? animal.getBeneficiary().getFullName() : "Not assigned"));
        if (animal.getLocation() != null) {
            rows.add(new DetailRow("Location", animal.getLocation().getName()));
        }
        if (animal.getCurrentValue() != null) {
            rows.add(new DetailRow("Current Value", formatMoney(animal.getCurrentValue()) + " RWF"));
        }
        String message = animal.getTagNumber() + " has been registered in the system.";
        send(LifecycleNotificationType.ANIMAL_REGISTERED,
                "Animal registered — " + animal.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * ✅ NEW: Bulk import summary email.
     * Fires ONCE after a bulk import finishes — NOT once per animal.
     * Called from BulkLivestockImportService.importValidatedRows().
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendBulkImportCompletedNotification(
            int createdCount,
            int totalRows,
            int okRows,
            int warningRows,
            int errorRows,
            String fileName,
            String uploadedBy,
            List<String> createdTags) {

        logger.info("📧 QUEUED: Bulk import completed email ({} created)", createdCount);

        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("File",                  fileName != null ? fileName : "—"));
        rows.add(new DetailRow("Uploaded By",           uploadedBy != null ? uploadedBy : "system"));
        rows.add(new DetailRow("Total Rows",            String.valueOf(totalRows)));
        rows.add(new DetailRow("✅ Successfully Created", String.valueOf(createdCount)));
        rows.add(new DetailRow("⚠️ Warnings",           String.valueOf(warningRows)));
        rows.add(new DetailRow("❌ Errors (skipped)",   String.valueOf(errorRows)));

        if (createdTags != null && !createdTags.isEmpty()) {
            String preview = createdTags.size() <= 30
                    ? String.join(", ", createdTags)
                    : String.join(", ", createdTags.subList(0, 30))
                    + " … +" + (createdTags.size() - 30) + " more";
            rows.add(new DetailRow("Created Tags", preview));
        }

        String message = "Bulk import finished: " + createdCount
                + " animal(s) registered from " + fileName + ".";

        send(LifecycleNotificationType.BULK_IMPORT_COMPLETED,
                "Bulk import completed — " + createdCount + " animal(s)",
                message, rows);

        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendAnimalUpdatedNotification(Livestock animal,
                                                                 List<ChangeRow> changes,
                                                                 String changedBy) {
        if (animal == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        rows.add(new DetailRow("Category", animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "—"));
        rows.add(new DetailRow("Updated By", changedBy != null ? changedBy : "system"));
        for (ChangeRow change : changes) {
            rows.add(new DetailRow("  ⚡ " + change.getLabel(),
                    change.getOldValue() + " → " + change.getNewValue()));
        }
        String message = animal.getTagNumber() + " was updated. " + changes.size() + " field(s) changed.";
        send(LifecycleNotificationType.STAGE_CHANGED,
                "Animal updated — " + animal.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendValuationChangedNotification(LivestockValuation valuation,
                                                                    Double oldValue) {
        if (valuation == null || valuation.getLivestock() == null) {
            return CompletableFuture.completedFuture(null);
        }
        Livestock animal = valuation.getLivestock();
        logger.info("📧 QUEUED: Valuation changed email for: {}", animal.getTagNumber());

        Double newValue = valuation.getEstimatedValue();
        BigDecimal delta = BigDecimal.ZERO;
        if (oldValue != null && newValue != null) {
            delta = BigDecimal.valueOf(newValue - oldValue);
        }

        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        rows.add(new DetailRow("Category", animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "—"));
        rows.add(new DetailRow("Previous Value",
                oldValue != null ? formatMoney(BigDecimal.valueOf(oldValue)) + " RWF" : "—"));
        rows.add(new DetailRow("New Value",
                newValue != null ? formatMoney(BigDecimal.valueOf(newValue)) + " RWF" : "—"));
        rows.add(new DetailRow("Change",
                (delta.signum() >= 0 ? "+" : "") + formatMoney(delta) + " RWF"));
        rows.add(new DetailRow("Valuation Date", format(valuation.getValuationDate())));
        rows.add(new DetailRow("Method", valuation.methodLabel()));
        rows.add(new DetailRow("Notes", valuation.getNotes() != null ? valuation.getNotes() : "—"));
        rows.add(new DetailRow("Recorded By", valuation.getValuedBy() != null
                ? valuation.getValuedBy() : "system"));

        String message = "New valuation recorded for " + animal.getTagNumber()
                + (oldValue != null
                ? ": " + formatMoney(BigDecimal.valueOf(oldValue)) + " RWF → "
                + formatMoney(BigDecimal.valueOf(newValue)) + " RWF"
                : ": " + formatMoney(BigDecimal.valueOf(newValue)) + " RWF");

        send(LifecycleNotificationType.STAGE_CHANGED,
                "Valuation changed — " + animal.getTagNumber(),
                message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendBreedingStartedNotification(LivestockBreeding breeding) {
        if (breeding == null || breeding.getLivestock() == null) return CompletableFuture.completedFuture(null);
        Livestock dam = breeding.getLivestock();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Dam Tag", dam.getTagNumber()));
        rows.add(new DetailRow("Sire", breeding.getMale() != null
                ? breeding.getMale().getTagNumber() : "External / Not recorded"));
        rows.add(new DetailRow("Breeding Method", breeding.getInseminationMethodLabel()));
        rows.add(new DetailRow("Breeding Date", format(breeding.getBreedingDate())));
        rows.add(new DetailRow("Status", breeding.getStatusLabel()));
        String message = "A new breeding has been recorded for " + dam.getTagNumber() + ".";
        send(LifecycleNotificationType.BREEDING_RECORDED,
                "Breeding recorded — " + dam.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendBreedingFailedNotification(LivestockBreeding breeding) {
        if (breeding == null || breeding.getLivestock() == null) return CompletableFuture.completedFuture(null);
        Livestock dam = breeding.getLivestock();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Dam Tag", dam.getTagNumber()));
        rows.add(new DetailRow("Failure Reason", breeding.getFailureReason() != null
                ? breeding.getFailureReason() : "Not specified"));
        String message = "Breeding attempt for " + dam.getTagNumber() + " was marked as failed.";
        send(LifecycleNotificationType.BREEDING_FAILED,
                "Breeding failed — " + dam.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendPregnancyConfirmedNotification(LivestockBreeding breeding) {
        if (breeding == null || breeding.getLivestock() == null) return CompletableFuture.completedFuture(null);
        Livestock dam = breeding.getLivestock();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Dam Tag", dam.getTagNumber()));
        rows.add(new DetailRow("Breeding Date", format(breeding.getBreedingDate())));
        rows.add(new DetailRow("Expected Due Date", format(breeding.getExpectedDueDate())));
        String message = "Pregnancy confirmed for " + dam.getTagNumber() + ".";
        send(LifecycleNotificationType.PREGNANCY_CONFIRMED,
                "Pregnancy confirmed — " + dam.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendBirthRecordedNotification(LivestockBirth birth) {
        if (birth == null || birth.getLivestock() == null) return CompletableFuture.completedFuture(null);
        Livestock dam = birth.getLivestock();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Mother Tag", dam.getTagNumber()));
        rows.add(new DetailRow("Birth Date", format(birth.getBirthDate())));
        rows.add(new DetailRow("Offspring Count", String.valueOf(birth.getOffspringCount())));
        String message = "A birth has been recorded for " + dam.getTagNumber() + ".";
        send(LifecycleNotificationType.BIRTH_RECORDED,
                "Birth recorded — " + dam.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendDeathNotification(LivestockDeath death) {
        if (death == null || death.getLivestock() == null) return CompletableFuture.completedFuture(null);
        Livestock animal = death.getLivestock();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        rows.add(new DetailRow("Death Date", format(death.getDeathDate())));
        rows.add(new DetailRow("Cause of Death", death.getCauseOfDeath() != null
                ? death.getCauseOfDeath() : "Not specified"));
        String message = "A death has been recorded for " + animal.getTagNumber() + ".";
        send(LifecycleNotificationType.ANIMAL_DEATH,
                "Death recorded — " + animal.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSaleCreatedNotification(LivestockSale sale, String createdBy) {
        if (sale == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Sale Reference", sale.getSaleReference()));
        rows.add(new DetailRow("Sale Price", formatMoney(sale.getSalePrice()) + " RWF"));
        String message = "Sale " + sale.getSaleReference() + " was created by " + createdBy + ".";
        send(LifecycleNotificationType.SALE_CREATED,
                "Sale created — " + sale.getSaleReference(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSaleConfirmedNotification(LivestockSale sale, String confirmedBy) {
        if (sale == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Sale Reference", sale.getSaleReference()));
        rows.add(new DetailRow("Confirmed By", confirmedBy));
        String message = "Sale " + sale.getSaleReference() + " was confirmed.";
        send(LifecycleNotificationType.SALE_CONFIRMED,
                "Sale confirmed — " + sale.getSaleReference(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendPaymentRecordedNotification(LivestockSale sale,
                                                                   String recordedBy,
                                                                   String paymentMethod,
                                                                   String paymentReference) {
        if (sale == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Sale Reference", sale.getSaleReference()));
        rows.add(new DetailRow("Amount Paid", formatMoney(sale.getAmountPaid()) + " RWF"));
        rows.add(new DetailRow("Balance", formatMoney(sale.getBalance()) + " RWF"));
        String message = "Payment recorded for sale " + sale.getSaleReference() + ".";
        send(LifecycleNotificationType.SALE_PAYMENT_RECORDED,
                "Payment recorded — " + sale.getSaleReference(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSaleCancelledNotification(LivestockSale sale, String cancelledBy) {
        if (sale == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Sale Reference", sale.getSaleReference()));
        rows.add(new DetailRow("Cancelled By", cancelledBy));
        String message = "Sale " + sale.getSaleReference() + " was cancelled.";
        send(LifecycleNotificationType.SALE_CANCELLED,
                "Sale cancelled — " + sale.getSaleReference(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSaleRefundedNotification(LivestockSale sale, String refundedBy) {
        if (sale == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Sale Reference", sale.getSaleReference()));
        rows.add(new DetailRow("Refunded By", refundedBy));
        String message = "Sale " + sale.getSaleReference() + " was refunded.";
        send(LifecycleNotificationType.SALE_REFUNDED,
                "Sale refunded — " + sale.getSaleReference(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    // ============ ZONE 2: SCHEDULED ============

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendReadyToBreedNotification(Livestock animal) {
        if (animal == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        rows.add(new DetailRow("Age", animal.getAgeDisplay()));
        String message = animal.getTagNumber() + " is ready to breed.";
        send(LifecycleNotificationType.READY_TO_BREED,
                "Ready to breed — " + animal.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendYoungStageNotification(Livestock animal) {
        if (animal == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        String message = animal.getTagNumber() + " entered young stage.";
        send(LifecycleNotificationType.YOUNG_ANIMAL,
                "Young animal — " + animal.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendPreBreedingNotification(Livestock animal) {
        if (animal == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        String message = animal.getTagNumber() + " approaching breeding age.";
        send(LifecycleNotificationType.PRE_BREEDING,
                "Pre-breeding — " + animal.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendNewbornNotification(Livestock animal) {
        if (animal == null) return CompletableFuture.completedFuture(null);
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
        String message = animal.getTagNumber() + " is a newborn.";
        send(LifecycleNotificationType.YOUNG_ANIMAL,
                "Newborn — " + animal.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendDueSoonNotification(List<Livestock> animals, int daysUntilDue) {
        if (animals == null || animals.isEmpty()) return CompletableFuture.completedFuture(null);
        for (Livestock animal : animals) {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Tag Number", animal.getTagNumber()));
            rows.add(new DetailRow("Days Until Due", String.valueOf(daysUntilDue)));
            String message = animal.getTagNumber() + " is due in " + daysUntilDue + " day(s).";
            send(LifecycleNotificationType.UPCOMING_BIRTH,
                    "Upcoming birth — " + animal.getTagNumber(), message, rows);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendOverduePregnancyNotification(LivestockBreeding breeding,
                                                                    int daysOverdue, int tier) {
        if (breeding == null || breeding.getLivestock() == null) return CompletableFuture.completedFuture(null);
        Livestock dam = breeding.getLivestock();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Dam Tag", dam.getTagNumber()));
        rows.add(new DetailRow("Days Overdue", String.valueOf(daysOverdue)));
        String message = dam.getTagNumber() + " is " + daysOverdue + " day(s) overdue.";
        send(LifecycleNotificationType.OVERDUE_PREGNANCY,
                "URGENT: Overdue pregnancy — " + dam.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendPregnancyTimelineNotification(LifecycleNotificationType type,
                                                                     LivestockBreeding breeding,
                                                                     int daysUntilDue) {
        if (breeding == null || breeding.getLivestock() == null) return CompletableFuture.completedFuture(null);
        Livestock dam = breeding.getLivestock();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Dam Tag", dam.getTagNumber()));
        rows.add(new DetailRow("Days Until Due", String.valueOf(daysUntilDue)));
        String message = "Pregnancy update for " + dam.getTagNumber();
        send(type, "Pregnancy update — " + dam.getTagNumber(), message, rows);
        return CompletableFuture.completedFuture(null);
    }

    // ============ SENDING ============

    private void send(LifecycleNotificationType type, String subject, String message, List<DetailRow> rows) {
        logger.info("📧 ATTEMPTING to send {} email: {}", type, subject);

        if (!notificationsEnabled) {
            logger.warn("⛔ Notifications disabled — skipping {}", type);
            lastSendResult = "Skipped — notifications disabled.";
            return;
        }

        List<String> recipients = recipients();
        if (recipients.isEmpty()) {
            logger.error("❌ NO RECIPIENTS configured — skipping {}", type);
            lastSendResult = "Skipped — no recipients configured.";
            return;
        }

        if (mailSender == null) {
            logger.error("❌ mailSender is NULL");
            lastSendResult = "FAILED — mailSender is null.";
            return;
        }

        try {
            Context ctx = new Context();
            ctx.setVariable("title", type.getLabel());
            ctx.setVariable("emoji", type.getEmoji());
            ctx.setVariable("colorHex", type.getColorHex());
            ctx.setVariable("subtitle", "Livestock Lifecycle Notification");
            ctx.setVariable("message", message);
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process("email-lifecycle-notification", ctx);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(type.getEmoji() + " " + subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            logger.info("✅ SUCCESS: Sent {} notification to {} recipient(s).", type, recipients.size());
            lastSendResult = "Success — " + type + " sent.";
            lastSendDate = LocalDate.now();
        } catch (Exception e) {
            logger.error("❌ FAILED to send {} notification: {}", type, e.getMessage(), e);
            lastSendResult = "FAILED — " + type + ": " + e.getMessage();
        }
    }

    private List<String> recipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(recipientsCsv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private String format(LocalDate date) {
        return date == null ? "—" : date.format(DISPLAY_DATE_FMT);
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(0, RoundingMode.HALF_UP)
                .toBigInteger().toString()
                .replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
    }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public String getFromAddress() { return fromAddress; }
    public List<String> getConfiguredRecipients() { return recipients(); }
    public String getLastSendResult() { return lastSendResult; }
    public LocalDate getLastSendDate() { return lastSendDate; }

    public record DetailRow(String label, String value) {}
    public record ChangeRow(String label, String oldValue, String newValue) {
        public String getLabel() { return label; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
    }
}