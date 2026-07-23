package rw.animalproduct.animal.production.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import rw.animalproduct.animal.production.entity.LivestockSick;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Sends the "sick animal recorded" notification email.
 *
 * IMPORTANT — WHY @Async MATTERS HERE:
 * Without it, the HTTP request that saves the sick record blocks on the SMTP
 * round-trip to Gmail before it can redirect back to the list page — that is
 * almost certainly why the form feels slow to respond right now. With @Async
 * the record is saved and the browser gets its redirect immediately, while
 * the email goes out in the background a moment later.
 *
 * To enable it, add @EnableAsync to any one @Configuration class in the app
 * (or directly on your main @SpringBootApplication class):
 *
 *   @SpringBootApplication
 *   @EnableAsync
 *   public class AnimalProductionApplication { ... }
 *
 * If you already have a general-purpose EmailService/NotificationService in
 * the project (the fact that email-animal-registered.html works implies you
 * do), the cleanest move is to add sendSickRecordedEmail(...) as a method on
 * that existing class instead of introducing a second one — copy the method
 * body below into it and skip creating this file.
 */
@Service
public class SickAnimalNotificationService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.notification.email.from}")
    private String fromAddress;

    @Value("${app.notification.email.to}")
    private String toAddress;

    @Value("${app.notification.email.enabled:true}")
    private boolean enabled;

    public SickAnimalNotificationService(JavaMailSender mailSender,
                                          SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendSickRecordedEmail(LivestockSick sick) {
        if (!enabled || sick == null || sick.getLivestock() == null) {
            return;
        }

        try {
            Context ctx = new Context(LocaleContextHolder.getLocale());
            ctx.setVariable("title", "Sick Animal Recorded");
            ctx.setVariable("emoji", "🐄🩺");
            ctx.setVariable("tagNumber", sick.getLivestock().getTagNumber());
            ctx.setVariable("category", sick.getLivestock().getLivestockCategory() != null
                    ? sick.getLivestock().getLivestockCategory().getName() : "—");
            ctx.setVariable("reportedDate", sick.getReportedDate() != null
                    ? sick.getReportedDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "—");
            ctx.setVariable("status", sick.getStatus() != null ? sick.getStatus().name() : "—");
            ctx.setVariable("severity", sick.getSeverityLevel() != null
                    ? sick.getSeverityLevel().name() : "Not specified");
            ctx.setVariable("symptoms", sick.getSymptoms() != null && !sick.getSymptoms().isBlank()
                    ? sick.getSymptoms() : "Not recorded");
            ctx.setVariable("temperature", sick.getTemperature() != null
                    ? sick.getTemperature() + "°C" : "Not recorded");
            ctx.setVariable("vetName", sick.getVeterinarian() != null
                    ? sick.getVeterinarian().getFullName() : "Not assigned");
            ctx.setVariable("today", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            String html = templateEngine.process("email-sick-recorded", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject("🩺 Sick Animal Recorded — " + sick.getLivestock().getTagNumber());
            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            // An email hiccup must never roll back or fail the sick-record save.
            System.err.println("Failed to send sick-animal email: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error sending sick-animal email: " + e.getMessage());
        }
    }
}
