package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.entity.LivestockValuation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LivestockValuationRepository extends JpaRepository<LivestockValuation, UUID> {

    // ═══════════════════════════════════════════════════════════════
    // SINGLE-ANIMAL QUERIES
    // ═══════════════════════════════════════════════════════════════

    Optional<LivestockValuation> findFirstByLivestockIdOrderByValuationDateDesc(UUID livestockId);

    List<LivestockValuation> findByLivestockIdOrderByValuationDateDesc(UUID livestockId);

    /** Initial valuation for one animal — used for purchase-cost calculation. */
    @Query("SELECT v FROM LivestockValuation v " +
            "WHERE v.livestock.id = :livestockId " +
            "AND v.valuationMethod = 'INITIAL' " +
            "ORDER BY v.valuationDate ASC")
    List<LivestockValuation> findInitialValuations(@Param("livestockId") UUID livestockId);

    // ═══════════════════════════════════════════════════════════════
    // BULK QUERIES (dashboard)
    // ═══════════════════════════════════════════════════════════════

    /** Latest valuations for a specific set of animals. */
    @Query("SELECT v FROM LivestockValuation v " +
            "WHERE v.livestock.id IN :livestockIds " +
            "AND v.valuationDate = (SELECT MAX(v2.valuationDate) " +
            "                      FROM LivestockValuation v2 " +
            "                      WHERE v2.livestock.id = v.livestock.id)")
    List<LivestockValuation> findLatestValuationsForLivestockIds(@Param("livestockIds") List<UUID> livestockIds);

    /** Bulk latest valuation per livestock — used by the dashboard for the whole herd. */
    @Query("SELECT v FROM LivestockValuation v " +
            "WHERE v.valuationDate = (SELECT MAX(v2.valuationDate) " +
            "                         FROM LivestockValuation v2 " +
            "                         WHERE v2.livestock.id = v.livestock.id)")
    List<LivestockValuation> findAllLatestValuations();

    // ═══════════════════════════════════════════════════════════════
    // ✅ NEW — bulk INITIAL valuations for the whole herd (kills N+1)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the earliest INITIAL valuation per livestock.
     * Replaces the per-animal findInitialValuations() call inside the
     * purchase-cost loop in DashboardController — one query instead of N.
     */
    @Query("SELECT v FROM LivestockValuation v " +
            "WHERE v.valuationMethod = 'INITIAL' " +
            "AND v.valuationDate = (SELECT MIN(v2.valuationDate) " +
            "                      FROM LivestockValuation v2 " +
            "                      WHERE v2.livestock.id = v.livestock.id " +
            "                      AND v2.valuationMethod = 'INITIAL')")
    List<LivestockValuation> findAllInitialValuations();

    // ═══════════════════════════════════════════════════════════════
    // AGGREGATES
    // ═══════════════════════════════════════════════════════════════

    @Query("SELECT SUM(v.estimatedValue) FROM LivestockValuation v " +
            "WHERE v.valuationDate = (SELECT MAX(v2.valuationDate) " +
            "                      FROM LivestockValuation v2 " +
            "                      WHERE v2.livestock.id = v.livestock.id)")
    Double getTotalLivestockValue();
}