package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.Livestock;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Feeds livestock-category-report.html (and the paginated variant), which
 * reads: categoryId, categoryName, totalCount, activeCount, soldCount,
 * deadCount, sickCount, maleCount, femaleCount, pregnantCount, totalValue,
 * livestockList.
 */
public class CategoryStat {

    private UUID categoryId;
    private String categoryName;
    private long totalCount;
    private long activeCount;
    private long soldCount;
    private long deadCount;
    private long sickCount;
    private long maleCount;
    private long femaleCount;
    private long pregnantCount;
    private BigDecimal totalValue = BigDecimal.ZERO;
    private List<Livestock> livestockList;

    // ── Getters & setters ───────────────────────────────────────────────
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public long getActiveCount() { return activeCount; }
    public void setActiveCount(long activeCount) { this.activeCount = activeCount; }

    public long getSoldCount() { return soldCount; }
    public void setSoldCount(long soldCount) { this.soldCount = soldCount; }

    public long getDeadCount() { return deadCount; }
    public void setDeadCount(long deadCount) { this.deadCount = deadCount; }

    public long getSickCount() { return sickCount; }
    public void setSickCount(long sickCount) { this.sickCount = sickCount; }

    public long getMaleCount() { return maleCount; }
    public void setMaleCount(long maleCount) { this.maleCount = maleCount; }

    public long getFemaleCount() { return femaleCount; }
    public void setFemaleCount(long femaleCount) { this.femaleCount = femaleCount; }

    public long getPregnantCount() { return pregnantCount; }
    public void setPregnantCount(long pregnantCount) { this.pregnantCount = pregnantCount; }

    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

    public List<Livestock> getLivestockList() { return livestockList; }
    public void setLivestockList(List<Livestock> livestockList) { this.livestockList = livestockList; }
}
