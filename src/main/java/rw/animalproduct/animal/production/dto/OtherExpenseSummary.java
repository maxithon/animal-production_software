package rw.animalproduct.animal.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data JPA interface-based projection for the Other Expenses list
 * page — same pattern as BuyerSummary, avoids N+1 and keeps the list
 * query to a single round trip.
 */
public interface OtherExpenseSummary {
    UUID getId();
    LocalDate getExpenseDate();
    String getCategory();
    String getDescription();
    BigDecimal getAmount();
    String getPaymentMethod();
    String getPaidTo();
    String getReceiptReference();
    String getNotes();
    LocalDateTime getCreatedAt();
    String getCreatedByUsername();
}