package rw.animalproduct.animal.production.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LifecycleEmailService — sends HTML emails using Thymeleaf templates.
 *
 * HOW TO ENABLE GMAIL SMTP (required one-time setup):
 * 1. Go to https://myaccount.google.com/security
 * 2. Enable 2-Step Verification (required for App Passwords).
 * 3. Search "App passwords" → generate one for "Mail / Other".
 * 4. Paste the 16-char password (no spaces) into application.properties:
 *        spring.mail.password=xxxxxxxxxxxx
 *
 * Also ensure these are set in application.properties:
 *   spring.mail.host=smtp.gmail.com
 *   spring.mail.port=587
 *   spring.mail.username=your@gmail.com
 *   spring.mail.password=<app-password>
 *   spring.mail.properties.mail.smtp.auth=true
 *   spring.mail.properties.mail.smtp.starttls.enable=true
 *   spring.mail.properties.mail.smtp.starttls.required=true
 *   app.notification.email.to=recipient@gmail.com
 *   app.notification.email.from=your@gmail.com
 *   app.notification.email.enabled=true
 *
 * FAO TRACEABILITY NOTE:
 * International livestock traceability standards (FAO) call for a complete,
 * auditable record of every event affecting an animal — not just its
 * registration. This service covers both ends of that requirement:
 *   - sendAnimalRegisteredNotification(): fired once, at creation.
 *   - sendAnimalUpdatedNotification():   fired on every subsequent edit
 *     that actually changes a tracked field, with a full old → new diff.
 * Combined with AuditLogService (which persists the same events to the
 * database), this gives both a durable record AND a real-time notification.
 */
@Service
public class LifecycleEmailService {

    private static final Logger log = LoggerFactory.getLogger(LifecycleEmailService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notification.email.to}")
    private String emailTo;

    @Value("${app.notification.email.from:${spring.mail.username}}")
    private String emailFrom;

    @Value("${app.notification.email.enabled:true}")
    private boolean emailEnabled;

    public LifecycleEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
    }

    // =========================================================================
    // PUBLIC API — lifecycle stage notifications
    // =========================================================================

    /** Newborn registered */
    public void sendNewbornNotification(Livestock animal) {
        if (!emailEnabled) {
            log.info("Email disabled — skipping newborn notification");
            return;
        }

        Context ctx = buildStageContext(animal, "NEWBORN",
                "🐄🍼", "Newborn Registered",
                "A new animal has been registered as a NEWBORN (0–30 days old).",
                "Young Stage (at ~30 days)");

        sendHtmlEmail("🐣 NEWBORN Animal Registered - " + animal.getTagNumber(),
                "emails/email-lifecycle-stage", ctx);
        log.info("✅ Newborn notification sent for: {} → {}", animal.getTagNumber(), emailTo);
    }

    /** Animal entered YOUNG stage */
    public void sendYoungStageNotification(Livestock animal) {
        if (!emailEnabled) return;

        Context ctx = buildStageContext(animal, "YOUNG",
                "🐂🌱", "Animal Entered YOUNG Stage",
                "This animal has entered the YOUNG stage (31–180 days old).",
                "Pre-Breeding Stage (at ~6 months)");

        sendHtmlEmail("🌱 Animal Entered YOUNG Stage - " + animal.getTagNumber(),
                "emails/email-lifecycle-stage", ctx);
        log.info("✅ Young stage notification sent for: {}", animal.getTagNumber());
    }

    /** Animal entered PRE-BREEDING stage */
    public void sendPreBreedingNotification(Livestock animal) {
        if (!emailEnabled) return;

        Context ctx = buildStageContext(animal, "PRE-BREEDING",
                "📈🐏", "Animal Entered PRE-BREEDING Stage",
                "This animal has entered the PRE-BREEDING stage (181–365 days old).",
                "Ready to Breed (at ~12 months)");

        sendHtmlEmail("📅 Animal Entered PRE-BREEDING Stage - " + animal.getTagNumber(),
                "emails/email-lifecycle-stage", ctx);
        log.info("✅ Pre-breeding notification sent for: {}", animal.getTagNumber());
    }

    /** Animal is READY TO BREED */
    public void sendReadyToBreedNotification(Livestock animal) {
        if (!emailEnabled) return;

        Context ctx = buildStageContext(animal, "READY TO BREED",
                "❤️🐑", "Animal is READY TO BREED",
                "This animal is now READY TO BREED (12+ months old). Consider scheduling a breeding event.",
                "In Breeding / Pregnant");

        sendHtmlEmail("💜 Animal is READY TO BREED - " + animal.getTagNumber(),
                "emails/email-lifecycle-stage", ctx);
        log.info("✅ Ready-to-breed notification sent for: {}", animal.getTagNumber());
    }

    /** Breeding event recorded */
    public void sendBreedingStartedNotification(LivestockBreeding breeding) {
        if (!emailEnabled) return;
        if (breeding == null || breeding.getLivestock() == null) {
            log.warn("sendBreedingStartedNotification called with null breeding or livestock — skipped");
            return;
        }

        Livestock animal = breeding.getLivestock();
        Livestock male   = breeding.getMaleLivestock();

        Context ctx = new Context();
        ctx.setVariable("tagNumber",    animal.getTagNumber());
        ctx.setVariable("category",     animal.getLivestockCategory() != null ? animal.getLivestockCategory().getName() : "Unknown");
        ctx.setVariable("sire",         male != null ? male.getTagNumber() : "Unknown");
        ctx.setVariable("breedingDate", breeding.getBreedingDate() != null ? breeding.getBreedingDate().format(FMT) : "Not set");
        ctx.setVariable("method",       breeding.getBreedingMethod() != null ? breeding.getBreedingMethod() : "Natural");
        ctx.setVariable("checkDate",    breeding.getExpectedPregnancyCheckDate() != null ? breeding.getExpectedPregnancyCheckDate().format(FMT) : "Not set");
        ctx.setVariable("today",        LocalDate.now().format(FMT));

        sendHtmlEmail("🔗 BREEDING Started - " + animal.getTagNumber(),
                "emails/email-breeding-started", ctx);
        log.info("✅ Breeding notification sent for: {}", animal.getTagNumber());
    }

    /** Pregnancy confirmed */
    public void sendPregnancyConfirmedNotification(LivestockBreeding breeding) {
        if (!emailEnabled) return;
        if (breeding == null || breeding.getLivestock() == null) {
            log.warn("sendPregnancyConfirmedNotification called with null breeding or livestock — skipped");
            return;
        }

        Livestock animal = breeding.getLivestock();
        int daysLeft = breeding.getExpectedDueDate() != null ? getDaysUntil(breeding.getExpectedDueDate()) : 0;

        Context ctx = new Context();
        ctx.setVariable("tagNumber",    animal.getTagNumber());
        ctx.setVariable("category",     animal.getLivestockCategory() != null ? animal.getLivestockCategory().getName() : "Unknown");
        ctx.setVariable("gender",       animal.getGender() != null ? animal.getGender() : "Unknown");
        ctx.setVariable("dueDate",      breeding.getExpectedDueDate() != null ? breeding.getExpectedDueDate().format(FMT) : "Not set");
        ctx.setVariable("daysUntilDue", daysLeft);
        ctx.setVariable("today",        LocalDate.now().format(FMT));

        sendHtmlEmail("🤱 PREGNANCY Confirmed - " + animal.getTagNumber(),
                "emails/email-pregnancy-confirmed", ctx);
        log.info("✅ Pregnancy confirmation sent for: {}", animal.getTagNumber());
    }

    /** Animals due within N days */
    public void sendDueSoonNotification(List<Livestock> dueSoonAnimals, int withinDays) {
        if (!emailEnabled || dueSoonAnimals == null || dueSoonAnimals.isEmpty()) return;

        Context ctx = new Context();
        ctx.setVariable("animals",    dueSoonAnimals);
        ctx.setVariable("withinDays", withinDays);
        ctx.setVariable("today",      LocalDate.now().format(FMT));

        sendHtmlEmail("⏰ " + dueSoonAnimals.size() + " Animal(s) Due Within " + withinDays + " Days",
                "emails/email-due-soon", ctx);
        log.info("✅ Due-soon notification sent for {} animals", dueSoonAnimals.size());
    }

    /** Overdue animals */
    public void sendOverdueNotification(List<Livestock> overdueAnimals) {
        if (!emailEnabled || overdueAnimals == null || overdueAnimals.isEmpty()) return;

        Context ctx = new Context();
        ctx.setVariable("animals", overdueAnimals);
        ctx.setVariable("today",   LocalDate.now().format(FMT));

        sendHtmlEmail("🚨 URGENT: " + overdueAnimals.size() + " Overdue Pregnancy/Pregnancies",
                "emails/email-overdue", ctx);
        log.info("✅ Overdue notification sent for {} animals", overdueAnimals.size());
    }

    /** Offspring born */
    public void sendOffspringBornNotification(Livestock mother, Livestock offspring) {
        if (!emailEnabled) return;

        Context ctx = new Context();
        ctx.setVariable("mother",    mother);
        ctx.setVariable("offspring", offspring);
        ctx.setVariable("birthDate", LocalDate.now().format(FMT));
        ctx.setVariable("today",     LocalDate.now().format(FMT));

        sendHtmlEmail("🌟 NEW OFFSPRING Born - " + offspring.getTagNumber(),
                "emails/email-offspring-born", ctx);
        log.info("✅ Offspring birth notification sent for: {}", offspring.getTagNumber());
    }

    /**
     * Sent whenever LivestockValuationService.recordValuation(...) appends a
     * new valuation entry — i.e. any time an animal's value changes, whether
     * that's the very first valuation at registration or a later revaluation.
     */
    public void sendValuationChangedNotification(Livestock animal,
                                                 BigDecimal oldValue,
                                                 BigDecimal newValue,
                                                 String method,
                                                 String notes) {
        if (!emailEnabled) {
            log.debug("⏩ Email disabled — skipping valuation-changed email for {}", animal.getTagNumber());
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("emoji", "💰📊");
            ctx.setVariable("title", "Animal Valuation Updated");
            ctx.setVariable("tagNumber", animal.getTagNumber());
            ctx.setVariable("category", animal.getLivestockCategory() != null
                    ? animal.getLivestockCategory().getName() : "N/A");
            ctx.setVariable("oldValue", oldValue != null ? oldValue.toPlainString() : "Not previously valued");
            ctx.setVariable("newValue", newValue.toPlainString());
            ctx.setVariable("method", method);
            ctx.setVariable("notes", (notes != null && !notes.isBlank()) ? notes : "—");
            ctx.setVariable("today", LocalDate.now().toString());

            String html = templateEngine.process("emails/email-valuation-changed", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(emailTo);
            helper.setSubject("💰 Valuation Updated — " + animal.getTagNumber());
            helper.setText(html, true);
            mailSender.send(message);

            log.info("✅ Valuation-changed email sent for {}", animal.getTagNumber());
        } catch (Exception e) {
            log.error("❌ Failed to send valuation-changed email for {}: {}",
                    animal.getTagNumber(), e.getMessage(), e);
        }
    }

    /**
     * Sent once, right after a new animal is successfully registered
     * (LivestockController.register(...)). Mirrors sendNewbornNotification
     * but is for ANY registration (purchase, donation, transfer, birth, etc.),
     * not specifically newborns.
     */
    public void sendAnimalRegisteredNotification(Livestock animal) {
        if (!emailEnabled) {
            log.debug("⏩ Email disabled — skipping registration email for {}", animal.getTagNumber());
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("emoji", "🐄📋");
            ctx.setVariable("title", "New Animal Registered");
            ctx.setVariable("tagNumber", animal.getTagNumber());
            ctx.setVariable("category", animal.getLivestockCategory() != null
                    ? animal.getLivestockCategory().getName() : "N/A");
            ctx.setVariable("gender", animal.getGender() != null ? animal.getGender() : "Unknown");
            ctx.setVariable("acquisitionMethod", animal.getAcquisitionMethod() != null
                    ? animal.getAcquisitionMethod() : "Not set");
            ctx.setVariable("currentValue", animal.getCurrentValue() != null
                    ? animal.getCurrentValue().toPlainString() + " RWF" : "Not yet valued");
            ctx.setVariable("today", LocalDate.now().toString());

            String html = templateEngine.process("emails/email-animal-registered", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(emailTo);
            helper.setSubject("🐄 New Animal Registered — " + animal.getTagNumber());
            helper.setText(html, true);
            mailSender.send(message);

            log.info("✅ Registration email sent for {}", animal.getTagNumber());
        } catch (Exception e) {
            log.error("❌ Failed to send registration email for {}: {}",
                    animal.getTagNumber(), e.getMessage(), e);
        }
    }

    /**
     * NEW — FAO STANDARD CHANGE NOTIFICATION.
     * Sent from LivestockService.update() whenever an edit actually changes
     * at least one tracked field. `changes` is keyed by human-readable field
     * label, with value = {oldValueDisplay, newValueDisplay}. Converts that
     * into a List<FieldChange> for clean template iteration (avoids relying
     * on Thymeleaf's #maps.entrySet, which has caused issues elsewhere in
     * this codebase).
     */
    public void sendAnimalUpdatedNotification(Livestock animal,
                                              Map<String, String[]> changes,
                                              String changedBy) {
        if (!emailEnabled) {
            log.debug("⏩ Email disabled — skipping update email for {}", animal.getTagNumber());
            return;
        }
        if (changes == null || changes.isEmpty()) {
            log.debug("⏩ No tracked field changes — skipping update email for {}", animal.getTagNumber());
            return;
        }
        try {
            List<FieldChange> fieldChanges = new ArrayList<>();
            for (Map.Entry<String, String[]> entry : changes.entrySet()) {
                String[] values = entry.getValue();
                String oldVal = values.length > 0 ? values[0] : null;
                String newVal = values.length > 1 ? values[1] : null;
                fieldChanges.add(new FieldChange(entry.getKey(), oldVal, newVal));
            }

            Context ctx = new Context();
            ctx.setVariable("emoji", "✏️📝");
            ctx.setVariable("title", "Animal Record Updated");
            ctx.setVariable("tagNumber", animal.getTagNumber());
            ctx.setVariable("category", animal.getLivestockCategory() != null
                    ? animal.getLivestockCategory().getName() : "N/A");
            ctx.setVariable("status", animal.getStatus() != null ? animal.getStatus() : "N/A");
            ctx.setVariable("changedBy", (changedBy != null && !changedBy.isBlank()) ? changedBy : "system");
            ctx.setVariable("changeCount", fieldChanges.size());
            ctx.setVariable("changes", fieldChanges);
            ctx.setVariable("today", LocalDate.now().format(FMT));

            String html = templateEngine.process("emails/email-animal-updated", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(emailTo);
            helper.setSubject("✏️ Animal Updated — " + animal.getTagNumber()
                    + " (" + fieldChanges.size() + " field" + (fieldChanges.size() == 1 ? "" : "s") + " changed)");
            helper.setText(html, true);
            mailSender.send(message);

            log.info("✅ Update-notification email sent for {} ({} field(s) changed)",
                    animal.getTagNumber(), fieldChanges.size());
        } catch (Exception e) {
            log.error("❌ Failed to send update-notification email for {}: {}",
                    animal.getTagNumber(), e.getMessage(), e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Build Thymeleaf context for the generic lifecycle-stage email template.
     */
    private Context buildStageContext(Livestock animal, String stageName,
                                      String emoji, String title,
                                      String message, String nextStage) {
        long ageDays   = getAgeInDays(animal);
        long ageMonths = ageDays / 30;

        Context ctx = new Context();
        ctx.setVariable("emoji",      emoji);
        ctx.setVariable("title",      title);
        ctx.setVariable("stageName",  stageName);
        ctx.setVariable("message",    message);
        ctx.setVariable("tagNumber",  animal.getTagNumber());
        ctx.setVariable("category",   animal.getLivestockCategory() != null ? animal.getLivestockCategory().getName() : "Unknown");
        ctx.setVariable("gender",     animal.getGender() != null ? animal.getGender() : "Unknown");
        ctx.setVariable("ageDays",    ageDays);
        ctx.setVariable("ageMonths",  ageMonths);
        ctx.setVariable("nextStage",  nextStage);
        ctx.setVariable("today",      LocalDate.now().format(FMT));
        return ctx;
    }

    /**
     * Process a Thymeleaf template and send as HTML email.
     */
    private void sendHtmlEmail(String subject, String templateName, Context ctx) {
        try {
            String htmlBody = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            // true = multipart, "UTF-8" = encoding
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(emailTo);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            mailSender.send(message);
            log.info("✅ Email sent  → subject: '{}', to: {}", subject, emailTo);

        } catch (MessagingException e) {
            log.error("❌ Failed to send email '{}': {}", subject, e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error sending email '{}': {}", subject, e.getMessage(), e);
        }
    }

    private long getAgeInDays(Livestock animal) {
        if (animal == null) return 0;
        LocalDate ref = animal.getDateReceived() != null ? animal.getDateReceived() : animal.getBirthDate();
        if (ref == null) return 0;
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(ref, LocalDate.now()));
    }

    private int getDaysUntil(LocalDate dueDate) {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }

    private int getDaysOverdue(LocalDate dueDate) {
        return Math.abs((int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now()));
    }

    /**
     * NEW — small display-only DTO for the "Animal Updated" email template.
     * Keeps template iteration simple (th:each over a List) instead of
     * relying on Thymeleaf map-entry iteration.
     */
    public static class FieldChange {
        private final String label;
        private final String oldValue;
        private final String newValue;

        public FieldChange(String label, String oldValue, String newValue) {
            this.label = label;
            this.oldValue = (oldValue == null || oldValue.isBlank()) ? "—" : oldValue;
            this.newValue = (newValue == null || newValue.isBlank()) ? "—" : newValue;
        }

        public String getLabel()    { return label; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
    }
}