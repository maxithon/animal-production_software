package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Representative;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepresentativeRepository extends JpaRepository<Representative, UUID> {

    Optional<Representative> findByNid(String nid);

    List<Representative> findByLocationId(UUID locationId);

    List<Representative> findByStatus(String status);

    Page<Representative> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    boolean existsByNid(String nid);

    boolean existsByEmail(String email);

    List<Representative> findByGender(String gender);

    long countByGender(String gender);

    List<Representative> findByOccupation(String occupation);

    // *************** FIXED ***************
    List<Representative> findByCreatedByUserId(UUID userId);
    // ************************************

    List<Representative> findByCreatedDateBetween(Date startDate, Date endDate);

    List<Representative> findByFirstNameContainingOrLastNameContaining(
            String firstName,
            String lastName);

    // Location-based methods
    List<Representative> findByLocationIdIn(List<UUID> locationIds);

    List<Representative> findByLocationIdInAndStatus(
            List<UUID> locationIds,
            String status);

    List<Representative> findByLocationIdAndStatus(
            UUID locationId,
            String status);

    long countByLocationId(UUID locationId);

    long countByLocationIdAndStatus(
            UUID locationId,
            String status);

    @Query("""
            SELECT r
            FROM Representative r
            WHERE r.location.id IN :locationIds
              AND r.isDeleted = false
              AND r.status = 'ACTIVE'
            """)
    List<Representative> findByLocationIdInAndIsDeletedFalse(
            @Param("locationIds") List<UUID> locationIds);

    @Query("""
            SELECT r
            FROM Representative r
            WHERE r.location.id IN :locationIds
              AND r.status = 'ACTIVE'
            """)
    List<Representative> findActiveByLocationIdIn(
            @Param("locationIds") List<UUID> locationIds);

    @Query("""
            SELECT r, COUNT(b)
            FROM Representative r
            LEFT JOIN Beneficiary b
                   ON b.representative = r
            GROUP BY r
            """)
    List<Object[]> findRepresentativesWithBeneficiaryCount();

    @Query("""
            SELECT r
            FROM Representative r
            WHERE
            (:keyword IS NULL OR
             LOWER(r.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
             LOWER(r.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
             LOWER(r.nid) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
             LOWER(r.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:status IS NULL OR r.status = :status)
            AND (:gender IS NULL OR r.gender = :gender)
            AND (:locationId IS NULL OR r.location.id = :locationId)
            """)
    List<Representative> findByAdvancedCriteria(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("gender") String gender,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT r
            FROM Representative r
            JOIN FETCH r.location
            WHERE r.id IN :ids
            """)
    List<Representative> findByIdsWithLocation(
            @Param("ids") List<UUID> ids);

    @Query("""
            SELECT r
            FROM Representative r
            JOIN FETCH r.location
            WHERE r.id = :id
            """)
    Optional<Representative> findByIdWithLocation(
            @Param("id") UUID id);
}