package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.UhagarariyeAborora;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UhagarariyeAbororaRepository extends JpaRepository<UhagarariyeAborora, UUID> {

    Optional<UhagarariyeAborora> findByNid(String nid);

    List<UhagarariyeAborora> findByLocationId(UUID locationId);

    List<UhagarariyeAborora> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName);
}