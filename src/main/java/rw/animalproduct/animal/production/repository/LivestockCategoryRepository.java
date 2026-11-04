package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockCategoryRepository extends JpaRepository<LivestockCategory, UUID> {

    // ---------------------------------------------------------
    // Basic lookups
    // ---------------------------------------------------------

    Optional<LivestockCategory> findByCode(String code);

    Optional<LivestockCategory> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    // ---------------------------------------------------------
    // Active categories (all are active now)
    // ---------------------------------------------------------

    List<LivestockCategory> findAllByOrderByNameAsc();

    List<LivestockCategory> findAllByOrderByCodeAsc();

    // ---------------------------------------------------------
    // Duplicate checks
    // ---------------------------------------------------------

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByNameAndIdNot(String name, UUID id);
}