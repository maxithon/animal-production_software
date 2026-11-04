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
import rw.animalproduct.animal.production.entity.Veterinarian;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sends CRUD notification emails for the Veterinarian entity.
 * Uses @Async("notificationExecutor") and void return — same pattern as
 * RepresentativeEmailService, BeneficiaryEmailService, BuyerEmailService.
 */
@Service
public class VeterinarianEmailService {

    private static final Logger log = LoggerFactory.getLogger(VeterinarianEmailService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** Template path — lives under templates/emails/ */
    private static final String TEMPLATE = "emails/email-veterinarian-notification";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationEnabled;

    public VeterinarianEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        log.info("✅ VeterinarianEmailService initialized — template={}", TEMPLATE);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REGISTERED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendVeterinarianRegistered(Veterinarian v, String createdBy) {
        log.info("★★★ ASYNC ENTERED sendVeterinarianRegistered — thread={}", Thread.currentThread().getName());
        if (v == null) { log.warn("⚠️ Veterinarian is null — skipping"); return; }
        if (!notificationEnabled) { log.info("⛔ Notifications disabled"); return; }

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) { log.error("❌ No recipients configured"); return; }

        try {
            Context ctx = new Context();
            ctx.setVariable("title", "Veterinarian Registered");
            ctx.setVariable("emoji", "🩺");
            ctx.setVariable("colorHex", "#1a5f7a");
            ctx.setVariable("subtitle", "Veterinarian Management Notification");
            ctx.setVariable("mode", "REGISTERED");
            ctx.setVariable("message", fullName(v) + " has been registered as a veterinarian.");
            ctx.setVariable("detailRows", baseRows(v, createdBy, "Registered By"));
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🩺 Veterinarian registered — " + fullName(v));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Veterinarian REGISTERED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(v));
        } catch (Exception e) {
            log.error("❌ Failed to send veterinarian registered email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UPDATED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendVeterinarianUpdated(Veterinarian v, List<ChangeRow> changes, String updatedBy) {
        log.info("★★★ ASYNC ENTERED sendVeterinarianUpdated — thread={}", Thread.currentThread().getName());
        if (v == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(v, updatedBy, "Updated By");
            rows.add(new DetailRow("Fields Changed", String.valueOf(changes != null ? changes.size() : 0)));
            if (changes != null) {
                for (ChangeRow c : changes) {
                    rows.add(new DetailRow("  ⚡ " + c.getLabel(),
                            c.getOldValue() + " → " + c.getNewValue()));
                }
            }

            Context ctx = new Context();
            ctx.setVariable("title", "Veterinarian Updated");
            ctx.setVariable("emoji", "✏️");
            ctx.setVariable("colorHex", "#0369a1");
            ctx.setVariable("subtitle", "Veterinarian Management Notification");
            ctx.setVariable("mode", "UPDATED");
            ctx.setVariable("message", fullName(v) + " was updated. "
                    + (changes != null ? changes.size() : 0) + " field(s) changed.");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("✏️ Veterinarian updated — " + fullName(v));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Veterinarian UPDATED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(v));
        } catch (Exception e) {
            log.error("❌ Failed to send veterinarian updated email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DELETED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendVeterinarianDeleted(Veterinarian v, String deletedBy) {
        log.info("★★★ ASYNC ENTERED sendVeterinarianDeleted — thread={}", Thread.currentThread().getName());
        if (v == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(v, deletedBy, "Deleted By");
            rows.add(new DetailRow("Deletion Date", LocalDate.now().format(DISPLAY_DATE_FMT)));

            Context ctx = new Context();
            ctx.setVariable("title", "Veterinarian Deleted");
            ctx.setVariable("emoji", "🗑️");
            ctx.setVariable("colorHex", "#b91c1c");
            ctx.setVariable("subtitle", "Veterinarian Management Notification");
            ctx.setVariable("mode", "DELETED");
            ctx.setVariable("message", fullName(v) + " has been removed from the system.");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🗑️ Veterinarian deleted — " + fullName(v));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Veterinarian DELETED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(v));
        } catch (Exception e) {
            log.error("❌ Failed to send veterinarian deleted email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STATUS CHANGED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendVeterinarianStatusChanged(Veterinarian v, String oldStatus, String newStatus, String changedBy) {
        log.info("★★★ ASYNC ENTERED sendVeterinarianStatusChanged — thread={}", Thread.currentThread().getName());
        if (v == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(v, changedBy, "Changed By");
            rows.add(new DetailRow("Previous Status", safe(oldStatus)));
            rows.add(new DetailRow("New Status", safe(newStatus)));

            Context ctx = new Context();
            ctx.setVariable("title", "Veterinarian Status Changed");
            ctx.setVariable("emoji", "🔄");
            ctx.setVariable("colorHex", "#d97706");
            ctx.setVariable("subtitle", "Veterinarian Management Notification");
            ctx.setVariable("mode", "STATUS_CHANGED");
            ctx.setVariable("message", fullName(v) + " status changed from "
                    + safe(oldStatus) + " to " + safe(newStatus) + ".");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🔄 Veterinarian status changed — " + fullName(v));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Veterinarian STATUS_CHANGED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(v));
        } catch (Exception e) {
            log.error("❌ Failed to send veterinarian status email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTERNALS
    // ═══════════════════════════════════════════════════════════════════════

    private List<DetailRow> baseRows(Veterinarian v, String actor, String actorLabel) {
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Full Name", fullName(v)));
        rows.add(new DetailRow("National ID", safe(v.getNationalId())));
        rows.add(new DetailRow("License Number", safe(v.getLicenseNumber())));
        rows.add(new DetailRow("Phone", safe(v.getPhone())));
        rows.add(new DetailRow("Email", safe(v.getEmail())));
        rows.add(new DetailRow("Specialization", safe(v.getSpecialization())));
        rows.add(new DetailRow("Clinic", safe(v.getClinicName())));
        rows.add(new DetailRow("Location", v.getLocation() != null ? v.getLocation().getName() : "—"));
        rows.add(new DetailRow("Notes", safe(v.getNotes())));
        rows.add(new DetailRow("Status", Boolean.TRUE.equals(v.getIsActive()) ? "Active" : "Inactive"));
        rows.add(new DetailRow(actorLabel, actor != null ? actor : "system"));
        return rows;
    }

    private static String fullName(Veterinarian v) {
        if (v == null) return "—";
        String fn = v.getFirstName() != null ? v.getFirstName() : "";
        String ln = v.getLastName() != null ? v.getLastName() : "";
        String name = (fn + " " + ln).trim();
        return name.isEmpty() ? "—" : name;
    }

    private static String safe(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private List<String> getRecipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) return List.of();
        return Arrays.stream(recipientsCsv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    public record DetailRow(String label, String value) {}

    public static class ChangeRow {
        private final String label;
        private final String oldValue;
        private final String newValue;

        public ChangeRow(String label, String oldValue, String newValue) {
            this.label = label;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getLabel() { return label; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
    }
}