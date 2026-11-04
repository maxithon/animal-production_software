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
import rw.animalproduct.animal.production.entity.LivestockAbortion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sends an email notification whenever a new abortion is recorded.
 * Uses the shared "notificationExecutor" (same one that works for breeding emails).
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.0.0
 */
@Service
public class AbortionEmailService {

    private static final Logger log = LoggerFactory.getLogger(AbortionEmailService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationEnabled;

    public AbortionEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        log.info("✅ AbortionEmailService initialized");
    }

    @Async("notificationExecutor")
    public void sendAbortionRecordedNotification(LivestockAbortion abortion) {
        if (abortion == null) {
            log.warn("⚠️ Abortion is null — skipping email");
            return;
        }
        if (!notificationEnabled) {
            log.info("⛔ Notifications disabled — skipping abortion email");
            return;
        }

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) {
            log.error("❌ No recipients configured — skipping abortion email");
            return;
        }

        try {
            Livestock animal = abortion.getLivestock();

            Context ctx = new Context();
            ctx.setVariable("title",    "Abortion Recorded");
            ctx.setVariable("emoji",    "🐄💔");
            ctx.setVariable("subtitle", "Automatic livestock reproduction notification");

            ctx.setVariable("tagNumber",    animal != null ? animal.getTagNumber() : "N/A");
            ctx.setVariable("category",     (animal != null && animal.getLivestockCategory() != null)
                    ? animal.getLivestockCategory().getName() : "—");
            ctx.setVariable("gender",       animal != null ? animal.getGender() : "—");
            ctx.setVariable("healthStatus", animal != null ? animal.healthStatusLabel() : "—");

            if (animal != null && animal.getBeneficiary() != null) {
                ctx.setVariable("beneficiaryName",  animal.getBeneficiary().getFullName());
                ctx.setVariable("beneficiaryPhone", animal.getBeneficiary().getPhoneNumber());
            } else {
                ctx.setVariable("beneficiaryName",  "Not assigned");
                ctx.setVariable("beneficiaryPhone", "—");
            }

            ctx.setVariable("locationPath", buildLocationPath(animal));

            ctx.setVariable("abortionDate",     abortion.getAbortionDate() != null
                    ? abortion.getAbortionDate().format(DISPLAY_DATE_FMT) : "—");
            ctx.setVariable("pregnancyNumber",  abortion.getPregnancyNumber() != null
                    ? String.valueOf(abortion.getPregnancyNumber()) : "—");
            ctx.setVariable("expectedBirth",    abortion.getExpectedBirthDate() != null
                    ? abortion.getExpectedBirthDate().format(DISPLAY_DATE_FMT) : "—");
            ctx.setVariable("stageOfPregnancy", abortion.getStageOfPregnancy() != null
                    ? abortion.getStageOfPregnancy() : "—");
            ctx.setVariable("reason",           abortion.getAbortionReason() != null
                    && !abortion.getAbortionReason().isBlank()
                    ? abortion.getAbortionReason() : "Not specified");

            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            String html = templateEngine.process("email-abortion-recorded", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🐄💔 Abortion Recorded — " +
                    (animal != null ? animal.getTagNumber() : "Unknown"));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Abortion email sent to {} recipient(s) for: {}",
                    recipients.size(),
                    animal != null ? animal.getTagNumber() : "unknown");

        } catch (Exception e) {
            log.error("❌ Failed to send abortion email: {}", e.getMessage(), e);
        }
    }

    private String buildLocationPath(Livestock animal) {
        if (animal == null || animal.getLocation() == null) return "—";
        List<String> parts = new ArrayList<>();
        rw.animalproduct.animal.production.entity.Location loc = animal.getLocation();
        while (loc != null) {
            parts.add(loc.getName());
            loc = loc.getParent();
        }
        Collections.reverse(parts);
        return String.join(" › ", parts);
    }

    private List<String> getRecipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(recipientsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}