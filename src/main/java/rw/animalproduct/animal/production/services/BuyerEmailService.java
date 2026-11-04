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
import rw.animalproduct.animal.production.entity.Buyer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sends CRUD notification emails for the Buyer entity.
 * Uses @Async("notificationExecutor") and void return — matching the
 * proven pattern from RepresentativeEmailService and BeneficiaryEmailService.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 */
@Service
public class BuyerEmailService {

    private static final Logger log = LoggerFactory.getLogger(BuyerEmailService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ★ All email templates live under templates/emails/
    private static final String TEMPLATE = "emails/email-buyer-notification";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationEnabled;

    public BuyerEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        log.info("✅ BuyerEmailService initialized — template={}", TEMPLATE);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REGISTERED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendBuyerRegistered(Buyer b, String createdBy) {
        log.info("★★★ ASYNC ENTERED sendBuyerRegistered — thread={}", Thread.currentThread().getName());
        if (b == null) { log.warn("⚠️ Buyer is null — skipping"); return; }
        if (!notificationEnabled) { log.info("⛔ Notifications disabled"); return; }

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) { log.error("❌ No recipients configured"); return; }

        try {
            Context ctx = new Context();
            ctx.setVariable("title", "Buyer Registered");
            ctx.setVariable("emoji", "🛒");
            ctx.setVariable("colorHex", "#1a5f7a");
            ctx.setVariable("subtitle", "Buyer Management Notification");
            ctx.setVariable("mode", "REGISTERED");
            ctx.setVariable("message", fullName(b) + " (" + safe(b.getPhone()) + ") has been registered as a buyer.");
            ctx.setVariable("detailRows", baseRows(b, createdBy, "Registered By"));
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🛒 Buyer registered — " + fullName(b));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Buyer REGISTERED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(b));
        } catch (Exception e) {
            log.error("❌ Failed to send buyer registered email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UPDATED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendBuyerUpdated(Buyer b, List<ChangeRow> changes, String updatedBy) {
        log.info("★★★ ASYNC ENTERED sendBuyerUpdated — thread={}", Thread.currentThread().getName());
        if (b == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(b, updatedBy, "Updated By");
            rows.add(new DetailRow("Fields Changed", String.valueOf(changes != null ? changes.size() : 0)));
            if (changes != null) {
                for (ChangeRow c : changes) {
                    rows.add(new DetailRow("  ⚡ " + c.getLabel(),
                            c.getOldValue() + " → " + c.getNewValue()));
                }
            }

            Context ctx = new Context();
            ctx.setVariable("title", "Buyer Updated");
            ctx.setVariable("emoji", "✏️");
            ctx.setVariable("colorHex", "#0369a1");
            ctx.setVariable("subtitle", "Buyer Management Notification");
            ctx.setVariable("mode", "UPDATED");
            ctx.setVariable("message", fullName(b) + " was updated. "
                    + (changes != null ? changes.size() : 0) + " field(s) changed.");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("✏️ Buyer updated — " + fullName(b));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Buyer UPDATED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(b));
        } catch (Exception e) {
            log.error("❌ Failed to send buyer updated email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DELETED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendBuyerDeleted(Buyer b, String deletedBy) {
        log.info("★★★ ASYNC ENTERED sendBuyerDeleted — thread={}", Thread.currentThread().getName());
        if (b == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(b, deletedBy, "Deleted By");
            rows.add(new DetailRow("Deletion Date", LocalDate.now().format(DISPLAY_DATE_FMT)));

            Context ctx = new Context();
            ctx.setVariable("title", "Buyer Deleted");
            ctx.setVariable("emoji", "🗑️");
            ctx.setVariable("colorHex", "#b91c1c");
            ctx.setVariable("subtitle", "Buyer Management Notification");
            ctx.setVariable("mode", "DELETED");
            ctx.setVariable("message", fullName(b) + " (" + safe(b.getPhone()) + ") has been removed from the system.");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🗑️ Buyer deleted — " + fullName(b));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Buyer DELETED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(b));
        } catch (Exception e) {
            log.error("❌ Failed to send buyer deleted email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STATUS CHANGED
    // ═══════════════════════════════════════════════════════════════════════
    @Async("notificationExecutor")
    public void sendBuyerStatusChanged(Buyer b, String oldStatus, String newStatus, String changedBy) {
        log.info("★★★ ASYNC ENTERED sendBuyerStatusChanged — thread={}", Thread.currentThread().getName());
        if (b == null) return;
        if (!notificationEnabled) return;

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) return;

        try {
            List<DetailRow> rows = baseRows(b, changedBy, "Changed By");
            rows.add(new DetailRow("Previous Status", safe(oldStatus)));
            rows.add(new DetailRow("New Status", safe(newStatus)));

            Context ctx = new Context();
            ctx.setVariable("title", "Buyer Status Changed");
            ctx.setVariable("emoji", "🔄");
            ctx.setVariable("colorHex", "#d97706");
            ctx.setVariable("subtitle", "Buyer Management Notification");
            ctx.setVariable("mode", "STATUS_CHANGED");
            ctx.setVariable("message", fullName(b) + " status changed from " + safe(oldStatus) + " to " + safe(newStatus) + ".");
            ctx.setVariable("detailRows", rows);
            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));
            ctx.setVariable("systemName", "Animal Production System");

            String html = templateEngine.process(TEMPLATE, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🔄 Buyer status changed — " + fullName(b));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Buyer STATUS_CHANGED email sent to {} recipient(s) for: {}",
                    recipients.size(), fullName(b));
        } catch (Exception e) {
            log.error("❌ Failed to send buyer status email: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTERNALS
    // ═══════════════════════════════════════════════════════════════════════

    private List<DetailRow> baseRows(Buyer b, String actor, String actorLabel) {
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Full Name", fullName(b)));
        rows.add(new DetailRow("Phone", safe(b.getPhone())));
        rows.add(new DetailRow("National ID", safe(b.getNationalId())));
        rows.add(new DetailRow("Email", safe(b.getEmail())));
        rows.add(new DetailRow("Address", safe(b.getAddress())));
        rows.add(new DetailRow("Buyer Type", safe(b.getBuyerType())));
        rows.add(new DetailRow("Notes", safe(b.getNotes())));
        rows.add(new DetailRow("Status", b.getIsActive() != null && b.getIsActive() ? "Active" : "Inactive"));
        rows.add(new DetailRow("Total Purchases", String.valueOf(b.getTotalPurchases())));
        rows.add(new DetailRow(actorLabel, actor != null ? actor : "system"));
        return rows;
    }

    private static String fullName(Buyer b) {
        if (b == null) return "—";
        String fn = b.getFirstName() != null ? b.getFirstName() : "";
        String ln = b.getLastName() != null ? b.getLastName() : "";
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