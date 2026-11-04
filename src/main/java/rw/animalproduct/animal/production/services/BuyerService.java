package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import rw.animalproduct.animal.production.dto.BuyerSummary;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.repository.BuyerRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class BuyerService {

    private static final Logger log = LoggerFactory.getLogger(BuyerService.class);

    private final BuyerRepository buyerRepository;
    private final BuyerEmailService buyerEmailService;

    private final Path uploadDir = Path.of("uploads", "buyers");

    public BuyerService(BuyerRepository buyerRepository,
                        BuyerEmailService buyerEmailService) {
        this.buyerRepository = buyerRepository;
        this.buyerEmailService = buyerEmailService;
        log.info("✅ BuyerService initialized — emailService={}",
                buyerEmailService != null ? "OK" : "NULL ❌");
    }

    // ── Reads ──────────────────────────────────────────────────────────
    public List<Buyer> getAll() {
        return buyerRepository.findAll();
    }

    public List<Buyer> getActive() {
        return buyerRepository.findByIsActiveTrue();
    }

    public Optional<Buyer> getById(UUID id) {
        return buyerRepository.findById(id);
    }

    public Optional<Buyer> getByNationalId(String nationalId) {
        return buyerRepository.findByNationalId(nationalId);
    }

    public Optional<Buyer> getByPhone(String phone) {
        return buyerRepository.findByPhone(phone);
    }

    public List<BuyerSummary> getAllSummaries() {
        List<Object[]> results = buyerRepository.findAllSummariesRaw();
        return mapToBuyerSummaries(results);
    }

    public List<BuyerSummary> search(String query) {
        if (!StringUtils.hasText(query)) {
            return getAllSummaries();
        }
        List<Object[]> results = buyerRepository.searchAllSummariesRaw(query.trim());
        return mapToBuyerSummaries(results);
    }

    public List<BuyerSummary> getTopBuyersByLimit(int limit) {
        List<Object[]> results = buyerRepository.findTopBuyerSummariesRaw();
        List<BuyerSummary> summaries = mapToBuyerSummaries(results);
        return summaries.stream().limit(limit).toList();
    }

    // ── Manual Mapping Helper ────────────────────────────────────────
    private List<BuyerSummary> mapToBuyerSummaries(List<Object[]> results) {
        List<BuyerSummary> summaries = new ArrayList<>();
        if (results == null) return summaries;
        for (Object[] row : results) {
            try {
                BuyerSummary summary = new BuyerSummary(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5],
                        (String) row[6],
                        (String) row[7],
                        (String) row[8],
                        (Boolean) row[9],
                        (String) row[10],
                        (LocalDateTime) row[11],
                        ((Number) row[12]).intValue(),
                        (BigDecimal) row[13]
                );
                summaries.add(summary);
            } catch (Exception e) {
                log.warn("Skipping malformed buyer summary row: {}", e.getMessage());
            }
        }
        return summaries;
    }

    // ── KPI counts ─────────────────────────────────────────────────────
    public long countActive() { return buyerRepository.countActiveBuyers(); }
    public long countInactive() { return buyerRepository.countInactiveBuyers(); }

    // ═══════════════════════════════════════════════════════════════════════
    //  CREATE — sends REGISTERED email
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Buyer addNew(Buyer buyer, MultipartFile photoFile) {
        log.info("▶️ addNew() STARTED for phone={}", buyer.getPhone());

        validateNoDuplicates(buyer, null);
        if (photoFile != null && !photoFile.isEmpty()) {
            buyer.setPhoto(storePhoto(photoFile));
        }
        Buyer saved = buyerRepository.save(buyer);
        buyerRepository.flush();

        log.info("💾 addNew() — saved id={}, name={}", saved.getId(), saved.getFullName());

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ addNew() — calling sendBuyerRegistered");
            buyerEmailService.sendBuyerRegistered(saved, username);
            log.info("✅ addNew() — sendBuyerRegistered returned");
        } catch (Throwable t) {
            log.error("❌ addNew() — sendBuyerRegistered threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  UPDATE — sends UPDATED email with field diff
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Buyer update(UUID id, Buyer updated, MultipartFile photoFile) {
        log.info("▶️ update() STARTED for id={}", id);

        Buyer existing = buyerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found with ID: " + id));

        Buyer snapshot = copyForDiff(existing);
        validateNoDuplicates(updated, id);

        existing.setFirstName(updated.getFirstName());
        existing.setLastName(updated.getLastName());
        existing.setPhone(updated.getPhone());
        existing.setAddress(updated.getAddress());
        existing.setNationalId(updated.getNationalId());
        existing.setEmail(updated.getEmail());
        existing.setBuyerType(updated.getBuyerType());
        existing.setNotes(updated.getNotes());
        existing.setIsActive(updated.getIsActive());

        if (photoFile != null && !photoFile.isEmpty()) {
            existing.setPhoto(storePhoto(photoFile));
        }

        Buyer saved = buyerRepository.save(existing);
        buyerRepository.flush();

        log.info("💾 update() — saved id={}", saved.getId());

        List<BuyerEmailService.ChangeRow> changes = diff(snapshot, saved);

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ update() — calling sendBuyerUpdated with {} change(s)", changes.size());
            buyerEmailService.sendBuyerUpdated(saved, changes, username);
            log.info("✅ update() — sendBuyerUpdated returned");
        } catch (Throwable t) {
            log.error("❌ update() — sendBuyerUpdated threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DELETE — soft-delete when the buyer has sales, hard-delete otherwise
    //  Sends DELETED email in both cases.
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public void delete(UUID id) {
        log.info("▶️ delete() STARTED for id={}", id);

        Buyer buyer = buyerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found with ID: " + id));

        // Send the DELETED email BEFORE we mutate/remove the row
        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ delete() — calling sendBuyerDeleted for {}", buyer.getFullName());
            buyerEmailService.sendBuyerDeleted(buyer, username);
            log.info("✅ delete() — sendBuyerDeleted returned (email queued)");
        } catch (Throwable t) {
            log.error("❌ delete() — sendBuyerDeleted threw: {}", t.getMessage(), t);
        }

        if (buyer.getSales() != null && !buyer.getSales().isEmpty()) {
            // Soft delete — keep the row for sales history
            buyer.setIsActive(false);
            buyer.setIsDeleted(true);
            buyerRepository.save(buyer);
            log.info("💾 delete() — soft-deleted buyer id={} (had sales)", id);
        } else {
            buyerRepository.delete(buyer);
            log.info("💾 delete() — hard-deleted buyer id={} (no sales)", id);
        }
        buyerRepository.flush();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TOGGLE STATUS — sends STATUS_CHANGED email
    // ═══════════════════════════════════════════════════════════════════════
    @Transactional
    public Buyer toggleStatus(UUID id) {
        log.info("▶️ toggleStatus() STARTED for id={}", id);

        Buyer buyer = buyerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found with ID: " + id));

        String oldStatus = Boolean.TRUE.equals(buyer.getIsActive()) ? "ACTIVE" : "INACTIVE";
        boolean wasActive = Boolean.TRUE.equals(buyer.getIsActive());
        String newStatus = wasActive ? "INACTIVE" : "ACTIVE";

        buyer.setIsActive(!wasActive);
        Buyer saved = buyerRepository.saveAndFlush(buyer);

        log.info("💾 toggleStatus() — {} → {}", oldStatus, newStatus);

        try {
            String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
            log.info("➡️ toggleStatus() — calling sendBuyerStatusChanged");
            buyerEmailService.sendBuyerStatusChanged(saved, oldStatus, newStatus, username);
            log.info("✅ toggleStatus() — sendBuyerStatusChanged returned");
        } catch (Throwable t) {
            log.error("❌ toggleStatus() — sendBuyerStatusChanged threw: {}", t.getMessage(), t);
        }

        return saved;
    }

    @Transactional
    public Buyer findOrCreate(String phone, String firstName, String lastName, String address,
                              String nationalId, String email, String buyerType, String notes) {
        if (StringUtils.hasText(phone)) {
            Optional<Buyer> existing = buyerRepository.findByPhone(phone);
            if (existing.isPresent()) return existing.get();
        }
        if (StringUtils.hasText(nationalId)) {
            Optional<Buyer> existing = buyerRepository.findByNationalId(nationalId);
            if (existing.isPresent()) return existing.get();
        }

        Buyer newBuyer = new Buyer();
        newBuyer.setFirstName(firstName);
        newBuyer.setLastName(lastName);
        newBuyer.setPhone(phone);
        newBuyer.setAddress(address);
        newBuyer.setNationalId(nationalId);
        newBuyer.setEmail(email);
        newBuyer.setBuyerType(buyerType);
        newBuyer.setNotes(notes);
        newBuyer.setIsActive(true);

        validateNoDuplicates(newBuyer, null);

        Buyer saved = buyerRepository.save(newBuyer);
        buyerRepository.flush();

        // Optional: email on find-or-create (usually silent to avoid noise)
        // Uncomment if you want an email every time a new buyer is created this way:
        // try {
        //     String username = resolveUsername(SecurityContextHolder.getContext().getAuthentication());
        //     buyerEmailService.sendBuyerRegistered(saved, username);
        // } catch (Throwable t) {
        //     log.error("findOrCreate() — sendBuyerRegistered threw: {}", t.getMessage(), t);
        // }

        return saved;
    }

    @Deprecated
    @Transactional
    public Buyer findOrCreate(String phone, String firstName, String lastName, String address, String nationalId) {
        return findOrCreate(phone, firstName, lastName, address, nationalId, null, null, null);
    }

    // ── Duplicate validation ───────────────────────────────────────────
    private void validateNoDuplicates(Buyer buyer, UUID excludeId) {
        if (StringUtils.hasText(buyer.getPhone())) {
            boolean duplicate = excludeId == null
                    ? buyerRepository.existsByPhone(buyer.getPhone())
                    : buyerRepository.existsByPhoneAndIdNot(buyer.getPhone(), excludeId);
            if (duplicate) {
                throw new rw.animalproduct.animal.production.exception.DuplicateBuyerException(
                        "phone", "A buyer with phone " + buyer.getPhone() + " already exists.");
            }
        }
        if (StringUtils.hasText(buyer.getNationalId())) {
            boolean duplicate = excludeId == null
                    ? buyerRepository.existsByNationalId(buyer.getNationalId())
                    : buyerRepository.existsByNationalIdAndIdNot(buyer.getNationalId(), excludeId);
            if (duplicate) {
                throw new rw.animalproduct.animal.production.exception.DuplicateBuyerException(
                        "nationalId", "A buyer with National ID " + buyer.getNationalId() + " already exists.");
            }
        }
        if (StringUtils.hasText(buyer.getEmail())) {
            boolean duplicate = excludeId == null
                    ? buyerRepository.existsByEmailIgnoreCase(buyer.getEmail())
                    : buyerRepository.existsByEmailIgnoreCaseAndIdNot(buyer.getEmail(), excludeId);
            if (duplicate) {
                throw new rw.animalproduct.animal.production.exception.DuplicateBuyerException(
                        "email", "A buyer with email " + buyer.getEmail() + " already exists.");
            }
        }
    }

    // ── Photo storage ──────────────────────────────────────────────────
    private String storePhoto(MultipartFile file) {
        try {
            Files.createDirectories(uploadDir);
            String original = StringUtils.cleanPath(file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "photo");
            String extension = original.contains(".")
                    ? original.substring(original.lastIndexOf('.')) : "";
            String filename = UUID.randomUUID() + extension;
            Path target = uploadDir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store buyer photo: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private String resolveUsername(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) return auth.getName();
        return "system";
    }

    private Buyer copyForDiff(Buyer src) {
        Buyer c = new Buyer();
        c.setId(src.getId());
        c.setFirstName(src.getFirstName());
        c.setLastName(src.getLastName());
        c.setPhone(src.getPhone());
        c.setAddress(src.getAddress());
        c.setNationalId(src.getNationalId());
        c.setEmail(src.getEmail());
        c.setBuyerType(src.getBuyerType());
        c.setNotes(src.getNotes());
        c.setIsActive(src.getIsActive());
        c.setPhoto(src.getPhoto());
        return c;
    }

    private List<BuyerEmailService.ChangeRow> diff(Buyer before, Buyer after) {
        List<BuyerEmailService.ChangeRow> changes = new ArrayList<>();
        if (before == null || after == null) return changes;

        addIfChanged(changes, "First Name",   before.getFirstName(),  after.getFirstName());
        addIfChanged(changes, "Last Name",    before.getLastName(),   after.getLastName());
        addIfChanged(changes, "Phone",        before.getPhone(),      after.getPhone());
        addIfChanged(changes, "Address",      before.getAddress(),    after.getAddress());
        addIfChanged(changes, "National ID",  before.getNationalId(), after.getNationalId());
        addIfChanged(changes, "Email",        before.getEmail(),      after.getEmail());
        addIfChanged(changes, "Buyer Type",   before.getBuyerType(),  after.getBuyerType());
        addIfChanged(changes, "Notes",        before.getNotes(),      after.getNotes());
        addIfChanged(changes, "Status",
                Boolean.TRUE.equals(before.getIsActive()) ? "Active" : "Inactive",
                Boolean.TRUE.equals(after.getIsActive()) ? "Active" : "Inactive");

        if (!Objects.equals(before.getPhoto(), after.getPhoto())) {
            changes.add(new BuyerEmailService.ChangeRow(
                    "Photo",
                    before.getPhoto() != null ? "old file" : "none",
                    after.getPhoto() != null ? "new file" : "removed"));
        }
        return changes;
    }

    private void addIfChanged(List<BuyerEmailService.ChangeRow> list,
                              String label, String oldV, String newV) {
        if (!Objects.equals(oldV, newV)) {
            list.add(new BuyerEmailService.ChangeRow(
                    label,
                    oldV != null && !oldV.isBlank() ? oldV : "—",
                    newV != null && !newV.isBlank() ? newV : "—"));
        }
    }
}