package rw.animalproduct.animal.production.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data interface projection for the Buyers list/table.
 *
 * WHY THIS EXISTS (fixes the "Buyers page is slow to display" bug):
 * The old code called buyerService.getAll() which returned full Buyer
 * entities, then the Thymeleaf template read buyer.totalPurchases for
 * EVERY row. Because "sales" is a @OneToMany(LAZY) collection,
 * buyer.getTotalPurchases() -> sales.size() triggers a *separate SQL
 * query per buyer* the first time it's touched (classic N+1 problem).
 * With, say, 200 buyers that's 200+ extra round trips to the database
 * on every single page load.
 *
 * This projection asks the database to compute the purchase count with
 * one single GROUP BY query, so the page loads in one query no matter
 * how many buyers exist.
 */
public interface BuyerSummary {
    UUID getId();
    String getFirstName();
    String getLastName();
    String getPhone();
    String getAddress();
    String getNationalId();
    String getEmail();
    String getBuyerType();
    String getPhoto();
    String getNotes();
    LocalDateTime getCreatedAt();
    Boolean getIsActive();
    Long getTotalPurchases();

    default String getFullName() {
        String f = getFirstName() != null ? getFirstName() : "";
        String l = getLastName() != null ? getLastName() : "";
        return (f + " " + l).trim();
    }
}