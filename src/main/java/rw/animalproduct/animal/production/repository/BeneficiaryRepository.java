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

    // NEW: paginated version to support the representative-filtered list view
    Page<Beneficiary> findByRepresentativeId(UUID representativesAbororaId, Pageable pageable);

    List<Beneficiary> findByFirstNameContainingOrLastNameContaining(String firstName, String lastName);

    // Find all beneficiaries by location
    List<Beneficiary> findByLocationId(UUID locationId);

    // Find beneficiaries by location with a custom query (alternative method)
    @Query("SELECT a FROM Beneficiary a WHERE a.location.id = :locationId")
    List<Beneficiary> findBeneficiariesByLocation(@Param("locationId") UUID locationId);

    // Find beneficiaries by representative and location
    @Query("SELECT a FROM Beneficiary a WHERE a.representative.id = :representativesId AND a.location.id = :locationId")
    List<Beneficiary> findByUhagarariyeAndLocation(@Param("representativesId") UUID representativesId,
                                                   @Param("locationId") UUID locationId);

    // Count beneficiaries by location
    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.location.id = :locationId")
    long countByLocation(@Param("locationId") UUID locationId);

    // Count beneficiaries by representative and location
    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.representative.id = :representativesId AND a.location.id = :locationId")
    long countByUhagarariyeAndLocation(@Param("representativesId") UUID representativesId,
                                       @Param("locationId") UUID locationId);

    // Pagination support
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

    // ---- Added to support /livestock/beneficiary-impact-report ----

    @Query("SELECT a.gender, COUNT(a) FROM Beneficiary a GROUP BY a.gender")
    List<Object[]> countByGenderGrouped();

    @Query("SELECT a.maritialStatus, COUNT(a) FROM Beneficiary a GROUP BY a.maritialStatus")
    List<Object[]> countByMaritalStatusGrouped();

    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.contractAgreement IS NOT NULL AND a.contractAgreement <> ''")
    long countWithContractAgreement();

    // ---- Added to support /livestock/location-distribution-report ----

    @Query("SELECT a FROM Beneficiary a " +
            "WHERE a.location.id IN :locationIds " +
            "AND (a.isDeleted = false OR a.isDeleted IS NULL)")
    List<Beneficiary> findByLocationIdInAndIsDeletedFalse(@Param("locationIds") List<UUID> locationIds);

    // ---- NEW: active/inactive status support ----

    Page<Beneficiary> findByStatus(String status, Pageable pageable);

    Page<Beneficiary> findByRepresentativeIdAndStatus(UUID representativesAbororaId, String status, Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT COUNT(a) FROM Beneficiary a WHERE a.representative.id = :representativesId AND a.status = :status")
    long countByUhagarariyeAndStatus(@Param("representativesId") UUID representativesId,
                                     @Param("status") String status);
}
