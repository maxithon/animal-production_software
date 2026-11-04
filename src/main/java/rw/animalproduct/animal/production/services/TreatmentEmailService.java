package rw.animalproduct.animal.production.services;

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
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.notification.LifecycleNotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Email notification service for treatment-related events.
 *
 * Uses the SAME "notificationExecutor" that already works for
 * breeding / animal-registration emails in this project. The previous
 * "emailTaskExecutor" executor did not work reliably.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.0.0
 */
@Service
public class TreatmentEmailService {

    private static final Logger logger = LoggerFactory.getLogger(TreatmentEmailService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    public TreatmentEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        logger.info("✅ TreatmentEmailService initialized");
        logger.info("📧 MailSender: {}", mailSender != null ? "Available" : "NULL");
        logger.info("📧 From address: {}", fromAddress);
        logger.info("📧 Recipients: {}", recipientsCsv);
        logger.info("📧 Notifications enabled: {}", notificationsEnabled);
    }

    // ─── TREATMENT CREATED ──────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void sendTreatmentCreatedNotification(LivestockTreatment treatment) {
        if (treatment == null) {
            logger.warn("⚠️ Cannot send treatment notification: treatment is null");
            return;
        }

        logger.info("📧 QUEUED: Treatment created email for: {}", treatment.getTreatmentReference());

        if (!notificationsEnabled) {
            logger.warn("⛔ Notifications disabled — skipping {}", treatment.getTreatmentReference());
            return;
        }

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) {
            logger.error("❌ NO RECIPIENTS configured!");
            return;
        }

        try {
            List<DetailRow> rows = new ArrayList<>();

            rows.add(new DetailRow("Treatment Reference", treatment.getTreatmentReference()));

            if (treatment.getLivestock() != null) {
                Livestock animal = treatment.getLivestock();
                rows.add(new DetailRow("Animal Tag", animal.getTagNumber()));
                rows.add(new DetailRow("Animal Category", animal.getLivestockCategory() != null
                        ? animal.getLivestockCategory().getName() : "—"));
                rows.add(new DetailRow("Animal Gender", animal.getGender()));
                rows.add(new DetailRow("Animal Status", animal.getStatus()));
                rows.add(new DetailRow("Animal Health Status", animal.healthStatusLabel()));

                if (animal.getBeneficiary() != null) {
                    rows.add(new DetailRow("Beneficiary Name", animal.getBeneficiary().getFullName()));
                    rows.add(new DetailRow("Beneficiary Phone", animal.getBeneficiary().getPhoneNumber()));
                }
            }

            if (treatment.getVeterinarian() != null) {
                Veterinarian vet = treatment.getVeterinarian();
                rows.add(new DetailRow("Veterinarian Name", vet.getFullName()));
                rows.add(new DetailRow("Veterinarian Phone", vet.getPhone() != null ? vet.getPhone() : "—"));
            }

            rows.add(new DetailRow("Treatment Date", treatment.getTreatmentDate() != null
                    ? format(treatment.getTreatmentDate()) : "—"));
            rows.add(new DetailRow("Diagnosis", treatment.getDiagnosisName() != null
                    ? treatment.getDiagnosisName() : "—"));
            rows.add(new DetailRow("Treatment Type", treatment.getTreatmentType() != null
                    ? treatment.getTreatmentType().name() : "—"));
            rows.add(new DetailRow("Status", treatment.getTreatmentStatus() != null
                    ? treatment.getTreatmentStatus().name() : "—"));

            if (treatment.getMedication() != null) {
                rows.add(new DetailRow("Medication", treatment.getMedication().getName()));
            }

            if (treatment.getTreatmentCost() != null) {
                rows.add(new DetailRow("Cost", formatMoney(treatment.getTreatmentCost()) + " RWF"));
                rows.add(new DetailRow("Paid", formatMoney(treatment.getAmountPaid()) + " RWF"));
                rows.add(new DetailRow("Balance", formatMoney(treatment.getRemainingBalance()) + " RWF"));
            }

            String subject = "Treatment Recorded — " + treatment.getTreatmentReference();
            String message = "A new treatment has been recorded for "
                    + (treatment.getLivestock() != null ? treatment.getLivestock().getTagNumber() : "unknown animal")
                    + ".";

            sendEmail(LifecycleNotificationType.TREATMENT_RECORDED, subject, message, rows);

        } catch (Exception e) {
            logger.error("❌ Failed to send treatment-created email: {}", e.getMessage(), e);
        }
    }

    // ─── TREATMENT UPDATED ──────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void sendTreatmentUpdatedNotification(LivestockTreatment treatment, List<ChangeRow> changes) {
        if (treatment == null) return;
        if (!notificationsEnabled) return;
        if (getRecipients().isEmpty()) return;

        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", treatment.getTreatmentReference()));

            if (treatment.getLivestock() != null) {
                rows.add(new DetailRow("Animal Tag", treatment.getLivestock().getTagNumber()));
            }

            if (changes != null) {
                for (ChangeRow c : changes) {
                    rows.add(new DetailRow("⚡ " + c.label(), c.oldValue() + " → " + c.newValue()));
                }
            }

            String subject = "Treatment Updated — " + treatment.getTreatmentReference();
            String message = "Treatment " + treatment.getTreatmentReference() + " has been updated.";

            sendEmail(LifecycleNotificationType.TREATMENT_UPDATED, subject, message, rows);

        } catch (Exception e) {
            logger.error("❌ Failed to send treatment-updated email: {}", e.getMessage(), e);
        }
    }

    // ─── TREATMENT PAYMENT ──────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void sendTreatmentPaymentNotification(LivestockTreatment treatment,
                                                 BigDecimal amount,
                                                 String paymentMethod,
                                                 String paymentReference) {
        if (treatment == null) return;
        if (!notificationsEnabled) return;
        if (getRecipients().isEmpty()) return;

        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", treatment.getTreatmentReference()));

            if (treatment.getLivestock() != null) {
                rows.add(new DetailRow("Animal", treatment.getLivestock().getTagNumber()));
            }

            rows.add(new DetailRow("Payment Amount", formatMoney(amount) + " RWF"));
            rows.add(new DetailRow("Total Cost", formatMoney(treatment.getTreatmentCost()) + " RWF"));
            rows.add(new DetailRow("Total Paid", formatMoney(treatment.getAmountPaid()) + " RWF"));
            rows.add(new DetailRow("Balance", formatMoney(treatment.getRemainingBalance()) + " RWF"));

            if (paymentMethod != null)    rows.add(new DetailRow("Payment Method", paymentMethod));
            if (paymentReference != null) rows.add(new DetailRow("Payment Reference", paymentReference));

            String subject = "Payment Recorded — " + treatment.getTreatmentReference();
            String message = "Payment of " + formatMoney(amount) + " RWF has been recorded.";

            sendEmail(LifecycleNotificationType.TREATMENT_PAYMENT_RECORDED, subject, message, rows);

        } catch (Exception e) {
            logger.error("❌ Failed to send treatment-payment email: {}", e.getMessage(), e);
        }
    }

    // ─── TREATMENT COMPLETED ────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void sendTreatmentCompletedNotification(LivestockTreatment treatment) {
        if (treatment == null) return;
        if (!notificationsEnabled) return;
        if (getRecipients().isEmpty()) return;

        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", treatment.getTreatmentReference()));

            if (treatment.getLivestock() != null) {
                rows.add(new DetailRow("Animal Tag", treatment.getLivestock().getTagNumber()));
            }

            rows.add(new DetailRow("Outcome", treatment.getOutcomeStatus() != null
                    ? treatment.getOutcomeStatus().name() : "—"));

            String subject = "Treatment Completed — " + treatment.getTreatmentReference();
            String message = "Treatment " + treatment.getTreatmentReference() + " has been completed.";

            sendEmail(LifecycleNotificationType.TREATMENT_COMPLETED, subject, message, rows);

        } catch (Exception e) {
            logger.error("❌ Failed to send treatment-completed email: {}", e.getMessage(), e);
        }
    }

    // ─── SEND EMAIL (single source of truth) ────────────────────────────────

    private void sendEmail(LifecycleNotificationType type, String subject,
                           String message, List<DetailRow> rows) {
        logger.info("📧 SENDING {} email: {}", type, subject);

        try {
            Context ctx = new Context();
            ctx.setVariable("title",    type.getLabel());
            ctx.setVariable("emoji",    type.getEmoji());
            ctx.setVariable("colorHex", type.getColorHex());
            ctx.setVariable("subtitle", "Treatment Management Notification");
            ctx.setVariable("message",  message);
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            String html = templateEngine.process("email-treatment-notification", ctx);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(getRecipients().toArray(new String[0]));
            helper.setSubject(type.getEmoji() + " " + subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            logger.info("✅ Sent {} notification to {} recipient(s)", type, getRecipients().size());

        } catch (Exception e) {
            logger.error("❌ FAILED to send {} notification: {}", type, e.getMessage(), e);
        }
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    private List<String> getRecipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(recipientsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
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

    public record DetailRow(String label, String value) {}
    public record ChangeRow(String label, String oldValue, String newValue) {}
}