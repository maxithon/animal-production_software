-- =============================================================================
-- LIVESTOCK MANAGEMENT SYSTEM - FULL DATABASE SCHEMA
-- Includes: all original tables + veterinarians + livestock_breeding + aps_log
-- Changes applied:
--   1. Added veterinarians table (central vet directory)
--   2. Added livestock_breeding table (full breeding lifecycle)
--   3. Added aps_log table (centralized audit log - replaces deleted_at everywhere)
--   4. Added is_deleted BOOLEAN to every table
--   5. Added veterinarian_id FK to livestock_births, livestock_sick,
--      livestock_treatments, livestock_abortions
--   6. Added breeding_id FK to livestock_births and livestock_abortions
--   7. Added pregnancy_status, expected_due_date, first_breeding_date to livestock
--   8. Added created_by / created_at where missing
--   9. Kept vet_name as legacy text fallback on sick/treatment tables
-- =============================================================================


-- =============================================================================
-- 1. a_location
-- =============================================================================
CREATE TABLE a_location (
    id              BIGSERIAL PRIMARY KEY,
    state           VARCHAR(100),
    version         VARCHAR(50),
    code            VARCHAR(50),
    regulator_code  VARCHAR(50),
    name            VARCHAR(200) NOT NULL,
    location_type   VARCHAR(100),
    parent_id       BIGINT REFERENCES a_location(id),
    comments        TEXT,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 2. sec_user_type
-- =============================================================================
CREATE TABLE sec_user_type (
    user_type_id    BIGSERIAL PRIMARY KEY,
    user_type_name  VARCHAR(100) NOT NULL,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 3. sec_user
-- =============================================================================
CREATE TABLE sec_user (
    user_id           BIGSERIAL PRIMARY KEY,
    email             VARCHAR(200) NOT NULL UNIQUE,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    password          VARCHAR(255) NOT NULL,
    registration_date TIMESTAMP NOT NULL DEFAULT NOW(),
    user_type_id      BIGINT REFERENCES sec_user_type(user_type_id),
    photo_url         VARCHAR(500),
    is_deleted        BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 4. representatives_aborora  (supervisor / farm manager)
-- =============================================================================
CREATE TABLE representatives_aborora (
    id              BIGSERIAL PRIMARY KEY,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    gender          VARCHAR(20),
    maritial_status VARCHAR(30),
    nid             VARCHAR(50),
    phone           VARCHAR(20),
    email           VARCHAR(150),
    icyo_akora      VARCHAR(200),
    contractAgreement     VARCHAR(200),
    photo           VARCHAR(500),
    location_id     BIGINT REFERENCES a_location(id),
    created_date    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      BIGINT REFERENCES sec_user(user_id),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 5. beneficiaries_amatungo  (livestock owners / farmers)
-- =============================================================================
CREATE TABLE beneficiaries_amatungo (
    id                      BIGSERIAL PRIMARY KEY,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    gender                  VARCHAR(20),
    maritial_status         VARCHAR(30),
    nid                     VARCHAR(50),
    phone                   VARCHAR(20),
    contractAgreement             VARCHAR(200),
    representatives_aborora_id  BIGINT REFERENCES representatives_aborora(id),
    location_id             BIGINT REFERENCES a_location(id),
    photo                   VARCHAR(500),
    created_date            TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by              BIGINT REFERENCES sec_user(user_id),
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 6. buyers
-- =============================================================================
CREATE TABLE buyers (
    id                  BIGSERIAL PRIMARY KEY,
    buyer_name          VARCHAR(200) NOT NULL,
    buyer_phone         VARCHAR(20),
    buyer_address       TEXT,
    buyer_national_id   VARCHAR(50),
    buyer_email         VARCHAR(150),
    buyer_type          VARCHAR(50),
    notes               TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    created_by          BIGINT REFERENCES sec_user(user_id),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 7. livestock_categories
-- =============================================================================
CREATE TABLE livestock_categories (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(50) NOT NULL UNIQUE,
    name                    VARCHAR(200) NOT NULL,
    gestation_period_months NUMERIC(4,1) NOT NULL,  -- e.g. 9.0 for cattle, 5.0 for goats
    description             TEXT,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 8. veterinarians  (NEW TABLE)
-- =============================================================================
CREATE TABLE veterinarians (
    id              BIGSERIAL PRIMARY KEY,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(150),
    license_number  VARCHAR(100),
    specialization  VARCHAR(200),
    clinic_name     VARCHAR(200),
    location_id     BIGINT REFERENCES a_location(id),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      BIGINT REFERENCES sec_user(user_id),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 9. livestock  (core animal table — updated)
-- =============================================================================
CREATE TABLE livestock (
    id                      BIGSERIAL PRIMARY KEY,
    tag_number              VARCHAR(100) NOT NULL UNIQUE,
    gender                  VARCHAR(10) NOT NULL CHECK (gender IN ('MALE','FEMALE')),
    acquisition_method      VARCHAR(50),           -- BORN, PURCHASED, DONATED, etc.
    date_received           DATE,
    current_value           NUMERIC(15,2),
    sold_price              NUMERIC(15,2),
    photo                   VARCHAR(500),
    status                  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE','SOLD','DEAD','TRANSFERRED')),

    -- Breeding & pregnancy lifecycle (UPDATED)
    pregnancy_status        VARCHAR(20) NOT NULL DEFAULT 'NOT_PREGNANT'
                                CHECK (pregnancy_status IN (
                                    'NOT_PREGNANT','BRED','PREGNANT','GAVE_BIRTH'
                                )),
    first_breeding_date     DATE,                  -- very first time this animal was bred
    last_breeding_date      DATE,                  -- most recent breeding event
    conception_date         DATE,                  -- confirmed conception date
    expected_due_date       DATE,                  -- calculated from breeding_date + gestation_period
    last_birth_date         DATE,
    offspring_count         INT NOT NULL DEFAULT 0,

    -- Relations
    mother_id               BIGINT REFERENCES livestock(id),
    beneficiaries_amatungo_id  BIGINT REFERENCES beneficiaries_amatungo(id),
    livestock_category_id   BIGINT NOT NULL REFERENCES livestock_categories(id),
    location_id             BIGINT REFERENCES a_location(id),

    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by              BIGINT REFERENCES sec_user(user_id),
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 10. livestock_breeding  (NEW TABLE — tracks every breeding event)
-- =============================================================================
CREATE TABLE livestock_breeding (
    id                              BIGSERIAL PRIMARY KEY,
    livestock_id                    BIGINT NOT NULL REFERENCES livestock(id),
    breeding_date                   DATE NOT NULL,
    breeding_method                 VARCHAR(20) NOT NULL
                                        CHECK (breeding_method IN ('NATURAL','ARTIFICIAL')),
    male_livestock_id               BIGINT REFERENCES livestock(id),   -- optional: sire
    veterinarian_id                 BIGINT REFERENCES veterinarians(id), -- optional: AI vet
    status                          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                        CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    expected_pregnancy_check_date   DATE,
    -- Auto-filled when status → SUCCESS:
    -- breeding_date + livestock_categories.gestation_period_months
    expected_due_date               DATE,
    notes                           TEXT,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by                      BIGINT REFERENCES sec_user(user_id),
    is_deleted                      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 11. livestock_births  (updated: added veterinarian_id + breeding_id)
-- =============================================================================
CREATE TABLE livestock_births (
    id                  BIGSERIAL PRIMARY KEY,
    livestock_id        BIGINT NOT NULL REFERENCES livestock(id),
    breeding_id         BIGINT REFERENCES livestock_breeding(id),  -- link back to breeding event
    birth_date          DATE NOT NULL,
    offspring_count     INT NOT NULL DEFAULT 1,
    offspring_gender    VARCHAR(20),
    weaning_date        DATE,
    veterinarian_id     BIGINT REFERENCES veterinarians(id),       -- replaces free-text assisted_by
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          BIGINT REFERENCES sec_user(user_id),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 12. livestock_offspring
-- =============================================================================
CREATE TABLE livestock_offspring (
    id                  BIGSERIAL PRIMARY KEY,
    birth_id            BIGINT NOT NULL REFERENCES livestock_births(id),
    child_livestock_id  BIGINT NOT NULL REFERENCES livestock(id),
    generation          INT,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 13. livestock_abortions  (updated: added veterinarian_id + breeding_id)
-- =============================================================================
CREATE TABLE livestock_abortions (
    id                  BIGSERIAL PRIMARY KEY,
    livestock_id        BIGINT NOT NULL REFERENCES livestock(id),
    breeding_id         BIGINT REFERENCES livestock_breeding(id),  -- which breeding event failed
    abortion_date       DATE NOT NULL,
    pregnancy_number    INT,
    abortion_reason     TEXT,
    stage_of_pregnancy  VARCHAR(100),
    veterinarian_id     BIGINT REFERENCES veterinarians(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          BIGINT REFERENCES sec_user(user_id),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 14. medications
-- =============================================================================
CREATE TABLE medications (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    generic_name        VARCHAR(200),
    category            VARCHAR(100),
    default_dosage      NUMERIC(10,2),
    default_dosage_unit VARCHAR(50),
    manufacturer        VARCHAR(200),
    description         TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    created_by          BIGINT REFERENCES sec_user(user_id),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 15. livestock_sick  (updated: added veterinarian_id FK; vet_name kept as legacy)
-- =============================================================================
CREATE TABLE livestock_sick (
    id                  BIGSERIAL PRIMARY KEY,
    livestock_id        BIGINT NOT NULL REFERENCES livestock(id),
    reported_date       DATE NOT NULL,
    recovery_date       DATE,
    status              VARCHAR(30) NOT NULL DEFAULT 'SICK'
                            CHECK (status IN ('SICK','RECOVERED','DEAD','CHRONIC')),
    symptoms            TEXT,
    diagnosis           TEXT,
    severity_level      VARCHAR(20)
                            CHECK (severity_level IN ('MILD','MODERATE','SEVERE','CRITICAL')),
    temperature         NUMERIC(5,2),
    treatment_notes     TEXT,
    veterinarian_id     BIGINT REFERENCES veterinarians(id),  -- FK (new)
    vet_name            VARCHAR(200),                          -- legacy free-text fallback
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          BIGINT REFERENCES sec_user(user_id),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 16. livestock_sick_history
-- =============================================================================
CREATE TABLE livestock_sick_history (
    id              BIGSERIAL PRIMARY KEY,
    sick_id         BIGINT NOT NULL REFERENCES livestock_sick(id),
    changed_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    changed_by      BIGINT REFERENCES sec_user(user_id),
    status          VARCHAR(30),
    severity_level  VARCHAR(20),
    notes           TEXT,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 17. livestock_treatments  (updated: added veterinarian_id FK; vet_name kept)
-- =============================================================================
CREATE TABLE livestock_treatments (
    id                  BIGSERIAL PRIMARY KEY,
    sick_livestock_id   BIGINT REFERENCES livestock_sick(id),
    livestock_id        BIGINT REFERENCES livestock(id),
    medication_id       BIGINT REFERENCES medications(id),
    treatment_date      DATE NOT NULL,
    next_treatment_date DATE,
    treatment_type      VARCHAR(100),
    treatment_duration  INT,
    treatment_status    VARCHAR(30) DEFAULT 'ONGOING'
                            CHECK (treatment_status IN ('ONGOING','COMPLETED','CANCELLED')),
    description         TEXT,
    medication          VARCHAR(200),              -- legacy free text
    dosage              NUMERIC(10,2),
    dosage_unit         VARCHAR(50),
    frequency           VARCHAR(100),
    treatment_cost      NUMERIC(15,2),
    is_paid             BOOLEAN NOT NULL DEFAULT FALSE,
    payment_date        DATE,
    veterinarian_id     BIGINT REFERENCES veterinarians(id),  -- FK (new)
    vet_name            VARCHAR(200),                          -- legacy free-text fallback
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          BIGINT REFERENCES sec_user(user_id),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 18. livestock_deaths
-- =============================================================================
CREATE TABLE livestock_deaths (
    id              BIGSERIAL PRIMARY KEY,
    livestock_id    BIGINT NOT NULL REFERENCES livestock(id),
    death_date      DATE NOT NULL,
    cause_of_death  TEXT,
    veterinarian_id BIGINT REFERENCES veterinarians(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      BIGINT REFERENCES sec_user(user_id),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 19. livestock_sales
-- =============================================================================
CREATE TABLE livestock_sales (
    id              BIGSERIAL PRIMARY KEY,
    livestock_id    BIGINT NOT NULL REFERENCES livestock(id),
    buyer_id        BIGINT REFERENCES buyers(id),
    sale_date       DATE NOT NULL,
    sale_price      NUMERIC(15,2),
    sale_location   VARCHAR(200),
    sale_reason     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      BIGINT REFERENCES sec_user(user_id),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);


-- =============================================================================
-- 20. aps_log  (NEW — centralized audit log for ALL tables)
-- =============================================================================
CREATE TABLE aps_log (
    id              BIGSERIAL PRIMARY KEY,
    table_name      VARCHAR(100) NOT NULL,
    record_id       BIGINT NOT NULL,
    action          VARCHAR(30) NOT NULL
                        CHECK (action IN (
                            'INSERT','UPDATE','DELETE','RESTORE','STATUS_CHANGE'
                        )),
    field_changed   VARCHAR(100),   -- column name that changed (null for INSERT/DELETE)
    old_value       TEXT,           -- value before change
    new_value       TEXT,           -- value after change
    notes           TEXT,
    performed_by    BIGINT REFERENCES sec_user(user_id),
    performed_at    TIMESTAMP NOT NULL DEFAULT NOW()
);


-- =============================================================================
-- INDEXES
-- =============================================================================

-- livestock core lookups
CREATE INDEX idx_livestock_tag            ON livestock (tag_number);
CREATE INDEX idx_livestock_category       ON livestock (livestock_category_id);
CREATE INDEX idx_livestock_owner          ON livestock (beneficiaries_amatungo_id);
CREATE INDEX idx_livestock_pregnancy      ON livestock (pregnancy_status) WHERE is_deleted = FALSE;
CREATE INDEX idx_livestock_due_date       ON livestock (expected_due_date) WHERE is_deleted = FALSE;
CREATE INDEX idx_livestock_status         ON livestock (status) WHERE is_deleted = FALSE;

-- breeding
CREATE INDEX idx_breeding_livestock       ON livestock_breeding (livestock_id);
CREATE INDEX idx_breeding_status          ON livestock_breeding (status) WHERE is_deleted = FALSE;
CREATE INDEX idx_breeding_date            ON livestock_breeding (breeding_date);

-- births
CREATE INDEX idx_births_livestock         ON livestock_births (livestock_id);
CREATE INDEX idx_births_breeding          ON livestock_births (breeding_id);
CREATE INDEX idx_births_date              ON livestock_births (birth_date);

-- sick
CREATE INDEX idx_sick_livestock           ON livestock_sick (livestock_id);
CREATE INDEX idx_sick_status              ON livestock_sick (status) WHERE is_deleted = FALSE;

-- treatments
CREATE INDEX idx_treatments_livestock     ON livestock_treatments (livestock_id);
CREATE INDEX idx_treatments_sick          ON livestock_treatments (sick_livestock_id);

-- aps_log (fast audit queries)
CREATE INDEX idx_aps_log_table_record     ON aps_log (table_name, record_id);
CREATE INDEX idx_aps_log_performed_at     ON aps_log (performed_at DESC);
CREATE INDEX idx_aps_log_performed_by     ON aps_log (performed_by);
CREATE INDEX idx_aps_log_action           ON aps_log (action);


-- =============================================================================
-- USEFUL QUERIES
-- =============================================================================

-- Q1: Animals giving birth THIS month
-- SELECT
--     l.id, l.tag_number, l.gender, l.pregnancy_status,
--     l.expected_due_date,
--     lc.name                             AS category,
--     lc.gestation_period_months,
--     aa.first_name || ' ' || aa.last_name AS owner_name,
--     lb.breeding_date, lb.breeding_method
-- FROM livestock l
-- JOIN livestock_categories lc        ON lc.id = l.livestock_category_id
-- JOIN beneficiaries_amatungo aa         ON aa.id = l.beneficiaries_amatungo_id
-- LEFT JOIN livestock_breeding lb     ON lb.livestock_id = l.id
--     AND lb.status = 'SUCCESS' AND lb.is_deleted = FALSE
-- WHERE l.is_deleted = FALSE
--   AND l.pregnancy_status = 'PREGNANT'
--   AND DATE_TRUNC('month', l.expected_due_date) = DATE_TRUNC('month', CURRENT_DATE)
-- ORDER BY l.expected_due_date ASC;


-- Q2: Full lifecycle of one animal
-- SELECT
--     l.tag_number, l.gender, l.pregnancy_status,
--     l.first_breeding_date, l.last_breeding_date,
--     l.expected_due_date, l.last_birth_date, l.offspring_count,
--     lc.name AS category, lc.gestation_period_months
-- FROM livestock l
-- JOIN livestock_categories lc ON lc.id = l.livestock_category_id
-- WHERE l.id = :livestock_id AND l.is_deleted = FALSE;


-- Q3: All breeding history of one animal
-- SELECT
--     lb.breeding_date, lb.breeding_method, lb.status,
--     lb.expected_due_date,
--     v.first_name || ' ' || v.last_name AS vet_name,
--     ml.tag_number AS sire_tag
-- FROM livestock_breeding lb
-- LEFT JOIN veterinarians v   ON v.id = lb.veterinarian_id
-- LEFT JOIN livestock ml      ON ml.id = lb.male_livestock_id
-- WHERE lb.livestock_id = :livestock_id AND lb.is_deleted = FALSE
-- ORDER BY lb.breeding_date DESC;


-- Q4: Auto-update livestock when breeding is confirmed SUCCESS
-- UPDATE livestock
-- SET
--     pregnancy_status    = 'PREGNANT',
--     last_breeding_date  = lb.breeding_date,
--     conception_date     = lb.breeding_date,
--     first_breeding_date = COALESCE(livestock.first_breeding_date, lb.breeding_date),
--     expected_due_date   = lb.breeding_date
--                           + (lc.gestation_period_months || ' months')::INTERVAL
-- FROM livestock_breeding lb
-- JOIN livestock_categories lc ON lc.id = livestock.livestock_category_id
-- WHERE livestock.id = lb.livestock_id
--   AND lb.id = :breeding_id;


-- Q5: Full audit log for one record
-- SELECT
--     al.table_name, al.action, al.field_changed,
--     al.old_value, al.new_value, al.notes,
--     al.performed_at,
--     u.email AS performed_by
-- FROM aps_log al
-- LEFT JOIN sec_user u ON u.user_id = al.performed_by
-- WHERE al.table_name = 'livestock'
--   AND al.record_id  = :livestock_id
-- ORDER BY al.performed_at DESC;
