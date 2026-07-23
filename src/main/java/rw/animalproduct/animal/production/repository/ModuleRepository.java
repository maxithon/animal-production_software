package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.Module;

import java.util.List;

public interface ModuleRepository extends JpaRepository<Module, Integer> {

    List<Module> findByActiveTrueOrderByDisplayOrderAsc();
}
