package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.UserTypeModule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTypeModuleRepository extends JpaRepository<UserTypeModule, Integer> {

    List<UserTypeModule> findByUserTypeId(UUID userTypeId);

    // findFirst, not find/get: your current data has no unique constraint yet
    // on (user_type_id, module_id), so there could technically be duplicate
    // rows. Run sql/01_add_veterinarian_role_and_constraints.sql to add the
    // constraint and this will always resolve to exactly one row going forward.
    Optional<UserTypeModule> findFirstByUserTypeIdAndModuleId(UUID userTypeId, Integer moduleId);

    void deleteByUserTypeId(UUID userTypeId);
}
