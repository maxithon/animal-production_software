package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import rw.animalproduct.animal.production.dto.BuyerSummary;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.repository.BuyerRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BuyerService {

    private final BuyerRepository buyerRepository;

    /** Where buyer photos are stored on disk. Point this at a persistent, writable folder in production. */
    private final Path uploadDir = Path.of("uploads", "buyers");

    public BuyerService(BuyerRepository buyerRepository) {
        this.buyerRepository = buyerRepository;
    }

    // ── Reads ──────────────────────────────────────────────────────────
    public List<Buyer> getAll() { return buyerRepository.findAll(); }
    public List<Buyer> getActive() { return buyerRepository.findByIsActiveTrue(); }
    public Optional<Buyer> getById(UUID id) { return buyerRepository.findById(id); }
    public Optional<Buyer> getByNationalId(String nationalId) { return buyerRepository.findByNationalId(nationalId); }
    public Optional<Buyer> getByPhone(String phone) { return buyerRepository.findByPhone(phone); }

    /** Fast, N+1-free list for the Buyers dashboard table. */
    public List<BuyerSummary> getAllSummaries() { return buyerRepository.findAllSummaries(); }

    /**
     * Search across all buyers (active AND inactive) for the dashboard.
     * This is more useful for the admin view.
     */
    public List<BuyerSummary> search(String query) {
        if (!StringUtils.hasText(query)) {
            return buyerRepository.findAllSummaries();
        }
        // Search across ALL buyers, not just active ones
        return buyerRepository.searchAllSummaries(query.trim());
    }

    public List<BuyerSummary> getTopBuyersByLimit(int limit) {
        return buyerRepository.findTopBuyerSummaries().stream().limit(limit).toList();
    }

    // ── KPI counts ─────────────────────────────────────────────────────
    public long countActive() { return buyerRepository.countActiveBuyers(); }
    public long countInactive() { return buyerRepository.countInactiveBuyers(); }

    // ── Create ─────────────────────────────────────────────────────────
    @Transactional
    public Buyer addNew(Buyer buyer, MultipartFile photoFile) {
        validateNoDuplicates(buyer, null);
        if (photoFile != null && !photoFile.isEmpty()) {
            buyer.setPhoto(storePhoto(photoFile));
        }
        Buyer saved = buyerRepository.save(buyer);
        // Force the insert to hit the DB now, inside this transaction, so
        // any constraint violation (e.g. a race-condition duplicate) surfaces
        // immediately instead of silently failing later.
        buyerRepository.flush();
        return saved;
    }

    // ── Update ─────────────────────────────────────────────────────────
    @Transactional
    public Buyer update(UUID id, Buyer updated, MultipartFile photoFile) {
        Buyer existing = buyerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found with ID: " + id));

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
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Buyer buyer = buyerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found with ID: " + id));
        if (buyer.getSales() != null && !buyer.getSales().isEmpty()) {
            buyer.setIsActive(false);
            buyerRepository.save(buyer);
        } else {
            buyerRepository.delete(buyer);
        }
        buyerRepository.flush();
    }

    /**
     * ENHANCED: previously only accepted phone/firstName/lastName/address/
     * nationalId, so any buyer created from the Livestock Sales "quick add"
     * form silently lost email, buyer type and notes even though the field
     * existed on the Buyer entity and on the real "Add Buyer" form. This
     * now carries every field the real form collects, so a buyer created
     * from either screen ends up identical in the database.
     *
     * If a buyer with the same phone or National ID already exists, that
     * existing buyer is reused as-is (we don't silently overwrite someone's
     * existing record from a quick-add form on a different page).
     */
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

        // Catches the email-uniqueness case (phone/nationalId are already
        // covered by the lookups above, but email could still collide).
        validateNoDuplicates(newBuyer, null);

        Buyer saved = buyerRepository.save(newBuyer);
        buyerRepository.flush();
        return saved;
    }

    /**
     * @deprecated kept only in case older callers still use the short form.
     * Prefer the 8-argument overload so email/buyerType/notes aren't lost.
     */
    @Deprecated
    @Transactional
    public Buyer findOrCreate(String phone, String firstName, String lastName, String address, String nationalId) {
        return findOrCreate(phone, firstName, lastName, address, nationalId, null, null, null);
    }

    // ── Duplicate validation ───────────────────────────────────────────

    /**
     * Checks phone, National ID and email for duplicates and throws a
     * DuplicateBuyerException carrying the specific field name that
     * collided, so the controller can attach the error to that exact
     * form field instead of a generic flash message.
     *
     * excludeId: pass the buyer's own id on UPDATE so it doesn't flag
     * itself as a duplicate of itself; pass null on CREATE.
     */
    private void validateNoDuplicates(Buyer buyer, UUID excludeId) {
        if (StringUtils.hasText(buyer.getPhone())) {
            boolean duplicate = excludeId == null
                    ? buyerRepository.existsByPhone(buyer.getPhone())
                    : buyerRepository.existsByPhoneAndIdNot(buyer.getPhone(), excludeId);
            if (duplicate) {
                throw new rw.animalproduct.animal.production.exception.DuplicateBuyerException("phone", "A buyer with phone " + buyer.getPhone() + " already exists.");
            }
        }
        if (StringUtils.hasText(buyer.getNationalId())) {
            boolean duplicate = excludeId == null
                    ? buyerRepository.existsByNationalId(buyer.getNationalId())
                    : buyerRepository.existsByNationalIdAndIdNot(buyer.getNationalId(), excludeId);
            if (duplicate) {
                throw new rw.animalproduct.animal.production.exception.DuplicateBuyerException("nationalId", "A buyer with National ID " + buyer.getNationalId() + " already exists.");
            }
        }
        if (StringUtils.hasText(buyer.getEmail())) {
            boolean duplicate = excludeId == null
                    ? buyerRepository.existsByEmailIgnoreCase(buyer.getEmail())
                    : buyerRepository.existsByEmailIgnoreCaseAndIdNot(buyer.getEmail(), excludeId);
            if (duplicate) {
                throw new rw.animalproduct.animal.production.exception.DuplicateBuyerException("email", "A buyer with email " + buyer.getEmail() + " already exists.");
            }
        }
    }

    // ── Photo storage ──────────────────────────────────────────────────
    private String storePhoto(MultipartFile file) {
        try {
            Files.createDirectories(uploadDir);
            String original = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo");
            String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
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
}
