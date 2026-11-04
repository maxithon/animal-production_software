package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.SalePayment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SalePaymentRepository extends JpaRepository<SalePayment, UUID> {

    List<SalePayment> findBySaleIdOrderByPaymentDateDesc(UUID saleId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SalePayment p WHERE p.sale.id = :saleId")
    BigDecimal sumAmountBySaleId(@Param("saleId") UUID saleId);
}