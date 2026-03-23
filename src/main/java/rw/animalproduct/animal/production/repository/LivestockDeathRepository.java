package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.LivestockDeath;

import java.util.List;
import java.util.UUID;

public interface LivestockDeathRepository extends JpaRepository<LivestockDeath, UUID> {
    List<LivestockDeath> findByLivestockId(UUID livestockId);
}
