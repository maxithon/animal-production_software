package rw.animalproduct.animal.production.dto;

import java.math.BigDecimal;

public class CategorySummaryDTO {
    private String categoryName;
    private Long numberOfAnimalsSold;
    private BigDecimal totalRevenue;
    private BigDecimal averageSalePrice;
    private BigDecimal totalGrossProfit;

    // Default constructor (required for some frameworks)
    public CategorySummaryDTO() {
    }

    // Full constructor
    public CategorySummaryDTO(String categoryName, Long numberOfAnimalsSold,
                              BigDecimal totalRevenue, BigDecimal averageSalePrice,
                              BigDecimal totalGrossProfit) {
        this.categoryName = categoryName;
        this.numberOfAnimalsSold = numberOfAnimalsSold;
        this.totalRevenue = totalRevenue;
        this.averageSalePrice = averageSalePrice;
        this.totalGrossProfit = totalGrossProfit;
    }

    // Getters and Setters
    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getNumberOfAnimalsSold() {
        return numberOfAnimalsSold;
    }

    public void setNumberOfAnimalsSold(Long numberOfAnimalsSold) {
        this.numberOfAnimalsSold = numberOfAnimalsSold;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getAverageSalePrice() {
        return averageSalePrice;
    }

    public void setAverageSalePrice(BigDecimal averageSalePrice) {
        this.averageSalePrice = averageSalePrice;
    }

    public BigDecimal getTotalGrossProfit() {
        return totalGrossProfit;
    }

    public void setTotalGrossProfit(BigDecimal totalGrossProfit) {
        this.totalGrossProfit = totalGrossProfit;
    }

    // Alias for Thymeleaf template compatibility
    public BigDecimal getTotalProfit() {
        return totalGrossProfit;
    }
}