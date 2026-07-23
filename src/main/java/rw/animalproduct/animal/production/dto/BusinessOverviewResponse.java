package rw.animalproduct.animal.production.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response shape for GET /livestock/business-overview-data.
 *
 * Field names here are intentionally identical to what admin-dashboard.html's
 * buildOverview(el, o) JavaScript function reads off the JSON body — do not
 * rename them without also updating that function.
 */
public class BusinessOverviewResponse {

    private String businessStatus;      // "gain" | "loss" | "breakeven"
    private BigDecimal netPosition;

    private Long totalAnimals;
    private Long activeCount;
    private Long soldCount;
    private Long sickCount;

    private BigDecimal totalIncome;
    private BigDecimal salesRevenue;
    private BigDecimal activeStockValue;

    private BigDecimal totalExpenses;
    private BigDecimal treatmentCosts;
    private BigDecimal purchaseCosts;
    private BigDecimal deathLoss;

    private Indicators indicators;
    private Alerts alerts;
    private List<CategoryPerformance> categoryPerformance;

    private List<String> monthLabels;
    private List<BigDecimal> monthlyRevenue;
    private List<BigDecimal> monthlyCosts;
    private List<Long> monthlyBirths;
    private List<Long> monthlySales;
    private List<Long> monthlyDeaths;
    private List<Long> monthlyTreatments;

    // ── Getters / Setters ────────────────────────────────────────────────
    public String getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(String businessStatus) { this.businessStatus = businessStatus; }

    public BigDecimal getNetPosition() { return netPosition; }
    public void setNetPosition(BigDecimal netPosition) { this.netPosition = netPosition; }

    public Long getTotalAnimals() { return totalAnimals; }
    public void setTotalAnimals(Long totalAnimals) { this.totalAnimals = totalAnimals; }

    public Long getActiveCount() { return activeCount; }
    public void setActiveCount(Long activeCount) { this.activeCount = activeCount; }

    public Long getSoldCount() { return soldCount; }
    public void setSoldCount(Long soldCount) { this.soldCount = soldCount; }

    public Long getSickCount() { return sickCount; }
    public void setSickCount(Long sickCount) { this.sickCount = sickCount; }

    public BigDecimal getTotalIncome() { return totalIncome; }
    public void setTotalIncome(BigDecimal totalIncome) { this.totalIncome = totalIncome; }

    public BigDecimal getSalesRevenue() { return salesRevenue; }
    public void setSalesRevenue(BigDecimal salesRevenue) { this.salesRevenue = salesRevenue; }

    public BigDecimal getActiveStockValue() { return activeStockValue; }
    public void setActiveStockValue(BigDecimal activeStockValue) { this.activeStockValue = activeStockValue; }

    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

    public BigDecimal getTreatmentCosts() { return treatmentCosts; }
    public void setTreatmentCosts(BigDecimal treatmentCosts) { this.treatmentCosts = treatmentCosts; }

    public BigDecimal getPurchaseCosts() { return purchaseCosts; }
    public void setPurchaseCosts(BigDecimal purchaseCosts) { this.purchaseCosts = purchaseCosts; }

    public BigDecimal getDeathLoss() { return deathLoss; }
    public void setDeathLoss(BigDecimal deathLoss) { this.deathLoss = deathLoss; }

    public Indicators getIndicators() { return indicators; }
    public void setIndicators(Indicators indicators) { this.indicators = indicators; }

    public Alerts getAlerts() { return alerts; }
    public void setAlerts(Alerts alerts) { this.alerts = alerts; }

    public List<CategoryPerformance> getCategoryPerformance() { return categoryPerformance; }
    public void setCategoryPerformance(List<CategoryPerformance> categoryPerformance) { this.categoryPerformance = categoryPerformance; }

    public List<String> getMonthLabels() { return monthLabels; }
    public void setMonthLabels(List<String> monthLabels) { this.monthLabels = monthLabels; }

    public List<BigDecimal> getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(List<BigDecimal> monthlyRevenue) { this.monthlyRevenue = monthlyRevenue; }

    public List<BigDecimal> getMonthlyCosts() { return monthlyCosts; }
    public void setMonthlyCosts(List<BigDecimal> monthlyCosts) { this.monthlyCosts = monthlyCosts; }

    public List<Long> getMonthlyBirths() { return monthlyBirths; }
    public void setMonthlyBirths(List<Long> monthlyBirths) { this.monthlyBirths = monthlyBirths; }

    public List<Long> getMonthlySales() { return monthlySales; }
    public void setMonthlySales(List<Long> monthlySales) { this.monthlySales = monthlySales; }

    public List<Long> getMonthlyDeaths() { return monthlyDeaths; }
    public void setMonthlyDeaths(List<Long> monthlyDeaths) { this.monthlyDeaths = monthlyDeaths; }

    public List<Long> getMonthlyTreatments() { return monthlyTreatments; }
    public void setMonthlyTreatments(List<Long> monthlyTreatments) { this.monthlyTreatments = monthlyTreatments; }

    // ── Nested DTOs ──────────────────────────────────────────────────────

    public static class Indicators {
        private Double mortalityRate;
        private Double offtakeRate;
        private Double replacementRate;
        private BigDecimal avgSalePrice;

        public Double getMortalityRate() { return mortalityRate; }
        public void setMortalityRate(Double mortalityRate) { this.mortalityRate = mortalityRate; }

        public Double getOfftakeRate() { return offtakeRate; }
        public void setOfftakeRate(Double offtakeRate) { this.offtakeRate = offtakeRate; }

        public Double getReplacementRate() { return replacementRate; }
        public void setReplacementRate(Double replacementRate) { this.replacementRate = replacementRate; }

        public BigDecimal getAvgSalePrice() { return avgSalePrice; }
        public void setAvgSalePrice(BigDecimal avgSalePrice) { this.avgSalePrice = avgSalePrice; }
    }

    public static class Alerts {
        private Long unpaidTreatments;
        private BigDecimal unpaidTreatmentValue;
        private Long ongoingTreatments;
        private Long activeSickCases;
        private Long pregnantCount;

        public Long getUnpaidTreatments() { return unpaidTreatments; }
        public void setUnpaidTreatments(Long unpaidTreatments) { this.unpaidTreatments = unpaidTreatments; }

        public BigDecimal getUnpaidTreatmentValue() { return unpaidTreatmentValue; }
        public void setUnpaidTreatmentValue(BigDecimal unpaidTreatmentValue) { this.unpaidTreatmentValue = unpaidTreatmentValue; }

        public Long getOngoingTreatments() { return ongoingTreatments; }
        public void setOngoingTreatments(Long ongoingTreatments) { this.ongoingTreatments = ongoingTreatments; }

        public Long getActiveSickCases() { return activeSickCases; }
        public void setActiveSickCases(Long activeSickCases) { this.activeSickCases = activeSickCases; }

        public Long getPregnantCount() { return pregnantCount; }
        public void setPregnantCount(Long pregnantCount) { this.pregnantCount = pregnantCount; }
    }

    public static class CategoryPerformance {
        private String name;
        private Long total;
        private Long active;
        private BigDecimal revenue;
        private BigDecimal costs;
        private BigDecimal profit;
        private BigDecimal value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }

        public Long getActive() { return active; }
        public void setActive(Long active) { this.active = active; }

        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

        public BigDecimal getCosts() { return costs; }
        public void setCosts(BigDecimal costs) { this.costs = costs; }

        public BigDecimal getProfit() { return profit; }
        public void setProfit(BigDecimal profit) { this.profit = profit; }

        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
    }
}
