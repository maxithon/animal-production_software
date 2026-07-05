package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BeneficiaryImpactService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockSaleRepository livestockSaleRepository;

    public BeneficiaryImpactService(BeneficiaryRepository beneficiaryRepository,
                                    LivestockRepository livestockRepository,
                                    LivestockSaleRepository livestockSaleRepository) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.livestockRepository = livestockRepository;
        this.livestockSaleRepository = livestockSaleRepository;
    }

    /**
     * Get beneficiary impact report
     */
    public BeneficiaryImpactReport getBeneficiaryImpactReport(UUID beneficiaryId, Date startDate, Date endDate) {
        // Convert Date to LocalDate
        LocalDate startLocalDate = convertToLocalDate(startDate);
        LocalDate endLocalDate = convertToLocalDate(endDate);

        BeneficiaryImpactReport report = new BeneficiaryImpactReport();

        // Get beneficiary
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new RuntimeException("Beneficiary not found"));

        report.setBeneficiaryId(beneficiaryId);

        // FIX: Use getFullName() method instead of getName()
        report.setBeneficiaryName(beneficiary.getFullName());

        report.setStartDate(startLocalDate);
        report.setEndDate(endLocalDate);

        // Get livestock for this beneficiary
        List<Livestock> livestock = livestockRepository.findByBeneficiaryId(beneficiaryId);

        // Get sales in date range (you'll need to implement this method)
        // List<LivestockSale> sales = livestockSaleRepository.findByBeneficiaryIdAndSaleDateBetween(
        //     beneficiaryId, startLocalDate, endLocalDate);

        // For now, get all sales and filter
        List<LivestockSale> sales = livestockSaleRepository.findAll().stream()
                .filter(sale -> sale.getLivestock() != null &&
                        sale.getLivestock().getBeneficiary() != null &&
                        sale.getLivestock().getBeneficiary().getId().equals(beneficiaryId) &&
                        sale.getSaleDate() != null &&
                        !sale.getSaleDate().isBefore(startLocalDate) &&
                        !sale.getSaleDate().isAfter(endLocalDate))
                .collect(Collectors.toList());

        // Calculate metrics
        int totalAnimals = livestock.size();
        int activeAnimals = (int) livestock.stream()
                .filter(l -> "ACTIVE".equals(l.getStatus()))
                .count();

        BigDecimal totalRevenue = sales.stream()
                .map(LivestockSale::getSalePrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalSales = sales.size();
        BigDecimal averageSalePrice = totalSales > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalSales), 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;

        report.setTotalAnimals(totalAnimals);
        report.setActiveAnimals(activeAnimals);
        report.setTotalRevenue(totalRevenue);
        report.setTotalSales(totalSales);
        report.setAverageSalePrice(averageSalePrice);

        return report;
    }

    /**
     * Convert java.util.Date to LocalDate
     */
    private LocalDate convertToLocalDate(Date date) {
        if (date == null) {
            return LocalDate.now().minusYears(1);
        }
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    // ── Inner DTO class ──────────────────────────────────────────────────────

    public static class BeneficiaryImpactReport {
        private UUID beneficiaryId;
        private String beneficiaryName;
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalAnimals;
        private int activeAnimals;
        private BigDecimal totalRevenue;
        private int totalSales;
        private BigDecimal averageSalePrice;

        // Getters and Setters
        public UUID getBeneficiaryId() { return beneficiaryId; }
        public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }

        public String getBeneficiaryName() { return beneficiaryName; }
        public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public int getTotalAnimals() { return totalAnimals; }
        public void setTotalAnimals(int totalAnimals) { this.totalAnimals = totalAnimals; }

        public int getActiveAnimals() { return activeAnimals; }
        public void setActiveAnimals(int activeAnimals) { this.activeAnimals = activeAnimals; }

        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

        public int getTotalSales() { return totalSales; }
        public void setTotalSales(int totalSales) { this.totalSales = totalSales; }

        public BigDecimal getAverageSalePrice() { return averageSalePrice; }
        public void setAverageSalePrice(BigDecimal averageSalePrice) { this.averageSalePrice = averageSalePrice; }
    }
}