package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.SalesBuyerAnalyticsDto;
import rw.animalproduct.animal.production.entity.LivestockSale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ADD-ON for your existing sales report controller. Autowire this bean
 * alongside your current sale service and call
 * {@code salesAnalyticsService.generateBuyerAnalytics(from, to)}, then
 * add the result to your existing Model, e.g.:
 *
 *   model.addAttribute("buyerAnalytics", salesAnalyticsService.generateBuyerAnalytics(from, to));
 *
 * and drop the fragment in sales-report-additions.html into your
 * sales-report.html template.
 */
@Service
@RequiredArgsConstructor
public class SalesAnalyticsService {

    private final LivestockSaleService saleService;

    public SalesBuyerAnalyticsDto generateBuyerAnalytics(LocalDate from, LocalDate to) {
        SalesBuyerAnalyticsDto dto = new SalesBuyerAnalyticsDto();

        List<LivestockSale> sales = saleService.getAll().stream()
                .filter(s -> s.getSaleDate() != null && !s.getSaleDate().isBefore(from) && !s.getSaleDate().isAfter(to))
                .collect(Collectors.toList());

        Map<String, SalesBuyerAnalyticsDto.BuyerRow> byBuyer = new LinkedHashMap<>();

        for (LivestockSale s : sales) {
            String buyerKey = s.getBuyer() != null ? s.getBuyer().getId().toString() : "unrecorded";
            SalesBuyerAnalyticsDto.BuyerRow row = byBuyer.computeIfAbsent(buyerKey, k -> {
                SalesBuyerAnalyticsDto.BuyerRow r = new SalesBuyerAnalyticsDto.BuyerRow();
                r.setBuyerId(k);
                if (s.getBuyer() != null) {
                    r.setBuyerName(s.getBuyer().getBuyerName());
                    r.setBuyerType(s.getBuyer().getBuyerType());
                } else {
                    r.setBuyerName("Unrecorded / Walk-in Buyer");
                    r.setBuyerType("UNKNOWN");
                }
                return r;
            });
            row.setAnimalsBought(row.getAnimalsBought() + 1);
            if (s.getSalePrice() != null) {
                row.setTotalSpent(row.getTotalSpent().add(s.getSalePrice()));
            }

            String reason = s.getSaleReason() != null ? s.getSaleReason() : "UNSPECIFIED";
            dto.getCountBySaleReason().merge(reason, 1L, Long::sum);

            if (s.getLivestock() != null && s.getLivestock().getLivestockCategory() != null) {
                String cat = s.getLivestock().getLivestockCategory().getName();
                BigDecimal price = s.getSalePrice() != null ? s.getSalePrice() : BigDecimal.ZERO;
                dto.getRevenueByCategory().merge(cat, price, BigDecimal::add);
            }
        }

        List<SalesBuyerAnalyticsDto.BuyerRow> rows = new ArrayList<>(byBuyer.values());
        rows.sort(Comparator.comparing(SalesBuyerAnalyticsDto.BuyerRow::getTotalSpent).reversed());
        dto.setByBuyer(rows);

        return dto;
    }
}
