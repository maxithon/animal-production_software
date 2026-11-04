package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockBirth;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockBirthRepository extends JpaRepository<LivestockBirth, UUID> {

    Page<LivestockBirth> findAllByOrderByBirthDateDesc(Pageable pageable);
    List<LivestockBirth> findAllByOrderByBirthDateDesc();

    List<LivestockBirth> findByLivestockId(UUID livestockId);
    List<LivestockBirth> findByLivestockIdOrderByBirthDateAsc(UUID livestockId);
    List<LivestockBirth> findByLivestockIdIn(List<UUID> livestockIds);

    Optional<LivestockBirth> findFirstByLivestockIdOrderByBirthDateDesc(UUID livestockId);

    List<LivestockBirth> findByBirthDateBetween(LocalDate startDate, LocalDate endDate);

    long countByLivestockId(UUID livestockId);

    boolean existsByBreedingId(UUID breedingId);

    long countByCompletedAtIsNull();
    long countByCompletedAtIsNotNull();

    @Query("""
           SELECT b FROM LivestockBirth b
           JOIN b.children o
           WHERE o.childLivestock.id = :animalId
           """)
    Optional<LivestockBirth> findByChildAnimalId(@Param("animalId") UUID animalId);

    @Query("""
           SELECT b FROM LivestockBirth b
           LEFT JOIN FETCH b.breeding
           WHERE b.livestockId = :livestockId
           ORDER BY b.birthDate ASC
           """)
    List<LivestockBirth> findByLivestockIdWithBreeding(@Param("livestockId") UUID livestockId);
}