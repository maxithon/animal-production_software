package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.LivestockAbortion;

import java.util.List;
import java.util.UUID;

public interface LivestockAbortionRepository extends JpaRepository<LivestockAbortion, UUID> {
    List<LivestockAbortion> findByLivestockId(UUID livestockId);
}
