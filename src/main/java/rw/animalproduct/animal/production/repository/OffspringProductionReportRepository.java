package rw.animalproduct.animal.production.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.animalproduct.animal.production.entity.Livestock;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OffspringProductionReportRepository extends JpaRepository<Livestock, UUID> {

    @Query(value = """
        WITH birth_agg AS (
            SELECT
                lb.livestock_id AS mother_id,
                COUNT(*) AS number_of_births,
                COALESCE(SUM(lb.offspring_count), 0) AS total_offspring,
                COALESCE(SUM(lb.number_stillborn), 0) AS stillborn_count,
                MIN(lb.birth_date) AS first_birth_date,
                MAX(lb.birth_date) AS last_birth_date,
                COUNT(*) FILTER (
                    WHERE lb.weaning_date IS NOT NULL AND lb.weaning_date <= CURRENT_DATE
                ) AS weaned_births
            FROM livestock_births lb
            WHERE lb.is_deleted = false
              AND (CAST(:fromDate AS date) IS NULL OR lb.birth_date >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR lb.birth_date <= CAST(:toDate AS date))
            GROUP BY lb.livestock_id
        ),
        offspring_agg AS (
            SELECT
                lb.livestock_id AS mother_id,
                COUNT(*) FILTER (WHERE UPPER(cl.gender) = 'MALE')   AS male_offspring,
                COUNT(*) FILTER (WHERE UPPER(cl.gender) = 'FEMALE') AS female_offspring,
                COUNT(*) FILTER (WHERE lo.is_alive = false)         AS death_count,
                COUNT(*) FILTER (WHERE cl.status = 'SOLD')          AS sold_count,
                COUNT(*) FILTER (WHERE cl.status = 'TRANSFERRED')   AS transferred_count
            FROM livestock_offspring lo
            JOIN livestock_births lb ON lb.id = lo.birth_id
            LEFT JOIN livestock cl ON cl.id = lo.child_livestock_id
            WHERE lo.is_deleted = false
              AND (CAST(:fromDate AS date) IS NULL OR lb.birth_date >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR lb.birth_date <= CAST(:toDate AS date))
            GROUP BY lb.livestock_id
        ),
        breeding_agg AS (
            SELECT 
                livestock_id,
                MAX(breeding_date) AS last_breeding_date,
                MAX(CASE WHEN expected_due_date >= CURRENT_DATE THEN expected_due_date END) AS next_expected_due_date
            FROM livestock_breeding
            WHERE is_deleted = false
            GROUP BY livestock_id
        )
        SELECT
            m.id                                AS animal_id,
            m.tag_number                        AS tag_number,
            cat.name                            AS category_name,
            m.birth_date                        AS mother_birth_date,
            m.status                            AS status,
            m.reproductive_status               AS reproductive_status,
            m.location_id                       AS location_id,
            loc.name                            AS location_name,
            NULLIF(TRIM(CONCAT(COALESCE(ben.first_name, ''), ' ', COALESCE(ben.last_name, ''))), '') AS beneficiary_name,
            ben.phone                           AS beneficiary_phone,
            ba.number_of_births                 AS number_of_births,
            ba.total_offspring                  AS total_offspring,
            COALESCE(oa.male_offspring, 0)      AS male_offspring,
            COALESCE(oa.female_offspring, 0)    AS female_offspring,
            ba.first_birth_date                 AS first_birth_date,
            ba.last_birth_date                  AS last_birth_date,
            COALESCE(oa.death_count, 0) + ba.stillborn_count AS death_count,
            COALESCE(oa.sold_count, 0)          AS sold_count,
            COALESCE(oa.transferred_count, 0)   AS transferred_count,
            ba.weaned_births                    AS weaned_count,
            ba.stillborn_count                  AS stillborn_count,
            br.last_breeding_date               AS last_breeding_date,
            br.next_expected_due_date           AS expected_due_date,
            RANK() OVER (ORDER BY ba.total_offspring DESC) AS production_rank
        FROM livestock m
        JOIN birth_agg ba ON ba.mother_id = m.id
        LEFT JOIN offspring_agg oa ON oa.mother_id = m.id
        LEFT JOIN breeding_agg br ON br.livestock_id = m.id
        LEFT JOIN livestock_categories cat ON cat.id = m.livestock_category_id
        LEFT JOIN a_location loc ON loc.id = m.location_id
        LEFT JOIN beneficiaries ben ON ben.id = m.beneficiary_id
        WHERE m.is_deleted = false
          AND (:categoryId IS NULL OR m.livestock_category_id = :categoryId)
          AND (:status IS NULL OR m.status = :status)
          AND (:search IS NULL OR LOWER(m.tag_number) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
        ORDER BY ba.total_offspring DESC
        """,
            countQuery = """
        SELECT COUNT(*) FROM (
            SELECT m.id
            FROM livestock m
            JOIN livestock_births lb ON lb.livestock_id = m.id AND lb.is_deleted = false
              AND (CAST(:fromDate AS date) IS NULL OR lb.birth_date >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR lb.birth_date <= CAST(:toDate AS date))
            WHERE m.is_deleted = false
              AND (:categoryId IS NULL OR m.livestock_category_id = :categoryId)
              AND (:status IS NULL OR m.status = :status)
              AND (:search IS NULL OR LOWER(m.tag_number) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            GROUP BY m.id
        ) sub
        """,
            nativeQuery = true)
    Page<OffspringRow> findOffspringProduction(
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            @Param("search") String search,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    interface OffspringRow {
        UUID getAnimal_id();
        String getTag_number();
        String getCategory_name();
        Date getMother_birth_date();
        String getStatus();
        String getReproductive_status();
        UUID getLocation_id();
        String getLocation_name();
        String getBeneficiary_name();
        String getBeneficiary_phone();
        Long getNumber_of_births();
        Long getTotal_offspring();
        Long getMale_offspring();
        Long getFemale_offspring();
        Date getFirst_birth_date();
        Date getLast_birth_date();
        Long getDeath_count();
        Long getSold_count();
        Long getTransferred_count();
        Long getWeaned_count();
        Long getStillborn_count();
        Date getLast_breeding_date();
        Date getExpected_due_date();
        Long getProduction_rank();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // KPI Queries
    // ──────────────────────────────────────────────────────────────────────────

    @Query(value = """
        WITH all_females AS (
            SELECT COUNT(*) AS total_females
            FROM livestock 
            WHERE gender = 'FEMALE' 
              AND is_deleted = false
              AND (:categoryId IS NULL OR livestock_category_id = :categoryId)
              AND (:status IS NULL OR status = :status)
        ),
        productive_mothers AS (
            SELECT COUNT(DISTINCT lb.livestock_id) AS productive_count
            FROM livestock_births lb
            JOIN livestock m ON m.id = lb.livestock_id AND m.is_deleted = false
            WHERE lb.is_deleted = false
              AND m.gender = 'FEMALE'
              AND (:categoryId IS NULL OR m.livestock_category_id = :categoryId)
              AND (:status IS NULL OR m.status = :status)
              AND (CAST(:fromDate AS date) IS NULL OR lb.birth_date >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR lb.birth_date <= CAST(:toDate AS date))
        ),
        birth_agg AS (
            SELECT
                lb.livestock_id AS mother_id,
                COUNT(*) AS number_of_births,
                COALESCE(SUM(lb.offspring_count), 0) AS total_offspring,
                COALESCE(SUM(lb.number_stillborn), 0) AS stillborn_count,
                COUNT(*) FILTER (
                    WHERE lb.weaning_date IS NOT NULL AND lb.weaning_date <= CURRENT_DATE
                ) AS weaned_births
            FROM livestock_births lb
            WHERE lb.is_deleted = false
              AND (CAST(:fromDate AS date) IS NULL OR lb.birth_date >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR lb.birth_date <= CAST(:toDate AS date))
            GROUP BY lb.livestock_id
        ),
        offspring_agg AS (
            SELECT
                lb.livestock_id AS mother_id,
                COUNT(*) FILTER (WHERE UPPER(cl.gender) = 'MALE')   AS male_offspring,
                COUNT(*) FILTER (WHERE UPPER(cl.gender) = 'FEMALE') AS female_offspring,
                COUNT(*) FILTER (WHERE lo.is_alive = false)         AS death_count,
                COUNT(*) FILTER (WHERE cl.status = 'SOLD')          AS sold_count,
                COUNT(*) FILTER (WHERE cl.status = 'TRANSFERRED')   AS transferred_count
            FROM livestock_offspring lo
            JOIN livestock_births lb ON lb.id = lo.birth_id
            LEFT JOIN livestock cl ON cl.id = lo.child_livestock_id
            WHERE lo.is_deleted = false
              AND (CAST(:fromDate AS date) IS NULL OR lb.birth_date >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR lb.birth_date <= CAST(:toDate AS date))
            GROUP BY lb.livestock_id
        )
        SELECT
            af.total_females AS total_mothers,
            COALESCE(pm.productive_count, 0) AS productive_mothers,
            af.total_females - COALESCE(pm.productive_count, 0) AS non_productive_mothers,
            COALESCE(SUM(ba.number_of_births), 0) AS total_birth_events,
            COALESCE(SUM(ba.total_offspring), 0) AS total_offspring,
            COALESCE(SUM(oa.male_offspring), 0) AS total_male,
            COALESCE(SUM(oa.female_offspring), 0) AS total_female,
            COALESCE(SUM(oa.death_count), 0) + COALESCE(SUM(ba.stillborn_count), 0) AS total_died,
            COALESCE(SUM(oa.sold_count), 0) AS total_sold,
            COALESCE(SUM(oa.transferred_count), 0) AS total_transferred,
            COALESCE(SUM(ba.weaned_births), 0) AS total_weaned,
            COALESCE(SUM(ba.stillborn_count), 0) AS total_stillborn
        FROM all_females af
        CROSS JOIN productive_mothers pm
        LEFT JOIN birth_agg ba ON 1=1
        LEFT JOIN offspring_agg oa ON 1=1
        GROUP BY af.total_females, pm.productive_count
        """, nativeQuery = true)
    KpiRow findKpis(
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    interface KpiRow {
        Long getTotal_mothers();
        Long getProductive_mothers();
        Long getNon_productive_mothers();
        Long getTotal_birth_events();
        Long getTotal_offspring();
        Long getTotal_male();
        Long getTotal_female();
        Long getTotal_died();
        Long getTotal_sold();
        Long getTotal_transferred();
        Long getTotal_weaned();
        Long getTotal_stillborn();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Monthly Trend with Offspring Count and Average
    // ──────────────────────────────────────────────────────────────────────────

    @Query(value = """
        SELECT
            CAST(DATE_TRUNC('month', lb.birth_date) AS date) AS month,
            COALESCE(COUNT(DISTINCT lb.livestock_id), 0) AS mothers,
            COALESCE(COUNT(*), 0) AS births,
            COALESCE(SUM(lb.offspring_count), 0) AS offspring,
            CASE 
                WHEN COUNT(*) > 0 THEN COALESCE(SUM(lb.offspring_count), 0)::float / COUNT(*)
                ELSE 0
            END AS avg_offspring_per_birth
        FROM livestock_births lb
        JOIN livestock m ON m.id = lb.livestock_id AND m.is_deleted = false
        WHERE lb.is_deleted = false
          AND (:categoryId IS NULL OR m.livestock_category_id = :categoryId)
          AND (:status IS NULL OR m.status = :status)
          AND (:search IS NULL OR LOWER(m.tag_number) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
          AND (CAST(:fromDate AS date) IS NULL OR lb.birth_date >= CAST(:fromDate AS date))
          AND (CAST(:toDate AS date) IS NULL OR lb.birth_date <= CAST(:toDate AS date))
        GROUP BY DATE_TRUNC('month', lb.birth_date)
        ORDER BY month
        """, nativeQuery = true)
    List<TrendRow> findMonthlyTrend(
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            @Param("search") String search,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    interface TrendRow {
        Date getMonth();
        Long getMothers();
        Long getBirths();
        Long getOffspring();
        Double getAvg_offspring_per_birth();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mother Detail
    // ──────────────────────────────────────────────────────────────────────────

    @Query(value = """
        WITH birth_agg AS (
            SELECT
                lb.livestock_id AS mother_id,
                COUNT(*) AS number_of_births,
                COALESCE(SUM(lb.offspring_count), 0) AS total_offspring,
                COALESCE(SUM(lb.number_stillborn), 0) AS stillborn_count,
                COUNT(*) FILTER (
                    WHERE lb.weaning_date IS NOT NULL AND lb.weaning_date <= CURRENT_DATE
                ) AS weaned_births
            FROM livestock_births lb
            WHERE lb.is_deleted = false AND lb.livestock_id = :motherId
            GROUP BY lb.livestock_id
        ),
        offspring_agg AS (
            SELECT
                lb.livestock_id AS mother_id,
                COUNT(*) FILTER (WHERE UPPER(cl.gender) = 'MALE')   AS male_offspring,
                COUNT(*) FILTER (WHERE UPPER(cl.gender) = 'FEMALE') AS female_offspring,
                COUNT(*) FILTER (WHERE lo.is_alive = false)         AS death_count,
                COUNT(*) FILTER (WHERE cl.status = 'SOLD')          AS sold_count,
                COUNT(*) FILTER (WHERE cl.status = 'TRANSFERRED')   AS transferred_count
            FROM livestock_offspring lo
            JOIN livestock_births lb ON lb.id = lo.birth_id
            LEFT JOIN livestock cl ON cl.id = lo.child_livestock_id
            WHERE lo.is_deleted = false AND lb.livestock_id = :motherId
            GROUP BY lb.livestock_id
        ),
        breeding_agg AS (
            SELECT 
                livestock_id,
                MAX(breeding_date) AS last_breeding_date,
                MAX(CASE WHEN expected_due_date >= CURRENT_DATE THEN expected_due_date END) AS next_expected_due_date
            FROM livestock_breeding
            WHERE is_deleted = false AND livestock_id = :motherId
            GROUP BY livestock_id
        )
        SELECT
            m.id                               AS animal_id,
            m.tag_number                       AS tag_number,
            cat.name                           AS category_name,
            m.birth_date                       AS mother_birth_date,
            m.status                           AS status,
            m.reproductive_status              AS reproductive_status,
            COALESCE(ba.number_of_births, 0)   AS number_of_births,
            COALESCE(ba.total_offspring, 0)    AS total_offspring,
            COALESCE(oa.male_offspring, 0)     AS male_offspring,
            COALESCE(oa.female_offspring, 0)   AS female_offspring,
            COALESCE(oa.death_count, 0) + COALESCE(ba.stillborn_count, 0) AS death_count,
            COALESCE(oa.sold_count, 0)         AS sold_count,
            COALESCE(oa.transferred_count, 0)  AS transferred_count,
            COALESCE(ba.weaned_births, 0)      AS weaned_count,
            ba.stillborn_count                 AS stillborn_count,
            br.last_breeding_date              AS last_breeding_date,
            br.next_expected_due_date          AS expected_due_date
        FROM livestock m
        LEFT JOIN birth_agg ba ON ba.mother_id = m.id
        LEFT JOIN offspring_agg oa ON oa.mother_id = m.id
        LEFT JOIN breeding_agg br ON br.livestock_id = m.id
        LEFT JOIN livestock_categories cat ON cat.id = m.livestock_category_id
        WHERE m.id = :motherId AND m.is_deleted = false
        """, nativeQuery = true)
    MotherSummaryRow findMotherSummary(@Param("motherId") UUID motherId);

    interface MotherSummaryRow {
        UUID getAnimal_id();
        String getTag_number();
        String getCategory_name();
        Date getMother_birth_date();
        String getStatus();
        String getReproductive_status();
        Long getNumber_of_births();
        Long getTotal_offspring();
        Long getMale_offspring();
        Long getFemale_offspring();
        Long getDeath_count();
        Long getSold_count();
        Long getTransferred_count();
        Long getWeaned_count();
        Long getStillborn_count();
        Date getLast_breeding_date();
        Date getExpected_due_date();
    }

    @Query(value = """
        SELECT lb.birth_date AS birth_date, lb.offspring_count AS offspring_count
        FROM livestock_births lb
        WHERE lb.livestock_id = :motherId AND lb.is_deleted = false
        ORDER BY lb.birth_date DESC
        """, nativeQuery = true)
    List<BirthHistoryRow> findBirthHistory(@Param("motherId") UUID motherId);

    interface BirthHistoryRow {
        Date getBirth_date();
        Long getOffspring_count();
    }
}