package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import rw.animalproduct.animal.production.entity.LivestockCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivestockCategoryRepository extends JpaRepository<LivestockCategory, UUID> {

    Optional<LivestockCategory> findByCode(String code);

    Optional<LivestockCategory> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    // ── NEW: Find all non-deleted categories ──────────────────────────────────
    @Query("SELECT c FROM LivestockCategory c WHERE c.isDeleted = false OR c.isDeleted IS NULL")
    List<LivestockCategory> findAllByIsDeletedFalse();
}