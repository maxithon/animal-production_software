/*
ANIMAL PRODUCTION SYSTEM DATABASE SCHEMA
*/

--&&&&&&&&&&&&&&&&&&&&&&&& 1. ALL TABLES &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&---

-- public.audit_log definition

-- Drop table

-- DROP TABLE public.audit_log;

CREATE TABLE public.audit_log (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	entity_type varchar(100) NOT NULL,
	entity_id uuid NOT NULL,
	"action" varchar(50) NOT NULL,
	changed_by varchar(255) NULL,
	changed_at timestamp DEFAULT now() NOT NULL,
	old_data text NULL,
	new_data text NULL,
	notes text NULL,
	is_deleted bool DEFAULT false NULL,
	CONSTRAINT audit_log_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_audit_log_changed_at ON public.audit_log USING btree (changed_at DESC);
CREATE INDEX idx_audit_log_entity ON public.audit_log USING btree (entity_type, entity_id);

-- Permissions

ALTER TABLE public.audit_log OWNER TO postgres;
GRANT ALL ON TABLE public.audit_log TO postgres;


-- public.livestock_categories definition

-- Drop table

-- DROP TABLE public.livestock_categories;

CREATE TABLE public.livestock_categories (
	id uuid NOT NULL,
	code varchar(20) NOT NULL,
	"name" varchar(100) NOT NULL,
	gestation_period_months int4 NULL,
	description text NULL,
	is_deleted bool DEFAULT false NOT NULL,
	min_breeding_age_months int4 NULL,
	postpartum_recovery_days int4 DEFAULT 45 NOT NULL, -- Minimum days after giving birth before a female is considered recovered enough to breed again. NULL = no recovery gate applied.
	gestation_period_days int4 NULL,
	typical_litter_size int4 DEFAULT 1 NOT NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	updated_at timestamp NULL,
	max_breeding_age_months int4 NULL,
	weaning_period_days int4 DEFAULT 90 NOT NULL, -- Typical age (in days) at which offspring of this category are weaned off the mother. Used to auto-calculate a birth record's Weaning Date.
	CONSTRAINT chk_category_gestation_days CHECK (((gestation_period_days IS NULL) OR (gestation_period_days > 0))),
	CONSTRAINT chk_category_litter_size CHECK ((typical_litter_size >= 1)),
	CONSTRAINT chk_category_recovery_days CHECK ((postpartum_recovery_days >= 0)),
	CONSTRAINT chk_category_weaning_days CHECK ((weaning_period_days >= 0)),
	CONSTRAINT livestock_categories_pkey PRIMARY KEY (id),
	CONSTRAINT ukk3sace64rlriq8wb7s7g1kwcr UNIQUE (code)
);

-- Column comments

COMMENT ON COLUMN public.livestock_categories.postpartum_recovery_days IS 'Minimum days after giving birth before a female is considered recovered enough to breed again. NULL = no recovery gate applied.';
COMMENT ON COLUMN public.livestock_categories.weaning_period_days IS 'Typical age (in days) at which offspring of this category are weaned off the mother. Used to auto-calculate a birth record''s Weaning Date.';

-- Permissions

ALTER TABLE public.livestock_categories OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_categories TO postgres;


-- public.livestock_reproductive_data_archive definition

-- Drop table

-- DROP TABLE public.livestock_reproductive_data_archive;

CREATE TABLE public.livestock_reproductive_data_archive (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	livestock_id uuid NOT NULL,
	archived_at timestamp DEFAULT now() NOT NULL,
	conception_date date NULL,
	first_breeding_date date NULL,
	last_breeding_date date NULL,
	expected_due_date date NULL,
	insemination_method varchar(50) NULL,
	pregnancy_status varchar(255) NULL,
	is_pregnant bool NULL,
	last_birth_date date NULL,
	previous_status varchar(20) NULL,
	CONSTRAINT livestock_repro_archive_pkey PRIMARY KEY (id)
);

-- Permissions

ALTER TABLE public.livestock_reproductive_data_archive OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_reproductive_data_archive TO postgres;


-- public.other_expenses definition

-- Drop table

-- DROP TABLE public.other_expenses;

CREATE TABLE public.other_expenses (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	expense_date date NOT NULL,
	category varchar(50) NOT NULL,
	description varchar(255) NOT NULL,
	amount numeric(14, 2) NOT NULL,
	payment_method varchar(30) NULL,
	paid_to varchar(150) NULL,
	receipt_reference varchar(100) NULL,
	notes text NULL,
	is_deleted bool DEFAULT false NOT NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	updated_at timestamp NULL,
	created_by_username varchar(100) NULL,
	CONSTRAINT other_expenses_amount_check CHECK ((amount > (0)::numeric)),
	CONSTRAINT other_expenses_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_other_expenses_category ON public.other_expenses USING btree (category);
CREATE INDEX idx_other_expenses_date ON public.other_expenses USING btree (expense_date);
CREATE INDEX idx_other_expenses_is_deleted ON public.other_expenses USING btree (is_deleted);

-- Permissions

ALTER TABLE public.other_expenses OWNER TO postgres;
GRANT ALL ON TABLE public.other_expenses TO postgres;


-- public.roles definition

-- Drop table

-- DROP TABLE public.roles;

CREATE TABLE public.roles (
	id bigserial NOT NULL,
	role_name varchar(50) NOT NULL,
	description text NULL,
	created_at timestamp DEFAULT now() NULL,
	CONSTRAINT roles_pkey PRIMARY KEY (id),
	CONSTRAINT roles_role_name_key UNIQUE (role_name)
);

-- Permissions

ALTER TABLE public.roles OWNER TO postgres;
GRANT ALL ON TABLE public.roles TO postgres;


-- public.sec_user_type definition

-- Drop table

-- DROP TABLE public.sec_user_type;

CREATE TABLE public.sec_user_type (
	user_type_id uuid DEFAULT uuid_generate_v4() NOT NULL,
	user_type_name varchar(255) NOT NULL,
	CONSTRAINT sec_user_type_ck_01 CHECK (((user_type_name)::text = ANY (ARRAY[('Admin'::character varying)::text, ('Regular User'::character varying)::text]))),
	CONSTRAINT sec_user_type_pk PRIMARY KEY (user_type_id)
);

-- Permissions

ALTER TABLE public.sec_user_type OWNER TO postgres;
GRANT ALL ON TABLE public.sec_user_type TO postgres;


-- public.a_location definition

-- Drop table

-- DROP TABLE public.a_location;

CREATE TABLE public.a_location (
	id uuid NOT NULL,
	state int4 NOT NULL,
	"version" int8 NOT NULL,
	code varchar(255) NOT NULL,
	regulator_code varchar(255) NULL,
	"name" varchar(255) NOT NULL,
	location_type varchar(255) NOT NULL,
	parent_id uuid NULL,
	"comments" varchar(255) NULL,
	CONSTRAINT adm_location_ck_01 CHECK ((state = ANY (ARRAY[0, 1, 2]))),
	CONSTRAINT adm_location_ck_02 CHECK (((location_type)::text = ANY (ARRAY['COUNTRY'::text, 'PROVINCE'::text, 'DISTRICT'::text, 'SECTOR'::text, 'CELL'::text, 'VILLAGE'::text]))),
	CONSTRAINT adm_location_pk PRIMARY KEY (id),
	CONSTRAINT adm_location_uk1 UNIQUE (code),
	CONSTRAINT adm_location_uk2 UNIQUE (name, parent_id),
	CONSTRAINT adm_location_fk1 FOREIGN KEY (parent_id) REFERENCES public.a_location(id)
);

-- Permissions

ALTER TABLE public.a_location OWNER TO postgres;
GRANT ALL ON TABLE public.a_location TO postgres;


-- public.modules definition

-- Drop table

-- DROP TABLE public.modules;

CREATE TABLE public.modules (
	module_id bigserial NOT NULL,
	module_name varchar(100) NOT NULL,
	module_code varchar(50) NOT NULL,
	description text NULL,
	icon varchar(50) NULL,
	display_order int4 DEFAULT 0 NULL,
	is_active bool DEFAULT true NULL,
	parent_module_id int8 NULL,
	module_url varchar(255) NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	CONSTRAINT modules_module_code_key UNIQUE (module_code),
	CONSTRAINT modules_module_name_key UNIQUE (module_name),
	CONSTRAINT modules_pkey PRIMARY KEY (module_id),
	CONSTRAINT modules_parent_module_id_fkey FOREIGN KEY (parent_module_id) REFERENCES public.modules(module_id)
);
CREATE INDEX idx_modules_code ON public.modules USING btree (module_code);
CREATE INDEX idx_modules_parent ON public.modules USING btree (parent_module_id);

-- Permissions

ALTER TABLE public.modules OWNER TO postgres;
GRANT ALL ON TABLE public.modules TO postgres;


-- public.role_module_permissions definition

-- Drop table

-- DROP TABLE public.role_module_permissions;

CREATE TABLE public.role_module_permissions (
	id bigserial NOT NULL,
	role_id int8 NULL,
	module_id int8 NULL,
	can_view bool DEFAULT false NULL,
	can_create bool DEFAULT false NULL,
	can_edit bool DEFAULT false NULL,
	can_delete bool DEFAULT false NULL,
	CONSTRAINT role_module_permissions_pkey PRIMARY KEY (id),
	CONSTRAINT role_module_permissions_role_id_module_id_key UNIQUE (role_id, module_id),
	CONSTRAINT role_module_permissions_module_id_fkey FOREIGN KEY (module_id) REFERENCES public.modules(module_id),
	CONSTRAINT role_module_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id)
);

-- Permissions

ALTER TABLE public.role_module_permissions OWNER TO postgres;
GRANT ALL ON TABLE public.role_module_permissions TO postgres;


-- public.aps_log definition

-- Drop table

-- DROP TABLE public.aps_log;

CREATE TABLE public.aps_log (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	table_name varchar(100) NOT NULL,
	record_id uuid NOT NULL,
	"action" varchar(30) NOT NULL,
	field_changed varchar(100) NULL,
	old_value varchar(255) NULL,
	new_value varchar(255) NULL,
	performed_by uuid NULL,
	performed_at timestamp DEFAULT now() NOT NULL,
	notes text NULL,
	CONSTRAINT aps_log_action_check CHECK (((action)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying, 'RESTORE'::character varying, 'STATUS_CHANGE'::character varying])::text[]))),
	CONSTRAINT aps_log_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_aps_log_action ON public.aps_log USING btree (action);
CREATE INDEX idx_aps_log_performed_at ON public.aps_log USING btree (performed_at DESC);
CREATE INDEX idx_aps_log_performed_by ON public.aps_log USING btree (performed_by);
CREATE INDEX idx_aps_log_table_record ON public.aps_log USING btree (table_name, record_id);

-- Permissions

ALTER TABLE public.aps_log OWNER TO postgres;
GRANT ALL ON TABLE public.aps_log TO postgres;


-- public.beneficiaries definition

-- Drop table

-- DROP TABLE public.beneficiaries;

CREATE TABLE public.beneficiaries (
	id uuid NOT NULL,
	first_name varchar(50) NOT NULL,
	last_name varchar(50) NOT NULL,
	gender varchar(255) NOT NULL,
	maritial_status varchar(255) NOT NULL,
	nid varchar(255) NOT NULL,
	phone varchar(255) NOT NULL,
	contract_agreement varchar(255) NOT NULL,
	representative_id uuid NOT NULL,
	location_id uuid NOT NULL,
	photo varchar(255) NULL,
	created_date timestamp(6) NULL,
	created_by uuid NULL,
	is_deleted bool NULL,
	status varchar(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
	CONSTRAINT abaragizwa_amatungo_pk PRIMARY KEY (id)
);
CREATE INDEX idx_abaragizwa_created_date ON public.beneficiaries USING btree (created_date DESC);
CREATE INDEX idx_abaragizwa_location ON public.beneficiaries USING btree (location_id);
CREATE INDEX idx_abaragizwa_uhagarariye ON public.beneficiaries USING btree (representative_id);
CREATE INDEX idx_beneficiaries_status ON public.beneficiaries USING btree (status);

-- Permissions

ALTER TABLE public.beneficiaries OWNER TO postgres;
GRANT ALL ON TABLE public.beneficiaries TO postgres;


-- public.buyers definition

-- Drop table

-- DROP TABLE public.buyers;

CREATE TABLE public.buyers (
	id uuid NOT NULL,
	buyer_phone varchar(20) NOT NULL,
	buyer_address varchar(255) NULL,
	buyer_national_id varchar(16) NULL,
	buyer_email varchar(100) NULL,
	buyer_type varchar(50) NULL,
	notes text NULL,
	is_active bool DEFAULT true NULL,
	created_at timestamp NOT NULL,
	updated_at timestamp NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	first_name varchar(60) NOT NULL,
	last_name varchar(60) NOT NULL,
	buyer_photo varchar(255) NULL,
	CONSTRAINT buyers_buyer_national_id_key UNIQUE (buyer_national_id),
	CONSTRAINT buyers_pkey PRIMARY KEY (id),
	CONSTRAINT uk_buyer_phone UNIQUE (buyer_phone)
);
CREATE INDEX idx_buyer_national_id ON public.buyers USING btree (buyer_national_id);
CREATE INDEX idx_buyer_phone ON public.buyers USING btree (buyer_phone);

-- Permissions

ALTER TABLE public.buyers OWNER TO postgres;
GRANT ALL ON TABLE public.buyers TO postgres;


-- public.livestock definition

-- Drop table

-- DROP TABLE public.livestock;

CREATE TABLE public.livestock (
	id uuid NOT NULL,
	acquisition_method varchar(255) NULL,
	current_value numeric(38, 2) NULL,
	date_received date NULL,
	gender varchar(255) NULL,
	offspring_count int4 NULL,
	photo varchar(255) NULL,
	status varchar(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
	tag_number varchar(255) NOT NULL,
	beneficiary_id uuid NULL,
	created_by uuid NULL,
	livestock_category_id uuid NULL,
	location_id uuid NULL,
	mother_id uuid NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	is_deleted bool DEFAULT false NOT NULL,
	birth_date date NULL,
	source_location varchar(255) NULL,
	is_draft bool DEFAULT false NOT NULL,
	draft_birth_id uuid NULL,
	acquisition_source varchar(255) NULL,
	updated_at timestamp NULL,
	deleted_at timestamp NULL,
	deleted_by varchar(255) NULL,
	reproductive_status varchar(20) DEFAULT 'OPEN'::character varying NOT NULL,
	health_status varchar(20) DEFAULT 'HEALTHY'::character varying NOT NULL,
	father_id uuid NULL,
	breeding_ready_notified_at timestamp NULL, -- Timestamp of the last "ready to breed" email sent via BreedingReadinessNotificationService. NULL = not yet notified for the current eligibility window. Reset to NULL if you want the animal re-notified (e.g. after a failed breeding puts a female back to OPEN).
	last_lifecycle_stage varchar(30) NULL,
	is_group bool DEFAULT false NOT NULL,
	quantity int4 NULL,
	notes varchar(2000) NULL,
	CONSTRAINT chk_livestock_health_status CHECK (((health_status)::text = ANY ((ARRAY['HEALTHY'::character varying, 'SICK'::character varying, 'UNDER_TREATMENT'::character varying])::text[]))),
	CONSTRAINT chk_livestock_reproductive_status CHECK (((reproductive_status)::text = ANY ((ARRAY['READY_TO_BREED'::character varying, 'BRED'::character varying, 'PREGNANT'::character varying, 'OPEN'::character varying, 'NOT_APPLICABLE'::character varying])::text[]))),
	CONSTRAINT chk_livestock_status CHECK (((status)::text = ANY (ARRAY['ACTIVE'::text, 'SOLD'::text, 'DEAD'::text, 'TRANSFERRED'::text]))),
	CONSTRAINT chk_quantity_positive CHECK (((quantity IS NULL) OR (quantity >= 1))),
	CONSTRAINT livestock_pkey PRIMARY KEY (id),
	CONSTRAINT ukfcgt8b12n9pcm1dr2nuy3gx61 UNIQUE (tag_number)
);
CREATE INDEX idx_livestock_acquisition ON public.livestock USING btree (acquisition_method, date_received) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_breeding_females ON public.livestock USING btree (gender, date_received, status) WHERE (((gender)::text = 'FEMALE'::text) AND (is_deleted = false));
CREATE INDEX idx_livestock_breeding_males ON public.livestock USING btree (gender, date_received, status) WHERE (((gender)::text = 'MALE'::text) AND (is_deleted = false));
CREATE INDEX idx_livestock_category ON public.livestock USING btree (livestock_category_id);
CREATE INDEX idx_livestock_deleted ON public.livestock USING btree (is_deleted) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_deleted_at ON public.livestock USING btree (deleted_at) WHERE (deleted_at IS NOT NULL);
CREATE INDEX idx_livestock_deleted_rows ON public.livestock USING btree (id) WHERE (is_deleted = true);
CREATE INDEX idx_livestock_draft_birth_id ON public.livestock USING btree (draft_birth_id) WHERE (is_draft = true);
CREATE INDEX idx_livestock_gender_status ON public.livestock USING btree (gender, status) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_health_status ON public.livestock USING btree (health_status) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_is_draft ON public.livestock USING btree (is_draft) WHERE (is_draft = true);
CREATE INDEX idx_livestock_is_group ON public.livestock USING btree (is_group) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_lifecycle ON public.livestock USING btree (livestock_category_id, date_received, gender, status, is_deleted) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_mother ON public.livestock USING btree (mother_id) WHERE ((mother_id IS NOT NULL) AND (is_deleted = false));
CREATE INDEX idx_livestock_owner ON public.livestock USING btree (beneficiary_id);
CREATE INDEX idx_livestock_reproductive_status ON public.livestock USING btree (reproductive_status) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_source_location ON public.livestock USING btree (source_location) WHERE (source_location IS NOT NULL);
CREATE INDEX idx_livestock_status ON public.livestock USING btree (status);

-- Column comments

COMMENT ON COLUMN public.livestock.breeding_ready_notified_at IS 'Timestamp of the last "ready to breed" email sent via BreedingReadinessNotificationService. NULL = not yet notified for the current eligibility window. Reset to NULL if you want the animal re-notified (e.g. after a failed breeding puts a female back to OPEN).';

-- Permissions

ALTER TABLE public.livestock OWNER TO postgres;
GRANT ALL ON TABLE public.livestock TO postgres;


-- public.livestock_abortions definition

-- Drop table

-- DROP TABLE public.livestock_abortions;

CREATE TABLE public.livestock_abortions (
	id uuid NOT NULL,
	abortion_date date NOT NULL,
	pregnancy_number int4 NOT NULL,
	livestock_id uuid NOT NULL,
	abortion_reason varchar(255) NULL,
	stage_of_pregnancy varchar(255) NULL,
	breeding_id uuid NULL,
	veterinarian_id uuid NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	expected_birth_date date NULL,
	CONSTRAINT itungo_ryimye_pk PRIMARY KEY (id)
);

-- Permissions

ALTER TABLE public.livestock_abortions OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_abortions TO postgres;


-- public.livestock_births definition

-- Drop table

-- DROP TABLE public.livestock_births;

CREATE TABLE public.livestock_births (
	id uuid NOT NULL,
	breeding_id uuid NULL,
	veterinarian_id uuid NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	birth_date date NULL,
	livestock_id uuid NULL,
	next_breeding_date date NULL,
	notes text NULL,
	offspring_count int4 NULL,
	offspring_gender varchar(20) NULL,
	weaning_date date NULL,
	is_external_birth bool DEFAULT false NOT NULL,
	source_location varchar(255) NULL,
	completed_at timestamp NULL,
	birth_date_note varchar(500) NULL,
	updated_at timestamp NULL,
	birth_type varchar(20) NULL,
	assistance_required bool DEFAULT false NULL,
	number_born_alive int4 NULL,
	number_stillborn int4 DEFAULT 0 NULL,
	attendant_name varchar(150) NULL,
	CONSTRAINT chk_livestock_births_birth_type CHECK (((birth_type IS NULL) OR ((birth_type)::text = ANY ((ARRAY['NORMAL'::character varying, 'DIFFICULT'::character varying])::text[])))),
	CONSTRAINT livestock_births_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_birth_date ON public.livestock_births USING btree (birth_date);
CREATE INDEX idx_births_breeding ON public.livestock_births USING btree (breeding_id);
CREATE INDEX idx_livestock_births_birth_date ON public.livestock_births USING btree (birth_date DESC);
CREATE INDEX idx_livestock_births_is_deleted ON public.livestock_births USING btree (is_deleted) WHERE (is_deleted = false);
CREATE INDEX idx_livestock_births_livestock_id ON public.livestock_births USING btree (livestock_id);

-- Permissions

ALTER TABLE public.livestock_births OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_births TO postgres;


-- public.livestock_breeding definition

-- Drop table

-- DROP TABLE public.livestock_breeding;

CREATE TABLE public.livestock_breeding (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	livestock_id uuid NOT NULL,
	first_breeding_date date NULL,
	last_breeding_date date NULL,
	breeding_attempt int4 NULL,
	insemination_method varchar(50) NULL,
	conception_date date NULL,
	expected_due_date date NULL,
	is_pregnant bool DEFAULT false NOT NULL,
	pregnancy_status varchar(20) DEFAULT 'NOT_PREGNANT'::character varying NOT NULL,
	failure_reason text NULL,
	notes text NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	updated_at timestamp NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	deleted_at timestamp NULL,
	deleted_by varchar(255) NULL,
	status varchar(30) DEFAULT 'PENDING'::character varying NOT NULL,
	breeding_date date NOT NULL,
	breeding_method varchar(50) NULL,
	male_livestock_id uuid NULL,
	veterinarian_id uuid NULL,
	expected_pregnancy_check_date date NULL,
	is_active bool DEFAULT true NULL,
	male_id uuid NULL,
	updated_by varchar(100) NULL,
	pregnancy_confirmed_date date NULL, -- Date confirmPregnancy() was called for this record. Distinct from conception_date (presumed = breeding date until proven otherwise) and expected_due_date (calculated from gestation length).
	CONSTRAINT chk_breeding_pregnancy_status CHECK (((pregnancy_status)::text = ANY (ARRAY['PREGNANT'::text, 'NOT_PREGNANT'::text, 'CONFIRMED'::text, 'FAILED'::text]))),
	CONSTRAINT chk_livestock_breeding_method CHECK (((breeding_method IS NULL) OR ((breeding_method)::text = ANY ((ARRAY['NATURAL'::character varying, 'ARTIFICIAL_INSEMINATION'::character varying, 'EMBRYO_TRANSFER'::character varying, 'PURCHASE_PREGNANT'::character varying])::text[])))),
	CONSTRAINT chk_livestock_breeding_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED_PREGNANT'::character varying, 'FAILED'::character varying, 'COMPLETED'::character varying])::text[]))),
	CONSTRAINT livestock_breeding_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_breeding_active_preg ON public.livestock_breeding USING btree (livestock_id, expected_due_date) WHERE ((is_pregnant = true) AND (is_deleted = false));
CREATE INDEX idx_breeding_deleted_at ON public.livestock_breeding USING btree (deleted_at);
CREATE INDEX idx_breeding_due_date ON public.livestock_breeding USING btree (expected_due_date) WHERE (is_deleted = false);
CREATE INDEX idx_breeding_livestock_id ON public.livestock_breeding USING btree (livestock_id);
CREATE INDEX idx_breeding_pregnancy ON public.livestock_breeding USING btree (pregnancy_status) WHERE (is_deleted = false);
CREATE INDEX idx_breeding_status ON public.livestock_breeding USING btree (status);

-- Column comments

COMMENT ON COLUMN public.livestock_breeding.pregnancy_confirmed_date IS 'Date confirmPregnancy() was called for this record. Distinct from conception_date (presumed = breeding date until proven otherwise) and expected_due_date (calculated from gestation length).';

-- Permissions

ALTER TABLE public.livestock_breeding OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_breeding TO postgres;


-- public.livestock_deaths definition

-- Drop table

-- DROP TABLE public.livestock_deaths;

CREATE TABLE public.livestock_deaths (
	id uuid NOT NULL,
	cause_of_death varchar(255) NULL,
	death_date date NOT NULL,
	livestock_id uuid NOT NULL,
	veterinarian_id uuid NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	notes text NULL,
	CONSTRAINT livestock_deaths_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_death_date ON public.livestock_deaths USING btree (death_date);

-- Permissions

ALTER TABLE public.livestock_deaths OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_deaths TO postgres;


-- public.livestock_import_batch definition

-- Drop table

-- DROP TABLE public.livestock_import_batch;

CREATE TABLE public.livestock_import_batch (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	imported_by uuid NULL,
	imported_at timestamp DEFAULT now() NOT NULL,
	file_name varchar(255) NULL,
	file_size_bytes int8 NULL,
	total_rows int4 DEFAULT 0 NOT NULL,
	ok_rows int4 DEFAULT 0 NOT NULL,
	warning_rows int4 DEFAULT 0 NOT NULL,
	error_rows int4 DEFAULT 0 NOT NULL,
	created_count int4 DEFAULT 0 NOT NULL,
	created_tags text NULL,
	status varchar(30) DEFAULT 'COMPLETED'::character varying NOT NULL,
	notes text NULL,
	CONSTRAINT livestock_import_batch_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_import_batch_date ON public.livestock_import_batch USING btree (imported_at DESC);
CREATE INDEX idx_import_batch_user ON public.livestock_import_batch USING btree (imported_by);

-- Permissions

ALTER TABLE public.livestock_import_batch OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_import_batch TO postgres;


-- public.livestock_notification_log definition

-- Drop table

-- DROP TABLE public.livestock_notification_log;

CREATE TABLE public.livestock_notification_log (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	livestock_id uuid NOT NULL,
	breeding_id uuid NULL,
	notification_type varchar(40) NOT NULL,
	dedupe_key varchar(120) NOT NULL,
	reference_date date NULL,
	sent_at timestamp DEFAULT now() NOT NULL,
	additional_info varchar(500) NULL,
	CONSTRAINT livestock_notification_log_pkey PRIMARY KEY (id),
	CONSTRAINT uq_notification_dedupe UNIQUE (notification_type, dedupe_key)
);
CREATE INDEX idx_notification_log_breeding ON public.livestock_notification_log USING btree (breeding_id);
CREATE INDEX idx_notification_log_livestock ON public.livestock_notification_log USING btree (livestock_id);

-- Permissions

ALTER TABLE public.livestock_notification_log OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_notification_log TO postgres;


-- public.livestock_offspring definition

-- Drop table

-- DROP TABLE public.livestock_offspring;

CREATE TABLE public.livestock_offspring (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	birth_id uuid NOT NULL,
	child_livestock_id uuid NULL,
	generation int4 DEFAULT 1 NULL,
	is_deleted bool DEFAULT false NOT NULL,
	is_alive bool DEFAULT true NOT NULL,
	outcome varchar(30) DEFAULT 'ALIVE'::character varying NOT NULL,
	birth_weight_kg numeric(6, 2) NULL,
	CONSTRAINT chk_offspring_outcome CHECK (((outcome)::text = ANY ((ARRAY['ALIVE'::character varying, 'STILLBORN'::character varying, 'DIED_SHORTLY_AFTER'::character varying])::text[]))),
	CONSTRAINT livestock_offspring_pkey PRIMARY KEY (id),
	CONSTRAINT uq_child UNIQUE (child_livestock_id)
);
CREATE INDEX idx_offspring_birth_id ON public.livestock_offspring USING btree (birth_id);
CREATE INDEX idx_offspring_child_id ON public.livestock_offspring USING btree (child_livestock_id);

-- Permissions

ALTER TABLE public.livestock_offspring OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_offspring TO postgres;


-- public.livestock_sale_payments definition

-- Drop table

-- DROP TABLE public.livestock_sale_payments;

CREATE TABLE public.livestock_sale_payments (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	sale_id uuid NOT NULL,
	amount numeric(12, 2) NOT NULL,
	payment_date timestamp NOT NULL,
	payment_method varchar(50) NULL,
	payment_reference varchar(100) NULL,
	notes text NULL,
	recorded_by uuid NULL,
	recorded_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	CONSTRAINT livestock_sale_payments_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_livestock_sale_payments_sale_id ON public.livestock_sale_payments USING btree (sale_id);

-- Permissions

ALTER TABLE public.livestock_sale_payments OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_sale_payments TO postgres;


-- public.livestock_sales definition

-- Drop table

-- DROP TABLE public.livestock_sales;

CREATE TABLE public.livestock_sales (
	id uuid NOT NULL,
	sale_date date NOT NULL,
	sale_location varchar(255) NULL,
	sale_price numeric(12, 2) NULL,
	sale_reason varchar(255) NULL,
	livestock_id uuid NOT NULL,
	buyer_id uuid NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	sale_reference varchar(50) NULL,
	acquisition_cost numeric(12, 2) NULL,
	amount_paid numeric(12, 2) DEFAULT 0 NULL,
	payment_status varchar(30) DEFAULT 'PENDING'::character varying NULL,
	payment_method varchar(50) NULL,
	approved_by uuid NULL,
	approved_at timestamp NULL,
	notes text NULL,
	payment_reference varchar(255) NULL,
	sale_status varchar(30) DEFAULT 'DRAFT'::character varying NULL,
	confirmed_at timestamp NULL,
	confirmed_by uuid NULL,
	cancelled_at timestamp NULL,
	cancelled_by uuid NULL,
	cancellation_reason text NULL,
	refunded_at timestamp NULL,
	refunded_by uuid NULL,
	refund_reason text NULL,
	refund_amount numeric(12, 2) NULL,
	CONSTRAINT livestock_sales_pkey PRIMARY KEY (id),
	CONSTRAINT livestock_sales_sale_reference_key UNIQUE (sale_reference)
);
CREATE INDEX idx_livestock_sales_acquisition_cost ON public.livestock_sales USING btree (acquisition_cost);
CREATE INDEX idx_livestock_sales_amount_paid ON public.livestock_sales USING btree (amount_paid);
CREATE INDEX idx_livestock_sales_approved_by ON public.livestock_sales USING btree (approved_by);
CREATE INDEX idx_livestock_sales_payment_status ON public.livestock_sales USING btree (payment_status);
CREATE INDEX idx_livestock_sales_sale_date ON public.livestock_sales USING btree (sale_date);
CREATE INDEX idx_livestock_sales_sale_reference ON public.livestock_sales USING btree (sale_reference);
CREATE INDEX idx_livestock_sales_sale_status ON public.livestock_sales USING btree (sale_status);
CREATE INDEX idx_sale_date ON public.livestock_sales USING btree (sale_date);
CREATE INDEX idx_sales_buyer ON public.livestock_sales USING btree (buyer_id);

-- Permissions

ALTER TABLE public.livestock_sales OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_sales TO postgres;


-- public.livestock_sick definition

-- Drop table

-- DROP TABLE public.livestock_sick;

CREATE TABLE public.livestock_sick (
	id uuid NOT NULL,
	recovery_date date NULL,
	reported_date date NOT NULL,
	status varchar(255) NOT NULL,
	symptoms text NULL,
	livestock_id uuid NOT NULL,
	diagnosis text NULL,
	severity_level varchar(255) NULL,
	temperature numeric(4, 1) NULL,
	treatment_notes text NULL,
	veterinarian_id uuid NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	CONSTRAINT livestock_sick_pkey PRIMARY KEY (id),
	CONSTRAINT livestock_sick_severity_level_check CHECK (((severity_level)::text = ANY ((ARRAY['MILD'::character varying, 'MODERATE'::character varying, 'SEVERE'::character varying, 'LIFE_THREATENING'::character varying])::text[]))),
	CONSTRAINT livestock_sick_status_check CHECK (((status)::text = ANY ((ARRAY['SICK'::character varying, 'CRITICAL'::character varying, 'RECOVERING'::character varying, 'RECOVERED'::character varying, 'DEAD'::character varying])::text[])))
);
CREATE INDEX idx_sick_livestock ON public.livestock_sick USING btree (livestock_id);
CREATE INDEX idx_sick_reported ON public.livestock_sick USING btree (reported_date);
CREATE INDEX idx_sick_status ON public.livestock_sick USING btree (status) WHERE (is_deleted = false);
CREATE INDEX idx_sick_vet_reported ON public.livestock_sick USING btree (veterinarian_id, reported_date);

-- Permissions

ALTER TABLE public.livestock_sick OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_sick TO postgres;


-- public.livestock_sick_history definition

-- Drop table

-- DROP TABLE public.livestock_sick_history;

CREATE TABLE public.livestock_sick_history (
	id uuid NOT NULL,
	changed_at timestamp(6) NOT NULL,
	changed_by varchar(150) NULL,
	notes text NULL,
	severity_level varchar(20) NULL,
	status varchar(20) NOT NULL,
	sick_id uuid NOT NULL,
	is_deleted bool DEFAULT false NOT NULL,
	CONSTRAINT livestock_sick_history_pkey PRIMARY KEY (id),
	CONSTRAINT livestock_sick_history_severity_level_check CHECK (((severity_level)::text = ANY ((ARRAY['MILD'::character varying, 'MODERATE'::character varying, 'SEVERE'::character varying, 'LIFE_THREATENING'::character varying])::text[]))),
	CONSTRAINT livestock_sick_history_status_check CHECK (((status)::text = ANY ((ARRAY['SICK'::character varying, 'CRITICAL'::character varying, 'RECOVERING'::character varying, 'RECOVERED'::character varying])::text[])))
);
CREATE INDEX idx_sick_history_changed_at ON public.livestock_sick_history USING btree (changed_at);
CREATE INDEX idx_sick_history_sick_id ON public.livestock_sick_history USING btree (sick_id);
CREATE INDEX idx_sick_history_status ON public.livestock_sick_history USING btree (status);

-- Permissions

ALTER TABLE public.livestock_sick_history OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_sick_history TO postgres;


-- public.livestock_treatment_payments definition

-- Drop table

-- DROP TABLE public.livestock_treatment_payments;

CREATE TABLE public.livestock_treatment_payments (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	treatment_id uuid NOT NULL,
	amount numeric(12, 2) NOT NULL,
	payment_date date NOT NULL,
	payment_method varchar(30) NULL,
	payment_reference varchar(100) NULL,
	payment_status varchar(20) DEFAULT 'COMPLETED'::character varying NULL,
	invoice_number varchar(50) NULL,
	notes text NULL,
	recorded_by uuid NULL,
	recorded_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	is_deleted bool DEFAULT false NULL,
	CONSTRAINT livestock_treatment_payments_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_treatment_payments_payment_date ON public.livestock_treatment_payments USING btree (payment_date);
CREATE INDEX idx_treatment_payments_treatment_id ON public.livestock_treatment_payments USING btree (treatment_id);

-- Permissions

ALTER TABLE public.livestock_treatment_payments OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_treatment_payments TO postgres;


-- public.livestock_treatments definition

-- Drop table

-- DROP TABLE public.livestock_treatments;

CREATE TABLE public.livestock_treatments (
	id uuid NOT NULL,
	medication varchar(255) NULL,
	next_treatment_date date NULL,
	treatment_cost numeric(12, 2) NULL,
	treatment_date date NOT NULL,
	sick_livestock_id uuid NULL,
	description varchar(255) NULL,
	dosage varchar(30) NULL,
	treatment_type varchar(30) NULL,
	medication_id uuid NULL,
	dosage_unit varchar(20) NULL,
	frequency varchar(30) NULL,
	treatment_duration varchar(50) NULL,
	treatment_status varchar(20) DEFAULT 'ONGOING'::character varying NULL,
	is_paid bool DEFAULT false NULL,
	payment_date date NULL,
	created_at timestamp NULL,
	livestock_id uuid NULL,
	veterinarian_id uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	created_by uuid NULL,
	amount_paid numeric(12, 2) DEFAULT 0.00 NULL,
	payment_status varchar(20) DEFAULT 'UNPAID'::character varying NULL,
	payment_method varchar(50) NULL,
	payment_reference varchar(100) NULL,
	payment_due_date date NULL,
	payment_notes text NULL,
	start_date date NULL,
	end_date date NULL,
	updated_at timestamp NULL,
	updated_by uuid NULL,
	withdrawal_period_days int4 NULL,
	withdrawal_end_date date NULL,
	product_affected varchar(20) NULL,
	batch_number varchar(100) NULL,
	expiry_date date NULL,
	manufacturer varchar(255) NULL,
	outcome_status varchar(30) NULL,
	outcome_notes text NULL,
	follow_up_instructions text NULL,
	follow_up_date date NULL,
	diagnosis_name varchar(255) NULL,
	symptoms text NULL,
	clinical_observations text NULL,
	severity varchar(20) NULL,
	treatment_purpose text NULL,
	treatment_reference varchar(50) DEFAULT concat('TXN-', to_char(CURRENT_DATE::timestamp with time zone, 'YYYYMMDD'::text), '-', lpad(floor(random() * 10000::double precision)::character varying::text, 4, '0'::text)) NOT NULL,
	CONSTRAINT livestock_treatments_pkey PRIMARY KEY (id),
	CONSTRAINT livestock_treatments_treatment_reference_key UNIQUE (treatment_reference)
);
CREATE INDEX idx_lt_medication_id ON public.livestock_treatments USING btree (medication_id);
CREATE INDEX idx_treatment_date ON public.livestock_treatments USING btree (treatment_date);
CREATE INDEX idx_treatment_livestock_date ON public.livestock_treatments USING btree (livestock_id, treatment_date);
CREATE INDEX idx_treatment_vet_date ON public.livestock_treatments USING btree (veterinarian_id, treatment_date);
CREATE INDEX idx_treatments_is_deleted ON public.livestock_treatments USING btree (is_deleted);
CREATE INDEX idx_treatments_livestock ON public.livestock_treatments USING btree (livestock_id);
CREATE INDEX idx_treatments_livestock_id ON public.livestock_treatments USING btree (livestock_id);
CREATE INDEX idx_treatments_medication_id ON public.livestock_treatments USING btree (medication_id);
CREATE INDEX idx_treatments_sick ON public.livestock_treatments USING btree (sick_livestock_id);
CREATE INDEX idx_treatments_treatment_date ON public.livestock_treatments USING btree (treatment_date);
CREATE INDEX idx_treatments_treatment_reference ON public.livestock_treatments USING btree (treatment_reference);
CREATE INDEX idx_treatments_treatment_status ON public.livestock_treatments USING btree (treatment_status);
CREATE INDEX idx_treatments_veterinarian_id ON public.livestock_treatments USING btree (veterinarian_id);

-- Permissions

ALTER TABLE public.livestock_treatments OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_treatments TO postgres;


-- public.livestock_valuation_history definition

-- Drop table

-- DROP TABLE public.livestock_valuation_history;

CREATE TABLE public.livestock_valuation_history (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	livestock_id uuid NOT NULL,
	valuation_date date NOT NULL,
	value numeric(12, 2) NOT NULL,
	valuation_method varchar(30) NOT NULL,
	notes varchar(500) NULL,
	recorded_by varchar(100) NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	CONSTRAINT livestock_valuation_history_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_valuation_date ON public.livestock_valuation_history USING btree (livestock_id, valuation_date DESC);
CREATE INDEX idx_valuation_livestock_id ON public.livestock_valuation_history USING btree (livestock_id);

-- Permissions

ALTER TABLE public.livestock_valuation_history OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_valuation_history TO postgres;


-- public.livestock_weight_history definition

-- Drop table

-- DROP TABLE public.livestock_weight_history;

CREATE TABLE public.livestock_weight_history (
	id uuid NOT NULL,
	livestock_id uuid NOT NULL,
	weighed_on date NOT NULL,
	weight_kg numeric(7, 2) NOT NULL,
	weight_method varchar(30) DEFAULT 'SCALE'::character varying NOT NULL,
	head_count int4 DEFAULT 1 NOT NULL,
	body_condition varchar(20) NULL,
	recorded_by varchar(100) NULL,
	notes varchar(500) NULL,
	is_deleted bool DEFAULT false NOT NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	updated_at timestamp NULL,
	CONSTRAINT livestock_weight_history_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_weight_livestock_id ON public.livestock_weight_history USING btree (livestock_id);
CREATE INDEX idx_weight_weighed_on ON public.livestock_weight_history USING btree (weighed_on);

-- Permissions

ALTER TABLE public.livestock_weight_history OWNER TO postgres;
GRANT ALL ON TABLE public.livestock_weight_history TO postgres;


-- public.medications definition

-- Drop table

-- DROP TABLE public.medications;

CREATE TABLE public.medications (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	"name" varchar(150) NOT NULL,
	generic_name varchar(150) NULL,
	category varchar(40) NULL,
	default_dosage varchar(30) NULL,
	default_dosage_unit varchar(20) NULL,
	manufacturer varchar(150) NULL,
	description text NULL,
	is_active bool DEFAULT true NOT NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	updated_at timestamp DEFAULT now() NOT NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	CONSTRAINT medications_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_medications_category ON public.medications USING btree (category);
CREATE INDEX idx_medications_is_active ON public.medications USING btree (is_active);
CREATE INDEX idx_medications_name ON public.medications USING btree (name);

-- Permissions

ALTER TABLE public.medications OWNER TO postgres;
GRANT ALL ON TABLE public.medications TO postgres;


-- public.representatives definition

-- Drop table

-- DROP TABLE public.representatives;

CREATE TABLE public.representatives (
	id uuid NOT NULL,
	first_name varchar(50) NOT NULL,
	last_name varchar(50) NOT NULL,
	gender varchar(255) NOT NULL,
	maritial_status varchar(255) NOT NULL,
	nid varchar(255) NOT NULL,
	phone varchar(255) NOT NULL,
	email varchar(255) NOT NULL,
	occupation varchar(255) NOT NULL,
	contract_agreement varchar(255) NOT NULL,
	photo varchar(255) NOT NULL,
	location_id uuid NOT NULL,
	created_date timestamp(6) NULL,
	created_by uuid NULL,
	is_deleted bool NULL,
	status varchar(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
	CONSTRAINT uhagarariye_aborora_pk PRIMARY KEY (id)
);

-- Permissions

ALTER TABLE public.representatives OWNER TO postgres;
GRANT ALL ON TABLE public.representatives TO postgres;


-- public.sec_user definition

-- Drop table

-- DROP TABLE public.sec_user;

CREATE TABLE public.sec_user (
	user_id uuid NOT NULL,
	email varchar(255) NOT NULL,
	is_active bool NOT NULL,
	"password" varchar(255) NOT NULL,
	registration_date timestamp(6) NULL,
	user_type_id uuid NOT NULL,
	photo_url varchar(255) NULL,
	beneficiary_id uuid NULL,
	CONSTRAINT sec_user_pk PRIMARY KEY (user_id)
);

-- Permissions

ALTER TABLE public.sec_user OWNER TO postgres;
GRANT ALL ON TABLE public.sec_user TO postgres;


-- public.treatment_audit_log definition

-- Drop table

-- DROP TABLE public.treatment_audit_log;

CREATE TABLE public.treatment_audit_log (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	treatment_id uuid NOT NULL,
	"action" varchar(50) NOT NULL,
	old_value text NULL,
	new_value text NULL,
	performed_by uuid NULL,
	performed_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	notes text NULL,
	CONSTRAINT treatment_audit_log_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_treatment_audit_performed_at ON public.treatment_audit_log USING btree (performed_at);
CREATE INDEX idx_treatment_audit_treatment_id ON public.treatment_audit_log USING btree (treatment_id);

-- Permissions

ALTER TABLE public.treatment_audit_log OWNER TO postgres;
GRANT ALL ON TABLE public.treatment_audit_log TO postgres;


-- public.user_modules definition

-- Drop table

-- DROP TABLE public.user_modules;

CREATE TABLE public.user_modules (
	user_module_id bigserial NOT NULL,
	user_id uuid NOT NULL,
	module_id int8 NOT NULL,
	can_view bool DEFAULT true NULL,
	can_create bool DEFAULT false NULL,
	can_edit bool DEFAULT false NULL,
	can_delete bool DEFAULT false NULL,
	assigned_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	assigned_by uuid NULL,
	CONSTRAINT user_modules_pkey PRIMARY KEY (user_module_id),
	CONSTRAINT user_modules_user_id_module_id_key UNIQUE (user_id, module_id)
);
CREATE INDEX idx_user_modules_module ON public.user_modules USING btree (module_id);
CREATE INDEX idx_user_modules_user ON public.user_modules USING btree (user_id);

-- Permissions

ALTER TABLE public.user_modules OWNER TO postgres;
GRANT ALL ON TABLE public.user_modules TO postgres;


-- public.user_roles definition

-- Drop table

-- DROP TABLE public.user_roles;

CREATE TABLE public.user_roles (
	id bigserial NOT NULL,
	user_id uuid NULL,
	role_id int8 NULL,
	CONSTRAINT user_roles_pkey PRIMARY KEY (id),
	CONSTRAINT user_roles_user_id_role_id_key UNIQUE (user_id, role_id)
);

-- Permissions

ALTER TABLE public.user_roles OWNER TO postgres;
GRANT ALL ON TABLE public.user_roles TO postgres;


-- public.user_type_modules definition

-- Drop table

-- DROP TABLE public.user_type_modules;

CREATE TABLE public.user_type_modules (
	user_type_module_id bigserial NOT NULL,
	user_type_id uuid NOT NULL,
	module_id int8 NOT NULL,
	can_view bool DEFAULT true NULL,
	can_create bool DEFAULT false NULL,
	can_edit bool DEFAULT false NULL,
	can_delete bool DEFAULT false NULL,
	assigned_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	assigned_by uuid NULL,
	CONSTRAINT user_type_modules_pkey PRIMARY KEY (user_type_module_id),
	CONSTRAINT user_type_modules_user_type_id_module_id_key UNIQUE (user_type_id, module_id)
);
CREATE INDEX idx_user_type_modules_module ON public.user_type_modules USING btree (module_id);
CREATE INDEX idx_user_type_modules_user_type ON public.user_type_modules USING btree (user_type_id);

-- Permissions

ALTER TABLE public.user_type_modules OWNER TO postgres;
GRANT ALL ON TABLE public.user_type_modules TO postgres;


-- public.veterinarians definition

-- Drop table

-- DROP TABLE public.veterinarians;

CREATE TABLE public.veterinarians (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	first_name varchar(100) NOT NULL,
	last_name varchar(100) NOT NULL,
	phone varchar(20) NULL,
	email varchar(150) NULL,
	license_number varchar(100) NULL,
	specialization varchar(200) NULL,
	clinic_name varchar(200) NULL,
	location_id uuid NULL,
	is_active bool DEFAULT true NOT NULL,
	notes text NULL,
	created_at timestamp DEFAULT now() NOT NULL,
	created_by uuid NULL,
	is_deleted bool DEFAULT false NOT NULL,
	national_id varchar(16) NULL, -- National ID number for veterinarian identification
	CONSTRAINT veterinarians_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_veterinarians_active ON public.veterinarians USING btree (is_active);
CREATE INDEX idx_veterinarians_nid ON public.veterinarians USING btree (national_id) WHERE (national_id IS NOT NULL);
CREATE INDEX idx_veterinarians_search ON public.veterinarians USING btree (first_name, last_name, license_number);

-- Column comments

COMMENT ON COLUMN public.veterinarians.national_id IS 'National ID number for veterinarian identification';

-- Permissions

ALTER TABLE public.veterinarians OWNER TO postgres;
GRANT ALL ON TABLE public.veterinarians TO postgres;


-- public.aps_log foreign keys

ALTER TABLE public.aps_log ADD CONSTRAINT aps_log_performed_by_fkey FOREIGN KEY (performed_by) REFERENCES public.sec_user(user_id);


-- public.beneficiaries foreign keys

ALTER TABLE public.beneficiaries ADD CONSTRAINT fkthu7d1rrkwnid4ot8cw2qkwn3 FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.beneficiaries ADD CONSTRAINT location_fk FOREIGN KEY (location_id) REFERENCES public.a_location(id);
ALTER TABLE public.beneficiaries ADD CONSTRAINT uhagarariye_aborora_fk FOREIGN KEY (representative_id) REFERENCES public.representatives(id);


-- public.buyers foreign keys

ALTER TABLE public.buyers ADD CONSTRAINT buyers_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);


-- public.livestock foreign keys

ALTER TABLE public.livestock ADD CONSTRAINT fk4e011e7fdi0uicjpdtm6umdg8 FOREIGN KEY (beneficiary_id) REFERENCES public.beneficiaries(id);
ALTER TABLE public.livestock ADD CONSTRAINT fk8clvqr2eux4tkfamiid5l6qv3 FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock ADD CONSTRAINT fkhb612hbwpogo8q4plb7hfouuw FOREIGN KEY (livestock_category_id) REFERENCES public.livestock_categories(id);
ALTER TABLE public.livestock ADD CONSTRAINT fkiyd2i5e6kogxsvinqdprgdnmc FOREIGN KEY (location_id) REFERENCES public.a_location(id);
ALTER TABLE public.livestock ADD CONSTRAINT livestock_draft_birth_id_fkey FOREIGN KEY (draft_birth_id) REFERENCES public.livestock_births(id) ON DELETE SET NULL;
ALTER TABLE public.livestock ADD CONSTRAINT livestock_father_fk FOREIGN KEY (father_id) REFERENCES public.livestock(id) ON DELETE SET NULL;
ALTER TABLE public.livestock ADD CONSTRAINT livestock_mother_id_fkey FOREIGN KEY (mother_id) REFERENCES public.livestock(id) ON DELETE SET NULL;


-- public.livestock_abortions foreign keys

ALTER TABLE public.livestock_abortions ADD CONSTRAINT fkipc6jui2uybddrqi3ct60wmkg FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_abortions ADD CONSTRAINT livestock_abortions_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_abortions ADD CONSTRAINT livestock_abortions_veterinarian_id_fkey FOREIGN KEY (veterinarian_id) REFERENCES public.veterinarians(id);


-- public.livestock_births foreign keys

ALTER TABLE public.livestock_births ADD CONSTRAINT fkc7u7ed2m01v118hbf1ne5u47n FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_births ADD CONSTRAINT livestock_births_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_births ADD CONSTRAINT livestock_births_veterinarian_id_fkey FOREIGN KEY (veterinarian_id) REFERENCES public.veterinarians(id);


-- public.livestock_breeding foreign keys

ALTER TABLE public.livestock_breeding ADD CONSTRAINT fk_breeding_created_by FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_breeding ADD CONSTRAINT fk_breeding_livestock FOREIGN KEY (livestock_id) REFERENCES public.livestock(id) ON DELETE CASCADE;
ALTER TABLE public.livestock_breeding ADD CONSTRAINT fk_livestock_breeding_male FOREIGN KEY (male_livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_breeding ADD CONSTRAINT fk_livestock_breeding_veterinarian FOREIGN KEY (veterinarian_id) REFERENCES public.veterinarians(id);


-- public.livestock_deaths foreign keys

ALTER TABLE public.livestock_deaths ADD CONSTRAINT fkaowjm5fo18a1s44ti1taujb4i FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_deaths ADD CONSTRAINT livestock_deaths_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_deaths ADD CONSTRAINT livestock_deaths_veterinarian_id_fkey FOREIGN KEY (veterinarian_id) REFERENCES public.veterinarians(id);


-- public.livestock_import_batch foreign keys

ALTER TABLE public.livestock_import_batch ADD CONSTRAINT livestock_import_batch_imported_by_fkey FOREIGN KEY (imported_by) REFERENCES public.sec_user(user_id);


-- public.livestock_notification_log foreign keys

ALTER TABLE public.livestock_notification_log ADD CONSTRAINT livestock_notification_log_breeding_id_fkey FOREIGN KEY (breeding_id) REFERENCES public.livestock_breeding(id);
ALTER TABLE public.livestock_notification_log ADD CONSTRAINT livestock_notification_log_livestock_id_fkey FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);


-- public.livestock_offspring foreign keys

ALTER TABLE public.livestock_offspring ADD CONSTRAINT livestock_offspring_birth_id_fkey FOREIGN KEY (birth_id) REFERENCES public.livestock_births(id) ON DELETE CASCADE;
ALTER TABLE public.livestock_offspring ADD CONSTRAINT livestock_offspring_child_livestock_id_fkey FOREIGN KEY (child_livestock_id) REFERENCES public.livestock(id) ON DELETE SET NULL;


-- public.livestock_sale_payments foreign keys

ALTER TABLE public.livestock_sale_payments ADD CONSTRAINT livestock_sale_payments_recorded_by_fkey FOREIGN KEY (recorded_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_sale_payments ADD CONSTRAINT livestock_sale_payments_sale_id_fkey FOREIGN KEY (sale_id) REFERENCES public.livestock_sales(id) ON DELETE CASCADE;


-- public.livestock_sales foreign keys

ALTER TABLE public.livestock_sales ADD CONSTRAINT fk_livestock_sales_approved_by FOREIGN KEY (approved_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_sales ADD CONSTRAINT fk_sales_buyer FOREIGN KEY (buyer_id) REFERENCES public.buyers(id) ON DELETE SET NULL;
ALTER TABLE public.livestock_sales ADD CONSTRAINT fkfwyppk7n6h3y7mjbuykihc106 FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_sales ADD CONSTRAINT livestock_sales_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);


-- public.livestock_sick foreign keys

ALTER TABLE public.livestock_sick ADD CONSTRAINT fktpr021wv8jh98yssag903o1v7 FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_sick ADD CONSTRAINT livestock_sick_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_sick ADD CONSTRAINT livestock_sick_veterinarian_id_fkey FOREIGN KEY (veterinarian_id) REFERENCES public.veterinarians(id);


-- public.livestock_sick_history foreign keys

ALTER TABLE public.livestock_sick_history ADD CONSTRAINT fkm5t5oyh6kkkp1hpy7bwso5eel FOREIGN KEY (sick_id) REFERENCES public.livestock_sick(id);


-- public.livestock_treatment_payments foreign keys

ALTER TABLE public.livestock_treatment_payments ADD CONSTRAINT fk_treatment_payment_recorded_by FOREIGN KEY (recorded_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_treatment_payments ADD CONSTRAINT fk_treatment_payment_treatment FOREIGN KEY (treatment_id) REFERENCES public.livestock_treatments(id);


-- public.livestock_treatments foreign keys

ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk3bs6hfgck0q2pa9iv1m57tm9r FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk9osc0l1cc472p5fmx8m5ouf3a FOREIGN KEY (sick_livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk_created_by_user FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk_treatment_created_by FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk_treatment_livestock FOREIGN KEY (livestock_id) REFERENCES public.livestock(id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk_treatment_medication FOREIGN KEY (medication_id) REFERENCES public.medications(id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk_treatment_updated_by FOREIGN KEY (updated_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fk_treatment_veterinarian FOREIGN KEY (veterinarian_id) REFERENCES public.veterinarians(id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT fktfxxnk1i1tmamea026jansg74 FOREIGN KEY (sick_livestock_id) REFERENCES public.livestock_sick(id);
ALTER TABLE public.livestock_treatments ADD CONSTRAINT livestock_treatments_medication_id_fkey FOREIGN KEY (medication_id) REFERENCES public.medications(id) ON DELETE SET NULL;
ALTER TABLE public.livestock_treatments ADD CONSTRAINT livestock_treatments_veterinarian_id_fkey FOREIGN KEY (veterinarian_id) REFERENCES public.veterinarians(id);


-- public.livestock_valuation_history foreign keys

ALTER TABLE public.livestock_valuation_history ADD CONSTRAINT livestock_valuation_history_livestock_id_fkey FOREIGN KEY (livestock_id) REFERENCES public.livestock(id) ON DELETE CASCADE;


-- public.livestock_weight_history foreign keys

ALTER TABLE public.livestock_weight_history ADD CONSTRAINT fk_weight_livestock FOREIGN KEY (livestock_id) REFERENCES public.livestock(id) ON DELETE CASCADE;


-- public.medications foreign keys

ALTER TABLE public.medications ADD CONSTRAINT medications_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);


-- public.representatives foreign keys

ALTER TABLE public.representatives ADD CONSTRAINT fks9vrmwc4ehux9nr87qw9p6c79 FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.representatives ADD CONSTRAINT location_fk FOREIGN KEY (location_id) REFERENCES public.a_location(id);


-- public.sec_user foreign keys

ALTER TABLE public.sec_user ADD CONSTRAINT fk_sec_user_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES public.beneficiaries(id);
ALTER TABLE public.sec_user ADD CONSTRAINT user_type_fk1 FOREIGN KEY (user_type_id) REFERENCES public.sec_user_type(user_type_id);


-- public.treatment_audit_log foreign keys

ALTER TABLE public.treatment_audit_log ADD CONSTRAINT fk_audit_treatment FOREIGN KEY (treatment_id) REFERENCES public.livestock_treatments(id);
ALTER TABLE public.treatment_audit_log ADD CONSTRAINT fk_audit_treatment_performed_by FOREIGN KEY (performed_by) REFERENCES public.sec_user(user_id);


-- public.user_modules foreign keys

ALTER TABLE public.user_modules ADD CONSTRAINT user_modules_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.user_modules ADD CONSTRAINT user_modules_module_id_fkey FOREIGN KEY (module_id) REFERENCES public.modules(module_id);
ALTER TABLE public.user_modules ADD CONSTRAINT user_modules_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sec_user(user_id);


-- public.user_roles foreign keys

ALTER TABLE public.user_roles ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);
ALTER TABLE public.user_roles ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.sec_user(user_id);


-- public.user_type_modules foreign keys

ALTER TABLE public.user_type_modules ADD CONSTRAINT user_type_modules_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.user_type_modules ADD CONSTRAINT user_type_modules_module_id_fkey FOREIGN KEY (module_id) REFERENCES public.modules(module_id);
ALTER TABLE public.user_type_modules ADD CONSTRAINT user_type_modules_user_type_id_fkey FOREIGN KEY (user_type_id) REFERENCES public.sec_user_type(user_type_id);


-- public.veterinarians foreign keys

ALTER TABLE public.veterinarians ADD CONSTRAINT veterinarians_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.sec_user(user_id);
ALTER TABLE public.veterinarians ADD CONSTRAINT veterinarians_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.a_location(id);





















--&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& 2.ALL VIEW &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&--

-- public.v_females_ready_to_breed source

CREATE OR REPLACE VIEW public.v_females_ready_to_breed
AS SELECT ls.id,
    ls.tag_number,
    lc.id AS category_id,
    lc.name AS category_name,
    lc.code AS category_code,
    lc.gestation_period_months,
    floor((CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received))::numeric / 30.4375)::integer AS age_months,
    ls.offspring_count,
    agg.last_breeding_date,
    agg.first_breeding_date,
    ( SELECT max(c.birth_date) AS max
           FROM livestock c
          WHERE c.mother_id = ls.id) AS last_birth_date,
    COALESCE(cur.is_pregnant, false) AS is_pregnant,
    cur.pregnancy_status,
    cur.conception_date,
    cur.expected_due_date,
    COALESCE(agg.total_breedings, 0::bigint) AS total_breedings,
    COALESCE(agg.successful_breedings, 0::bigint) AS successful_breedings,
    ls.date_received,
    ls.birth_date,
    ls.status,
    ls.gender,
    ls.current_value
   FROM livestock ls
     LEFT JOIN livestock_categories lc ON lc.id = ls.livestock_category_id
     LEFT JOIN LATERAL ( SELECT count(*) AS total_breedings,
            count(*) FILTER (WHERE b.status::text = ANY (ARRAY['CONFIRMED_PREGNANT'::character varying, 'COMPLETED'::character varying]::text[])) AS successful_breedings,
            min(COALESCE(b.first_breeding_date, b.breeding_date)) AS first_breeding_date,
            max(COALESCE(b.last_breeding_date, b.breeding_date)) AS last_breeding_date
           FROM livestock_breeding b
          WHERE b.livestock_id = ls.id AND b.is_deleted = false) agg ON true
     LEFT JOIN LATERAL ( SELECT b.is_pregnant,
            b.pregnancy_status,
            b.conception_date,
            b.expected_due_date
           FROM livestock_breeding b
          WHERE b.livestock_id = ls.id AND b.is_deleted = false
          ORDER BY b.conception_date DESC NULLS LAST, b.created_at DESC
         LIMIT 1) cur ON true
  WHERE ls.gender::text = 'FEMALE'::text AND (ls.status::text <> ALL (ARRAY['DEAD'::character varying, 'SOLD'::character varying]::text[])) AND ls.is_deleted = false AND COALESCE(cur.is_pregnant, false) = false AND (CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received)) >= 365;

-- Permissions

ALTER TABLE public.v_females_ready_to_breed OWNER TO postgres;
GRANT ALL ON TABLE public.v_females_ready_to_breed TO postgres;


-- public.v_livestock_health_status source

CREATE OR REPLACE VIEW public.v_livestock_health_status
AS WITH latest_sick AS (
         SELECT DISTINCT ON (livestock_sick.livestock_id) livestock_sick.livestock_id,
            livestock_sick.id AS sick_record_id,
            livestock_sick.status AS sick_status,
            livestock_sick.severity_level,
            livestock_sick.reported_date,
            livestock_sick.recovery_date
           FROM livestock_sick
          WHERE livestock_sick.is_deleted = false
          ORDER BY livestock_sick.livestock_id, livestock_sick.reported_date DESC, livestock_sick.created_at DESC
        )
 SELECT l.id AS livestock_id,
        CASE
            WHEN ls.sick_status::text = ANY (ARRAY['SICK'::character varying, 'CRITICAL'::character varying]::text[]) THEN 'SICK'::text
            WHEN ls.sick_status::text = 'RECOVERING'::text THEN 'UNDER_TREATMENT'::text
            ELSE 'HEALTHY'::text
        END AS health_status,
    ls.sick_record_id AS active_sick_record_id,
    ls.severity_level,
    ls.reported_date AS sick_reported_date
   FROM livestock l
     LEFT JOIN latest_sick ls ON ls.livestock_id = l.id
  WHERE l.is_deleted = false;

COMMENT ON VIEW public.v_livestock_health_status IS 'Computed, never stored — health status derived live from the latest livestock_sick record.';

-- Permissions

ALTER TABLE public.v_livestock_health_status OWNER TO postgres;
GRANT ALL ON TABLE public.v_livestock_health_status TO postgres;


-- public.v_livestock_with_age source

CREATE OR REPLACE VIEW public.v_livestock_with_age
AS SELECT l.id,
    l.acquisition_method,
    l.current_value,
    l.date_received,
    l.gender,
    l.offspring_count,
    l.photo,
    l.status,
    l.tag_number,
    l.beneficiary_id,
    l.created_by,
    l.livestock_category_id,
    l.location_id,
    l.mother_id,
    l.created_at,
    l.is_deleted,
    l.birth_date,
    l.source_location,
    l.is_draft,
    l.draft_birth_id,
    l.acquisition_source,
    l.updated_at,
    l.deleted_at,
    l.deleted_by,
    l.reproductive_status,
    l.health_status,
    l.father_id,
    l.breeding_ready_notified_at,
    l.last_lifecycle_stage,
    l.is_group,
    l.quantity,
    l.notes,
    br.conception_date,
    br.expected_due_date,
    br.first_breeding_date,
    br.last_breeding_date,
    ( SELECT max(c.birth_date) AS max
           FROM livestock c
          WHERE c.mother_id = l.id) AS last_birth_date,
    COALESCE(br.is_pregnant, false) AS is_pregnant,
    br.pregnancy_status,
    NULL::numeric AS sold_price,
    COALESCE(CURRENT_DATE - l.date_received, 0) AS age_in_days,
    COALESCE(date_part('month'::text, age(CURRENT_DATE::timestamp with time zone, l.date_received::timestamp with time zone))::integer, 0) AS age_in_months,
    lc.name AS category_name,
    lc.code AS category_code,
    lc.gestation_period_months,
        CASE
            WHEN l.date_received IS NULL THEN 'UNKNOWN'::text
            WHEN (CURRENT_DATE - l.date_received) <= 30 THEN 'NEWBORN'::text
            WHEN (CURRENT_DATE - l.date_received) <= 365 THEN 'YOUNG'::text
            WHEN l.gender::text = 'FEMALE'::text AND (CURRENT_DATE - l.date_received) > 365 AND l.status::text <> 'PREGNANT'::text THEN 'READY_TO_BREED'::text
            WHEN l.gender::text = 'MALE'::text AND (CURRENT_DATE - l.date_received) > 365 THEN 'BREEDING_MALE'::text
            WHEN l.status::text = 'PREGNANT'::text THEN 'PREGNANT'::text
            ELSE 'MATURE'::text
        END AS lifecycle_stage
   FROM livestock l
     LEFT JOIN livestock_categories lc ON l.livestock_category_id = lc.id
     LEFT JOIN LATERAL ( SELECT b.conception_date,
            b.expected_due_date,
            b.first_breeding_date,
            b.last_breeding_date,
            b.is_pregnant,
            b.pregnancy_status
           FROM livestock_breeding b
          WHERE b.livestock_id = l.id AND b.is_deleted = false
          ORDER BY b.conception_date DESC NULLS LAST, b.created_at DESC
         LIMIT 1) br ON true
  WHERE l.is_deleted = false;

-- Permissions

ALTER TABLE public.v_livestock_with_age OWNER TO postgres;
GRANT ALL ON TABLE public.v_livestock_with_age TO postgres;


-- public.v_males_ready_to_breed source

CREATE OR REPLACE VIEW public.v_males_ready_to_breed
AS SELECT ls.id,
    ls.tag_number,
    lc.id AS category_id,
    lc.name AS category_name,
    lc.code AS category_code,
    floor((CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received))::numeric / 30.4375)::integer AS age_months,
    COALESCE(agg.total_breedings, 0::bigint) AS total_breedings,
    COALESCE(agg.successful_breedings, 0::bigint) AS successful_breedings,
    ls.date_received,
    ls.birth_date,
    ls.status,
    ls.gender,
    ls.current_value,
    ls.acquisition_method,
    COALESCE(agg.natural_breedings, 0::bigint) AS natural_breedings,
    COALESCE(agg.natural_successful, 0::bigint) AS natural_successful
   FROM livestock ls
     LEFT JOIN livestock_categories lc ON lc.id = ls.livestock_category_id
     LEFT JOIN LATERAL ( SELECT count(*) AS total_breedings,
            count(*) FILTER (WHERE b.status::text = ANY (ARRAY['CONFIRMED_PREGNANT'::character varying, 'COMPLETED'::character varying]::text[])) AS successful_breedings,
            count(*) FILTER (WHERE b.breeding_method::text = 'NATURAL'::text) AS natural_breedings,
            count(*) FILTER (WHERE b.breeding_method::text = 'NATURAL'::text AND (b.status::text = ANY (ARRAY['CONFIRMED_PREGNANT'::character varying, 'COMPLETED'::character varying]::text[]))) AS natural_successful
           FROM livestock_breeding b
          WHERE COALESCE(b.male_livestock_id, b.male_id) = ls.id AND b.is_deleted = false) agg ON true
  WHERE ls.gender::text = 'MALE'::text AND (ls.status::text <> ALL (ARRAY['DEAD'::character varying, 'SOLD'::character varying]::text[])) AND ls.is_deleted = false AND (CURRENT_DATE - COALESCE(ls.birth_date, ls.date_received)) >= 365;

-- Permissions

ALTER TABLE public.v_males_ready_to_breed OWNER TO postgres;
GRANT ALL ON TABLE public.v_males_ready_to_breed TO postgres;


-- public.v_newborn_animals source

CREATE OR REPLACE VIEW public.v_newborn_animals
AS SELECT l.id,
    l.acquisition_method,
    l.current_value,
    l.date_received,
    l.gender,
    l.offspring_count,
    l.photo,
    l.status,
    l.tag_number,
    l.beneficiary_id,
    l.created_by,
    l.livestock_category_id,
    l.location_id,
    l.mother_id,
    l.created_at,
    l.is_deleted,
    l.birth_date,
    l.source_location,
    l.is_draft,
    l.draft_birth_id,
    l.acquisition_source,
    l.updated_at,
    l.deleted_at,
    l.deleted_by,
    l.reproductive_status,
    l.health_status,
    l.father_id,
    l.breeding_ready_notified_at,
    l.last_lifecycle_stage,
    l.is_group,
    l.quantity,
    l.notes,
    lc.name AS category_name,
    CURRENT_DATE - l.date_received AS age_days,
    m.tag_number AS mother_tag_number
   FROM livestock l
     JOIN livestock_categories lc ON l.livestock_category_id = lc.id
     LEFT JOIN livestock m ON l.mother_id = m.id
  WHERE l.is_deleted = false AND l.status::text = 'ACTIVE'::text AND l.date_received IS NOT NULL AND (CURRENT_DATE - l.date_received) <= 30
  ORDER BY l.date_received DESC;

-- Permissions

ALTER TABLE public.v_newborn_animals OWNER TO postgres;
GRANT ALL ON TABLE public.v_newborn_animals TO postgres;


-- public.v_user_permissions source

CREATE OR REPLACE VIEW public.v_user_permissions
AS SELECT u.user_id,
    m.module_id,
    m.module_name,
    m.module_code,
    m.module_url,
    m.parent_module_id,
    m.icon,
    m.display_order,
    COALESCE(um.can_view, utm.can_view, false) AS can_view,
    COALESCE(um.can_create, utm.can_create, false) AS can_create,
    COALESCE(um.can_edit, utm.can_edit, false) AS can_edit,
    COALESCE(um.can_delete, utm.can_delete, false) AS can_delete
   FROM sec_user u
     CROSS JOIN modules m
     LEFT JOIN user_type_modules utm ON utm.user_type_id = u.user_type_id AND utm.module_id = m.module_id
     LEFT JOIN user_modules um ON um.user_id = u.user_id AND um.module_id = m.module_id
  WHERE u.is_active = true AND COALESCE(m.is_active, true) = true AND (um.user_module_id IS NOT NULL OR utm.user_type_module_id IS NOT NULL);

-- Permissions

ALTER TABLE public.v_user_permissions OWNER TO postgres;
GRANT ALL ON TABLE public.v_user_permissions TO postgres;


-- public.v_young_animals source

CREATE OR REPLACE VIEW public.v_young_animals
AS SELECT l.id,
    l.acquisition_method,
    l.current_value,
    l.date_received,
    l.gender,
    l.offspring_count,
    l.photo,
    l.status,
    l.tag_number,
    l.beneficiary_id,
    l.created_by,
    l.livestock_category_id,
    l.location_id,
    l.mother_id,
    l.created_at,
    l.is_deleted,
    l.birth_date,
    l.source_location,
    l.is_draft,
    l.draft_birth_id,
    l.acquisition_source,
    l.updated_at,
    l.deleted_at,
    l.deleted_by,
    l.reproductive_status,
    l.health_status,
    l.father_id,
    l.breeding_ready_notified_at,
    l.last_lifecycle_stage,
    l.is_group,
    l.quantity,
    l.notes,
    lc.name AS category_name,
    CURRENT_DATE - l.date_received AS age_days,
    365 - (CURRENT_DATE - l.date_received) AS days_until_breeding_age
   FROM livestock l
     JOIN livestock_categories lc ON l.livestock_category_id = lc.id
  WHERE l.is_deleted = false AND l.status::text = 'ACTIVE'::text AND l.date_received IS NOT NULL AND (CURRENT_DATE - l.date_received) > 30 AND (CURRENT_DATE - l.date_received) < 365
  ORDER BY l.date_received DESC;

-- Permissions

ALTER TABLE public.v_young_animals OWNER TO postgres;
GRANT ALL ON TABLE public.v_young_animals TO postgres;
















--&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& 3.ALL FUNCTIONS &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&--

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


-- DROP FUNCTION public.get_lifecycle_stage_counts();

CREATE OR REPLACE FUNCTION public.get_lifecycle_stage_counts()
 RETURNS TABLE(stage character varying, count bigint)
 LANGUAGE plpgsql
AS $function$
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
$function$
;

-- Permissions

ALTER FUNCTION public.get_lifecycle_stage_counts() OWNER TO postgres;
GRANT ALL ON FUNCTION public.get_lifecycle_stage_counts() TO postgres;

-- DROP FUNCTION public.get_livestock_lifecycle_stage(date, varchar, varchar);

CREATE OR REPLACE FUNCTION public.get_livestock_lifecycle_stage(p_date_received date, p_gender character varying, p_status character varying)
 RETURNS character varying
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
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
$function$
;

-- Permissions

ALTER FUNCTION public.get_livestock_lifecycle_stage(date, varchar, varchar) OWNER TO postgres;
GRANT ALL ON FUNCTION public.get_livestock_lifecycle_stage(date, varchar, varchar) TO postgres;



















----- &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& 4. ALL INDEX &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&--
CREATE UNIQUE INDEX adm_location_pk ON public.a_location USING btree (id);

CREATE UNIQUE INDEX adm_location_uk1 ON public.a_location USING btree (code);

CREATE UNIQUE INDEX adm_location_uk2 ON public.a_location USING btree (name, parent_id);

CREATE UNIQUE INDEX audit_log_pkey ON public.audit_log USING btree (id);

CREATE INDEX idx_audit_log_changed_at ON public.audit_log USING btree (changed_at DESC);

CREATE INDEX idx_audit_log_entity ON public.audit_log USING btree (entity_type, entity_id);

CREATE UNIQUE INDEX livestock_categories_pkey ON public.livestock_categories USING btree (id);

CREATE UNIQUE INDEX ukk3sace64rlriq8wb7s7g1kwcr ON public.livestock_categories USING btree (code);

CREATE UNIQUE INDEX livestock_repro_archive_pkey ON public.livestock_reproductive_data_archive USING btree (id);

CREATE INDEX idx_other_expenses_category ON public.other_expenses USING btree (category);

CREATE INDEX idx_other_expenses_date ON public.other_expenses USING btree (expense_date);

CREATE INDEX idx_other_expenses_is_deleted ON public.other_expenses USING btree (is_deleted);

CREATE UNIQUE INDEX other_expenses_pkey ON public.other_expenses USING btree (id);

CREATE UNIQUE INDEX roles_pkey ON public.roles USING btree (id);

CREATE UNIQUE INDEX roles_role_name_key ON public.roles USING btree (role_name);

CREATE UNIQUE INDEX sec_user_type_pk ON public.sec_user_type USING btree (user_type_id);

CREATE INDEX idx_modules_code ON public.modules USING btree (module_code);

CREATE INDEX idx_modules_parent ON public.modules USING btree (parent_module_id);

CREATE UNIQUE INDEX modules_module_code_key ON public.modules USING btree (module_code);

CREATE UNIQUE INDEX modules_module_name_key ON public.modules USING btree (module_name);

CREATE UNIQUE INDEX modules_pkey ON public.modules USING btree (module_id);

CREATE UNIQUE INDEX role_module_permissions_pkey ON public.role_module_permissions USING btree (id);

CREATE UNIQUE INDEX role_module_permissions_role_id_module_id_key ON public.role_module_permissions USING btree (role_id, module_id);

CREATE UNIQUE INDEX aps_log_pkey ON public.aps_log USING btree (id);

CREATE INDEX idx_aps_log_action ON public.aps_log USING btree (action);

CREATE INDEX idx_aps_log_performed_at ON public.aps_log USING btree (performed_at DESC);

CREATE INDEX idx_aps_log_performed_by ON public.aps_log USING btree (performed_by);

CREATE INDEX idx_aps_log_table_record ON public.aps_log USING btree (table_name, record_id);

CREATE UNIQUE INDEX abaragizwa_amatungo_pk ON public.beneficiaries USING btree (id);

CREATE INDEX idx_abaragizwa_created_date ON public.beneficiaries USING btree (created_date DESC);

CREATE INDEX idx_abaragizwa_location ON public.beneficiaries USING btree (location_id);

CREATE INDEX idx_abaragizwa_uhagarariye ON public.beneficiaries USING btree (representative_id);

CREATE INDEX idx_beneficiaries_status ON public.beneficiaries USING btree (status);

CREATE UNIQUE INDEX buyers_buyer_national_id_key ON public.buyers USING btree (buyer_national_id);

CREATE UNIQUE INDEX buyers_pkey ON public.buyers USING btree (id);

CREATE INDEX idx_buyer_national_id ON public.buyers USING btree (buyer_national_id);

CREATE INDEX idx_buyer_phone ON public.buyers USING btree (buyer_phone);

CREATE UNIQUE INDEX uk_buyer_phone ON public.buyers USING btree (buyer_phone);

CREATE INDEX idx_livestock_acquisition ON public.livestock USING btree (acquisition_method, date_received) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_breeding_females ON public.livestock USING btree (gender, date_received, status) WHERE (((gender)::text = 'FEMALE'::text) AND (is_deleted = false));

CREATE INDEX idx_livestock_breeding_males ON public.livestock USING btree (gender, date_received, status) WHERE (((gender)::text = 'MALE'::text) AND (is_deleted = false));

CREATE INDEX idx_livestock_category ON public.livestock USING btree (livestock_category_id);

CREATE INDEX idx_livestock_deleted ON public.livestock USING btree (is_deleted) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_deleted_at ON public.livestock USING btree (deleted_at) WHERE (deleted_at IS NOT NULL);

CREATE INDEX idx_livestock_deleted_rows ON public.livestock USING btree (id) WHERE (is_deleted = true);

CREATE INDEX idx_livestock_draft_birth_id ON public.livestock USING btree (draft_birth_id) WHERE (is_draft = true);

CREATE INDEX idx_livestock_gender_status ON public.livestock USING btree (gender, status) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_health_status ON public.livestock USING btree (health_status) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_is_draft ON public.livestock USING btree (is_draft) WHERE (is_draft = true);

CREATE INDEX idx_livestock_is_group ON public.livestock USING btree (is_group) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_lifecycle ON public.livestock USING btree (livestock_category_id, date_received, gender, status, is_deleted) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_mother ON public.livestock USING btree (mother_id) WHERE ((mother_id IS NOT NULL) AND (is_deleted = false));

CREATE INDEX idx_livestock_owner ON public.livestock USING btree (beneficiary_id);

CREATE INDEX idx_livestock_reproductive_status ON public.livestock USING btree (reproductive_status) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_source_location ON public.livestock USING btree (source_location) WHERE (source_location IS NOT NULL);

CREATE INDEX idx_livestock_status ON public.livestock USING btree (status);

CREATE UNIQUE INDEX livestock_pkey ON public.livestock USING btree (id);

CREATE UNIQUE INDEX ukfcgt8b12n9pcm1dr2nuy3gx61 ON public.livestock USING btree (tag_number);

CREATE UNIQUE INDEX itungo_ryimye_pk ON public.livestock_abortions USING btree (id);

CREATE INDEX idx_birth_date ON public.livestock_births USING btree (birth_date);

CREATE INDEX idx_births_breeding ON public.livestock_births USING btree (breeding_id);

CREATE INDEX idx_livestock_births_birth_date ON public.livestock_births USING btree (birth_date DESC);

CREATE INDEX idx_livestock_births_is_deleted ON public.livestock_births USING btree (is_deleted) WHERE (is_deleted = false);

CREATE INDEX idx_livestock_births_livestock_id ON public.livestock_births USING btree (livestock_id);

CREATE UNIQUE INDEX livestock_births_pkey ON public.livestock_births USING btree (id);

CREATE INDEX idx_breeding_active_preg ON public.livestock_breeding USING btree (livestock_id, expected_due_date) WHERE ((is_pregnant = true) AND (is_deleted = false));

CREATE INDEX idx_breeding_deleted_at ON public.livestock_breeding USING btree (deleted_at);

CREATE INDEX idx_breeding_due_date ON public.livestock_breeding USING btree (expected_due_date) WHERE (is_deleted = false);

CREATE INDEX idx_breeding_livestock_id ON public.livestock_breeding USING btree (livestock_id);

CREATE INDEX idx_breeding_pregnancy ON public.livestock_breeding USING btree (pregnancy_status) WHERE (is_deleted = false);

CREATE INDEX idx_breeding_status ON public.livestock_breeding USING btree (status);

CREATE UNIQUE INDEX livestock_breeding_pkey ON public.livestock_breeding USING btree (id);

CREATE INDEX idx_death_date ON public.livestock_deaths USING btree (death_date);

CREATE UNIQUE INDEX livestock_deaths_pkey ON public.livestock_deaths USING btree (id);

CREATE INDEX idx_import_batch_date ON public.livestock_import_batch USING btree (imported_at DESC);

CREATE INDEX idx_import_batch_user ON public.livestock_import_batch USING btree (imported_by);

CREATE UNIQUE INDEX livestock_import_batch_pkey ON public.livestock_import_batch USING btree (id);

CREATE INDEX idx_notification_log_breeding ON public.livestock_notification_log USING btree (breeding_id);

CREATE INDEX idx_notification_log_livestock ON public.livestock_notification_log USING btree (livestock_id);

CREATE UNIQUE INDEX livestock_notification_log_pkey ON public.livestock_notification_log USING btree (id);

CREATE UNIQUE INDEX uq_notification_dedupe ON public.livestock_notification_log USING btree (notification_type, dedupe_key);

CREATE INDEX idx_offspring_birth_id ON public.livestock_offspring USING btree (birth_id);

CREATE INDEX idx_offspring_child_id ON public.livestock_offspring USING btree (child_livestock_id);

CREATE UNIQUE INDEX livestock_offspring_pkey ON public.livestock_offspring USING btree (id);

CREATE UNIQUE INDEX uq_child ON public.livestock_offspring USING btree (child_livestock_id);

CREATE INDEX idx_livestock_sale_payments_sale_id ON public.livestock_sale_payments USING btree (sale_id);

CREATE UNIQUE INDEX livestock_sale_payments_pkey ON public.livestock_sale_payments USING btree (id);

CREATE INDEX idx_livestock_sales_acquisition_cost ON public.livestock_sales USING btree (acquisition_cost);

CREATE INDEX idx_livestock_sales_amount_paid ON public.livestock_sales USING btree (amount_paid);

CREATE INDEX idx_livestock_sales_approved_by ON public.livestock_sales USING btree (approved_by);

CREATE INDEX idx_livestock_sales_payment_status ON public.livestock_sales USING btree (payment_status);

CREATE INDEX idx_livestock_sales_sale_date ON public.livestock_sales USING btree (sale_date);

CREATE INDEX idx_livestock_sales_sale_reference ON public.livestock_sales USING btree (sale_reference);

CREATE INDEX idx_livestock_sales_sale_status ON public.livestock_sales USING btree (sale_status);

CREATE INDEX idx_sale_date ON public.livestock_sales USING btree (sale_date);

CREATE INDEX idx_sales_buyer ON public.livestock_sales USING btree (buyer_id);

CREATE UNIQUE INDEX livestock_sales_pkey ON public.livestock_sales USING btree (id);

CREATE UNIQUE INDEX livestock_sales_sale_reference_key ON public.livestock_sales USING btree (sale_reference);

CREATE INDEX idx_sick_livestock ON public.livestock_sick USING btree (livestock_id);

CREATE INDEX idx_sick_reported ON public.livestock_sick USING btree (reported_date);

CREATE INDEX idx_sick_status ON public.livestock_sick USING btree (status) WHERE (is_deleted = false);

CREATE INDEX idx_sick_vet_reported ON public.livestock_sick USING btree (veterinarian_id, reported_date);

CREATE UNIQUE INDEX livestock_sick_pkey ON public.livestock_sick USING btree (id);

CREATE INDEX idx_sick_history_changed_at ON public.livestock_sick_history USING btree (changed_at);

CREATE INDEX idx_sick_history_sick_id ON public.livestock_sick_history USING btree (sick_id);

CREATE INDEX idx_sick_history_status ON public.livestock_sick_history USING btree (status);

CREATE UNIQUE INDEX livestock_sick_history_pkey ON public.livestock_sick_history USING btree (id);

CREATE INDEX idx_treatment_payments_payment_date ON public.livestock_treatment_payments USING btree (payment_date);

CREATE INDEX idx_treatment_payments_treatment_id ON public.livestock_treatment_payments USING btree (treatment_id);

CREATE UNIQUE INDEX livestock_treatment_payments_pkey ON public.livestock_treatment_payments USING btree (id);

CREATE INDEX idx_lt_medication_id ON public.livestock_treatments USING btree (medication_id);

CREATE INDEX idx_treatment_date ON public.livestock_treatments USING btree (treatment_date);

CREATE INDEX idx_treatment_livestock_date ON public.livestock_treatments USING btree (livestock_id, treatment_date);

CREATE INDEX idx_treatment_vet_date ON public.livestock_treatments USING btree (veterinarian_id, treatment_date);

CREATE INDEX idx_treatments_is_deleted ON public.livestock_treatments USING btree (is_deleted);

CREATE INDEX idx_treatments_livestock ON public.livestock_treatments USING btree (livestock_id);

CREATE INDEX idx_treatments_livestock_id ON public.livestock_treatments USING btree (livestock_id);

CREATE INDEX idx_treatments_medication_id ON public.livestock_treatments USING btree (medication_id);

CREATE INDEX idx_treatments_sick ON public.livestock_treatments USING btree (sick_livestock_id);

CREATE INDEX idx_treatments_treatment_date ON public.livestock_treatments USING btree (treatment_date);

CREATE INDEX idx_treatments_treatment_reference ON public.livestock_treatments USING btree (treatment_reference);

CREATE INDEX idx_treatments_treatment_status ON public.livestock_treatments USING btree (treatment_status);

CREATE INDEX idx_treatments_veterinarian_id ON public.livestock_treatments USING btree (veterinarian_id);

CREATE UNIQUE INDEX livestock_treatments_pkey ON public.livestock_treatments USING btree (id);

CREATE UNIQUE INDEX livestock_treatments_treatment_reference_key ON public.livestock_treatments USING btree (treatment_reference);

CREATE INDEX idx_valuation_date ON public.livestock_valuation_history USING btree (livestock_id, valuation_date DESC);

CREATE INDEX idx_valuation_livestock_id ON public.livestock_valuation_history USING btree (livestock_id);

CREATE UNIQUE INDEX livestock_valuation_history_pkey ON public.livestock_valuation_history USING btree (id);

CREATE INDEX idx_weight_livestock_id ON public.livestock_weight_history USING btree (livestock_id);

CREATE INDEX idx_weight_weighed_on ON public.livestock_weight_history USING btree (weighed_on);

CREATE UNIQUE INDEX livestock_weight_history_pkey ON public.livestock_weight_history USING btree (id);

CREATE INDEX idx_medications_category ON public.medications USING btree (category);

CREATE INDEX idx_medications_is_active ON public.medications USING btree (is_active);

CREATE INDEX idx_medications_name ON public.medications USING btree (name);

CREATE UNIQUE INDEX medications_pkey ON public.medications USING btree (id);

CREATE UNIQUE INDEX uhagarariye_aborora_pk ON public.representatives USING btree (id);

CREATE UNIQUE INDEX sec_user_pk ON public.sec_user USING btree (user_id);

CREATE INDEX idx_treatment_audit_performed_at ON public.treatment_audit_log USING btree (performed_at);

CREATE INDEX idx_treatment_audit_treatment_id ON public.treatment_audit_log USING btree (treatment_id);

CREATE UNIQUE INDEX treatment_audit_log_pkey ON public.treatment_audit_log USING btree (id);

CREATE INDEX idx_user_modules_module ON public.user_modules USING btree (module_id);

CREATE INDEX idx_user_modules_user ON public.user_modules USING btree (user_id);

CREATE UNIQUE INDEX user_modules_pkey ON public.user_modules USING btree (user_module_id);

CREATE UNIQUE INDEX user_modules_user_id_module_id_key ON public.user_modules USING btree (user_id, module_id);

CREATE UNIQUE INDEX user_roles_pkey ON public.user_roles USING btree (id);

CREATE UNIQUE INDEX user_roles_user_id_role_id_key ON public.user_roles USING btree (user_id, role_id);

CREATE INDEX idx_user_type_modules_module ON public.user_type_modules USING btree (module_id);

CREATE INDEX idx_user_type_modules_user_type ON public.user_type_modules USING btree (user_type_id);

CREATE UNIQUE INDEX user_type_modules_pkey ON public.user_type_modules USING btree (user_type_module_id);

CREATE UNIQUE INDEX user_type_modules_user_type_id_module_id_key ON public.user_type_modules USING btree (user_type_id, module_id);

CREATE INDEX idx_veterinarians_active ON public.veterinarians USING btree (is_active);

CREATE INDEX idx_veterinarians_nid ON public.veterinarians USING btree (national_id) WHERE (national_id IS NOT NULL);

CREATE INDEX idx_veterinarians_search ON public.veterinarians USING btree (first_name, last_name, license_number);

CREATE UNIQUE INDEX veterinarians_pkey ON public.veterinarians USING btree (id);


















--CONSTRAINT
-- ═══════════════════════════════════════════════════════════════════════
-- Foreign Key: beneficiaries.representative_id → representatives.id
-- ON DELETE RESTRICT — Postgres refuses to delete a representative
-- while any beneficiary still references it.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE public.beneficiaries
    DROP CONSTRAINT IF EXISTS fk_beneficiaries_representative;

ALTER TABLE public.beneficiaries
    ADD CONSTRAINT fk_beneficiaries_representative
        FOREIGN KEY (representative_id)
        REFERENCES public.representatives(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;
