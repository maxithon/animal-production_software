package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.AbaragizwaAmatungo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbaragizwaAmatungoRepository extends JpaRepository<AbaragizwaAmatungo, UUID> {

    Optional<AbaragizwaAmatungo> findByNid(String nid);

    List<AbaragizwaAmatungo> findByUhagarariyeAbororaId(UUID uhagarariyeAbororaId);

    List<AbaragizwaAmatungo> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName);

    // Find all beneficiaries by location
    List<AbaragizwaAmatungo> findByLocationId(UUID locationId);

    // Find beneficiaries by location with a custom query (alternative method)
    @Query("SELECT a FROM AbaragizwaAmatungo a WHERE a.location.id = :locationId")
    List<AbaragizwaAmatungo> findBeneficiariesByLocation(@Param("locationId") UUID locationId);

    // Find beneficiaries by representative and location
    @Query("SELECT a FROM AbaragizwaAmatungo a WHERE a.uhagarariyeAborora.id = :uhagarariyeId AND a.location.id = :locationId")
    List<AbaragizwaAmatungo> findByUhagarariyeAndLocation(@Param("uhagarariyeId") UUID uhagarariyeId,
                                                          @Param("locationId") UUID locationId);

    // Count beneficiaries by location
    @Query("SELECT COUNT(a) FROM AbaragizwaAmatungo a WHERE a.location.id = :locationId")
    long countByLocation(@Param("locationId") UUID locationId);

    // Count beneficiaries by representative and location
    @Query("SELECT COUNT(a) FROM AbaragizwaAmatungo a WHERE a.uhagarariyeAborora.id = :uhagarariyeId AND a.location.id = :locationId")
    long countByUhagarariyeAndLocation(@Param("uhagarariyeId") UUID uhagarariyeId,
                                       @Param("locationId") UUID locationId);

    // Pagination support
    Page<AbaragizwaAmatungo> findAll(Pageable pageable);

    Page<AbaragizwaAmatungo> findByLocationId(UUID locationId, Pageable pageable);

    @Query("SELECT a FROM AbaragizwaAmatungo a WHERE a.uhagarariyeAborora.id = :uhagarariyeId AND a.location.id = :locationId")
    Page<AbaragizwaAmatungo> findByUhagarariyeAndLocation(@Param("uhagarariyeId") UUID uhagarariyeId,
                                                          @Param("locationId") UUID locationId,
                                                          Pageable pageable);

    Page<AbaragizwaAmatungo> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName, Pageable pageable);
}