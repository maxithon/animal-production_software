package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockSick;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LivestockSickRepository extends JpaRepository<LivestockSick, UUID> {

    List<LivestockSick> findByLivestockId(UUID livestockId);

    @Query("SELECT DISTINCT s FROM LivestockSick s " +
           "LEFT JOIN FETCH s.statusHistory " +
           "LEFT JOIN FETCH s.livestock l " +
           "LEFT JOIN FETCH l.livestockCategory " +
           "WHERE s.reportedDate BETWEEN :from AND :to " +
           "ORDER BY s.reportedDate DESC")
    List<LivestockSick> findByReportedDateBetweenWithHistory(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);
}
