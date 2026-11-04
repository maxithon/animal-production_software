package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockWeightHistory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockWeightHistoryRepository
        extends JpaRepository<LivestockWeightHistory, UUID> {

    List<LivestockWeightHistory> findByLivestockIdAndIsDeletedFalseOrderByWeighedOnAsc(UUID livestockId);

    List<LivestockWeightHistory> findByLivestockIdAndIsDeletedFalseOrderByWeighedOnDesc(UUID livestockId);

    Page<LivestockWeightHistory> findByLivestockIdAndIsDeletedFalseOrderByWeighedOnDesc(
            UUID livestockId, Pageable pageable);

    Optional<LivestockWeightHistory> findFirstByLivestockIdAndIsDeletedFalseOrderByWeighedOnDesc(
            UUID livestockId);

    long countByLivestockIdAndIsDeletedFalse(UUID livestockId);

    List<LivestockWeightHistory> findByWeighedOnBetweenAndIsDeletedFalse(LocalDate from, LocalDate to);

    @Query("""
           SELECT w FROM LivestockWeightHistory w
           WHERE w.isDeleted = false
             AND w.weighedOn = (
               SELECT MAX(w2.weighedOn) FROM LivestockWeightHistory w2
               WHERE w2.livestock.id = w.livestock.id
                 AND w2.isDeleted = false
             )
           """)
    List<LivestockWeightHistory> findLatestForAllAnimals();

    @Query("""
           SELECT w FROM LivestockWeightHistory w
           WHERE w.isDeleted = false
             AND w.weighedOn < :cutoff
             AND w.weighedOn = (
               SELECT MAX(w2.weighedOn) FROM LivestockWeightHistory w2
               WHERE w2.livestock.id = w.livestock.id
                 AND w2.isDeleted = false
             )
           """)
    List<LivestockWeightHistory> findStaleWeighings(@Param("cutoff") LocalDate cutoff);
}