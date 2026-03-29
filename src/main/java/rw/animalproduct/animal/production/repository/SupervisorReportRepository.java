// ══════════════════════════════════════════════════════════════════════════════
// FILE 1 of 6:  SupervisorReportRepository.java
// Place at:  src/main/java/rw/animalproduct/animal/production/repository/
// Purpose:   All optimised JPQL queries for the supervisor report — avoids
//            the getAll() + Java-stream anti-pattern you currently use.
// ══════════════════════════════════════════════════════════════════════════════
package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// ── We need one interface per entity, but we keep them all in one file
//    so you can see exactly what to create. Split into separate files
//    when you copy them into your project. ─────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// 1.  AbaragizwaAmatungoRepository  (extend your existing one)
//     Add these methods to your existing AbaragizwaAmatungoRepository
// ─────────────────────────────────────────────────────────────────────────────

/*
 * ADD these methods to your existing AbaragizwaAmatungoRepository:
 *
 *  // All beneficiaries for one supervisor — single JOIN query, no getAll()
 *  List<AbaragizwaAmatungo> findByUhagarariyeAbororaId(UUID supervisorId);
 *
 *  // Paginated version for large datasets
 *  Page<AbaragizwaAmatungo> findByUhagarariyeAbororaId(UUID supervisorId, Pageable pageable);
 *
 *  // Count per supervisor (used in the supervisor summary list)
 *  @Query("SELECT a.uhagarariyeAborora.id, COUNT(a) " +
 *         "FROM AbaragizwaAmatungo a " +
 *         "WHERE a.uhagarariyeAborora IS NOT NULL " +
 *         "GROUP BY a.uhagarariyeAborora.id")
 *  List<Object[]> countByEachSupervisor();
 */

// ─────────────────────────────────────────────────────────────────────────────
// 2.  LivestockRepository  (extend your existing one)
//     Add these methods to your existing LivestockRepository
// ─────────────────────────────────────────────────────────────────────────────

/*
 * ADD these methods to your existing LivestockRepository:
 *
 *  // All animals for one beneficiary
 *  List<Livestock> findByAbaragizwaAmatungoId(UUID beneficiaryId);
 *
 *  // Paginated
 *  Page<Livestock> findByAbaragizwaAmatungoId(UUID beneficiaryId, Pageable pageable);
 *
 *  // Count active animals for a beneficiary
 *  long countByAbaragizwaAmatungoIdAndStatus(UUID beneficiaryId, String status);
 *
 *  // Born on farm (has a mother FK set)
 *  @Query("SELECT l FROM Livestock l WHERE l.abaragizwaAmatungo.id = :benId AND l.mother IS NOT NULL")
 *  List<Livestock> findBornOnFarmByBeneficiary(@Param("benId") UUID benId);
 *
 *  // Animal IDs for a beneficiary (lightweight — used by sick/treatment queries)
 *  @Query("SELECT l.id FROM Livestock l WHERE l.abaragizwaAmatungo.id = :benId")
 *  List<UUID> findIdsByBeneficiary(@Param("benId") UUID benId);
 *
 *  // Summary stats per beneficiary (used for the overview table)
 *  @Query("SELECT l.abaragizwaAmatungo.id, COUNT(l), " +
 *         "SUM(CASE WHEN l.status = 'ACTIVE'   THEN 1 ELSE 0 END), " +
 *         "SUM(CASE WHEN l.mother IS NOT NULL   THEN 1 ELSE 0 END), " +
 *         "SUM(CASE WHEN l.status = 'SOLD'      THEN 1 ELSE 0 END)  " +
 *         "FROM Livestock l " +
 *         "WHERE l.abaragizwaAmatungo.id IN :benIds " +
 *         "GROUP BY l.abaragizwaAmatungo.id")
 *  List<Object[]> livestockStatsByBeneficiaries(@Param("benIds") List<UUID> benIds);
 */

// ─────────────────────────────────────────────────────────────────────────────
// 3.  LivestockSickRepository  (extend your existing one)
// ─────────────────────────────────────────────────────────────────────────────

/*
 * ADD these methods to your existing LivestockSickRepository:
 *
 *  // Sick records for a set of animal IDs
 *  @Query("SELECT s FROM LivestockSick s WHERE s.livestock.id IN :animalIds")
 *  List<LivestockSick> findByLivestockIds(@Param("animalIds") List<UUID> animalIds);
 *
 *  // Aggregated sick stats per livestock within a beneficiary
 *  @Query("SELECT s.livestock.id, COUNT(s), " +
 *         "SUM(CASE WHEN s.status = 'CRITICAL'  THEN 1 ELSE 0 END), " +
 *         "SUM(CASE WHEN s.status = 'RECOVERED' THEN 1 ELSE 0 END), " +
 *         "SUM(COALESCE(s.treatmentCost, 0)) " +
 *         "FROM LivestockSick s " +
 *         "WHERE s.livestock.id IN :animalIds " +
 *         "GROUP BY s.livestock.id")
 *  List<Object[]> sickStatsByAnimalIds(@Param("animalIds") List<UUID> animalIds);
 *
 *  // Total sick cost for a beneficiary (all their animals)
 *  @Query("SELECT COALESCE(SUM(s.treatmentCost), 0) " +
 *         "FROM LivestockSick s " +
 *         "WHERE s.livestock.id IN :animalIds")
 *  BigDecimal totalSickCostByAnimalIds(@Param("animalIds") List<UUID> animalIds);
 */

// ─────────────────────────────────────────────────────────────────────────────
// 4.  LivestockTreatmentRepository  (extend your existing one)
// ─────────────────────────────────────────────────────────────────────────────

/*
 * ADD these methods to your existing LivestockTreatmentRepository:
 *
 *  // Treatment records for a set of animal IDs
 *  @Query("SELECT t FROM LivestockTreatment t WHERE t.livestock.id IN :animalIds")
 *  List<LivestockTreatment> findByLivestockIds(@Param("animalIds") List<UUID> animalIds);
 *
 *  // Aggregated treatment stats per livestock
 *  @Query("SELECT t.livestock.id, COUNT(t), SUM(COALESCE(t.treatmentCost, 0)) " +
 *         "FROM LivestockTreatment t " +
 *         "WHERE t.livestock.id IN :animalIds " +
 *         "GROUP BY t.livestock.id")
 *  List<Object[]> treatmentStatsByAnimalIds(@Param("animalIds") List<UUID> animalIds);
 *
 *  // Total treatment cost for a beneficiary
 *  @Query("SELECT COALESCE(SUM(t.treatmentCost), 0) " +
 *         "FROM LivestockTreatment t " +
 *         "WHERE t.livestock.id IN :animalIds")
 *  BigDecimal totalTreatmentCostByAnimalIds(@Param("animalIds") List<UUID> animalIds);
 */

// ─────────────────────────────────────────────────────────────────────────────
// HOW TO USE:  The controller (SupervisorReportController.java — FILE 2)
//              calls these methods directly instead of getAll() + stream filter.
//              This means ONE SQL query per data need instead of loading every
//              row in every table into memory each time.
// ─────────────────────────────────────────────────────────────────────────────

public class SupervisorReportRepository {
    // This class is just a documentation holder.
    // See the comments above — add the annotated methods to your EXISTING
    // repository interfaces.
}
