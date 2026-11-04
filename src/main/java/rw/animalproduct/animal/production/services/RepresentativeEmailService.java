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
import rw.animalproduct.animal.production.entity.Representative;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class RepresentativeEmailService {

    private static final Logger log = LoggerFactory.getLogger(RepresentativeEmailService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ★ Single constant for the template path — includes the emails/ subfolder
    private static final String TEMPLATE = "emails/email-representative-notification";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationEnabled;

    public RepresentativeEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        log.info("✅ RepresentativeEmailService initialized — template={}", TEMPLATE);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REGISTERED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendRepresentativeRegistered(Representative r, String createdBy) {
        log.info("★★★ ASYNC ENTERED sendRepresentativeRegistered — thread={}", Thread.currentThread().getName());
        if (r == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            Context ctx = new Context();
            ctx.setVariable("title", "Representative Registered");
            ctx.setVariable("emoji", "👤");
            ctx.setVariable("colorHex", "#1a5f7a");
            ctx.setVariable("subtitle", "Representative Management Notification");
            ctx.setVariable("mode", "REGISTERED");
            ctx.setVariable("message", fullName(r) + " (" + safe(r.getNid()) + ") has been registered as a livestock representative.");
            ctx.setVariable("detailRows", baseRows(r, createdBy, "Registered By"));
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("👤 Representative registered — " + fullName(r));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Representative REGISTERED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(r));
        } catch (Exception e) {
            log.error("❌ Failed to send representative registered email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UPDATED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendRepresentativeUpdated(Representative r, List<ChangeRow> changes, String updatedBy) {
        log.info("★★★ ASYNC ENTERED sendRepresentativeUpdated — thread={}", Thread.currentThread().getName());
        if (r == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(r, updatedBy, "Updated By");
            rows.add(new DetailRow("Fields Changed", String.valueOf(changes != null ? changes.size() : 0)));
            if (changes != null) {
                for (ChangeRow c : changes) {
                    rows.add(new DetailRow("  ⚡ " + c.getLabel(),
                            c.getOldValue() + " → " + c.getNewValue()));
                }
            }

            Context ctx = new Context();
            ctx.setVariable("title", "Representative Updated");
            ctx.setVariable("emoji", "✏️");
            ctx.setVariable("colorHex", "#0369a1");
            ctx.setVariable("subtitle", "Representative Management Notification");
            ctx.setVariable("mode", "UPDATED");
            ctx.setVariable("message", fullName(r) + " was updated. "
                    + (changes != null ? changes.size() : 0) + " field(s) changed.");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("✏️ Representative updated — " + fullName(r));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Representative UPDATED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(r));
        } catch (Exception e) {
            log.error("❌ Failed to send representative updated email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ★★★ DELETED — this is the one that fires on delete ★★★
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendRepresentativeDeleted(Representative r, String deletedBy) {
        log.info("★★★ ASYNC ENTERED sendRepresentativeDeleted — thread={}", Thread.currentThread().getName());
        if (r == null) {
            log.warn("⚠️ Representative is null — skipping delete email");
            return;
        }
        if (!notificationEnabled) {
            log.info("⛔ Notifications disabled — skipping delete email");
            return;
        }

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) {
            log.error("❌ No recipients configured — skipping delete email");
            return;
        }

        try {
            List<DetailRow> rows = baseRows(r, deletedBy, "Deleted By");
            rows.add(new DetailRow("Deletion Date", LocalDate.now().format(DISPLAY_DATE_FMT)));

            Context ctx = new Context();
            ctx.setVariable("title", "Representative Deleted");
            ctx.setVariable("emoji", "🗑️");
            ctx.setVariable("colorHex", "#b91c1c");
            ctx.setVariable("subtitle", "Representative Management Notification");
            ctx.setVariable("mode", "DELETED");
            ctx.setVariable("message", fullName(r) + " (" + safe(r.getNid()) + ") has been removed from the system.");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🗑️ Representative deleted — " + fullName(r));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Representative DELETED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(r));
        } catch (Exception e) {
            log.error("❌ Failed to send representative deleted email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STATUS CHANGED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendRepresentativeStatusChanged(Representative r, String oldStatus, String newStatus, String changedBy) {
        log.info("★★★ ASYNC ENTERED sendRepresentativeStatusChanged — thread={}", Thread.currentThread().getName());
        if (r == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(r, changedBy, "Changed By");
            rows.add(new DetailRow("Previous Status", safe(oldStatus)));
            rows.add(new DetailRow("New Status", safe(newStatus)));

            Context ctx = new Context();
            ctx.setVariable("title", "Representative Status Changed");
            ctx.setVariable("emoji", "🔄");
            ctx.setVariable("colorHex", "#d97706");
            ctx.setVariable("subtitle", "Representative Management Notification");
            ctx.setVariable("mode", "STATUS_CHANGED");
            ctx.setVariable("message", fullName(r) + " status changed from " + safe(oldStatus) + " to " + safe(newStatus) + ".");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🔄 Representative status changed — " + fullName(r));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Representative STATUS_CHANGED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(r));
        } catch (Exception e) {
            log.error("❌ Failed to send representative status email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTERNALS
    // ═══════════════════════════════════════════════════════════════════════

    private List<DetailRow> baseRows(Representative r, String actor, String actorLabel) {
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Full Name", fullName(r)));
        rows.add(new DetailRow("National ID", safe(r.getNid())));
        rows.add(new DetailRow("Gender", safe(r.getGender())));
        rows.add(new DetailRow("Marital Status", safe(r.getMaritialStatus())));
        rows.add(new DetailRow("Phone", safe(r.getPhone())));
        rows.add(new DetailRow("Email", safe(r.getEmail())));
        rows.add(new DetailRow("Occupation", safe(r.getOccupation())));
        rows.add(new DetailRow("Location", safe(r.getFullLocationPath())));
        rows.add(new DetailRow("Status", safe(r.getStatus())));
        rows.add(new DetailRow(actorLabel, actor != null ? actor : "system"));
        return rows;
    }

    private static String fullName(Representative r) {
        if (r == null) return "—";
        String fn = r.getFirstName() != null ? r.getFirstName() : "";
        String ln = r.getLastName() != null ? r.getLastName() : "";
        String name = (fn + " " + ln).trim();
        return name.isEmpty() ? "—" : name;
    }

    private static String safe(String v) { return (v == null || v.isBlank()) ? "—" : v; }

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