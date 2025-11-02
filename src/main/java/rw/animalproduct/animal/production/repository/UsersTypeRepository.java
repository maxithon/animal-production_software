package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.UsersType;

import java.util.UUID;

public interface UsersTypeRepository extends JpaRepository<UsersType, UUID> {
}
