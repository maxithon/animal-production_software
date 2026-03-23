package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.LivestockCategory;

import java.util.Optional;
import java.util.UUID;

public interface LivestockCategoryRepository extends JpaRepository<LivestockCategory, UUID> {

    Optional<LivestockCategory> findByCode(String code);

    Optional<LivestockCategory> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}
