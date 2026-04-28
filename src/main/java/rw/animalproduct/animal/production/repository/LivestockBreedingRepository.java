package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LivestockBreedingRepository extends JpaRepository<LivestockBreeding, UUID> {

    List<LivestockBreeding> findByStatus(String status);

    List<LivestockBreeding> findByExpectedPregnancyCheckDateBeforeAndStatus(LocalDate date, String status);

    List<LivestockBreeding> findByExpectedDueDateBetweenAndStatus(LocalDate from, LocalDate to, String status);

    List<LivestockBreeding> findByLivestockId(UUID livestockId);

    // Add pagination method
    Page<LivestockBreeding> findByIsDeletedFalse(Pageable pageable);
}