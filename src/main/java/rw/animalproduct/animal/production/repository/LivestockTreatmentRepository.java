package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.LivestockTreatment;

import java.util.List;
import java.util.UUID;

public interface LivestockTreatmentRepository extends JpaRepository<LivestockTreatment, UUID> {
    List<LivestockTreatment> findByLivestockId(UUID livestockId);
}
