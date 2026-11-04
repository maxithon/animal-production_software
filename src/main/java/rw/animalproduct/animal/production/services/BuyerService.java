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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BuyerService {

    private final BuyerRepository buyerRepository;

    private final Path uploadDir = Path.of("uploads", "buyers");

    public BuyerService(BuyerRepository buyerRepository) {
        this.buyerRepository = buyerRepository;
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
    // Row layout matches the repository queries:
    // 0=id, 1=firstName, 2=lastName, 3=phone, 4=address, 5=nationalId,
    // 6=email, 7=buyerType, 8=notes, 9=isActive, 10=photo, 11=createdAt,
    // 12=totalPurchases, 13=totalSpent

    private List<BuyerSummary> mapToBuyerSummaries(List<Object[]> results) {
        List<BuyerSummary> summaries = new ArrayList<>();
        if (results == null) {
            return summaries;
        }
        for (Object[] row : results) {
            try {
                BuyerSummary summary = new BuyerSummary(
                        (UUID) row[0],                          // id
                        (String) row[1],                        // firstName
                        (String) row[2],                        // lastName
                        (String) row[3],                        // phone
                        (String) row[4],                        // address
                        (String) row[5],                        // nationalId
                        (String) row[6],                        // email
                        (String) row[7],                        // buyerType
                        (String) row[8],                        // notes
                        (Boolean) row[9],                       // isActive
                        (String) row[10],                       // photo
                        (LocalDateTime) row[11],                // createdAt
                        ((Number) row[12]).intValue(),          // totalPurchases
                        (BigDecimal) row[13]                    // totalSpent
                );
                summaries.add(summary);
            } catch (Exception e) {
                // Skip invalid rows
            }
        }
        return summaries;
    }

    // ── KPI counts ─────────────────────────────────────────────────────
    public long countActive() {
        return buyerRepository.countActiveBuyers();
    }

    public long countInactive() {
        return buyerRepository.countInactiveBuyers();
    }

    // ── Create ─────────────────────────────────────────────────────────
    @Transactional
    public Buyer addNew(Buyer buyer, MultipartFile photoFile) {
        validateNoDuplicates(buyer, null);
        if (photoFile != null && !photoFile.isEmpty()) {
            buyer.setPhoto(storePhoto(photoFile));
        }
        Buyer saved = buyerRepository.save(buyer);
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