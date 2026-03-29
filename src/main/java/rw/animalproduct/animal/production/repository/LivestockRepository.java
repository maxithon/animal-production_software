package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSick;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivestockRepository extends JpaRepository<Livestock, UUID> {

    Optional<Livestock> findByTagNumber(String tagNumber);

    List<Livestock> findByStatus(String status);

    List<Livestock> findByLivestockCategoryId(UUID categoryId);

    // Existing method may not work depending on your entity mapping
    List<Livestock> findByLivestockCategory_Id(UUID categoryId);

    // Pagination version if needed
    Page<Livestock> findByLivestockCategory_Id(UUID categoryId, Pageable pageable);

    List<Livestock> findByAbaragizwaAmatungoId(UUID abaragizwaId);

    List<Livestock> findByLocationId(UUID locationId);

    List<Livestock> findByGender(String gender);

    List<Livestock> findByIsPregnant(Boolean isPregnant);

    List<Livestock> findByTagNumberContaining(String tagNumber);

    // ── Mother / Child queries ───────────────────────────────────────

    // All direct children of a given mother
    List<Livestock> findByMotherId(UUID motherId);

    // Founding animals (no mother — purchased/donated)
    List<Livestock> findByMotherIsNull();

    // Does this animal have any children?
    boolean existsByMotherId(UUID motherId);

    // All female animals (potential mothers for birth dropdown)
    List<Livestock> findByGenderIgnoreCase(String gender);

    // ── Counts ──────────────────────────────────────────────────────

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.livestockCategory.id = :categoryId")
    long countByCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(l) FROM Livestock l WHERE l.abaragizwaAmatungo.id = :abaragizwaId")
    long countByAbaragizwa(@Param("abaragizwaId") UUID abaragizwaId);

    // ── Pagination ──────────────────────────────────────────────────

    Page<Livestock> findAll(Pageable pageable);

    Page<Livestock> findByStatus(String status, Pageable pageable);

    Page<Livestock> findByLivestockCategoryId(UUID categoryId, Pageable pageable);

    Page<Livestock> findByTagNumberContaining(String tagNumber, Pageable pageable);

    @Query("SELECT s FROM LivestockSick s WHERE s.livestock.id IN :animalIds")
    List<LivestockSick> findByLivestockIds(@Param("animalIds") List<UUID> animalIds);

}
