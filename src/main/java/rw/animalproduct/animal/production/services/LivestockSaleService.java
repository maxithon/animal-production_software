package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockSaleService {

    private final LivestockSaleRepository saleRepository;
    private final LivestockRepository livestockRepository;
    private final BuyerService buyerService;

    public LivestockSaleService(LivestockSaleRepository saleRepository,
                                LivestockRepository livestockRepository,
                                BuyerService buyerService) {
        this.saleRepository = saleRepository;
        this.livestockRepository = livestockRepository;
        this.buyerService = buyerService;
    }

    public List<LivestockSale> getAll() {
        return saleRepository.findAll();
    }

    public Optional<LivestockSale> getById(UUID id) {
        return saleRepository.findById(id);
    }

    public List<LivestockSale> getByLivestock(UUID livestockId) {
        return saleRepository.findByLivestockId(livestockId);
    }

    @Transactional
    public LivestockSale addNew(LivestockSale sale) {
        resolveAndSetLivestock(sale);
        resolveAndSetBuyer(sale);

        if (sale.getLivestock() != null) {
            Livestock animal = sale.getLivestock();

            if (Livestock.STATUS_SOLD.equals(animal.getStatus())) {
                throw new RuntimeException(
                        "Animal " + animal.getTagNumber() + " is already marked as SOLD. " +
                                "Delete the existing sale record first if you need to re-record this sale."
                );
            }

            animal.setStatus(Livestock.STATUS_SOLD);
            livestockRepository.save(animal);
        }

        return saleRepository.save(sale);
    }

    @Transactional
    public LivestockSale update(UUID id, LivestockSale updated) {
        Optional<LivestockSale> existingOpt = saleRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockSale existing = existingOpt.get();
        Livestock oldAnimal = existing.getLivestock();

        existing.setSaleReason(updated.getSaleReason());
        existing.setSalePrice(updated.getSalePrice());
        existing.setSaleDate(updated.getSaleDate());
        existing.setSaleLocation(updated.getSaleLocation());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        existing.setBuyerIdValue(updated.getBuyerIdValue());

        resolveAndSetLivestock(existing);
        resolveAndSetBuyer(existing);

        Livestock newAnimal = existing.getLivestock();

        if (oldAnimal != null && newAnimal != null &&
                !oldAnimal.getId().equals(newAnimal.getId())) {
            oldAnimal.setStatus(Livestock.STATUS_ACTIVE);
            livestockRepository.save(oldAnimal);
            newAnimal.setStatus(Livestock.STATUS_SOLD);
            livestockRepository.save(newAnimal);
        } else if (newAnimal != null && !Livestock.STATUS_SOLD.equals(newAnimal.getStatus())) {
            newAnimal.setStatus(Livestock.STATUS_SOLD);
            livestockRepository.save(newAnimal);
        }

        return saleRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        Optional<LivestockSale> saleOpt = saleRepository.findById(id);

        saleOpt.ifPresent(sale -> {
            if (sale.getLivestock() != null) {
                Livestock animal = sale.getLivestock();
                if (Livestock.STATUS_SOLD.equals(animal.getStatus())) {
                    animal.setStatus(Livestock.STATUS_ACTIVE);
                    livestockRepository.save(animal);
                }
            }
            saleRepository.delete(sale);
        });

        if (saleOpt.isEmpty()) {
            saleRepository.deleteById(id);
        }
    }

    private void resolveAndSetLivestock(LivestockSale sale) {
        String idStr = sale.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException(
                            "Livestock not found with ID: " + idStr));
            sale.setLivestock(ls);
        }
    }

    private void resolveAndSetBuyer(LivestockSale sale) {
        String buyerIdStr = sale.getBuyerIdValue();
        if (buyerIdStr != null && !buyerIdStr.isEmpty()) {
            Buyer buyer = buyerService.getById(UUID.fromString(buyerIdStr))
                    .orElseThrow(() -> new RuntimeException(
                            "Buyer not found with ID: " + buyerIdStr));
            sale.setBuyer(buyer);
        }
    }
}