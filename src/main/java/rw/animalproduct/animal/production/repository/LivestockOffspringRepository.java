package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.LivestockOffspring;

import java.util.List;
import java.util.UUID;

public interface LivestockOffspringRepository extends JpaRepository<LivestockOffspring, UUID> {

    /**
     * Find all offspring for a specific birth event
     */
    List<LivestockOffspring> findByBirthEventId(UUID birthEventId);

    /**
     * Find all offspring for a specific birth event (non-deleted only)
     */
    @Query("SELECT o FROM LivestockOffspring o " +
            "WHERE o.birthEvent.id = :birthEventId " +
            "AND o.isDeleted = false")
    List<LivestockOffspring> findByBirthEventIdAndIsDeletedFalse(@Param("birthEventId") UUID birthEventId);

    /**
     * Find all offspring for a specific child livestock
     */
    @Query("SELECT o FROM LivestockOffspring o " +
            "WHERE o.childLivestock.id = :childLivestockId " +
            "AND o.isDeleted = false")
    List<LivestockOffspring> findByChildLivestockIdAndIsDeletedFalse(@Param("childLivestockId") UUID childLivestockId);

    /**
     * Count live offspring for a birth event
     */
    @Query("SELECT COUNT(o) FROM LivestockOffspring o " +
            "WHERE o.birthEvent.id = :birthEventId " +
            "AND o.isDeleted = false " +
            "AND o.isAlive = true")
    long countLiveOffspringByBirthEventId(@Param("birthEventId") UUID birthEventId);

    /**
     * Count offspring by gender for a birth event
     * This gets gender from the child livestock
     */
    @Query("SELECT COUNT(o) FROM LivestockOffspring o " +
            "JOIN o.childLivestock l " +
            "WHERE o.birthEvent.id = :birthEventId " +
            "AND o.isDeleted = false " +
            "AND l.gender = :gender")
    long countOffspringByGender(@Param("birthEventId") UUID birthEventId,
                                @Param("gender") String gender);
}