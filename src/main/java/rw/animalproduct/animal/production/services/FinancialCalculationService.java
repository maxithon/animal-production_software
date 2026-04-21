package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class FinancialCalculationService {

    @Autowired
    private LivestockSaleService saleService;

    @Autowired
    private LivestockDeathService deathService;

    @Autowired
    private LivestockRepository livestockRepository;

    public BigDecimal calculateSalesRevenue(LocalDate fromDate, LocalDate toDate) {
        return saleService.getAll().stream()
                .filter(s -> s.getSaleDate() != null &&
                        !s.getSaleDate().isBefore(fromDate) &&
                        !s.getSaleDate().isAfter(toDate))
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateDeathLoss(LocalDate fromDate, LocalDate toDate) {
        return deathService.getAll().stream()
                .filter(d -> d.getDeathDate() != null &&
                        !d.getDeathDate().isBefore(fromDate) &&
                        !d.getDeathDate().isAfter(toDate))
                .filter(d -> d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                .map(d -> d.getLivestock().getCurrentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculatePurchaseCosts(LocalDate fromDate, LocalDate toDate) {
        return livestockRepository.findAll().stream()
                .filter(l -> l.getDateReceived() != null &&
                        !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getMother() == null)
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateCurrentHerdValue() {
        return livestockRepository.findAll().stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}