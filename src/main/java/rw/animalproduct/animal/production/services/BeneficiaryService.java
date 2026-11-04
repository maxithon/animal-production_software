package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.repository.RepresentativeRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class BeneficiaryService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);

    private final BeneficiaryRepository beneficiaryRepository;
    private final RepresentativeRepository representativeRepository;
    private final LocationRepository locationRepository;
    private final UsersRepository usersRepository;
    private final BeneficiaryEmailService beneficiaryEmailService;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository,
                              RepresentativeRepository representativeRepository,
                              LocationRepository locationRepository,
                              UsersRepository usersRepository,
                              BeneficiaryEmailService beneficiaryEmailService) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.representativeRepository = representativeRepository;
        this.locationRepository = locationRepository;
        this.usersRepository = usersRepository;
        this.beneficiaryEmailService = beneficiaryEmailService;
        log.info("✅ BeneficiaryService initialized — emailService={}",
                beneficiaryEmailService != null ? "OK" : "NULL ❌");
    }

    // ─── READ ──────────────────────────────────────────────────────────────
    public List<Beneficiary> getAll() { return beneficiaryRepository.findAll(); }
    public Optional<Beneficiary> getById(UUID id) { return beneficiaryRepository.findById(id); }
    public Optional<Beneficiary> getByNid(String nid) { return beneficiaryRepository.findByNid(nid); }
    public List<Beneficiary> getByUhagarariye(UUID repId) { return beneficiaryRepository.findByRepresentativeId(repId); }
    public Page<Beneficiary> getByUhagarariyePaginated(UUID repId, Pageable pageable) {
        return beneficiaryRepository.findByRepresentativeId(repId, pageable);
    }
    public List<Beneficiary> getByLocation(UUID locationId) { return beneficiaryRepository.findByLocationId(locationId); }

    public Map<UUID, Long> getBeneficiaryCountsByRepresentative() {
        List<Object[]> results = beneficiaryRepository.countByEachSupervisor();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : results) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    // ★ NEW — used by representatives-list.html to show a warning when
    // a representative still has ACTIVE beneficiaries, blocking deactivation.
    public Map<UUID, Long> getActiveBeneficiaryCountsByRepresentative() {
        List<Object[]> results = beneficiaryRepository.countActiveByEachRepresentative();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : results) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Beneficiary addNew(Beneficiary b) {
        log.info("▶️ addNew() STARTED for NID={}", b.getNid());

        String repIdStr = b.getRepresentativeIdValue();
        if (repIdStr != null && !repIdStr.isEmpty()) {
            UUID repId = UUID.fromString(repIdStr);
            Representative rep = representativeRepository.findById(repId)
                    .orElseThrow(() -> new RuntimeException("Representative not found"));
            b.setRepresentative(rep);
        }

        if (b.getLocation() == null && b.getRepresentative() != null) {
            Location repLoc = b.getRepresentative().getLocation();
            if (repLoc != null) b.setLocation(repLoc);
        }

        b.setCreatedDate(new Date());
        b.setStatus(Beneficiary.STATUS_ACTIVE);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = resolveUsername(auth);
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            usersRepository.findByEmail(email).ifPresent(b::setCreatedBy);
        }

        Beneficiary saved = beneficiaryRepository.save(b);
        log.info("💾 addNew() — saved id={}, name={} {}",
                saved.getId(), saved.getFirstName(), saved.getLastName());

        try {
            log.info("➡️ addNew() — calling sendBeneficiaryRegistered");
            beneficiaryEmailService.sendBeneficiaryRegistered(saved, username);
            log.info("✅ addNew() — sendBeneficiaryRegistered returned");
        } catch (Throwable t) {
            log.error("❌ addNew() — sendBeneficiaryRegistered threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UPDATE
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Beneficiary update(UUID id, Beneficiary updatedData) {
        log.info("▶️ update() STARTED for id={}", id);

        Optional<Beneficiary> existingOpt = beneficiaryRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        Beneficiary existing = existingOpt.get();
        Beneficiary snapshot = copyForDiff(existing);

        existing.setFirstName(updatedData.getFirstName());
        existing.setLastName(updatedData.getLastName());
        existing.setGender(updatedData.getGender());
        existing.setMaritialStatus(updatedData.getMaritialStatus());
        existing.setNid(updatedData.getNid());
        existing.setPhone(updatedData.getPhone());

        if (updatedData.getContractAgreement() != null && !updatedData.getContractAgreement().isBlank()) {
            existing.setContractAgreement(updatedData.getContractAgreement());
        }
        if (updatedData.getPhoto() != null && !updatedData.getPhoto().isBlank()) {
            existing.setPhoto(updatedData.getPhoto());
        }

        String repIdStr = updatedData.getRepresentativeIdValue();
        if (repIdStr != null && !repIdStr.isEmpty()) {
            UUID repId = UUID.fromString(repIdStr);
            Representative rep = representativeRepository.findById(repId)
                    .orElseThrow(() -> new RuntimeException("Representative not found"));
            existing.setRepresentative(rep);
        }

        if (updatedData.getLocation() != null) {
            existing.setLocation(updatedData.getLocation());
        }

        Beneficiary saved = beneficiaryRepository.save(existing);
        log.info("💾 update() — saved id={}", saved.getId());

        List<BeneficiaryEmailService.ChangeRow> changes = diff(snapshot, saved);

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ update() — calling sendBeneficiaryUpdated with {} change(s)", changes.size());
            beneficiaryEmailService.sendBeneficiaryUpdated(saved, changes, username);
            log.info("✅ update() — sendBeneficiaryUpdated returned");
        } catch (Throwable t) {
            log.error("❌ update() — sendBeneficiaryUpdated threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TOGGLE STATUS
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Beneficiary toggleStatus(UUID id) {
        log.info("▶️ toggleStatus() STARTED for id={}", id);

        Optional<Beneficiary> opt = beneficiaryRepository.findById(id);
        if (opt.isEmpty()) return null;

        Beneficiary existing = opt.get();
        String oldStatus = existing.getStatus();
        boolean wasActive = existing.isActive();
        String newStatus = wasActive ? Beneficiary.STATUS_INACTIVE : Beneficiary.STATUS_ACTIVE;

        existing.setStatus(newStatus);
        Beneficiary saved = beneficiaryRepository.saveAndFlush(existing);
        log.info("💾 toggleStatus() — {} → {}", oldStatus, newStatus);

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ toggleStatus() — calling sendBeneficiaryStatusChanged");
            beneficiaryEmailService.sendBeneficiaryStatusChanged(saved, oldStatus, newStatus, username);
            log.info("✅ toggleStatus() — sendBeneficiaryStatusChanged returned");
        } catch (Throwable t) {
            log.error("❌ toggleStatus() — sendBeneficiaryStatusChanged threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DELETE
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public void delete(UUID id) {
        log.info("▶️ delete() STARTED for id={}", id);

        Optional<Beneficiary> opt = beneficiaryRepository.findById(id);
        if (opt.isEmpty()) {
            log.warn("delete({}) — not found", id);
            return;
        }

        Beneficiary existing = opt.get();

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ delete() — calling sendBeneficiaryDeleted for {} {}",
                    existing.getFirstName(), existing.getLastName());
            beneficiaryEmailService.sendBeneficiaryDeleted(existing, username);
            log.info("✅ delete() — sendBeneficiaryDeleted returned (email queued)");
        } catch (Throwable t) {
            log.error("❌ delete() — sendBeneficiaryDeleted threw: {}", t.getMessage(), t);
        }

        beneficiaryRepository.deleteById(id);
        log.info("💾 delete() — row removed from DB");
    }

    public List<Beneficiary> search(String keyword) {
        return beneficiaryRepository.findByFirstNameContainingOrLastNameContaining(keyword, keyword);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private String resolveUsername(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) return auth.getName();
        return "system";
    }

    private Beneficiary copyForDiff(Beneficiary src) {
        Beneficiary c = new Beneficiary();
        c.setId(src.getId());
        c.setFirstName(src.getFirstName());
        c.setLastName(src.getLastName());
        c.setGender(src.getGender());
        c.setMaritialStatus(src.getMaritialStatus());
        c.setNid(src.getNid());
        c.setPhone(src.getPhone());
        c.setPhoto(src.getPhoto());
        c.setContractAgreement(src.getContractAgreement());
        c.setStatus(src.getStatus());
        c.setLocation(src.getLocation());
        c.setRepresentative(src.getRepresentative());
        return c;
    }

    private List<BeneficiaryEmailService.ChangeRow> diff(Beneficiary before, Beneficiary after) {
        List<BeneficiaryEmailService.ChangeRow> changes = new ArrayList<>();
        if (before == null || after == null) return changes;

        addIfChanged(changes, "First Name",     before.getFirstName(),     after.getFirstName());
        addIfChanged(changes, "Last Name",      before.getLastName(),      after.getLastName());
        addIfChanged(changes, "Gender",         before.getGender(),        after.getGender());
        addIfChanged(changes, "Marital Status", before.getMaritialStatus(), after.getMaritialStatus());
        addIfChanged(changes, "National ID",    before.getNid(),           after.getNid());
        addIfChanged(changes, "Phone",          before.getPhone(),         after.getPhone());
        addIfChanged(changes, "Status",         before.getStatus(),        after.getStatus());

        if (!Objects.equals(before.getPhoto(), after.getPhoto())) {
            changes.add(new BeneficiaryEmailService.ChangeRow(
                    "Photo",
                    before.getPhoto() != null ? "old file" : "none",
                    after.getPhoto() != null ? "new file" : "removed"));
        }
        if (!Objects.equals(before.getContractAgreement(), after.getContractAgreement())) {
            changes.add(new BeneficiaryEmailService.ChangeRow(
                    "Contract PDF",
                    before.getContractAgreement() != null ? "old file" : "none",
                    after.getContractAgreement() != null ? "new file" : "removed"));
        }
        String oldLoc = before.getLocation() != null ? before.getLocation().getName() : "—";
        String newLoc = after.getLocation() != null ? after.getLocation().getName() : "—";
        if (!Objects.equals(oldLoc, newLoc)) {
            changes.add(new BeneficiaryEmailService.ChangeRow("Location", oldLoc, newLoc));
        }
        String oldRep = before.getRepresentative() != null
                ? before.getRepresentative().getFirstName() + " " + before.getRepresentative().getLastName() : "—";
        String newRep = after.getRepresentative() != null
                ? after.getRepresentative().getFirstName() + " " + after.getRepresentative().getLastName() : "—";
        if (!Objects.equals(oldRep, newRep)) {
            changes.add(new BeneficiaryEmailService.ChangeRow("Representative", oldRep, newRep));
        }
        return changes;
    }

    private void addIfChanged(List<BeneficiaryEmailService.ChangeRow> list,
                              String label, String oldV, String newV) {
        if (!Objects.equals(oldV, newV)) {
            list.add(new BeneficiaryEmailService.ChangeRow(
                    label,
                    oldV != null && !oldV.isBlank() ? oldV : "—",
                    newV != null && !newV.isBlank() ? newV : "—"));
        }
    }
}