package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockTreatmentPayment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LivestockTreatmentPaymentRepository
        extends JpaRepository<LivestockTreatmentPayment, UUID> {

    @Query("SELECT p FROM LivestockTreatmentPayment p " +
            "WHERE p.treatment.id = :treatmentId AND (p.isDeleted = false OR p.isDeleted IS NULL) " +
            "ORDER BY p.paymentDate DESC, p.recordedAt DESC")
    List<LivestockTreatmentPayment> findByTreatmentId(@Param("treatmentId") UUID treatmentId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM LivestockTreatmentPayment p " +
            "WHERE p.treatment.id = :treatmentId AND (p.isDeleted = false OR p.isDeleted IS NULL)")
    BigDecimal sumAmountByTreatmentId(@Param("treatmentId") UUID treatmentId);
}