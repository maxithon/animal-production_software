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
import rw.animalproduct.animal.production.entity.LivestockSick;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SickAnimalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SickAnimalNotificationService.class);
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationEnabled;

    public SickAnimalNotificationService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        log.info("✅ SickAnimalNotificationService initialized");
    }

    @Async("notificationExecutor")
    public void sendSickRecordedEmail(LivestockSick sick) {
        if (sick == null) return;
        if (!notificationEnabled) {
            log.info("⛔ Notifications disabled — skipping sick email");
            return;
        }

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) {
            log.error("❌ No recipients configured — skipping sick email");
            return;
        }

        try {
            Livestock animal = sick.getLivestock();

            Context ctx = new Context();
            ctx.setVariable("title", "Sick Animal Recorded");
            ctx.setVariable("emoji", "🐄🩺");
            ctx.setVariable("subtitle", "Automatic animal health notification");

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

            ctx.setVariable("reportedDate", sick.getReportedDate() != null
                    ? sick.getReportedDate().format(DISPLAY_DATE_FMT) : "—");
            ctx.setVariable("status",       sick.getStatus() != null ? sick.getStatus().name() : "—");
            ctx.setVariable("severity",     sick.getSeverityLevel() != null
                    ? sick.getSeverityLevel().name() : "—");
            ctx.setVariable("temperature",  sick.getTemperature() != null
                    ? sick.getTemperature() + " °C" : "—");
            ctx.setVariable("symptoms",     sick.getSymptoms() != null && !sick.getSymptoms().isBlank()
                    ? sick.getSymptoms() : "Not recorded");
            ctx.setVariable("diagnosis",    sick.getDiagnosis() != null && !sick.getDiagnosis().isBlank()
                    ? sick.getDiagnosis() : "Pending");
            ctx.setVariable("vetName",      sick.getVeterinarian() != null
                    ? sick.getVeterinarian().getFullName() : "Not assigned");

            ctx.setVariable("today", LocalDate.now().format(DISPLAY_DATE_FMT));

            String html = templateEngine.process("email-sick-recorded", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("🐄🩺 Sick Animal Recorded — " +
                    (animal != null ? animal.getTagNumber() : "Unknown"));
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ Sick email sent to {} recipient(s)", recipients.size());

        } catch (Exception e) {
            log.error("❌ Failed to send sick email: {}", e.getMessage(), e);
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