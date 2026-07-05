package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Location;

import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByLocationType(String locationType);

    List<Location> findByParentId(UUID parentId);

    @Query("SELECT l FROM Location l WHERE l.parent.id = :parentId")
    List<Location> findChildrenByParentId(@Param("parentId") UUID parentId);

    // Fetch a paginated set of locations restricted to a given list of IDs.
    // Spring Data derives this automatically from the method name: findAllBy + IdIn + Pageable
    Page<Location> findAllByIdIn(List<UUID> ids, Pageable pageable);
}