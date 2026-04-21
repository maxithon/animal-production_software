package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockAbortion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockAbortionRepository extends JpaRepository<LivestockAbortion, UUID> {

    List<LivestockAbortion> findByLivestockId(UUID livestockId);

    List<LivestockAbortion> findByAbortionDateBetween(LocalDate start, LocalDate end);

    List<LivestockAbortion> findByLivestockIdAndIsDeletedFalse(UUID livestockId);

    @Query("SELECT a FROM LivestockAbortion a WHERE a.isDeleted = false ORDER BY a.abortionDate DESC")
    List<LivestockAbortion> findAllActive();

    long countByAbortionDateBetween(LocalDate start, LocalDate end);

    long countByLivestockIdAndIsDeletedFalse(UUID livestockId);

    // Fixed: Using abortionDate instead of expectedBirthDate
    @Query("SELECT a FROM LivestockAbortion a WHERE a.abortionDate >= :date AND a.isDeleted = false ORDER BY a.abortionDate ASC")
    List<LivestockAbortion> findUpcomingExpectedBirths(@Param("date") LocalDate date);
}