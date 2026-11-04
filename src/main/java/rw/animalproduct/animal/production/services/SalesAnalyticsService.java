package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.LivestockSalePayment;
import rw.animalproduct.animal.production.repository.LivestockSalePaymentRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalesAnalyticsService {

    private final LivestockSaleRepository saleRepository;
    private final LivestockSalePaymentRepository paymentRepository;

    public SalesAnalyticsService(LivestockSaleRepository saleRepository,
                                 LivestockSalePaymentRepository paymentRepository) {
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Get complete analytics for sales report with all filters
     */
    public Map<String, Object> getAnalyticsForReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID categoryId,
            String gender,
            UUID buyerId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String paymentStatus) {

        Map<String, Object> result = new HashMap<>();

        // Build filtered sales
        List<LivestockSale> allFilteredSales = getFilteredSales(fromDate, toDate, categoryId,
                gender, buyerId, minPrice, maxPrice, paymentStatus);

        // Get confirmed sales only for financial calculations
        List<LivestockSale> confirmedSales = allFilteredSales.stream()
                .filter(s -> LivestockSale.SALE_CONFIRMED.equals(s.getSaleStatus()))
                .collect(Collectors.toList());

        // Calculate totals from confirmed sales only
        BigDecimal totalRevenue = confirmedSales.stream()
                .map(LivestockSale::getSalePrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmountPaid = confirmedSales.stream()
                .map(LivestockSale::getAmountPaid)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = confirmedSales.stream()
                .map(LivestockSale::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAcquisitionCost = confirmedSales.stream()
                .map(LivestockSale::getAcquisitionCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfit = confirmedSales.stream()
                .map(LivestockSale::getGrossProfit)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long salesMissingCost = confirmedSales.stream()
                .filter(s -> s.getAcquisitionCost() == null || s.getAcquisitionCost().compareTo(BigDecimal.ZERO) == 0)
                .count();

        // Calculate averages
        List<BigDecimal> prices = confirmedSales.stream()
                .map(LivestockSale::getSalePrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        BigDecimal averagePrice = prices.isEmpty() ? BigDecimal.ZERO :
                prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);

        BigDecimal highestSale = prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowestSale = prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        // Gross Margin
        BigDecimal grossMargin = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossMargin = totalProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        // Category summaries
        Map<String, CategorySummary> categorySummaries = new LinkedHashMap<>();
        for (LivestockSale sale : confirmedSales) {
            String categoryName = sale.getLivestock() != null &&
                    sale.getLivestock().getLivestockCategory() != null ?
                    sale.getLivestock().getLivestockCategory().getName() : "Uncategorized";

            CategorySummary summary = categorySummaries.computeIfAbsent(categoryName,
                    k -> new CategorySummary());
            summary.count++;
            summary.totalRevenue = safeAdd(summary.totalRevenue, sale.getSalePrice());
            summary.totalCost = safeAdd(summary.totalCost, sale.getAcquisitionCost());
            summary.totalProfit = safeAdd(summary.totalProfit, sale.getGrossProfit());
        }

        // Buyer analytics (confirmed sales only)
        Map<String, BuyerAnalytics> buyerAnalytics = new LinkedHashMap<>();
        for (LivestockSale sale : confirmedSales) {
            String buyerName = sale.getBuyer() != null ? sale.getBuyer().getFullName() : "Unknown";
            BuyerAnalytics ba = buyerAnalytics.computeIfAbsent(buyerName, k -> new BuyerAnalytics());
            ba.purchaseCount++;
            ba.totalSpent = safeAdd(ba.totalSpent, sale.getSalePrice());
            ba.totalPaid = safeAdd(ba.totalPaid, sale.getAmountPaid());
            ba.totalOutstanding = safeAdd(ba.totalOutstanding, sale.getBalance());
        }

        // Status counts (all sales)
        Map<String, Long> saleStatusCounts = allFilteredSales.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getSaleStatus() != null ? s.getSaleStatus() : LivestockSale.SALE_DRAFT,
                        Collectors.counting()
                ));

        // Payment status counts and amounts (confirmed sales only)
        Map<String, Long> paymentStatusCounts = confirmedSales.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getPaymentStatus() != null ? s.getPaymentStatus() : LivestockSale.PAYMENT_UNPAID,
                        Collectors.counting()
                ));

        Map<String, BigDecimal> paymentStatusAmounts = confirmedSales.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getPaymentStatus() != null ? s.getPaymentStatus() : LivestockSale.PAYMENT_UNPAID,
                        Collectors.reducing(BigDecimal.ZERO,
                                s -> s.getAmountPaid() != null ? s.getAmountPaid() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));

        long unpaidCount = paymentStatusCounts.getOrDefault(LivestockSale.PAYMENT_UNPAID, 0L);
        long partialCount = paymentStatusCounts.getOrDefault(LivestockSale.PAYMENT_PARTIAL, 0L);
        long paidCount = paymentStatusCounts.getOrDefault(LivestockSale.PAYMENT_PAID, 0L);

        BigDecimal unpaidAmount = paymentStatusAmounts.getOrDefault(LivestockSale.PAYMENT_UNPAID, BigDecimal.ZERO);
        BigDecimal partialAmount = paymentStatusAmounts.getOrDefault(LivestockSale.PAYMENT_PARTIAL, BigDecimal.ZERO);
        BigDecimal paidAmount = paymentStatusAmounts.getOrDefault(LivestockSale.PAYMENT_PAID, BigDecimal.ZERO);

        // Trend data by month
        Map<String, TrendData> trendData = new LinkedHashMap<>();
        for (LivestockSale sale : confirmedSales) {
            String monthKey = sale.getSaleDate() != null ?
                    YearMonth.from(sale.getSaleDate()).toString() : "Unknown";
            TrendData td = trendData.computeIfAbsent(monthKey, k -> new TrendData());
            td.salesCount++;
            td.totalRevenue = safeAdd(td.totalRevenue, sale.getSalePrice());
            td.totalPaid = safeAdd(td.totalPaid, sale.getAmountPaid());
            td.totalOutstanding = safeAdd(td.totalOutstanding, sale.getBalance());
            td.totalProfit = safeAdd(td.totalProfit, sale.getGrossProfit());
        }

        // Populate result
        result.put("filteredSales", allFilteredSales);
        result.put("sales", allFilteredSales);
        result.put("totalSalesCount", allFilteredSales.size());
        result.put("confirmedCount", confirmedSales.size());
        result.put("totalRevenue", totalRevenue);
        result.put("totalAmountPaid", totalAmountPaid);
        result.put("totalPaid", totalAmountPaid);
        result.put("totalOutstanding", totalOutstanding);
        result.put("totalCost", totalAcquisitionCost);
        result.put("totalAcquisitionCost", totalAcquisitionCost);
        result.put("totalGrossProfit", totalProfit);
        result.put("grossMargin", grossMargin);
        result.put("salesMissingCostCount", salesMissingCost);
        result.put("averageSalePrice", averagePrice);
        result.put("highestSale", highestSale);
        result.put("lowestSale", lowestSale);
        result.put("categorySummaries", categorySummaries);
        result.put("buyerAnalytics", buyerAnalytics);
        result.put("statusCounts", saleStatusCounts);
        result.put("paymentStatusCounts", paymentStatusCounts);
        result.put("paymentStatusAmounts", paymentStatusAmounts);
        result.put("trendData", trendData);
        result.put("unpaidCount", unpaidCount);
        result.put("partialCount", partialCount);
        result.put("paidCount", paidCount);
        result.put("unpaidAmount", unpaidAmount);
        result.put("partialAmount", partialAmount);
        result.put("paidAmount", paidAmount);

        return result;
    }

    /**
     * Get filtered sales based on criteria
     */
    private List<LivestockSale> getFilteredSales(
            LocalDate fromDate,
            LocalDate toDate,
            UUID categoryId,
            String gender,
            UUID buyerId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String paymentStatus) {

        return saleRepository.findAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .filter(s -> {
                    if (fromDate != null && s.getSaleDate() != null) {
                        return !s.getSaleDate().isBefore(fromDate);
                    }
                    return true;
                })
                .filter(s -> {
                    if (toDate != null && s.getSaleDate() != null) {
                        return !s.getSaleDate().isAfter(toDate);
                    }
                    return true;
                })
                .filter(s -> {
                    if (categoryId != null && s.getLivestock() != null &&
                            s.getLivestock().getLivestockCategory() != null) {
                        return categoryId.equals(s.getLivestock().getLivestockCategory().getId());
                    }
                    return true;
                })
                .filter(s -> {
                    if (gender != null && s.getLivestock() != null &&
                            s.getLivestock().getGender() != null) {
                        return gender.equals(s.getLivestock().getGender());
                    }
                    return true;
                })
                .filter(s -> {
                    if (buyerId != null && s.getBuyer() != null) {
                        return buyerId.equals(s.getBuyer().getId());
                    }
                    return true;
                })
                .filter(s -> {
                    if (minPrice != null && s.getSalePrice() != null) {
                        return s.getSalePrice().compareTo(minPrice) >= 0;
                    }
                    return true;
                })
                .filter(s -> {
                    if (maxPrice != null && s.getSalePrice() != null) {
                        return s.getSalePrice().compareTo(maxPrice) <= 0;
                    }
                    return true;
                })
                .filter(s -> {
                    if (paymentStatus != null && !paymentStatus.isEmpty()) {
                        if (LivestockSale.SALE_CONFIRMED.equals(s.getSaleStatus())) {
                            return paymentStatus.equals(s.getPaymentStatus());
                        }
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get payment summary for a specific sale
     */
    public PaymentSummary getPaymentSummary(UUID saleId) {
        List<LivestockSalePayment> payments = paymentRepository.findPaymentsBySaleId(saleId);

        PaymentSummary summary = new PaymentSummary();
        summary.totalPayments = payments.size();
        summary.totalAmountPaid = payments.stream()
                .map(LivestockSalePayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.lastPaymentDate = payments.stream()
                .map(LivestockSalePayment::getPaymentDate)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return summary;
    }

    // ─── Helper Methods ──────────────────────────────────────────

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.add(b);
    }

    // ─── Inner Classes ────────────────────────────────────────────

    public static class CategorySummary {
        public long count = 0;
        public BigDecimal totalRevenue = BigDecimal.ZERO;
        public BigDecimal totalCost = BigDecimal.ZERO;
        public BigDecimal totalProfit = BigDecimal.ZERO;

        public BigDecimal getAveragePrice() {
            return count > 0 ? totalRevenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
    }

    public static class BuyerAnalytics {
        public long purchaseCount = 0;
        public BigDecimal totalSpent = BigDecimal.ZERO;
        public BigDecimal totalPaid = BigDecimal.ZERO;
        public BigDecimal totalOutstanding = BigDecimal.ZERO;

        public BigDecimal getAverageSpent() {
            return purchaseCount > 0 ? totalSpent.divide(BigDecimal.valueOf(purchaseCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
    }

    public static class TrendData {
        public long salesCount = 0;
        public BigDecimal totalRevenue = BigDecimal.ZERO;
        public BigDecimal totalPaid = BigDecimal.ZERO;
        public BigDecimal totalOutstanding = BigDecimal.ZERO;
        public BigDecimal totalProfit = BigDecimal.ZERO;
    }

    public static class PaymentSummary {
        public long totalPayments = 0;
        public BigDecimal totalAmountPaid = BigDecimal.ZERO;
        public LocalDateTime lastPaymentDate = null;
    }
}