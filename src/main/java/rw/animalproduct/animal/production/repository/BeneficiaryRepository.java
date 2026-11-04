package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Beneficiary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    Optional<Beneficiary> findByNid(String nid);

    List<Beneficiary> findByRepresentativeId(UUID representativesAbororaId);

    Page<Beneficiary> findByRepresentativeId(UUID representativesAbororaId, Pageable pageable);

    List<Beneficiary> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName);

    List<Beneficiary> findByLocationId(UUID locationId);

    @Query("SELECT a FROM Beneficiary a WHERE a.location.id = :locationId")
    List<Beneficiary> findBeneficiariesByLocation(@Param("locationId") UUID locationId);

    @Query("SELECT a FROM Beneficiary a WHERE a.representative.id = :representativesId AND a.location.id = :locationId")
    List<Beneficiary> findByUhagarariyeAndLocation(@Param("representativesId") UUID representativesId,
                                                   @Param("locationId") UUID locationId);

    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.location.id = :locationId")
    long countByLocation(@Param("locationId") UUID locationId);

    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.representative.id = :representativesId AND a.location.id = :locationId")
    long countByUhagarariyeAndLocation(@Param("representativesId") UUID representativesId,
                                       @Param("locationId") UUID locationId);

    Page<Beneficiary> findAll(Pageable pageable);

    Page<Beneficiary> findByLocationId(UUID locationId, Pageable pageable);

    @Query("SELECT a FROM Beneficiary a WHERE a.representative.id = :representativesId AND a.location.id = :locationId")
    Page<Beneficiary> findByUhagarariyeAndLocation(@Param("representativesId") UUID representativesId,
                                                   @Param("locationId") UUID locationId,
                                                   Pageable pageable);

    Page<Beneficiary> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName, Pageable pageable);

    @Query("""
        SELECT a.representative.id, COUNT(a)
        FROM Beneficiary a
        WHERE a.representative IS NOT NULL
        GROUP BY a.representative.id
    """)
    List<Object[]> countByEachSupervisor();

    @Query("SELECT a.gender, COUNT(a) FROM Beneficiary a GROUP BY a.gender")
    List<Object[]> countByGenderGrouped();

    @Query("SELECT a.maritialStatus, COUNT(a) FROM Beneficiary a GROUP BY a.maritialStatus")
    List<Object[]> countByMaritalStatusGrouped();

    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.contractAgreement IS NOT NULL AND a.contractAgreement <> ''")
    long countWithContractAgreement();

    @Query("SELECT a FROM Beneficiary a " +
            "WHERE a.location.id IN :locationIds " +
            "AND (a.isDeleted = false OR a.isDeleted IS NULL)")
    List<Beneficiary> findByLocationIdInAndIsDeletedFalse(@Param("locationIds") List<UUID> locationIds);

    Page<Beneficiary> findByStatus(String status, Pageable pageable);

    Page<Beneficiary> findByRepresentativeIdAndStatus(UUID representativesAbororaId, String status, Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.representative.id = :representativesId AND a.status = :status")
    long countByUhagarariyeAndStatus(@Param("representativesId") UUID representativesId,
                                     @Param("status") String status);

    // ═══════════════════════════════════════════════════════════════════════
    //  ★ Dependency counts — used by RepresentativeService.delete()/toggleStatus()
    // ═══════════════════════════════════════════════════════════════════════

    long countByRepresentativeId(UUID representativeId);

    long countByRepresentativeIdAndStatus(UUID representativeId, String status);

    @Query("""
        SELECT b.representative.id, COUNT(b)
        FROM Beneficiary b
        WHERE b.representative IS NOT NULL
          AND b.status = 'ACTIVE'
          AND (b.isDeleted = false OR b.isDeleted IS NULL)
        GROUP BY b.representative.id
    """)
    List<Object[]> countActiveByEachRepresentative();
}