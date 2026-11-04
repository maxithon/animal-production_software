package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.UsersRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class VeterinarianService {

    private static final Logger log = LoggerFactory.getLogger(VeterinarianService.class);

    private final VeterinarianRepository veterinarianRepository;
    private final UsersRepository usersRepository;
    private final VeterinarianEmailService veterinarianEmailService;

    /** Where uploaded vet photos land on disk. Served via /uploads/vets/** */
    private final Path photoDir = Path.of("uploads", "vets");

    public VeterinarianService(VeterinarianRepository veterinarianRepository,
                               UsersRepository usersRepository,
                               VeterinarianEmailService veterinarianEmailService) {
        this.veterinarianRepository = veterinarianRepository;
        this.usersRepository = usersRepository;
        this.veterinarianEmailService = veterinarianEmailService;
        log.info("✅ VeterinarianService initialized — emailService={}",
                veterinarianEmailService != null ? "OK" : "NULL ❌");
    }

    // ── Reads ──────────────────────────────────────────────────────────
    public List<Veterinarian> getAll() { return veterinarianRepository.findAll(); }
    public List<Veterinarian> getActive() { return veterinarianRepository.findByIsDeletedFalseAndIsActiveTrue(); }
    public Optional<Veterinarian> getById(UUID id) { return veterinarianRepository.findById(id); }

    public List<Veterinarian> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return veterinarianRepository.findAllActive();
        }
        return veterinarianRepository.searchActive(query.trim());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CREATE — with optional photo upload + REGISTERED email
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Veterinarian addNew(Veterinarian vet, MultipartFile photoFile) {
        log.info("▶️ addNew() STARTED for {}", vet.getFullName());

        // ★ Save the uploaded photo first (if any)
        if (photoFile != null && !photoFile.isEmpty()) {
            vet.setPhoto(storePhoto(photoFile));
            log.info("📷 addNew() — photo stored at {}", vet.getPhoto());
        }

        setAuditFields(vet);
        Veterinarian saved = veterinarianRepository.save(vet);
        log.info("💾 addNew() — saved id={}, name={}", saved.getId(), saved.getFullName());

        try {
            String username = resolveUsername();
            log.info("➡️ addNew() — calling sendVeterinarianRegistered");
            veterinarianEmailService.sendVeterinarianRegistered(saved, username);
            log.info("✅ addNew() — sendVeterinarianRegistered returned");
        } catch (Throwable t) {
            log.error("❌ addNew() — sendVeterinarianRegistered threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    /** Kept for backward compatibility with callers that don't pass a photo. */
    @Deprecated
    @Transactional
    public Veterinarian addNew(Veterinarian vet) {
        return addNew(vet, null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UPDATE — with optional photo replacement + UPDATED email
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public void update(UUID id, Veterinarian updated, MultipartFile photoFile) {
        log.info("▶️ update() STARTED for id={}", id);

        veterinarianRepository.findById(id).ifPresent(existing -> {
            Veterinarian snapshot = copyForDiff(existing);

            existing.setFirstName(updated.getFirstName());
            existing.setLastName(updated.getLastName());
            existing.setPhone(updated.getPhone());
            existing.setEmail(updated.getEmail());
            existing.setLicenseNumber(updated.getLicenseNumber());
            existing.setNationalId(updated.getNationalId());
            existing.setSpecialization(updated.getSpecialization());
            existing.setClinicName(updated.getClinicName());
            existing.setLocation(updated.getLocation());
            existing.setIsActive(updated.getIsActive());
            existing.setNotes(updated.getNotes());

            // ★ Replace photo only when a new file is uploaded
            if (photoFile != null && !photoFile.isEmpty()) {
                existing.setPhoto(storePhoto(photoFile));
                log.info("📷 update() — photo replaced for id={}", id);
            }

            Veterinarian saved = veterinarianRepository.save(existing);
            log.info("💾 update() — saved id={}", saved.getId());

            List<VeterinarianEmailService.ChangeRow> changes = diff(snapshot, saved);

            try {
                String username = resolveUsername();
                log.info("➡️ update() — calling sendVeterinarianUpdated with {} change(s)", changes.size());
                veterinarianEmailService.sendVeterinarianUpdated(saved, changes, username);
                log.info("✅ update() — sendVeterinarianUpdated returned");
            } catch (Throwable t) {
                log.error("❌ update() — sendVeterinarianUpdated threw: {}", t.getMessage(), t);
            }
        });
    }

    /** Kept for backward compatibility. */
    @Deprecated
    @Transactional
    public void update(UUID id, Veterinarian updated) {
        update(id, updated, null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DELETE — soft-delete + DELETED email
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public void delete(UUID id) {
        log.info("▶️ delete() STARTED for id={}", id);

        veterinarianRepository.findById(id).ifPresent(vet -> {
            try {
                String username = resolveUsername();
                log.info("➡️ delete() — calling sendVeterinarianDeleted for {}", vet.getFullName());
                veterinarianEmailService.sendVeterinarianDeleted(vet, username);
                log.info("✅ delete() — sendVeterinarianDeleted returned (email queued)");
            } catch (Throwable t) {
                log.error("❌ delete() — sendVeterinarianDeleted threw: {}", t.getMessage(), t);
            }

            vet.setIsDeleted(true);
            veterinarianRepository.save(vet);
            log.info("💾 delete() — soft-deleted id={}", id);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TOGGLE STATUS + STATUS_CHANGED email
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Veterinarian toggleStatus(UUID id) {
        log.info("▶️ toggleStatus() STARTED for id={}", id);

        Optional<Veterinarian> opt = veterinarianRepository.findById(id);
        if (opt.isEmpty()) return null;

        Veterinarian vet = opt.get();
        String oldStatus = Boolean.TRUE.equals(vet.getIsActive()) ? "ACTIVE" : "INACTIVE";
        boolean wasActive = Boolean.TRUE.equals(vet.getIsActive());
        String newStatus = wasActive ? "INACTIVE" : "ACTIVE";

        vet.setIsActive(!wasActive);
        Veterinarian saved = veterinarianRepository.saveAndFlush(vet);
        log.info("💾 toggleStatus() — {} → {}", oldStatus, newStatus);

        try {
            String username = resolveUsername();
            log.info("➡️ toggleStatus() — calling sendVeterinarianStatusChanged");
            veterinarianEmailService.sendVeterinarianStatusChanged(saved, oldStatus, newStatus, username);
            log.info("✅ toggleStatus() — sendVeterinarianStatusChanged returned");
        } catch (Throwable t) {
            log.error("❌ toggleStatus() — sendVeterinarianStatusChanged threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PHOTO STORAGE
    // ═══════════════════════════════════════════════════════════════════════
    private String storePhoto(MultipartFile file) {
        try {
            Files.createDirectories(photoDir);
            String original = StringUtils.cleanPath(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo");
            String extension = original.contains(".")
                    ? original.substring(original.lastIndexOf(".")) : "";
            String filename = UUID.randomUUID() + extension;
            Path target = photoDir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            // URL path relative to the app root; served by WebConfig
            return "/uploads/vets/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store veterinarian photo: " + e.getMessage(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private void setAuditFields(Veterinarian vet) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String username = auth.getName();
                usersRepository.findByEmail(username).ifPresent(vet::setCreatedBy);
            }
        } catch (Exception ignored) {}
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) return auth.getName();
        return "system";
    }

    private Veterinarian copyForDiff(Veterinarian src) {
        Veterinarian c = new Veterinarian();
        c.setId(src.getId());
        c.setFirstName(src.getFirstName());
        c.setLastName(src.getLastName());
        c.setPhone(src.getPhone());
        c.setEmail(src.getEmail());
        c.setLicenseNumber(src.getLicenseNumber());
        c.setNationalId(src.getNationalId());
        c.setSpecialization(src.getSpecialization());
        c.setClinicName(src.getClinicName());
        c.setLocation(src.getLocation());
        c.setIsActive(src.getIsActive());
        c.setNotes(src.getNotes());
        c.setPhoto(src.getPhoto());
        return c;
    }

    private List<VeterinarianEmailService.ChangeRow> diff(Veterinarian before, Veterinarian after) {
        List<VeterinarianEmailService.ChangeRow> changes = new ArrayList<>();
        if (before == null || after == null) return changes;

        addIfChanged(changes, "First Name",      before.getFirstName(),      after.getFirstName());
        addIfChanged(changes, "Last Name",       before.getLastName(),       after.getLastName());
        addIfChanged(changes, "Phone",           before.getPhone(),          after.getPhone());
        addIfChanged(changes, "Email",           before.getEmail(),          after.getEmail());
        addIfChanged(changes, "License Number",  before.getLicenseNumber(),  after.getLicenseNumber());
        addIfChanged(changes, "National ID",     before.getNationalId(),     after.getNationalId());
        addIfChanged(changes, "Specialization",  before.getSpecialization(), after.getSpecialization());
        addIfChanged(changes, "Clinic",          before.getClinicName(),     after.getClinicName());
        addIfChanged(changes, "Notes",           before.getNotes(),          after.getNotes());

        String oldStatus = Boolean.TRUE.equals(before.getIsActive()) ? "Active" : "Inactive";
        String newStatus = Boolean.TRUE.equals(after.getIsActive())  ? "Active" : "Inactive";
        if (!Objects.equals(oldStatus, newStatus)) {
            changes.add(new VeterinarianEmailService.ChangeRow("Status", oldStatus, newStatus));
        }

        String oldLoc = before.getLocation() != null ? before.getLocation().getName() : "—";
        String newLoc = after.getLocation()  != null ? after.getLocation().getName()  : "—";
        if (!Objects.equals(oldLoc, newLoc)) {
            changes.add(new VeterinarianEmailService.ChangeRow("Location", oldLoc, newLoc));
        }

        if (!Objects.equals(before.getPhoto(), after.getPhoto())) {
            changes.add(new VeterinarianEmailService.ChangeRow(
                    "Photo",
                    before.getPhoto() != null ? "old file" : "none",
                    after.getPhoto() != null ? "new file" : "removed"));
        }

        return changes;
    }

    private void addIfChanged(List<VeterinarianEmailService.ChangeRow> list,
                              String label, String oldV, String newV) {
        if (!Objects.equals(oldV, newV)) {
            list.add(new VeterinarianEmailService.ChangeRow(
                    label,
                    oldV != null && !oldV.isBlank() ? oldV : "—",
                    newV != null && !newV.isBlank() ? newV : "—"));
        }
    }
}