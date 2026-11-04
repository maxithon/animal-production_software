package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockImportBatch;

import java.util.List;
import java.util.UUID;

@Repository
public interface LivestockImportBatchRepository
        extends JpaRepository<LivestockImportBatch, UUID> {

    Page<LivestockImportBatch> findAllByOrderByImportedAtDesc(Pageable pageable);

    List<LivestockImportBatch> findTop20ByOrderByImportedAtDesc();

    List<LivestockImportBatch> findByImportedByOrderByImportedAtDesc(UUID userId);
}