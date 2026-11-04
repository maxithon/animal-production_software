package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockSalePayment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LivestockSalePaymentRepository extends JpaRepository<LivestockSalePayment, UUID> {

    @Query("SELECT p FROM LivestockSalePayment p WHERE p.sale.id = :saleId ORDER BY p.paymentDate DESC")
    List<LivestockSalePayment> findPaymentsBySaleId(@Param("saleId") UUID saleId);

    // ✅ ADD THIS METHOD
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM LivestockSalePayment p WHERE p.sale.id = :saleId")
    BigDecimal sumAmountBySaleId(@Param("saleId") UUID saleId);
}