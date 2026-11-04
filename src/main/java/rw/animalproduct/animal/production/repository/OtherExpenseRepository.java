package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.dto.OtherExpenseSummary;
import rw.animalproduct.animal.production.entity.OtherExpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OtherExpenseRepository extends JpaRepository<OtherExpense, UUID> {

    @Query("SELECT e.id as id, e.expenseDate as expenseDate, e.category as category, " +
            "e.description as description, e.amount as amount, " +
            "e.paymentMethod as paymentMethod, e.paidTo as paidTo, " +
            "e.receiptReference as receiptReference, e.notes as notes, " +
            "e.createdAt as createdAt, e.createdByUsername as createdByUsername " +
            "FROM OtherExpense e " +
            "WHERE e.isDeleted = false " +
            "ORDER BY e.expenseDate DESC, e.createdAt DESC")
    List<OtherExpenseSummary> findAllSummaries();

    @Query("SELECT e.id as id, e.expenseDate as expenseDate, e.category as category, " +
            "e.description as description, e.amount as amount, " +
            "e.paymentMethod as paymentMethod, e.paidTo as paidTo, " +
            "e.receiptReference as receiptReference, e.notes as notes, " +
            "e.createdAt as createdAt, e.createdByUsername as createdByUsername " +
            "FROM OtherExpense e " +
            "WHERE e.isDeleted = false AND (" +
            "  LOWER(e.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(e.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(e.paidTo) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(e.receiptReference) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(e.notes) LIKE LOWER(CONCAT('%', :search, '%'))" +
            ") " +
            "ORDER BY e.expenseDate DESC, e.createdAt DESC")
    List<OtherExpenseSummary> searchSummaries(@Param("search") String search);

    // ── KPIs ───────────────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM OtherExpense e " +
            "WHERE e.isDeleted = false AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal sumBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM OtherExpense e WHERE e.isDeleted = false")
    BigDecimal sumAll();

    @Query("SELECT COUNT(e) FROM OtherExpense e WHERE e.isDeleted = false")
    long countAll();

    @Query("SELECT e.id as id, e.expenseDate as expenseDate, e.category as category, " +
            "e.description as description, e.amount as amount, " +
            "e.paymentMethod as paymentMethod, e.paidTo as paidTo, " +
            "e.receiptReference as receiptReference, e.notes as notes, " +
            "e.createdAt as createdAt, e.createdByUsername as createdByUsername " +
            "FROM OtherExpense e " +
            "WHERE e.isDeleted = false " +
            "AND e.expenseDate BETWEEN :start AND :end " +
            "AND (:category IS NULL OR e.category = :category) " +
            "ORDER BY e.expenseDate DESC, e.createdAt DESC")
    List<OtherExpenseSummary> findFiltered(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end,
                                           @Param("category") String category);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM OtherExpense e " +
            "WHERE e.isDeleted = false AND e.expenseDate BETWEEN :start AND :end " +
            "AND (:category IS NULL OR e.category = :category)")
    BigDecimal sumFiltered(@Param("start") LocalDate start,
                           @Param("end") LocalDate end,
                           @Param("category") String category);

    @Query("SELECT e.category as category, COALESCE(SUM(e.amount), 0) as total " +
            "FROM OtherExpense e WHERE e.isDeleted = false " +
            "GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<CategoryTotal> sumByCategory();

    @Query("SELECT e.category as category, COALESCE(SUM(e.amount), 0) as total " +
            "FROM OtherExpense e " +
            "WHERE e.isDeleted = false AND e.expenseDate BETWEEN :start AND :end " +
            "GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<CategoryTotal> sumByCategoryBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT FUNCTION('to_char', e.expenseDate, 'YYYY-MM') as month, " +
            "COALESCE(SUM(e.amount), 0) as total " +
            "FROM OtherExpense e " +
            "WHERE e.isDeleted = false AND e.expenseDate BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('to_char', e.expenseDate, 'YYYY-MM') " +
            "ORDER BY month ASC")
    List<MonthlyTotal> sumByMonthBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // ── Distinct categories for the report filter dropdown ─────────────
    @Query("SELECT DISTINCT e.category FROM OtherExpense e WHERE e.isDeleted = false ORDER BY e.category")
    List<String> findDistinctCategories();

    interface CategoryTotal {
        String getCategory();
        BigDecimal getTotal();
    }

    interface MonthlyTotal {
        String getMonth();
        BigDecimal getTotal();
    }
}
