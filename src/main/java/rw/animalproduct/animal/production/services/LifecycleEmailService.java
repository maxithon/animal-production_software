package rw.animalproduct.animal.production.services;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
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
import rw.animalproduct.animal.production.entity.LivestockValuation;
import rw.animalproduct.animal.production.notification.LifecycleNotificationType;
import rw.animalproduct.animal.production.patches.AsyncConfig;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LifecycleEmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // ✅ FALLBACK: If properties are missing, it defaults to your working email
    @Value("${livestock.notifications.to:samuelhabanabakize55@gmail.com}")
    private String defaultRecipient;

    @Value("${livestock.notifications.from:samuelhabanabakize55@gmail.com}")
    private String fromAddress;

    @Value("${livestock.notifications.dry-run:false}")
    private boolean dryRun;

    public LifecycleEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        log.info("✅ LifecycleEmailService Initialized. Sending TO: {}", defaultRecipient);
    }

    public record DetailRow(String label, String value) {}

    // ════════════════════════════════════════════════════════════════════
    // GENERIC PIPELINE
    // ════════════════════════════════════════════════════════════════════

    public void sendGeneric(LifecycleNotificationType type, String subject, String message, List<DetailRow> rows) {
        try {
            if (type == null) type = LifecycleNotificationType.STAGE_CHANGED;

            Map<String, Object> m = new HashMap<>();
            m.put("title",     type.getLabel());
            m.put("emoji",     type.getEmoji());
            m.put("message",   message != null ? message : "");
            m.put("tagNumber", extractRow(rows, "Tag Number", "—"));
            m.put("stageName", extractRow(rows, "Status", extractRow(rows, "Stage", "—")));
            m.put("category",  extractRow(rows, "Category", "—"));
            m.put("gender",    extractRow(rows, "Gender", "—"));
            m.put("ageDays",   extractRow(rows, "Age", extractRow(rows, "Age (days)", "—")));
            m.put("ageMonths", extractRow(rows, "Age", extractRow(rows, "Age (months)", "—")));
            m.put("nextStage", extractRow(rows, "Next Stage", "—"));
            m.put("today",     LocalDate.now().format(DATE_FMT));
            m.put("rows",      rows != null ? rows : List.of());

            sendHtmlEmail(subject, "email-lifecycle-stage", m);
        } catch (Exception e) {
            log.error("❌ sendGeneric failed for type={} subject='{}'", type, subject, e);
        }
    }

    private String extractRow(List<DetailRow> rows, String label, String fallback) {
        if (rows == null) return fallback;
        for (DetailRow r : rows) {
            if (r != null && label.equalsIgnoreCase(r.label()) && r.value() != null && !r.value().isBlank()) {
                return r.value();
            }
        }
        return fallback;
    }

    // ════════════════════════════════════════════════════════════════════
    // LIVESTOCK — REGISTERED / UPDATED / DELETED
    // ════════════════════════════════════════════════════════════════════

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendAnimalRegisteredNotification(String tagNumber, String category, String gender, String age, String status, String health, String beneficiaryName, String beneficiaryPhone, String fullLocation) {
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Tag Number", tagNumber != null ? tagNumber : "—"));
            rows.add(new DetailRow("Category", category != null ? category : "—"));
            rows.add(new DetailRow("Gender", gender != null ? gender : "—"));
            rows.add(new DetailRow("Age", age != null ? age : "—"));
            rows.add(new DetailRow("Status", status != null ? status : "ACTIVE"));
            rows.add(new DetailRow("Health Status", health != null ? health : "—"));
            rows.add(new DetailRow("Beneficiary", beneficiaryName != null ? beneficiaryName : "—"));
            rows.add(new DetailRow("Phone", beneficiaryPhone != null ? beneficiaryPhone : "—"));
            rows.add(new DetailRow("Location", fullLocation != null ? fullLocation : "—"));

            sendGeneric(LifecycleNotificationType.ANIMAL_REGISTERED, "[Livestock] Animal registered — " + tagNumber, "A new animal has been registered: " + tagNumber + ".", rows);
        } catch (Exception e) { log.error("❌ sendAnimalRegisteredNotification failed", e); }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendAnimalUpdatedNotification(String tagNumber, String category, String status, String changedSummary, String gender, String age, String health, String beneficiaryName, String beneficiaryPhone, String fullLocation) {
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Tag Number", tagNumber != null ? tagNumber : "—"));
            rows.add(new DetailRow("Category", category != null ? category : "—"));
            rows.add(new DetailRow("Status", status != null ? status : "—"));
            rows.add(new DetailRow("Change Summary", changedSummary != null ? changedSummary : "Fields updated."));
            rows.add(new DetailRow("Gender", gender != null ? gender : "—"));
            rows.add(new DetailRow("Age", age != null ? age : "—"));
            rows.add(new DetailRow("Health Status", health != null ? health : "—"));
            rows.add(new DetailRow("Beneficiary", beneficiaryName != null ? beneficiaryName : "—"));
            rows.add(new DetailRow("Phone", beneficiaryPhone != null ? beneficiaryPhone : "—"));
            rows.add(new DetailRow("Location", fullLocation != null ? fullLocation : "—"));

            sendGeneric(LifecycleNotificationType.STAGE_CHANGED, "[Livestock] Animal updated — " + tagNumber, "Animal " + tagNumber + " was updated.", rows);
        } catch (Exception e) { log.error("❌ sendAnimalUpdatedNotification failed", e); }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendAnimalDeletedNotification(String tagNumber, String category, String status, String reason, String gender, String age, String beneficiaryName, String beneficiaryPhone, String fullLocation) {
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Tag Number", tagNumber != null ? tagNumber : "—"));
            rows.add(new DetailRow("Category", category != null ? category : "—"));
            rows.add(new DetailRow("Status", status != null ? status : "—"));
            rows.add(new DetailRow("Reason", reason != null && !reason.isBlank() ? reason : "—"));
            rows.add(new DetailRow("Gender", gender != null ? gender : "—"));
            rows.add(new DetailRow("Age", age != null ? age : "—"));
            rows.add(new DetailRow("Beneficiary", beneficiaryName != null ? beneficiaryName : "—"));
            rows.add(new DetailRow("Phone", beneficiaryPhone != null ? beneficiaryPhone : "—"));
            rows.add(new DetailRow("Location", fullLocation != null ? fullLocation : "—"));

            sendGeneric(LifecycleNotificationType.ANIMAL_DELETED, "[Livestock] Animal deleted — " + tagNumber, "Animal " + tagNumber + " has been deleted.", rows);
        } catch (Exception e) { log.error("❌ sendAnimalDeletedNotification failed", e); }
    }

    // ════════════════════════════════════════════════════════════════════
    // PREGNANCY / BREEDING
    // ════════════════════════════════════════════════════════════════════

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendBreedingStartedNotification(LivestockBreeding breeding) {
        if (breeding == null || breeding.getLivestock() == null) return;
        try {
            String tag = breeding.getLivestock().getTagNumber();
            String method = breeding.getBreedingMethod() != null ? breeding.getBreedingMethod() : "—";
            String date = breeding.getBreedingDate() != null ? breeding.getBreedingDate().toString() : "—";

            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Tag Number", tag));
            rows.add(new DetailRow("Method", method));
            rows.add(new DetailRow("Date", date));

            sendGeneric(LifecycleNotificationType.BREEDING_RECORDED, "[Livestock] Breeding recorded — " + tag, "A new breeding record was created for " + tag + ".", rows);
        } catch (Exception e) { log.error("❌ sendBreedingStartedNotification failed", e); }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendBreedingUpdatedNotification(LivestockBreeding breeding, String changedSummary) {
        if (breeding == null || breeding.getLivestock() == null) return;
        String tag = breeding.getLivestock().getTagNumber();
        sendGeneric(LifecycleNotificationType.STAGE_CHANGED, "[Livestock] Breeding updated — " + tag, "Breeding updated: " + changedSummary, List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendBreedingFailedNotification(LivestockBreeding breeding) {
        if (breeding == null || breeding.getLivestock() == null) return;
        String tag = breeding.getLivestock().getTagNumber();
        sendGeneric(LifecycleNotificationType.BREEDING_FAILED, "[Livestock] Breeding failed — " + tag, "Breeding failed for " + tag + ".", List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendBreedingDeletedNotification(LivestockBreeding breeding) {
        if (breeding == null || breeding.getLivestock() == null) return;
        String tag = breeding.getLivestock().getTagNumber();
        sendGeneric(LifecycleNotificationType.ANIMAL_DELETED, "[Livestock] Breeding deleted — " + tag, "Breeding deleted.", List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendPregnancyTimelineNotification(LifecycleNotificationType type, LivestockBreeding breeding, int days) {
        if (breeding == null || breeding.getLivestock() == null) return;
        try {
            String tag = breeding.getLivestock().getTagNumber();
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Tag Number", tag));
            rows.add(new DetailRow("Breeding Date", breeding.getBreedingDate() != null ? breeding.getBreedingDate().toString() : "—"));
            rows.add(new DetailRow("Expected Due Date", breeding.getExpectedDueDate() != null ? breeding.getExpectedDueDate().toString() : "—"));
            rows.add(new DetailRow("Days", String.valueOf(days)));

            String subject = "[Livestock] Pregnancy Timeline — " + tag;
            String message = "Pregnancy timeline update for " + tag + " (" + days + " days).";

            if (type == LifecycleNotificationType.PREGNANCY_CONFIRMED) {
                subject = "[Livestock] Awaiting Pregnancy Confirmation — " + tag;
                message = "Please confirm pregnancy status for " + tag + " (" + Math.abs(days) + " days since breeding).";
            } else if (type == LifecycleNotificationType.UPCOMING_BIRTH) {
                subject = "[Livestock] Due Soon — " + tag;
                message = tag + " is due in " + days + " days.";
            }

            sendGeneric(type, subject, message, rows);
        } catch (Exception e) { log.error("❌ sendPregnancyTimelineNotification failed", e); }
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendPregnancyConfirmedNotification(LivestockBreeding breeding) {
        if (breeding == null || breeding.getLivestock() == null) return;
        String tag = breeding.getLivestock().getTagNumber();
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", tag));
        rows.add(new DetailRow("Breeding Date", breeding.getBreedingDate() != null ? breeding.getBreedingDate().toString() : "—"));
        rows.add(new DetailRow("Expected Due Date", breeding.getExpectedDueDate() != null ? breeding.getExpectedDueDate().toString() : "—"));

        sendGeneric(LifecycleNotificationType.PREGNANCY_CONFIRMED, "[Livestock] Pregnancy confirmed — " + tag, "Pregnancy has been confirmed for " + tag + ".", rows);
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendOverduePregnancyNotification(LivestockBreeding breeding, int daysOverdue, int tier) {
        if (breeding == null || breeding.getLivestock() == null) return;
        String tag = breeding.getLivestock().getTagNumber();
        sendGeneric(LifecycleNotificationType.OVERDUE_PREGNANCY, "[Livestock] Overdue pregnancy — " + tag, "Pregnancy overdue by " + daysOverdue + " days.",
                List.of(new DetailRow("Tag Number", tag), new DetailRow("Days Overdue", String.valueOf(daysOverdue))));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendDueSoonNotification(List<Livestock> animals, int withinDays) {
        if (animals == null || animals.isEmpty()) return;
        sendGeneric(LifecycleNotificationType.UPCOMING_BIRTH, "[Livestock] " + animals.size() + " animals due", "Animals due within " + withinDays + " days.",
                List.of(new DetailRow("Count", String.valueOf(animals.size()))));
    }

    // ════════════════════════════════════════════════════════════════════
    // BIRTHS & DEATHS & BULK IMPORT
    // ════════════════════════════════════════════════════════════════════

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendBirthUpdatedNotification(LivestockBirth b, String changedSummary) {
        if (b == null || b.getLivestock() == null) return;
        String tag = b.getLivestock().getTagNumber();
        sendGeneric(LifecycleNotificationType.STAGE_CHANGED, "[Livestock] Birth updated — " + tag, "Birth updated: " + changedSummary, List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendBirthDeletedNotification(LivestockBirth b) {
        if (b == null || b.getLivestock() == null) return;
        String tag = b.getLivestock().getTagNumber();
        sendGeneric(LifecycleNotificationType.ANIMAL_DELETED, "[Livestock] Birth deleted — " + tag, "Birth deleted.", List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendDeathNotification(LivestockDeath death) {
        if (death == null) return;
        Livestock animal = death.getLivestock();
        String tag = animal != null ? animal.getTagNumber() : "—";
        sendGeneric(LifecycleNotificationType.ANIMAL_DEATH, "[Livestock] Death recorded — " + tag, "A death record has been recorded for " + tag + ".",
                List.of(new DetailRow("Tag Number", tag), new DetailRow("Cause", death.getCauseOfDeath() != null ? death.getCauseOfDeath() : "—")));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendDeathNotification(String tagNumber, String category, String gender, String deathDate, String causeOfDeath, String notes, Long ageDays, Long ageMonths) {
        sendGeneric(LifecycleNotificationType.ANIMAL_DEATH, "[Livestock] Death recorded — " + tagNumber, "A death record has been recorded for " + tagNumber + ".",
                List.of(new DetailRow("Tag Number", tagNumber), new DetailRow("Cause", causeOfDeath)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendDeathChangeNotification(String action, String tagNumber, String category, String deathDate, String causeOfDeath, String previousCause, String notes, String performedBy) {
        String act = action != null ? action.toUpperCase() : "UPDATED";
        LifecycleNotificationType type = act.equals("CREATED") ? LifecycleNotificationType.ANIMAL_DEATH : act.equals("DELETED") ? LifecycleNotificationType.ANIMAL_DELETED : LifecycleNotificationType.STAGE_CHANGED;
        String verb = act.equals("CREATED") ? "recorded" : act.equals("DELETED") ? "deleted" : "updated";
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", tagNumber));
        rows.add(new DetailRow("Action", act));
        rows.add(new DetailRow("Cause", causeOfDeath));
        rows.add(new DetailRow("Performed By", performedBy));
        sendGeneric(type, "[Livestock] Death " + verb + " — " + tagNumber, "Death record for " + tagNumber + " was " + verb + ".", rows);
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendBulkImportCompletedNotification(int imported, int totalRows, int okRows, int warningRows, int errorRows, String fileName, String uploadedBy, List<String> createdTags) {
        try {
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("File Name", fileName != null ? fileName : "—"));
            rows.add(new DetailRow("Uploaded By", uploadedBy != null ? uploadedBy : "system"));
            rows.add(new DetailRow("Total Rows", String.valueOf(totalRows)));
            rows.add(new DetailRow("OK Rows", String.valueOf(okRows)));
            rows.add(new DetailRow("Warning Rows", String.valueOf(warningRows)));
            rows.add(new DetailRow("Error Rows", String.valueOf(errorRows)));
            rows.add(new DetailRow("Imported", String.valueOf(imported)));
            rows.add(new DetailRow("Created Tags", String.valueOf(createdTags != null ? createdTags.size() : 0)));

            sendGeneric(LifecycleNotificationType.BULK_IMPORT_COMPLETED, "[Livestock] Bulk import completed — " + (fileName != null ? fileName : "upload"), "Bulk import finished.", rows);
        } catch (Exception e) { log.error("❌ sendBulkImportCompletedNotification failed", e); }
    }

    // ════════════════════════════════════════════════════════════════════
    // STAGE TRANSITIONS
    // ════════════════════════════════════════════════════════════════════

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendNewbornNotification(Livestock animal) {
        if (animal == null) return;
        String tag = animal.getTagNumber();
        sendGeneric(LifecycleNotificationType.NEWBORN, "[Livestock] Newborn registered — " + tag, "A newborn animal has been registered: " + tag + ".",
                List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendNewbornStageNotification(Livestock animal) { sendNewbornNotification(animal); }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendYoungStageNotification(Livestock animal) {
        if (animal == null) return;
        String tag = animal.getTagNumber();
        sendGeneric(LifecycleNotificationType.YOUNG_ANIMAL, "[Livestock] Young animal — " + tag, tag + " has entered the Young Animal stage.",
                List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendPreBreedingNotification(Livestock animal) {
        if (animal == null) return;
        String tag = animal.getTagNumber();
        sendGeneric(LifecycleNotificationType.PRE_BREEDING, "[Livestock] Pre-breeding — " + tag, tag + " is approaching breeding age.",
                List.of(new DetailRow("Tag Number", tag)));
    }

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendReadyToBreedNotification(Livestock female) {
        if (female == null) return;
        String tag = female.getTagNumber();
        String category = female.getLivestockCategory() != null ? female.getLivestockCategory().getName() : "—";
        sendGeneric(LifecycleNotificationType.READY_TO_BREED, "[Livestock] Ready to breed — " + tag, tag + " is ready to breed.",
                List.of(new DetailRow("Tag Number", tag), new DetailRow("Category", category)));
    }

    // ════════════════════════════════════════════════════════════════════
    // VALUATION
    // ════════════════════════════════════════════════════════════════════

    @Async(AsyncConfig.EMAIL_EXECUTOR)
    public void sendValuationChangedNotification(LivestockValuation valuation, Double previousValue) {
        if (valuation == null || valuation.getLivestock() == null) return;
        try {
            String tag = valuation.getLivestock().getTagNumber();
            List<DetailRow> rows = new ArrayList<>();
            rows.add(new DetailRow("Tag Number", tag));
            rows.add(new DetailRow("New Value", String.valueOf(valuation.getEstimatedValue())));
            rows.add(new DetailRow("Previous Value", previousValue != null ? String.valueOf(previousValue) : "—"));
            rows.add(new DetailRow("Valuation Method", valuation.getValuationMethod() != null ? valuation.getValuationMethod() : "—"));
            rows.add(new DetailRow("Notes", valuation.getNotes() != null ? valuation.getNotes() : "—"));

            sendGeneric(LifecycleNotificationType.STAGE_CHANGED, "[Livestock] Valuation — " + tag, "A new valuation has been recorded for " + tag + ".", rows);
        } catch (Exception e) { log.error("❌ sendValuationChangedNotification failed", e); }
    }

    // ════════════════════════════════════════════════════════════════════
    // INTERNAL HELPER
    // ════════════════════════════════════════════════════════════════════

    private void sendHtmlEmail(String subject, String templateName, Map<String, Object> model) {
        log.info("📧 PREPARING SEND: {}", subject);
        try {
            if (defaultRecipient == null || defaultRecipient.isBlank()) {
                log.error("❌ FATAL: No recipient configured! Check app.notifications.recipients");
                return;
            }

            String html = "";
            try {
                Context ctx = new Context();
                ctx.setVariables(model);
                html = templateEngine.process("emails/" + templateName, ctx);
            } catch (Exception e) {
                log.warn("⚠️ Template processing failed for '{}': {}. Falling back to plain text.", templateName, e.getMessage());
                StringBuilder fallback = new StringBuilder();
                fallback.append("<h3>").append(subject).append("</h3>");
                fallback.append("<p>").append(model.getOrDefault("message", "")).append("</p>");
                fallback.append("<ul>");
                if (model.containsKey("rows")) {
                    List<DetailRow> rows = (List<DetailRow>) model.get("rows");
                    for (DetailRow row : rows) {
                        fallback.append("<li><b>").append(row.label()).append(":</b> ").append(row.value()).append("</li>");
                    }
                }
                fallback.append("</ul>");
                html = fallback.toString();
            }

            if (dryRun) {
                log.info("[DRY-RUN] Email '{}' would be sent to {}", subject, defaultRecipient);
                return;
            }

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(defaultRecipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);

            log.info("✅ SENT successfully: '{}' → {}", subject, defaultRecipient);
        } catch (Exception e) {
            log.error("❌ SMTP FAILED for '{}': {}", subject, e.getMessage(), e);
        }
    }
}