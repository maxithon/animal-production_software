package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.animalproduct.animal.production.entity.LivestockSale;

import java.util.List;
import java.util.UUID;

public interface LivestockSaleRepository extends JpaRepository<LivestockSale, UUID> {
    List<LivestockSale> findByLivestockId(UUID livestockId);
}
