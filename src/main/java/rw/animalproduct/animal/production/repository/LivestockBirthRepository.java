package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {
            "livestock",
            "livestock.livestockCategory",
            "children",
            "children.childLivestock",
            "children.childLivestock.livestockCategory",
            "children.childLivestock.mother",
            "children.childLivestock.father"
    })
    @Query("SELECT b FROM LivestockBirth b WHERE b.id = :id")
    Optional<LivestockBirth> findWithChildrenById(@Param("id") UUID id);

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

    @Query("""
           SELECT DISTINCT b.livestockId FROM LivestockBirth b
           WHERE b.id IN :ids AND b.livestockId IS NOT NULL
           """)
    List<UUID> findMotherIdsByBirthIds(@Param("ids") List<UUID> ids);

    @Query("""
           SELECT c.name,
                  COUNT(b.id),
                  COUNT(b.completedAt)
           FROM LivestockBirth b
           JOIN b.livestock m
           JOIN m.livestockCategory c
           GROUP BY c.name
           ORDER BY c.name
           """)
    List<Object[]> aggregateCategoryBirthStats();
}