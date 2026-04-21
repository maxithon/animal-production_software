package rw.animalproduct.animal.production.services;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.repository.BuyerRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BuyerService {

    private final BuyerRepository buyerRepository;

    public BuyerService(BuyerRepository buyerRepository) {
        this.buyerRepository = buyerRepository;
    }

    // Basic CRUD
    public List<Buyer> getAll() { return buyerRepository.findAll(); }
    public List<Buyer> getActive() { return buyerRepository.findByIsActiveTrue(); }
    public Optional<Buyer> getById(UUID id) { return buyerRepository.findById(id); }
    public Optional<Buyer> getByNationalId(String nationalId) { return buyerRepository.findByBuyerNationalId(nationalId); }
    public Optional<Buyer> getByPhone(String phone) { return buyerRepository.findByBuyerPhone(phone); }
    public List<Buyer> searchByName(String name) { return buyerRepository.findByBuyerNameContainingIgnoreCase(name); }
    public List<Buyer> search(String query) {
        if (query == null || query.trim().isEmpty()) return getActive();
        return buyerRepository.searchActiveBuyers(query.trim());
    }
    public List<Buyer> getTopBuyers() { return buyerRepository.findTopBuyers(); }

    // KPI Counts
    public long countActive() { return buyerRepository.countActiveBuyers(); }
    public long countInactive() { return buyerRepository.countInactiveBuyers(); }

    @Transactional
    public Buyer addNew(Buyer buyer) {
        if (buyer.getBuyerPhone() != null && !buyer.getBuyerPhone().isEmpty()) {
            buyerRepository.findByBuyerPhone(buyer.getBuyerPhone())
                    .ifPresent(b -> { throw new RuntimeException("Buyer with phone " + b.getBuyerPhone() + " already exists."); });
        }
        if (buyer.getBuyerNationalId() != null && !buyer.getBuyerNationalId().isEmpty()) {
            buyerRepository.findByBuyerNationalId(buyer.getBuyerNationalId())
                    .ifPresent(b -> { throw new RuntimeException("Buyer with National ID " + b.getBuyerNationalId() + " already exists."); });
        }
        return buyerRepository.save(buyer);
    }

    @Transactional
    public Buyer update(UUID id, Buyer updated) {
        Buyer existing = buyerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Buyer not found with ID: " + id));
        existing.setBuyerName(updated.getBuyerName());
        existing.setBuyerPhone(updated.getBuyerPhone());
        existing.setBuyerAddress(updated.getBuyerAddress());
        existing.setBuyerNationalId(updated.getBuyerNationalId());
        existing.setBuyerEmail(updated.getBuyerEmail());
        existing.setBuyerType(updated.getBuyerType());
        existing.setNotes(updated.getNotes());
        existing.setIsActive(updated.getIsActive());
        return buyerRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        Buyer buyer = buyerRepository.findById(id).orElseThrow();
        if (buyer.getSales() != null && !buyer.getSales().isEmpty()) {
            buyer.setIsActive(false);
            buyerRepository.save(buyer);
        } else {
            buyerRepository.delete(buyer);
        }
    }

    @Transactional
    public Buyer findOrCreate(String phone, String name, String address, String nationalId) {
        if (phone != null && !phone.isEmpty()) {
            Optional<Buyer> existing = buyerRepository.findByBuyerPhone(phone);
            if (existing.isPresent()) return existing.get();
        }
        if (nationalId != null && !nationalId.isEmpty()) {
            Optional<Buyer> existing = buyerRepository.findByBuyerNationalId(nationalId);
            if (existing.isPresent()) return existing.get();
        }
        Buyer newBuyer = new Buyer();
        newBuyer.setBuyerName(name);
        newBuyer.setBuyerPhone(phone);
        newBuyer.setBuyerAddress(address);
        newBuyer.setBuyerNationalId(nationalId);
        newBuyer.setIsActive(true);
        return buyerRepository.save(newBuyer);
    }

    // For top buyers chart with limit
    public List<Buyer> getTopBuyersByLimit(int limit) {
        return buyerRepository.findTopBuyers().stream().limit(limit).toList();
    }
}