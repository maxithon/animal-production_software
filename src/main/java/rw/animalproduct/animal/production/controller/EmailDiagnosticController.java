package rw.animalproduct.animal.production.controller;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.scheduler.LifecycleNotificationScheduler;
import rw.animalproduct.animal.production.services.LifecycleEmailService;
import rw.animalproduct.animal.production.notification.LivestockLifecycleService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic endpoints for testing and debugging the email notification system.
 *
 * Test in order:
 *   1. GET  /email-diagnostic/smtp-test        → verifies Gmail credentials work
 *   2. GET  /email-diagnostic/template-test    → verifies Thymeleaf can find templates
 *   3. POST /email-diagnostic/send-test-email  → sends a real email to the recipient
 *   4. POST /email-diagnostic/run-scheduler    → manually fires all scheduled jobs
 *   5. GET  /email-diagnostic/config           → shows current configuration
 *
 * ⚠️ This controller is NOT restricted by @Profile, so it is available in every
 * environment. If you deploy to production, either remove this file or add
 * @Profile("dev") to the class. See SchedulerTriggerController for the
 * dev-only alternative.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.1.0
 */
@RestController
@RequestMapping("/email-diagnostic")
public class EmailDiagnosticController {

    private static final Logger log = LoggerFactory.getLogger(EmailDiagnosticController.class);

    private final JavaMailSender                 mailSender;
    private final TemplateEngine                 templateEngine;
    private final LifecycleEmailService          emailService;
    private final LivestockLifecycleService      lifecycleService;
    private final LifecycleNotificationScheduler scheduler;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:${spring.mail.username:no-reply@animalproduction.rw}}")
    private String emailFrom;

    @Value("${app.notifications.enabled:true}")
    private boolean emailEnabled;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    public EmailDiagnosticController(JavaMailSender mailSender,
                                     TemplateEngine templateEngine,
                                     LifecycleEmailService emailService,
                                     LivestockLifecycleService lifecycleService,
                                     LifecycleNotificationScheduler scheduler) {
        this.mailSender       = mailSender;
        this.templateEngine   = templateEngine;
        this.emailService     = emailService;
        this.lifecycleService = lifecycleService;
        this.scheduler        = scheduler;
    }

    /** First configured recipient — used wherever a single "send to" address is needed */
    private String primaryRecipient() {
        List<String> recipients = recipients();
        return recipients.isEmpty() ? "(no recipients configured)" : recipients.get(0);
    }

    private List<String> recipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(recipientsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    // =========================================================================
    // STEP 1 — SMTP connection test (no template, no service)
    // =========================================================================

    @GetMapping("/smtp-test")
    public Map<String, Object> smtpTest() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step", "1 - SMTP connection test");
        result.put("smtpHost", smtpHost);
        result.put("smtpPort", smtpPort);
        result.put("smtpUsername", smtpUsername);

        String emailTo = primaryRecipient();
        result.put("sendingTo", emailTo);

        if (recipients().isEmpty()) {
            result.put("status", "FAILED");
            result.put("error", "No recipients configured");
            result.put("hint", "Set app.notifications.recipients in application.properties");
            return result;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(emailTo);
            helper.setSubject("[DIAGNOSTIC] SMTP Test - Livestock System");
            helper.setText(
                    "This is a plain-text SMTP test from your Livestock Management System.\n\n" +
                            "If you received this email, your Gmail App Password and SMTP settings are correct.\n\n" +
                            "Sent: " + LocalDate.now(),
                    false
            );
            mailSender.send(message);

            result.put("status", "SUCCESS");
            result.put("message", "Plain-text email sent successfully. Check your inbox at: " + emailTo);
            log.info("✅ SMTP test email sent successfully to: {}", emailTo);

        } catch (MessagingException e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            result.put("hint", "Check your App Password at: https://myaccount.google.com/apppasswords");
            log.error("❌ SMTP test failed: {}", e.getMessage(), e);
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("❌ SMTP test unexpected error: {}", e.getMessage(), e);
        }
        return result;
    }

    // =========================================================================
    // STEP 2 — Template rendering test (no email sent)
    // =========================================================================

    @GetMapping(value = "/template-test", produces = "text/html")
    public String templateTest() {
        try {
            Context ctx = new Context();
            ctx.setVariable("emoji",     "🐄🍼");
            ctx.setVariable("title",     "TEMPLATE TEST — Newborn Registered");
            ctx.setVariable("stageName", "NEWBORN");
            ctx.setVariable("message",   "This is a template rendering test. If you can see this, Thymeleaf found the template.");
            ctx.setVariable("tagNumber", "TEST-001");
            ctx.setVariable("category",  "Dairy Cow");
            ctx.setVariable("gender",    "FEMALE");
            ctx.setVariable("ageDays",   5L);
            ctx.setVariable("ageMonths", 0L);
            ctx.setVariable("nextStage", "Young Stage (at ~30 days)");
            ctx.setVariable("today",     LocalDate.now().toString());

            String html = templateEngine.process("emails/email-lifecycle-stage", ctx);
            log.info("✅ Template 'emails/email-lifecycle-stage' rendered successfully");
            return html;

        } catch (Exception e) {
            log.error("❌ Template rendering failed: {}", e.getMessage(), e);
            return "<html><body style='font-family:monospace;padding:20px;'>" +
                    "<h2 style='color:red'>❌ Template rendering failed</h2>" +
                    "<p><strong>Error:</strong> " + e.getClass().getSimpleName() + "</p>" +
                    "<p><strong>Message:</strong> " + e.getMessage() + "</p>" +
                    "<hr/><h3>Fix:</h3>" +
                    "<p>Make sure your email templates are in:</p>" +
                    "<code>src/main/resources/templates/emails/</code>" +
                    "<p>Required files:</p><ul>" +
                    "<li>email-lifecycle-stage.html</li>" +
                    "<li>email-breeding-started.html</li>" +
                    "<li>email-pregnancy-confirmed.html</li>" +
                    "<li>email-due-soon.html</li>" +
                    "<li>email-overdue.html</li>" +
                    "<li>email-offspring-born.html</li>" +
                    "</ul></body></html>";
        }
    }

    // =========================================================================
    // STEP 3 — Send a full HTML test email
    // =========================================================================

    @PostMapping("/send-test-email")
    public Map<String, Object> sendTestEmail() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step", "3 - Full HTML email test");

        Livestock fakeAnimal = new Livestock();
        fakeAnimal.setTagNumber("TEST-ANIMAL-001");

        try {
            emailService.sendNewbornNotification(fakeAnimal);
            result.put("status", "SUCCESS");
            result.put("message", "HTML test email sent to: " + primaryRecipient());
            result.put("note", "If SMTP test (step 1) worked but this fails, the issue is in the template path.");
            log.info("✅ HTML test email sent");

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("hint", "Check server logs for the full stack trace.");
            log.error("❌ HTML test email failed: {}", e.getMessage(), e);
        }
        return result;
    }

    // =========================================================================
    // STEP 4 — Manually run the scheduler jobs right now
    // =========================================================================

    /**
     * Forces all weekly reminder jobs to run immediately.
     * Uses the manual trigger methods in LifecycleNotificationScheduler.
     */
    @PostMapping("/run-scheduler")
    public Map<String, Object> runScheduler() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step", "4 - Manual scheduler execution");

        Map<String, Object> jobResults = new LinkedHashMap<>();

        // Job 1: Ready to breed (was previously part of notifyLifecycleStages)
        try {
            scheduler.notifyReadyToBreed();
            jobResults.put("notifyReadyToBreed", "executed");
        } catch (Exception e) {
            jobResults.put("notifyReadyToBreed", "ERROR: " + e.getMessage());
        }

        // Job 2: Pregnancy timelines (DUE_SOON + OVERDUE)
        try {
            scheduler.notifyPregnancyTimelines();
            jobResults.put("notifyPregnancyTimelines", "executed");
        } catch (Exception e) {
            jobResults.put("notifyPregnancyTimelines", "ERROR: " + e.getMessage());
        }

        // Job 3: Awaiting pregnancy confirmation
        try {
            scheduler.notifyAwaitingPregnancyConfirmation();
            jobResults.put("notifyAwaitingPregnancyConfirmation", "executed");
        } catch (Exception e) {
            jobResults.put("notifyAwaitingPregnancyConfirmation", "ERROR: " + e.getMessage());
        }

        result.put("status", "completed");
        result.put("jobs", jobResults);
        result.put("note", "Check server logs for ✅ or ❌ per job. " +
                "Jobs only send emails when matching animals exist in the DB.");
        return result;
    }

    // =========================================================================
    // STEP 4b — Force email for first available animal
    // =========================================================================

    @PostMapping("/force-email-now")
    public Map<String, Object> forceEmailNow() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step", "4b - Force email for first available animal");

        List<Livestock> allAnimals = lifecycleService.getActiveAnimalsByAgeRange(0, Integer.MAX_VALUE);
        if (allAnimals.isEmpty()) {
            result.put("status", "NO_ANIMALS");
            result.put("message", "No active animals found in the database. Add an animal first.");
            return result;
        }

        Livestock animal = allAnimals.get(0);
        try {
            String stage = lifecycleService.getCurrentStage(animal);
            emailService.sendNewbornNotification(animal);
            result.put("status", "SUCCESS");
            result.put("animal", animal.getTagNumber());
            result.put("stage", stage);
            result.put("emailSentTo", primaryRecipient());
            result.put("note", "Sent newborn notification for testing regardless of actual stage.");
            log.info("✅ Force email sent for animal: {} (stage: {})", animal.getTagNumber(), stage);
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("animal", animal.getTagNumber());
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("❌ Force email failed for animal {}: {}", animal.getTagNumber(), e.getMessage(), e);
        }
        return result;
    }

    // =========================================================================
    // STEP 5 — Configuration status
    // =========================================================================

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("smtpHost",      smtpHost);
        cfg.put("smtpPort",      smtpPort);
        cfg.put("smtpUsername",  smtpUsername);
        cfg.put("emailFrom",     emailFrom);
        cfg.put("emailRecipients", recipients());
        cfg.put("emailEnabled",  emailEnabled);
        cfg.put("schedulingEnabled", true);
        cfg.put("templateBasePath", "classpath:/templates/emails/");
        cfg.put("templates", List.of(
                "emails/email-lifecycle-stage.html",
                "emails/email-breeding-started.html",
                "emails/email-pregnancy-confirmed.html",
                "emails/email-due-soon.html",
                "emails/email-overdue.html",
                "emails/email-offspring-born.html"
        ));
        cfg.put("scheduledJobs", Map.of(
                "checkReadyToBreed",                  "Weekly Monday 08:00 (Africa/Kigali) — females that reached breeding age",
                "checkPregnancyTimelines",            "Weekly Monday 08:00 (Africa/Kigali) — DUE_SOON and OVERDUE pregnancies",
                "checkAwaitingPregnancyConfirmation", "Weekly Monday 08:00 (Africa/Kigali) — bred ≥21 days ago, awaiting confirmation"
        ));
        return cfg;
    }
}