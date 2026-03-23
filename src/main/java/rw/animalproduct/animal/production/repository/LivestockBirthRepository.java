package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.LivestockBirth;

import java.util.List;
import java.util.UUID;

public interface LivestockBirthRepository extends JpaRepository<LivestockBirth, UUID> {

    // All births where this animal was the mother
    List<LivestockBirth> findByLivestockId(UUID livestockId);

    // Paginated list of all births
    Page<LivestockBirth> findAll(Pageable pageable);
}
