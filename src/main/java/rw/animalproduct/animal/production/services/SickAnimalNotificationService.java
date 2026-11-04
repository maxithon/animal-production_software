package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.notification.LifecycleNotificationType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SickAnimalNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SickAnimalNotificationService.class);

    private final LifecycleEmailService emailService;

    public SickAnimalNotificationService(LifecycleEmailService emailService) {
        this.emailService = emailService;
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSickRecordedEmail(LivestockSick sick) {
        if (sick == null) return CompletableFuture.completedFuture(null);

        Livestock animal = sick.getLivestock();
        String tag = animal != null ? animal.getTagNumber() : "Unknown";

        logger.info("📧 QUEUED: Sick record created email for: {}", tag);

        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", tag));
        rows.add(new DetailRow("Category", animal != null && animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "—"));
        rows.add(new DetailRow("Reported Date",
                sick.getReportedDate() != null ? sick.getReportedDate().toString() : "—"));
        rows.add(new DetailRow("Status",
                sick.getStatus() != null ? sick.getStatus().name() : "—"));
        rows.add(new DetailRow("Severity",
                sick.getSeverityLevel() != null ? sick.getSeverityLevel().name() : "—"));
        rows.add(new DetailRow("Symptoms",
                sick.getSymptoms() != null ? sick.getSymptoms() : "—"));
        if (sick.getVeterinarian() != null) {
            rows.add(new DetailRow("Veterinarian",
                    sick.getVeterinarian().getFirstName() + " " + sick.getVeterinarian().getLastName()));
        }

        send(LifecycleNotificationType.STAGE_CHANGED,
                "Sick record created — " + tag,
                "A new sick record has been created for " + tag
                        + ". Please review the animal's condition.",
                rows);

        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSickRecordUpdatedEmail(LivestockSick sick) {
        if (sick == null) return CompletableFuture.completedFuture(null);

        Livestock animal = sick.getLivestock();
        String tag = animal != null ? animal.getTagNumber() : "Unknown";

        logger.info("📧 QUEUED: Sick record updated email for: {}", tag);

        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", tag));
        rows.add(new DetailRow("Category", animal != null && animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "—"));
        rows.add(new DetailRow("Reported Date",
                sick.getReportedDate() != null ? sick.getReportedDate().toString() : "—"));
        rows.add(new DetailRow("Status",
                sick.getStatus() != null ? sick.getStatus().name() : "—"));
        rows.add(new DetailRow("Severity",
                sick.getSeverityLevel() != null ? sick.getSeverityLevel().name() : "—"));
        rows.add(new DetailRow("Treatment Notes",
                sick.getTreatmentNotes() != null ? sick.getTreatmentNotes() : "—"));
        if (sick.getVeterinarian() != null) {
            rows.add(new DetailRow("Veterinarian",
                    sick.getVeterinarian().getFirstName() + " " + sick.getVeterinarian().getLastName()));
        }

        send(LifecycleNotificationType.STAGE_CHANGED,
                "Sick record updated — " + tag,
                "The sick record for " + tag + " has been updated.",
                rows);

        return CompletableFuture.completedFuture(null);
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSickRecordDeletedEmail(LivestockSick sick) {
        if (sick == null) return CompletableFuture.completedFuture(null);

        Livestock animal = sick.getLivestock();
        String tag = animal != null ? animal.getTagNumber() : "Unknown";

        logger.info("📧 QUEUED: Sick record deleted email for: {}", tag);

        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Tag Number", tag));
        rows.add(new DetailRow("Category", animal != null && animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "—"));
        rows.add(new DetailRow("Reported Date",
                sick.getReportedDate() != null ? sick.getReportedDate().toString() : "—"));
        rows.add(new DetailRow("Status at Deletion",
                sick.getStatus() != null ? sick.getStatus().name() : "—"));
        rows.add(new DetailRow("Severity at Deletion",
                sick.getSeverityLevel() != null ? sick.getSeverityLevel().name() : "—"));
        rows.add(new DetailRow("Deleted On", java.time.LocalDate.now().toString()));

        send(LifecycleNotificationType.ANIMAL_DELETED,
                "Sick record deleted — " + tag,
                "The sick record for " + tag + " has been permanently deleted.",
                rows);

        return CompletableFuture.completedFuture(null);
    }

    private void send(LifecycleNotificationType type,
                      String subject,
                      String message,
                      List<DetailRow> rows) {
        List<LifecycleEmailService.DetailRow> lifecycleRows = new ArrayList<>();
        for (DetailRow row : rows) {
            lifecycleRows.add(new LifecycleEmailService.DetailRow(row.label(), row.value()));
        }

        try {
            emailService.sendGeneric(type, subject, message, lifecycleRows);
            logger.info("✅ SUCCESS: Sent {} notification.", type);
        } catch (Exception e) {
            logger.error("❌ FAILED to send {} notification: {}", type, e.getMessage(), e);
        }
    }

    public record DetailRow(String label, String value) {}
}