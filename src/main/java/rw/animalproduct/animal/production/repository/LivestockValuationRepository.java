package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockValuation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivestockValuationRepository extends JpaRepository<LivestockValuation, UUID> {

    // ── Full history, newest first ────────────────────────────────────────────
    @Query("SELECT v FROM LivestockValuation v WHERE v.livestock.id = :livestockId " +
            "ORDER BY v.valuationDate DESC, v.createdAt DESC")
    List<LivestockValuation> findHistoryForLivestock(@Param("livestockId") UUID livestockId);

    // ── Most recent valuation only ────────────────────────────────────────────
    @Query("SELECT v FROM LivestockValuation v WHERE v.livestock.id = :livestockId " +
            "ORDER BY v.valuationDate DESC, v.createdAt DESC LIMIT 1")
    Optional<LivestockValuation> findLatestForLivestock(@Param("livestockId") UUID livestockId);

    long countByLivestockId(UUID livestockId);

    // ── Useful for herd-level reporting: sum of latest valuation per animal ──
    @Query("SELECT v FROM LivestockValuation v " +
            "WHERE v.id IN (" +
            "  SELECT MAX(v2.id) FROM LivestockValuation v2 " +
            "  WHERE v2.valuationDate = (" +
            "    SELECT MAX(v3.valuationDate) FROM LivestockValuation v3 " +
            "    WHERE v3.livestock.id = v2.livestock.id" +
            "  ) GROUP BY v2.livestock.id" +
            ")")
    List<LivestockValuation> findAllLatestValuations();

    // ── NEW: same "latest per animal" pattern as findAllLatestValuations(),
    // but scoped to a specific set of livestock ids. Used by the list page
    // to fetch valuation badges for exactly the animals on the current page,
    // without pulling the entire herd's latest valuations every time.
    // The MAX(id) tie-break avoids the double-row problem you'd get from a
    // plain "valuationDate = MAX(valuationDate)" match when two valuations
    // for the same animal share a date.
    @Query("SELECT v FROM LivestockValuation v " +
            "WHERE v.id IN (" +
            "  SELECT MAX(v2.id) FROM LivestockValuation v2 " +
            "  WHERE v2.livestock.id IN :livestockIds " +
            "  AND v2.valuationDate = (" +
            "    SELECT MAX(v3.valuationDate) FROM LivestockValuation v3 " +
            "    WHERE v3.livestock.id = v2.livestock.id" +
            "  ) GROUP BY v2.livestock.id" +
            ")")
    List<LivestockValuation> findLatestValuationsForLivestockIds(@Param("livestockIds") List<UUID> livestockIds);
}