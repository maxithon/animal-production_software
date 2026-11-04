package rw.animalproduct.animal.production.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response shape for the merged Business Overview report.
 * Serves both the JSON dashboard feed (via DashboardController's
 * LivestockBusinessOverviewController) and the standalone HTML report.
 */
public class BusinessOverviewResponse {

    private String businessStatus;      // "gain" | "loss" | "breakeven"
    private BigDecimal netPosition;

    private Long totalAnimals;
    private Long activeCount;
    private Long soldCount;
    private Long deadCount;
    private Long sickCount;
    private Long transferredCount;
    private Long pregnantCount;

    // ── Health-status counts (from Summary Report) ──────────────────────
    private Long healthyCount;
    private Long underTreatmentCount;
    private Long criticalHealthCount;

    // ── Reproductive-status counts (from Summary Report) ────────────────
    private Long readyToBreedCount;
    private Long bredCount;
    private Long openCount;
    private Long nursingCount;
    private Long notApplicableCount;

    // ── Values ──────────────────────────────────────────────────────────
    private BigDecimal activeStockValue;
    private BigDecimal averageActiveValue;

    // ── Financials ──────────────────────────────────────────────────────
    private BigDecimal totalIncome;
    private BigDecimal salesRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal treatmentCosts;
    private BigDecimal preventiveCosts;
    private BigDecimal curativeCosts;
    private BigDecimal purchaseCosts;
    private BigDecimal deathLoss;
    private BigDecimal bornAnimalsValue;

    private Long totalTreatmentsCount;
    private Long totalSalesCount;
    private Integer preventiveSharePct;

    // ── Sections ────────────────────────────────────────────────────────
    private Indicators indicators;
    private Alerts alerts;
    private List<CategoryPerformance> categoryPerformance;
    private List<CategoryIndicators> categoryIndicators;

    // ── Monthly trends ──────────────────────────────────────────────────
    private List<String> monthLabels;
    private List<BigDecimal> monthlyRevenue;
    private List<BigDecimal> monthlyCosts;
    private List<Long> monthlyBirths;
    private List<Long> monthlySales;
    private List<Long> monthlyDeaths;
    private List<Long> monthlyTreatments;

    // ── Category breakdowns ─────────────────────────────────────────────
    private Map<String, Long> statusTotalsByCategory;
    private Map<String, Map<String, Long>> statusByCategory;
    private Map<String, Long> categoryBreakdown;
    private Map<String, Long> locationBreakdown;
    private Map<String, Long> genderBreakdown;
    private Map<String, Long> healthBreakdown;
    private Map<String, Long> reproBreakdown;

    // ── Period metadata ─────────────────────────────────────────────────
    private Integer months;
    private String periodLabel;

    // ── Active animals (Animals tab) ────────────────────────────────────
    private List<ActiveAnimal> activeAnimals;

    // ═══════════════════════════════════════════════════════════════════
    //  GETTERS / SETTERS
    // ═══════════════════════════════════════════════════════════════════

    public String getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(String v) { this.businessStatus = v; }

    public BigDecimal getNetPosition() { return netPosition; }
    public void setNetPosition(BigDecimal v) { this.netPosition = v; }

    public Long getTotalAnimals() { return totalAnimals; }
    public void setTotalAnimals(Long v) { this.totalAnimals = v; }

    public Long getActiveCount() { return activeCount; }
    public void setActiveCount(Long v) { this.activeCount = v; }

    public Long getSoldCount() { return soldCount; }
    public void setSoldCount(Long v) { this.soldCount = v; }

    public Long getDeadCount() { return deadCount; }
    public void setDeadCount(Long v) { this.deadCount = v; }

    public Long getSickCount() { return sickCount; }
    public void setSickCount(Long v) { this.sickCount = v; }

    public Long getTransferredCount() { return transferredCount; }
    public void setTransferredCount(Long v) { this.transferredCount = v; }

    public Long getPregnantCount() { return pregnantCount; }
    public void setPregnantCount(Long v) { this.pregnantCount = v; }

    public Long getHealthyCount() { return healthyCount; }
    public void setHealthyCount(Long v) { this.healthyCount = v; }

    public Long getUnderTreatmentCount() { return underTreatmentCount; }
    public void setUnderTreatmentCount(Long v) { this.underTreatmentCount = v; }

    public Long getCriticalHealthCount() { return criticalHealthCount; }
    public void setCriticalHealthCount(Long v) { this.criticalHealthCount = v; }

    public Long getReadyToBreedCount() { return readyToBreedCount; }
    public void setReadyToBreedCount(Long v) { this.readyToBreedCount = v; }

    public Long getBredCount() { return bredCount; }
    public void setBredCount(Long v) { this.bredCount = v; }

    public Long getOpenCount() { return openCount; }
    public void setOpenCount(Long v) { this.openCount = v; }

    public Long getNursingCount() { return nursingCount; }
    public void setNursingCount(Long v) { this.nursingCount = v; }

    public Long getNotApplicableCount() { return notApplicableCount; }
    public void setNotApplicableCount(Long v) { this.notApplicableCount = v; }

    public BigDecimal getActiveStockValue() { return activeStockValue; }
    public void setActiveStockValue(BigDecimal v) { this.activeStockValue = v; }

    public BigDecimal getAverageActiveValue() { return averageActiveValue; }
    public void setAverageActiveValue(BigDecimal v) { this.averageActiveValue = v; }

    public BigDecimal getTotalIncome() { return totalIncome; }
    public void setTotalIncome(BigDecimal v) { this.totalIncome = v; }

    public BigDecimal getSalesRevenue() { return salesRevenue; }
    public void setSalesRevenue(BigDecimal v) { this.salesRevenue = v; }

    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal v) { this.totalExpenses = v; }

    public BigDecimal getTreatmentCosts() { return treatmentCosts; }
    public void setTreatmentCosts(BigDecimal v) { this.treatmentCosts = v; }

    public BigDecimal getPreventiveCosts() { return preventiveCosts; }
    public void setPreventiveCosts(BigDecimal v) { this.preventiveCosts = v; }

    public BigDecimal getCurativeCosts() { return curativeCosts; }
    public void setCurativeCosts(BigDecimal v) { this.curativeCosts = v; }

    public BigDecimal getPurchaseCosts() { return purchaseCosts; }
    public void setPurchaseCosts(BigDecimal v) { this.purchaseCosts = v; }

    public BigDecimal getDeathLoss() { return deathLoss; }
    public void setDeathLoss(BigDecimal v) { this.deathLoss = v; }

    public BigDecimal getBornAnimalsValue() { return bornAnimalsValue; }
    public void setBornAnimalsValue(BigDecimal v) { this.bornAnimalsValue = v; }

    public Long getTotalTreatmentsCount() { return totalTreatmentsCount; }
    public void setTotalTreatmentsCount(Long v) { this.totalTreatmentsCount = v; }

    public Long getTotalSalesCount() { return totalSalesCount; }
    public void setTotalSalesCount(Long v) { this.totalSalesCount = v; }

    public Integer getPreventiveSharePct() { return preventiveSharePct; }
    public void setPreventiveSharePct(Integer v) { this.preventiveSharePct = v; }

    public Indicators getIndicators() { return indicators; }
    public void setIndicators(Indicators v) { this.indicators = v; }

    public Alerts getAlerts() { return alerts; }
    public void setAlerts(Alerts v) { this.alerts = v; }

    public List<CategoryPerformance> getCategoryPerformance() { return categoryPerformance; }
    public void setCategoryPerformance(List<CategoryPerformance> v) { this.categoryPerformance = v; }

    public List<CategoryIndicators> getCategoryIndicators() { return categoryIndicators; }
    public void setCategoryIndicators(List<CategoryIndicators> v) { this.categoryIndicators = v; }

    public List<String> getMonthLabels() { return monthLabels; }
    public void setMonthLabels(List<String> v) { this.monthLabels = v; }

    public List<BigDecimal> getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(List<BigDecimal> v) { this.monthlyRevenue = v; }

    public List<BigDecimal> getMonthlyCosts() { return monthlyCosts; }
    public void setMonthlyCosts(List<BigDecimal> v) { this.monthlyCosts = v; }

    public List<Long> getMonthlyBirths() { return monthlyBirths; }
    public void setMonthlyBirths(List<Long> v) { this.monthlyBirths = v; }

    public List<Long> getMonthlySales() { return monthlySales; }
    public void setMonthlySales(List<Long> v) { this.monthlySales = v; }

    public List<Long> getMonthlyDeaths() { return monthlyDeaths; }
    public void setMonthlyDeaths(List<Long> v) { this.monthlyDeaths = v; }

    public List<Long> getMonthlyTreatments() { return monthlyTreatments; }
    public void setMonthlyTreatments(List<Long> v) { this.monthlyTreatments = v; }

    public Map<String, Long> getStatusTotalsByCategory() { return statusTotalsByCategory; }
    public void setStatusTotalsByCategory(Map<String, Long> v) { this.statusTotalsByCategory = v; }

    public Map<String, Map<String, Long>> getStatusByCategory() { return statusByCategory; }
    public void setStatusByCategory(Map<String, Map<String, Long>> v) { this.statusByCategory = v; }

    public Map<String, Long> getCategoryBreakdown() { return categoryBreakdown; }
    public void setCategoryBreakdown(Map<String, Long> v) { this.categoryBreakdown = v; }

    public Map<String, Long> getLocationBreakdown() { return locationBreakdown; }
    public void setLocationBreakdown(Map<String, Long> v) { this.locationBreakdown = v; }

    public Map<String, Long> getGenderBreakdown() { return genderBreakdown; }
    public void setGenderBreakdown(Map<String, Long> v) { this.genderBreakdown = v; }

    public Map<String, Long> getHealthBreakdown() { return healthBreakdown; }
    public void setHealthBreakdown(Map<String, Long> v) { this.healthBreakdown = v; }

    public Map<String, Long> getReproBreakdown() { return reproBreakdown; }
    public void setReproBreakdown(Map<String, Long> v) { this.reproBreakdown = v; }

    public Integer getMonths() { return months; }
    public void setMonths(Integer v) { this.months = v; }

    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String v) { this.periodLabel = v; }

    public List<ActiveAnimal> getActiveAnimals() { return activeAnimals; }
    public void setActiveAnimals(List<ActiveAnimal> v) { this.activeAnimals = v; }

    // ═══════════════════════════════════════════════════════════════════
    //  NESTED DTOs
    // ═══════════════════════════════════════════════════════════════════

    public static class Indicators {
        private Double mortalityRate;
        private Double offtakeRate;
        private Double replacementRate;
        private BigDecimal avgSalePrice;

        public Double getMortalityRate() { return mortalityRate; }
        public void setMortalityRate(Double v) { this.mortalityRate = v; }

        public Double getOfftakeRate() { return offtakeRate; }
        public void setOfftakeRate(Double v) { this.offtakeRate = v; }

        public Double getReplacementRate() { return replacementRate; }
        public void setReplacementRate(Double v) { this.replacementRate = v; }

        public BigDecimal getAvgSalePrice() { return avgSalePrice; }
        public void setAvgSalePrice(BigDecimal v) { this.avgSalePrice = v; }
    }

    public static class Alerts {
        private Long unpaidTreatments;
        private BigDecimal unpaidTreatmentValue;
        private Long ongoingTreatments;
        private Long activeSickCases;
        private Long pregnantCount;

        public Long getUnpaidTreatments() { return unpaidTreatments; }
        public void setUnpaidTreatments(Long v) { this.unpaidTreatments = v; }

        public BigDecimal getUnpaidTreatmentValue() { return unpaidTreatmentValue; }
        public void setUnpaidTreatmentValue(BigDecimal v) { this.unpaidTreatmentValue = v; }

        public Long getOngoingTreatments() { return ongoingTreatments; }
        public void setOngoingTreatments(Long v) { this.ongoingTreatments = v; }

        public Long getActiveSickCases() { return activeSickCases; }
        public void setActiveSickCases(Long v) { this.activeSickCases = v; }

        public Long getPregnantCount() { return pregnantCount; }
        public void setPregnantCount(Long v) { this.pregnantCount = v; }
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
        public void setName(String v) { this.name = v; }

        public Long getTotal() { return total; }
        public void setTotal(Long v) { this.total = v; }

        public Long getActive() { return active; }
        public void setActive(Long v) { this.active = v; }

        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal v) { this.revenue = v; }

        public BigDecimal getCosts() { return costs; }
        public void setCosts(BigDecimal v) { this.costs = v; }

        public BigDecimal getProfit() { return profit; }
        public void setProfit(BigDecimal v) { this.profit = v; }

        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal v) { this.value = v; }
    }

    public static class CategoryIndicators {
        private String name;
        private Long total;
        private Long active;
        private Long sold;
        private Long dead;
        private Double mortalityRate;
        private Double offtakeRate;
        private Double replacementRate;
        private BigDecimal avgSalePrice;

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public Long getTotal() { return total; }
        public void setTotal(Long v) { this.total = v; }

        public Long getActive() { return active; }
        public void setActive(Long v) { this.active = v; }

        public Long getSold() { return sold; }
        public void setSold(Long v) { this.sold = v; }

        public Long getDead() { return dead; }
        public void setDead(Long v) { this.dead = v; }

        public Double getMortalityRate() { return mortalityRate; }
        public void setMortalityRate(Double v) { this.mortalityRate = v; }

        public Double getOfftakeRate() { return offtakeRate; }
        public void setOfftakeRate(Double v) { this.offtakeRate = v; }

        public Double getReplacementRate() { return replacementRate; }
        public void setReplacementRate(Double v) { this.replacementRate = v; }

        public BigDecimal getAvgSalePrice() { return avgSalePrice; }
        public void setAvgSalePrice(BigDecimal v) { this.avgSalePrice = v; }
    }

    public static class ActiveAnimal {
        private String tagNumber;
        private String categoryName;
        private String gender;
        private String beneficiaryName;
        private String locationName;
        private java.time.LocalDate dateReceived;
        private BigDecimal currentValue;

        public String getTagNumber() { return tagNumber; }
        public void setTagNumber(String v) { this.tagNumber = v; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String v) { this.categoryName = v; }

        public String getGender() { return gender; }
        public void setGender(String v) { this.gender = v; }

        public String getBeneficiaryName() { return beneficiaryName; }
        public void setBeneficiaryName(String v) { this.beneficiaryName = v; }

        public String getLocationName() { return locationName; }
        public void setLocationName(String v) { this.locationName = v; }

        public java.time.LocalDate getDateReceived() { return dateReceived; }
        public void setDateReceived(java.time.LocalDate v) { this.dateReceived = v; }

        public BigDecimal getCurrentValue() { return currentValue; }
        public void setCurrentValue(BigDecimal v) { this.currentValue = v; }
    }
}