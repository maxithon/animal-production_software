package rw.animalproduct.animal.production.dto;

import java.util.UUID;

/**
 * Lightweight wrapper so livestock-category-filter-report.html can call
 * ${cat.livestockCount} in Thymeleaf. LivestockCategory itself intentionally
 * has no @OneToMany to Livestock (removed earlier to avoid loading the whole
 * livestock table), so this DTO is the correct place for the count instead
 * of putting it back on the entity.
 */
public class CategoryWithCount {

    private final UUID id;
    private final String name;
    private final String code;
    private final long livestockCount;

    public CategoryWithCount(UUID id, String name, String code, long livestockCount) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.livestockCount = livestockCount;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public long getLivestockCount() { return livestockCount; }
}
