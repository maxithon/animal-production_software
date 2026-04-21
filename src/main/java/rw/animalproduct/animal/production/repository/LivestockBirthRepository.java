package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockBirth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivestockBirthRepository extends JpaRepository<LivestockBirth, UUID> {

    List<LivestockBirth> findByLivestockId(UUID livestockId);

    Page<LivestockBirth> findAll(Pageable pageable);

    /**
     * Find the birth record for a specific animal (child).
     * This queries through the livestock_offspring table to find
     * which birth event this animal belongs to.
     */
    @Query("SELECT b FROM LivestockBirth b " +
            "JOIN b.children o " +
            "WHERE o.childLivestock.id = :animalId")
    Optional<LivestockBirth> findByChildAnimalId(@Param("animalId") UUID animalId);
}