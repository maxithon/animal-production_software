package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Representative;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepresentativeRepository extends JpaRepository<Representative, UUID> {

    Optional<Representative> findByNid(String nid);

    List<Representative> findByLocationId(UUID locationId);

    List<Representative> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName);

    // ---- Added to support /livestock/location-distribution-report ----

    @Query("SELECT r FROM Representative r " +
            "WHERE r.location.id IN :locationIds " +
            "AND (r.isDeleted = false OR r.isDeleted IS NULL)")
    List<Representative> findByLocationIdInAndIsDeletedFalse(@Param("locationIds") List<UUID> locationIds);
}