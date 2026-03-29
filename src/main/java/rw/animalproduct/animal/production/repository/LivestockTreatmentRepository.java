package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockTreatment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LivestockTreatmentRepository extends JpaRepository<LivestockTreatment, UUID> {
    List<LivestockTreatment> findByLivestockId(UUID livestockId);

    @Query("SELECT t FROM LivestockTreatment t WHERE t.livestock.id IN :animalIds")
    List<LivestockTreatment> findByLivestockIds(@Param("animalIds") List<UUID> animalIds);

    @Query("""
        SELECT t.livestock.id,
               COUNT(t),
               SUM(COALESCE(t.treatmentCost, 0))
        FROM LivestockTreatment t
        WHERE t.livestock.id IN :animalIds
        GROUP BY t.livestock.id
    """)
    List<Object[]> treatmentStatsByAnimalIds(@Param("animalIds") List<UUID> animalIds);

    @Query("""
        SELECT COALESCE(SUM(t.treatmentCost), 0)
        FROM LivestockTreatment t
        WHERE t.livestock.id IN :animalIds
    """)
    BigDecimal totalTreatmentCostByAnimalIds(@Param("animalIds") List<UUID> animalIds);

}
