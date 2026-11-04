package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.repository.RepresentativeRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RepresentativeService {

    private static final Logger log = LoggerFactory.getLogger(RepresentativeService.class);

    private final RepresentativeRepository representativeRepository;
    private final LocationRepository locationRepository;
    private final UsersRepository usersRepository;
    private final RepresentativeEmailService representativeEmailService;
    private final BeneficiaryRepository beneficiaryRepository;

    public RepresentativeService(RepresentativeRepository representativeRepository,
                                 LocationRepository locationRepository,
                                 UsersRepository usersRepository,
                                 RepresentativeEmailService representativeEmailService,
                                 BeneficiaryRepository beneficiaryRepository) {
        this.representativeRepository = representativeRepository;
        this.locationRepository = locationRepository;
        this.usersRepository = usersRepository;
        this.representativeEmailService = representativeEmailService;
        this.beneficiaryRepository = beneficiaryRepository;
        log.info("✅ RepresentativeService initialized — emailService={}, beneficiaryRepo={}",
                representativeEmailService != null ? "OK" : "NULL ❌",
                beneficiaryRepository != null ? "OK" : "NULL ❌");
    }

    // ─── READ ──────────────────────────────────────────────────────────────
    public List<Representative> getAll() { return representativeRepository.findAll(); }
    public Optional<Representative> getById(UUID id) { return representativeRepository.findById(id); }
    public Optional<Representative> getByNid(String nid) { return representativeRepository.findByNid(nid); }
    public List<Representative> getByLocation(UUID locationId) { return representativeRepository.findByLocationId(locationId); }
    public List<Representative> getByStatus(String status) { return representativeRepository.findByStatus(status); }

    // ═══════════════════════════════════════════════════════════════════════
    //  DEPENDENCY INSPECTION
    // ═══════════════════════════════════════════════════════════════════════
    public long countAllBeneficiaries(UUID representativeId) {
        return beneficiaryRepository.countByRepresentativeId(representativeId);
    }

    public long countActiveBeneficiaries(UUID representativeId) {
        return beneficiaryRepository.countByRepresentativeIdAndStatus(
                representativeId, "ACTIVE");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Representative addNew(Representative representatives) {
        log.info("▶️ addNew() STARTED for NID={}", representatives.getNid());

        String locationIdString = representatives.getLocationIdValue();
        if (locationIdString != null && !locationIdString.isEmpty()) {
            UUID locationId = UUID.fromString(locationIdString);
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Location not found"));
            representatives.setLocation(location);
        }

        representatives.setCreatedDate(new Date());
        if (representatives.getStatus() == null || representatives.getStatus().isEmpty()) {
            representatives.setStatus("ACTIVE");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = resolveUsername(authentication);
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            Optional<Users> userOpt = usersRepository.findByEmail(email);
            userOpt.ifPresent(representatives::setCreatedBy);
        }

        Representative saved = representativeRepository.save(representatives);
        log.info("💾 addNew() — saved id={}, name={} {}", saved.getId(), saved.getFirstName(), saved.getLastName());

        try {
            log.info("➡️ addNew() — calling sendRepresentativeRegistered");
            representativeEmailService.sendRepresentativeRegistered(saved, username);
            log.info("✅ addNew() — sendRepresentativeRegistered returned");
        } catch (Throwable t) {
            log.error("❌ addNew() — sendRepresentativeRegistered threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UPDATE
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Representative update(UUID id, Representative updatedData) {
        log.info("▶️ update() STARTED for id={}", id);

        Optional<Representative> existingOpt = representativeRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        Representative existing = existingOpt.get();
        Representative snapshot = copyForDiff(existing);

        existing.setFirstName(updatedData.getFirstName());
        existing.setLastName(updatedData.getLastName());
        existing.setGender(updatedData.getGender());
        existing.setMaritialStatus(updatedData.getMaritialStatus());
        existing.setNid(updatedData.getNid());
        existing.setPhone(updatedData.getPhone());
        existing.setEmail(updatedData.getEmail());
        existing.setOccupation(updatedData.getOccupation());

        if (updatedData.getContractAgreement() != null && !updatedData.getContractAgreement().isBlank()) {
            existing.setContractAgreement(updatedData.getContractAgreement());
        }
        if (updatedData.getPhoto() != null && !updatedData.getPhoto().isBlank()) {
            existing.setPhoto(updatedData.getPhoto());
        }
        if (updatedData.getStatus() != null && !updatedData.getStatus().isEmpty()) {
            existing.setStatus(updatedData.getStatus());
        }

        String locationIdString = updatedData.getLocationIdValue();
        if (locationIdString != null && !locationIdString.isEmpty()) {
            UUID locationId = UUID.fromString(locationIdString);
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Location not found"));
            existing.setLocation(location);
        }

        Representative saved = representativeRepository.save(existing);
        log.info("💾 update() — saved id={}", saved.getId());

        List<RepresentativeEmailService.ChangeRow> changes = diff(snapshot, saved);

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ update() — calling sendRepresentativeUpdated with {} change(s)", changes.size());
            representativeEmailService.sendRepresentativeUpdated(saved, changes, username);
            log.info("✅ update() — sendRepresentativeUpdated returned");
        } catch (Throwable t) {
            log.error("❌ update() — sendRepresentativeUpdated threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DELETE — refuses when beneficiaries are assigned
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public void delete(UUID id) {
        log.info("▶️ delete() STARTED for id={}", id);

        Optional<Representative> existingOpt = representativeRepository.findById(id);
        if (existingOpt.isEmpty()) {
            log.warn("delete({}) — not found", id);
            throw new IllegalStateException("Representative not found.");
        }

        Representative existing = existingOpt.get();

        // ★ Guard — refuse when any beneficiary is assigned
        long dependents = countAllBeneficiaries(id);
        if (dependents > 0) {
            String msg = "Cannot delete " + existing.getFirstName() + " " + existing.getLastName()
                    + " — " + dependents + " beneficiary(ies) are still assigned. "
                    + "Please reassign or remove those beneficiaries first, "
                    + "or use Deactivate instead.";
            log.warn("❌ delete({}) BLOCKED: {}", id, msg);
            throw new IllegalStateException(msg);
        }

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ delete() — calling sendRepresentativeDeleted for {} {}",
                    existing.getFirstName(), existing.getLastName());
            representativeEmailService.sendRepresentativeDeleted(existing, username);
            log.info("✅ delete() — sendRepresentativeDeleted returned (email queued)");
        } catch (Throwable t) {
            log.error("❌ delete() — sendRepresentativeDeleted threw: {}", t.getMessage(), t);
        }

        representativeRepository.deleteById(id);
        log.info("💾 delete() — row removed from DB for id={}", id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TOGGLE STATUS — refuses deactivation while active beneficiaries exist
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Representative toggleStatus(UUID id) {
        log.info("▶️ toggleStatus() STARTED for id={}", id);

        Optional<Representative> existingOpt = representativeRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        Representative existing = existingOpt.get();
        String oldStatus = existing.getStatus();
        boolean wasActive = oldStatus != null && "ACTIVE".equalsIgnoreCase(oldStatus.trim());
        String newStatus = wasActive ? "INACTIVE" : "ACTIVE";

        // ★ Guard — refuse to deactivate while active beneficiaries are assigned
        if (wasActive) {
            long activeDependents = countActiveBeneficiaries(id);
            if (activeDependents > 0) {
                String msg = "Cannot deactivate " + existing.getFirstName() + " " + existing.getLastName()
                        + " — " + activeDependents + " active beneficiary(ies) are still assigned. "
                        + "Please reassign or deactivate those beneficiaries first.";
                log.warn("❌ toggleStatus({}) BLOCKED: {}", id, msg);
                throw new IllegalStateException(msg);
            }
        }

        existing.setStatus(newStatus);
        Representative saved = representativeRepository.saveAndFlush(existing);
        log.info("💾 toggleStatus() — {} → {}", oldStatus, newStatus);

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ toggleStatus() — calling sendRepresentativeStatusChanged");
            representativeEmailService.sendRepresentativeStatusChanged(saved, oldStatus, newStatus, username);
            log.info("✅ toggleStatus() — sendRepresentativeStatusChanged returned");
        } catch (Throwable t) {
            log.error("❌ toggleStatus() — sendRepresentativeStatusChanged threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    public List<Representative> search(String keyword) {
        return representativeRepository.findByFirstNameContainingOrLastNameContaining(keyword, keyword);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }

    private Representative copyForDiff(Representative src) {
        Representative c = new Representative();
        c.setId(src.getId());
        c.setFirstName(src.getFirstName());
        c.setLastName(src.getLastName());
        c.setGender(src.getGender());
        c.setMaritialStatus(src.getMaritialStatus());
        c.setNid(src.getNid());
        c.setPhone(src.getPhone());
        c.setEmail(src.getEmail());
        c.setOccupation(src.getOccupation());
        c.setPhoto(src.getPhoto());
        c.setContractAgreement(src.getContractAgreement());
        c.setStatus(src.getStatus());
        c.setLocation(src.getLocation());
        return c;
    }

    private List<RepresentativeEmailService.ChangeRow> diff(Representative before, Representative after) {
        List<RepresentativeEmailService.ChangeRow> changes = new ArrayList<>();
        if (before == null || after == null) return changes;

        addIfChanged(changes, "First Name",     before.getFirstName(),     after.getFirstName());
        addIfChanged(changes, "Last Name",      before.getLastName(),      after.getLastName());
        addIfChanged(changes, "Gender",         before.getGender(),        after.getGender());
        addIfChanged(changes, "Marital Status", before.getMaritialStatus(), after.getMaritialStatus());
        addIfChanged(changes, "National ID",    before.getNid(),           after.getNid());
        addIfChanged(changes, "Phone",          before.getPhone(),         after.getPhone());
        addIfChanged(changes, "Email",          before.getEmail(),         after.getEmail());
        addIfChanged(changes, "Occupation",     before.getOccupation(),    after.getOccupation());
        addIfChanged(changes, "Status",         before.getStatus(),        after.getStatus());

        if (!Objects.equals(before.getPhoto(), after.getPhoto())) {
            changes.add(new RepresentativeEmailService.ChangeRow(
                    "Photo",
                    before.getPhoto() != null ? "old file" : "none",
                    after.getPhoto() != null ? "new file" : "removed"));
        }
        if (!Objects.equals(before.getContractAgreement(), after.getContractAgreement())) {
            changes.add(new RepresentativeEmailService.ChangeRow(
                    "Contract PDF",
                    before.getContractAgreement() != null ? "old file" : "none",
                    after.getContractAgreement() != null ? "new file" : "removed"));
        }
        String oldLoc = before.getFullLocationPath();
        String newLoc = after.getFullLocationPath();
        if (!Objects.equals(oldLoc, newLoc)) {
            changes.add(new RepresentativeEmailService.ChangeRow("Location", oldLoc, newLoc));
        }
        return changes;
    }

    private void addIfChanged(List<RepresentativeEmailService.ChangeRow> list,
                              String label, String oldV, String newV) {
        if (!Objects.equals(oldV, newV)) {
            list.add(new RepresentativeEmailService.ChangeRow(
                    label,
                    oldV != null && !oldV.isBlank() ? oldV : "—",
                    newV != null && !newV.isBlank() ? newV : "—"));
        }
    }
}