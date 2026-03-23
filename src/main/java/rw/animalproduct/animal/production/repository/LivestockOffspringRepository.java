package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.LivestockOffspring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivestockOffspringRepository extends JpaRepository<LivestockOffspring, UUID> {

    // All offspring records for a specific birth event
    List<LivestockOffspring> findByBirthEventId(UUID birthId);

    // Find the offspring record for a specific child animal
    Optional<LivestockOffspring> findByChildLivestockId(UUID childLivestockId);
}
