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
import rw.animalproduct.animal.production.notification.LifecycleNotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TreatmentEmailService {

    private static final Logger logger = LoggerFactory.getLogger(TreatmentEmailService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:samuelhabanabakize55@gmail.com}")
    private String recipientsCsv;

    @Value("${app.notifications.from:samuelhabanabakize55@gmail.com}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    public TreatmentEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        logger.info("✅ TreatmentEmailService initialized");
    }

    @Async("notificationExecutor")
    public void sendTreatmentCreatedNotification(TreatmentEmailData data) {
        if (data == null) {
            logger.warn("⚠️ Cannot send email: data is null");
            return;
        }
        if (!notificationsEnabled) return;
        if (getRecipients().isEmpty()) {
            logger.error("❌ NO RECIPIENTS configured! Check app.notifications.recipients");
            return;
        }

        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", data.reference()));
            if (data.animalTag() != null) {
                rows.add(new DetailRow("Animal Tag", data.animalTag()));
                rows.add(new DetailRow("Animal Category", data.animalCategory() != null ? data.animalCategory() : "—"));
                rows.add(new DetailRow("Beneficiary", data.beneficiaryName() != null ? data.beneficiaryName() : "—"));
            }
            if (data.vetName() != null) {
                rows.add(new DetailRow("Veterinarian", data.vetName()));
            }
            rows.add(new DetailRow("Treatment Date", format(data.treatmentDate())));
            rows.add(new DetailRow("Diagnosis", data.diagnosis() != null ? data.diagnosis() : "—"));
            rows.add(new DetailRow("Type", data.type() != null ? data.type() : "—"));
            rows.add(new DetailRow("Status", data.status() != null ? data.status() : "—"));
            if (data.medication() != null) rows.add(new DetailRow("Medication", data.medication()));
            if (data.cost() != null) {
                rows.add(new DetailRow("Cost", formatMoney(data.cost()) + " RWF"));
                rows.add(new DetailRow("Paid", formatMoney(data.amountPaid()) + " RWF"));
                rows.add(new DetailRow("Balance", formatMoney(data.balance()) + " RWF"));
            }

            String subject = "Treatment Recorded — " + data.reference();
            String msg = "A new treatment has been recorded for " + (data.animalTag() != null ? data.animalTag() : "unknown animal") + ".";

            sendEmail(LifecycleNotificationType.TREATMENT_RECORDED, subject, msg, rows);
        } catch (Exception e) {
            logger.error("❌ Failed to send treatment-created email: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    public void sendTreatmentUpdatedNotification(TreatmentEmailData data, List<ChangeRow> changes) {
        if (data == null || !notificationsEnabled || getRecipients().isEmpty()) return;
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", data.reference()));
            if (data.animalTag() != null) rows.add(new DetailRow("Animal Tag", data.animalTag()));
            if (changes != null) for (ChangeRow c : changes)
                rows.add(new DetailRow("⚡ " + c.label(), c.oldValue() + " → " + c.newValue()));

            sendEmail(LifecycleNotificationType.TREATMENT_UPDATED,
                    "Treatment Updated — " + data.reference(),
                    "Treatment " + data.reference() + " has been updated.", rows);
        } catch (Exception e) {
            logger.error("❌ Failed to send treatment-updated email: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    public void sendTreatmentPaymentNotification(TreatmentEmailData data, BigDecimal amount, String method, String reference) {
        if (data == null || !notificationsEnabled || getRecipients().isEmpty()) return;
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", data.reference()));
            if (data.animalTag() != null) rows.add(new DetailRow("Animal", data.animalTag()));
            rows.add(new DetailRow("Payment Amount", formatMoney(amount) + " RWF"));
            rows.add(new DetailRow("Total Cost", formatMoney(data.cost()) + " RWF"));
            rows.add(new DetailRow("Total Paid", formatMoney(data.amountPaid()) + " RWF"));
            rows.add(new DetailRow("Balance", formatMoney(data.balance()) + " RWF"));
            if (method != null) rows.add(new DetailRow("Method", method));
            if (reference != null) rows.add(new DetailRow("Reference", reference));

            sendEmail(LifecycleNotificationType.TREATMENT_PAYMENT_RECORDED,
                    "Payment Recorded — " + data.reference(),
                    "Payment of " + formatMoney(amount) + " RWF recorded.", rows);
        } catch (Exception e) {
            logger.error("❌ Failed to send payment email: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    public void sendTreatmentPaymentDeletedNotification(TreatmentEmailData data, BigDecimal amount) {
        if (data == null || !notificationsEnabled || getRecipients().isEmpty()) return;
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", data.reference()));
            if (data.animalTag() != null) rows.add(new DetailRow("Animal", data.animalTag()));
            rows.add(new DetailRow("Deleted Amount", formatMoney(amount) + " RWF"));
            rows.add(new DetailRow("New Total Paid", formatMoney(data.amountPaid()) + " RWF"));
            rows.add(new DetailRow("New Balance", formatMoney(data.balance()) + " RWF"));

            sendEmail(LifecycleNotificationType.TREATMENT_UPDATED,
                    "Payment Deleted — " + data.reference(),
                    "A payment of " + formatMoney(amount) + " RWF was deleted.", rows);
        } catch (Exception e) {
            logger.error("❌ Failed to send payment deleted email: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    public void sendTreatmentDeletedNotification(TreatmentEmailData data) {
        if (data == null || !notificationsEnabled || getRecipients().isEmpty()) return;
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Treatment Reference", data.reference()));
            if (data.animalTag() != null) rows.add(new DetailRow("Animal", data.animalTag()));
            rows.add(new DetailRow("Diagnosis", data.diagnosis() != null ? data.diagnosis() : "—"));
            rows.add(new DetailRow("Treatment Date", format(data.treatmentDate())));

            sendEmail(LifecycleNotificationType.TREATMENT_DELETED,
                    "Treatment Deleted — " + data.reference(),
                    "Treatment " + data.reference() + " has been permanently deleted.", rows);
        } catch (Exception e) {
            logger.error("❌ Failed to send delete email: {}", e.getMessage(), e);
        }
    }

    private void sendEmail(LifecycleNotificationType type, String subject, String message, List<DetailRow> rows) {
        logger.info("📧 PREPARING SEND: {}", subject);
        try {
            Context ctx = new Context();
            ctx.setVariable("title", type.getLabel());
            ctx.setVariable("emoji", type.getEmoji());
            ctx.setVariable("colorHex", type.getColorHex());
            ctx.setVariable("subtitle", "Treatment Management Notification");
            ctx.setVariable("message", message);
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            // ✅ FIXED: Added "emails/" prefix to match your template folder structure
            String html;
            try {
                html = templateEngine.process("emails/email-treatment-notification", ctx);
            } catch (Exception templateEx) {
                // ✅ FALLBACK: If template is missing, send a plain-text HTML version
                logger.warn("⚠️ Template not found — falling back to plain text for: {}", subject);
                StringBuilder fallback = new StringBuilder();
                fallback.append("<h3>").append(subject).append("</h3>");
                fallback.append("<p>").append(message).append("</p>");
                fallback.append("<ul>");
                for (DetailRow row : rows) {
                    fallback.append("<li><b>").append(row.label()).append(":</b> ").append(row.value()).append("</li>");
                }
                fallback.append("</ul>");
                html = fallback.toString();
            }

            MimeMessage mm = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mm, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(getRecipients().toArray(new String[0]));
            helper.setSubject(type.getEmoji() + " " + subject);
            helper.setText(html, true);

            mailSender.send(mm);
            logger.info("✅ SENT successfully: '{}'", subject);
        } catch (Exception e) {
            logger.error("❌ SMTP FAILED for '{}': {}", subject, e.getMessage(), e);
        }
    }

    private List<String> getRecipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(recipientsCsv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private String format(LocalDate d) { return d == null ? "—" : d.format(DISPLAY_DATE_FMT); }

    private String formatMoney(BigDecimal v) {
        if (v == null) return "0";
        return v.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString()
                .replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
    }

    public record DetailRow(String label, String value) {}
    public record ChangeRow(String label, String oldValue, String newValue) {}

    // ✅ The DTO is still needed for the method parameters
    public record TreatmentEmailData(
            String reference, String animalTag, String animalCategory, String animalGender,
            String animalStatus, String animalHealth, String beneficiaryName, String beneficiaryPhone,
            String vetName, String vetPhone, LocalDate treatmentDate, String diagnosis,
            String type, String status, String medication, BigDecimal cost, BigDecimal amountPaid,
            BigDecimal balance, String outcomeStatus
    ) {}
}