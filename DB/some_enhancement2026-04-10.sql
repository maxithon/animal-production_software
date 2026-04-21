-- Description: Optimizes queries for lifecycle management and adds helpful views

-- ==================== INDEXES ====================
-- Composite index for lifecycle queries (category + age + gender)
CREATE INDEX IF NOT EXISTS idx_livestock_lifecycle 
ON livestock(livestock_category_id, date_received, gender, status, is_deleted) 
WHERE is_deleted = FALSE;

-- Index for breeding-ready females
CREATE INDEX IF NOT EXISTS idx_livestock_breeding_females 
ON livestock(gender, date_received, status) 
WHERE gender = 'FEMALE' AND is_deleted = FALSE;

-- Index for breeding-ready males  
CREATE INDEX IF NOT EXISTS idx_livestock_breeding_males
ON livestock(gender, date_received, status) 
WHERE gender = 'MALE' AND is_deleted = FALSE;

-- Index for pregnant animals
CREATE INDEX IF NOT EXISTS idx_livestock_pregnant 
ON livestock(status, expected_due_date) 
WHERE status = 'PREGNANT' AND is_deleted = FALSE;

-- Index for acquisition method queries
CREATE INDEX IF NOT EXISTS idx_livestock_acquisition 
ON livestock(acquisition_method, date_received) 
WHERE is_deleted = FALSE;

-- Index for mother-child relationships
CREATE INDEX IF NOT EXISTS idx_livestock_mother 
ON livestock(mother_id) 
WHERE mother_id IS NOT NULL AND is_deleted = FALSE;

-- ==================== VIEWS ====================

-- View: Livestock with calculated age and lifecycle stage
CREATE OR REPLACE VIEW v_livestock_with_age AS
SELECT 
    l.*,

    -- Days (FIXED)
    COALESCE((CURRENT_DATE - l.date_received), 0) AS age_in_days,

    -- Months (this is OK)
    COALESCE(DATE_PART('month', AGE(CURRENT_DATE, l.date_received))::INTEGER, 0) AS age_in_months,

    lc.name AS category_name,
    lc.code AS category_code,
    lc.gestation_period_months,

    CASE 
        WHEN l.date_received IS NULL THEN 'UNKNOWN'

        WHEN (CURRENT_DATE - l.date_received) <= 30 THEN 'NEWBORN'

        WHEN (CURRENT_DATE - l.date_received) <= 365 THEN 'YOUNG'

        WHEN l.gender = 'FEMALE' 
             AND (CURRENT_DATE - l.date_received) > 365 
             AND l.status != 'PREGNANT' THEN 'READY_TO_BREED'

        WHEN l.gender = 'MALE' 
             AND (CURRENT_DATE - l.date_received) > 365 THEN 'BREEDING_MALE'

        WHEN l.status = 'PREGNANT' THEN 'PREGNANT'

        ELSE 'MATURE'
    END AS lifecycle_stage

FROM livestock l
LEFT JOIN livestock_categories lc 
    ON l.livestock_category_id = lc.id
WHERE l.is_deleted = FALSE;



-- ============================================================
-- VIEW 2: v_females_ready_to_breed
--
-- Extra columns used from livestock (all real):
--   offspring_count, last_breeding_date, first_breeding_date,
--   is_pregnant, pregnancy_status, conception_date,
--   expected_due_date, last_birth_date
--
-- Extra column from livestock_categories (real):
--   gestation_period_months
-- ============================================================

CREATE OR REPLACE VIEW public.v_females_ready_to_breed AS
SELECT
    ls.id,
    ls.tag_number,
    lc.id                                                                   AS category_id,
    lc.name                                                                 AS category_name,
    lc.code                                                                 AS category_code,
    lc.gestation_period_months,
    FLOOR(
        (CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received)) / 30.4375
    )::INTEGER                                                              AS age_months,
    ls.offspring_count,
    ls.last_breeding_date,
    ls.first_breeding_date,
    ls.last_birth_date,
    ls.is_pregnant,
    ls.pregnancy_status,
    ls.conception_date,
    ls.expected_due_date,
    COUNT(lb.id)                                                            AS total_breedings,
    SUM(
        CASE WHEN lb.status IN ('CONFIRMED_PREGNANT', 'COMPLETED')
             THEN 1 ELSE 0 END
    )                                                                       AS successful_breedings,
    ls.date_received,
    ls.birth_date,
    ls.status,
    ls.gender,
    ls.current_value
FROM livestock ls
LEFT JOIN livestock_categories lc
       ON lc.id = ls.livestock_category_id
      AND lc.is_deleted = FALSE
LEFT JOIN livestock_breeding lb
       ON lb.livestock_id = ls.id
      AND lb.is_deleted   = FALSE
WHERE ls.gender     = 'FEMALE'
  AND ls.status NOT IN ('DEAD', 'SOLD')
  AND ls.is_deleted = FALSE
  AND (ls.is_pregnant IS NULL OR ls.is_pregnant = FALSE)
  AND (CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received)) >= 365
GROUP BY
    ls.id, ls.tag_number,
    lc.id, lc.name, lc.code, lc.gestation_period_months,
    ls.offspring_count, ls.last_breeding_date, ls.first_breeding_date,
    ls.last_birth_date, ls.is_pregnant, ls.pregnancy_status,
    ls.conception_date, ls.expected_due_date,
    ls.date_received, ls.birth_date,
    ls.status, ls.gender, ls.current_value;

COMMENT ON VIEW public.v_females_ready_to_breed IS 'Female animals 12+ months old, active, non-pregnant, ready for breeding';


-- ============================================================
-- VIEW 1: v_males_ready_to_breed
--
-- Columns from livestock:
--   id, tag_number, gender, status, birth_date, date_received,
--   current_value, acquisition_method, livestock_category_id, is_deleted
--
-- Columns from livestock_categories:
--   id, name, code
--
-- Columns from livestock_breeding (male side):
--   male_livestock_id, status, is_deleted
-- ============================================================



CREATE OR REPLACE VIEW public.v_males_ready_to_breed AS
SELECT
    ls.id,
    ls.tag_number,
    lc.id                                                                   AS category_id,
    lc.name                                                                 AS category_name,
    lc.code                                                                 AS category_code,
    FLOOR(
        (CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received)) / 30.4375
    )::INTEGER                                                              AS age_months,
    COUNT(DISTINCT lb.id)                                                   AS total_breedings,
    COUNT(DISTINCT
        CASE WHEN lb.status = 'CONFIRMED_PREGNANT' THEN lb.id ELSE NULL END
    )                                                                       AS successful_breedings,
    ls.date_received,
    ls.birth_date,
    ls.status,
    ls.gender,
    ls.current_value,
    ls.acquisition_method
FROM livestock ls
LEFT JOIN livestock_categories lc
       ON lc.id = ls.livestock_category_id
      AND lc.is_deleted = FALSE
LEFT JOIN livestock_breeding lb
       ON lb.male_livestock_id = ls.id
      AND lb.is_deleted = FALSE
WHERE ls.gender     = 'MALE'
  AND ls.status NOT IN ('DEAD', 'SOLD')
  AND ls.is_deleted = FALSE
  AND (CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received)) >= 365
GROUP BY
    ls.id, ls.tag_number,
    lc.id, lc.name, lc.code,
    ls.date_received, ls.birth_date,
    ls.status, ls.gender,
    ls.current_value, ls.acquisition_method
ORDER BY total_breedings DESC, ls.date_received;

COMMENT ON VIEW public.v_males_ready_to_breed IS 'Male animals 12+ months old with breeding statistics';

-- View: Pregnant animals with due date calculations
CREATE OR REPLACE VIEW v_pregnant_animals AS
SELECT 
    l.*,
    lc.name AS category_name,
    lc.gestation_period_months,

    CASE 
        WHEN l.expected_due_date IS NULL THEN NULL

        -- overdue (positive)
        WHEN l.expected_due_date < CURRENT_DATE 
            THEN (CURRENT_DATE - l.expected_due_date)

        -- remaining (negative)
        ELSE -(l.expected_due_date - CURRENT_DATE)
    END AS days_overdue_or_remaining,

    CASE 
        WHEN l.expected_due_date IS NULL THEN 'NO_DATE'
        WHEN l.expected_due_date < CURRENT_DATE THEN 'OVERDUE'
        WHEN l.expected_due_date <= CURRENT_DATE + INTERVAL '7 days' THEN 'CRITICAL'
        WHEN l.expected_due_date <= CURRENT_DATE + INTERVAL '30 days' THEN 'SOON'
        ELSE 'OK'
    END AS urgency_status

FROM livestock l
JOIN livestock_categories lc 
    ON l.livestock_category_id = lc.id

WHERE l.is_deleted = FALSE
    AND l.status = 'PREGNANT'

ORDER BY 
    CASE 
        WHEN l.expected_due_date IS NULL THEN 999999
        ELSE (l.expected_due_date - CURRENT_DATE)
    END;
	


-- View: Young animals (pre-breeding, 31-365 days)
CREATE OR REPLACE VIEW v_young_animals AS
SELECT 
    l.*,
    lc.name AS category_name,

    -- FIXED: age in days
    (CURRENT_DATE - l.date_received) AS age_days,

    -- FIXED: days remaining to reach 365
    (365 - (CURRENT_DATE - l.date_received)) AS days_until_breeding_age

FROM livestock l
JOIN livestock_categories lc 
    ON l.livestock_category_id = lc.id

WHERE l.is_deleted = FALSE
    AND l.status = 'ACTIVE'
    AND l.date_received IS NOT NULL

    -- FIXED conditions
    AND (CURRENT_DATE - l.date_received) > 30
    AND (CURRENT_DATE - l.date_received) < 365

ORDER BY l.date_received DESC;

-- View: Newborn animals (0-30 days)
CREATE OR REPLACE VIEW v_newborn_animals AS
SELECT 
    l.*,
    lc.name AS category_name,

    (CURRENT_DATE - l.date_received) AS age_days,

    m.tag_number AS mother_tag_number

FROM livestock l
JOIN livestock_categories lc 
    ON l.livestock_category_id = lc.id

LEFT JOIN livestock m 
    ON l.mother_id = m.id

WHERE l.is_deleted = FALSE
    AND l.status = 'ACTIVE'
    AND l.date_received IS NOT NULL
    AND (CURRENT_DATE - l.date_received) <= 30

ORDER BY l.date_received DESC;

-- ==================== HELPER FUNCTIONS ====================

-- Function: Calculate lifecycle stage for an animal
CREATE OR REPLACE FUNCTION get_livestock_lifecycle_stage(
    p_date_received DATE,
    p_gender VARCHAR(10),
    p_status VARCHAR(20)
)
RETURNS VARCHAR(30)
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_age_days INTEGER;
BEGIN
    IF p_date_received IS NULL THEN
        RETURN 'UNKNOWN';
    END IF;
    
    v_age_days := DATE_PART('day', CURRENT_DATE - p_date_received)::INTEGER;
    
    IF v_age_days <= 30 THEN
        RETURN 'NEWBORN';
    ELSIF v_age_days <= 365 THEN
        RETURN 'YOUNG';
    ELSIF p_status = 'PREGNANT' THEN
        RETURN 'PREGNANT';
    ELSIF p_gender = 'FEMALE' AND v_age_days > 365 AND p_status != 'PREGNANT' THEN
        RETURN 'READY_TO_BREED';
    ELSIF p_gender = 'MALE' AND v_age_days > 365 THEN
        RETURN 'BREEDING_MALE';
    ELSE
        RETURN 'MATURE';
    END IF;
END;
$$;

-- Function: Get count of animals by lifecycle stage
CREATE OR REPLACE FUNCTION get_lifecycle_stage_counts()
RETURNS TABLE (
    stage VARCHAR(30),
    count BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT 
        get_livestock_lifecycle_stage(l.date_received, l.gender, l.status) AS stage,
        COUNT(*)::BIGINT AS count
    FROM livestock l
    WHERE l.is_deleted = FALSE
        AND l.status IN ('ACTIVE', 'SICK', 'PREGNANT')
    GROUP BY stage
    ORDER BY 
        CASE stage
            WHEN 'NEWBORN' THEN 1
            WHEN 'YOUNG' THEN 2
            WHEN 'READY_TO_BREED' THEN 3
            WHEN 'BREEDING_MALE' THEN 4
            WHEN 'PREGNANT' THEN 5
            WHEN 'MATURE' THEN 6
            ELSE 7
        END;
END;
$$;

-- ==================== COMMENTS ====================

COMMENT ON VIEW v_livestock_with_age IS 'All livestock with calculated age and lifecycle stage';
COMMENT ON VIEW v_females_ready_to_breed IS 'Female animals 12+ months old, ready for breeding';
COMMENT ON VIEW v_males_ready_to_breed IS 'Male animals 12+ months old with breeding statistics';
COMMENT ON VIEW v_pregnant_animals IS 'All pregnant animals with due date urgency status';
COMMENT ON VIEW v_young_animals IS 'Animals in pre-breeding stage (31-365 days)';
COMMENT ON VIEW v_newborn_animals IS 'Recently born animals (0-30 days)';

COMMIT;
