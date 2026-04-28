package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.Representative;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepresentativeRepository extends JpaRepository<Representative, UUID> {

    Optional<Representative> findByNid(String nid);

    List<Representative> findByLocationId(UUID locationId);

    List<Representative> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName);
}