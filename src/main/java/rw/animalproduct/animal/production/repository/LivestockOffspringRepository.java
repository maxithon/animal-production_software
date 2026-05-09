package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockOffspring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockOffspringRepository extends JpaRepository<LivestockOffspring, UUID> {

    // Find by birth ID (using the birthEvent field)
    List<LivestockOffspring> findByBirthEventId(UUID birthId);

    // Alternative method name if you prefer
    default List<LivestockOffspring> findByBirthId(UUID birthId) {
        return findByBirthEventId(birthId);
    }

    // Find by child livestock ID
    Optional<LivestockOffspring> findByChildLivestockId(UUID childLivestockId);

    // Find all offspring for a specific mother (through births)
    @Query("SELECT o FROM LivestockOffspring o WHERE o.birthEvent.livestockId = :motherId")
    List<LivestockOffspring> findByMotherId(@Param("motherId") UUID motherId);

    // Check if a child is linked to any birth
    boolean existsByChildLivestockId(UUID childLivestockId);

    // Delete by child livestock ID
    void deleteByChildLivestockId(UUID childLivestockId);
}