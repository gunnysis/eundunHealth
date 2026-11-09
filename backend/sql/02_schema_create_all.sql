-- ============================================================================
-- File: backend/sql/02_schema_create_all.sql
-- Purpose: Hard Reset Step 2 — Clean-slate normalized DDL for eundunHealth
--          (PostgreSQL 18 native: DEFAULT uuidv7(), strict FOREIGN KEY
--          ON DELETE CASCADE, domain CHECK constraints, zero redundant indexes).
-- Target:  Azure Database for PostgreSQL 18 (healthapp) & Docker PostgreSQL 18
-- ============================================================================

SET lock_timeout = '15s';
SET statement_timeout = '60s';
SET client_min_messages = warning;

-- ----------------------------------------------------------------------------
-- 1. user_profiles (Root User Profile Entity)
-- ----------------------------------------------------------------------------
CREATE TABLE public.user_profiles (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    height_cm DOUBLE PRECISION NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    gender VARCHAR(20) NOT NULL DEFAULT 'unspecified',
    body_fat_pct DOUBLE PRECISION NULL,
    muscle_mass_kg DOUBLE PRECISION NULL,
    rest_days INTEGER[] NOT NULL DEFAULT '{7}'::integer[],
    default_workout_plan_mode VARCHAR(4) NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_profiles PRIMARY KEY (id),
    CONSTRAINT uq_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_user_profiles_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_user_profiles_height_cm_range CHECK (height_cm >= 50.0 AND height_cm <= 300.0),
    CONSTRAINT ck_user_profiles_weight_kg_range CHECK (weight_kg >= 10.0 AND weight_kg <= 500.0),
    CONSTRAINT ck_user_profiles_gender CHECK (gender IN ('male', 'female', 'unspecified')),
    CONSTRAINT ck_user_profiles_body_fat_pct_range CHECK (body_fat_pct IS NULL OR (body_fat_pct >= 1.0 AND body_fat_pct <= 70.0)),
    CONSTRAINT ck_user_profiles_muscle_mass_kg_range CHECK (muscle_mass_kg IS NULL OR (muscle_mass_kg >= 1.0 AND muscle_mass_kg <= 200.0)),
    CONSTRAINT ck_user_profiles_default_workout_plan_mode CHECK (
        default_workout_plan_mode IS NULL OR default_workout_plan_mode IN ('gym', 'home')
    ),
    CONSTRAINT ck_user_profiles_rest_days_valid CHECK (
        cardinality(rest_days) BETWEEN 1 AND 6
        AND array_position(rest_days, NULL) IS NULL
        AND rest_days <@ ARRAY[1, 2, 3, 4, 5, 6, 7]
    )
);

-- ----------------------------------------------------------------------------
-- 2. user_profile_history (Time-Series Body Metrics History)
--    FK: user_id -> user_profiles(user_id) ON DELETE CASCADE
-- ----------------------------------------------------------------------------
CREATE TABLE public.user_profile_history (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    height_cm DOUBLE PRECISION NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    body_fat_pct DOUBLE PRECISION NULL,
    muscle_mass_kg DOUBLE PRECISION NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_profile_history PRIMARY KEY (id),
    CONSTRAINT fk_user_profile_history_user_id_user_profiles
        FOREIGN KEY (user_id) REFERENCES public.user_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profile_history_height_cm_range CHECK (height_cm >= 50.0 AND height_cm <= 300.0),
    CONSTRAINT ck_user_profile_history_weight_kg_range CHECK (weight_kg >= 10.0 AND weight_kg <= 500.0),
    CONSTRAINT ck_user_profile_history_body_fat_pct_range CHECK (body_fat_pct IS NULL OR (body_fat_pct >= 1.0 AND body_fat_pct <= 70.0)),
    CONSTRAINT ck_user_profile_history_muscle_mass_kg_range CHECK (muscle_mass_kg IS NULL OR (muscle_mass_kg >= 1.0 AND muscle_mass_kg <= 200.0))
);

CREATE INDEX ix_history_user_recorded
    ON public.user_profile_history (user_id, recorded_at);

-- ----------------------------------------------------------------------------
-- 3. weekly_plans (Weekly Workout Routine)
--    Unique index uq_weekly_plans_user_week covers leading (user_id) queries.
-- ----------------------------------------------------------------------------
CREATE TABLE public.weekly_plans (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    week_start DATE NOT NULL,
    plan_mode VARCHAR(4) NOT NULL DEFAULT 'gym',
    day_plans TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_weekly_plans PRIMARY KEY (id),
    CONSTRAINT uq_weekly_plans_user_week UNIQUE (user_id, week_start),
    CONSTRAINT ck_weekly_plans_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_weekly_plans_plan_mode CHECK (plan_mode IN ('gym', 'home')),
    CONSTRAINT ck_weekly_plans_day_plans_nonempty CHECK (length(trim(day_plans)) >= 2)
);

-- ----------------------------------------------------------------------------
-- 4. goals (Body Weight & Body Fat Percentage Targets)
-- ----------------------------------------------------------------------------
CREATE TABLE public.goals (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    goal_type VARCHAR(20) NOT NULL,
    target_value DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_goals PRIMARY KEY (id),
    CONSTRAINT uq_goals_user_type UNIQUE (user_id, goal_type),
    CONSTRAINT ck_goals_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_goals_goal_type CHECK (goal_type IN ('weight', 'body_fat')),
    CONSTRAINT ck_goals_target_value_range CHECK (
        (goal_type = 'weight' AND target_value > 0.0 AND target_value <= 500.0)
        OR (goal_type = 'body_fat' AND target_value > 0.0 AND target_value <= 100.0)
    )
);

-- ----------------------------------------------------------------------------
-- 5. badges (Achieved Milestone Badges — 9 Canonical Keys)
-- ----------------------------------------------------------------------------
CREATE TABLE public.badges (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    badge_key VARCHAR(64) NOT NULL,
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_badges PRIMARY KEY (id),
    CONSTRAINT uq_badges_user_badge UNIQUE (user_id, badge_key),
    CONSTRAINT ck_badges_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_badges_badge_key CHECK (
        badge_key IN (
            'week_1_complete',
            'week_2_complete',
            'streak_3weeks',
            'first_workout',
            'workouts_10',
            'workouts_50',
            'streak_8weeks',
            'goal_weight_achieved',
            'goal_body_fat_achieved'
        )
    )
);

-- ----------------------------------------------------------------------------
-- 6. meal_plans (AI Weekly Meal Plan Header)
-- ----------------------------------------------------------------------------
CREATE TABLE public.meal_plans (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    week_start_date DATE NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_meal_plans PRIMARY KEY (id),
    CONSTRAINT uq_meal_plans_user_week UNIQUE (user_id, week_start_date),
    CONSTRAINT ck_meal_plans_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_meal_plans_summary_length CHECK (
        length(trim(summary)) >= 1 AND length(summary) <= 1000
    )
);

-- ----------------------------------------------------------------------------
-- 7. meal_plan_items (Normalized Daily Meal Entries per Meal Plan)
--    FK: plan_id -> meal_plans(id) ON DELETE CASCADE
-- ----------------------------------------------------------------------------
CREATE TABLE public.meal_plan_items (
    id UUID NOT NULL DEFAULT uuidv7(),
    plan_id UUID NOT NULL,
    day VARCHAR(20) NOT NULL,
    is_rest_day BOOLEAN NOT NULL DEFAULT false,
    meal_type VARCHAR(50) NOT NULL,
    menu_name VARCHAR(200) NOT NULL,
    calories INTEGER NOT NULL,
    protein_g INTEGER NOT NULL,
    carbs_g INTEGER NOT NULL,
    fat_g INTEGER NOT NULL,
    CONSTRAINT pk_meal_plan_items PRIMARY KEY (id),
    CONSTRAINT fk_meal_plan_items_plan_id_meal_plans
        FOREIGN KEY (plan_id) REFERENCES public.meal_plans(id) ON DELETE CASCADE,
    CONSTRAINT uq_meal_plan_items_plan_day_type UNIQUE (plan_id, day, meal_type),
    CONSTRAINT ck_meal_plan_items_day_nonempty CHECK (
        length(trim(day)) >= 1 AND length(day) <= 20
    ),
    CONSTRAINT ck_meal_plan_items_meal_type_nonempty CHECK (
        length(trim(meal_type)) >= 1 AND length(meal_type) <= 50
    ),
    CONSTRAINT ck_meal_plan_items_menu_name_nonempty CHECK (
        length(trim(menu_name)) >= 1 AND length(menu_name) <= 200
    ),
    CONSTRAINT ck_meal_plan_items_calories_range CHECK (calories >= 0 AND calories <= 5000),
    CONSTRAINT ck_meal_plan_items_protein_g_range CHECK (protein_g >= 0 AND protein_g <= 1000),
    CONSTRAINT ck_meal_plan_items_carbs_g_range CHECK (carbs_g >= 0 AND carbs_g <= 1000),
    CONSTRAINT ck_meal_plan_items_fat_g_range CHECK (fat_g >= 0 AND fat_g <= 1000)
);

-- ----------------------------------------------------------------------------
-- 8. smile_training_sessions (On-Device Smile Training v2 Session Records)
-- ----------------------------------------------------------------------------
CREATE TABLE public.smile_training_sessions (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    duration_sec INTEGER NOT NULL,
    completed_reps INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_smile_training_sessions PRIMARY KEY (id),
    CONSTRAINT ck_smile_training_sessions_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_smile_training_sessions_duration_sec_range CHECK (duration_sec >= 0 AND duration_sec <= 1800),
    CONSTRAINT ck_smile_training_sessions_completed_reps_range CHECK (completed_reps >= 1 AND completed_reps <= 5)
);

CREATE INDEX ix_smile_training_sessions_user_occurred
    ON public.smile_training_sessions (user_id, occurred_at DESC);

-- ----------------------------------------------------------------------------
-- 9. biometric_daily_summaries (Wear OS 6 Continuous All-State Daily Summary)
-- ----------------------------------------------------------------------------
CREATE TABLE public.biometric_daily_summaries (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    record_date DATE NOT NULL,
    zone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    total_steps INTEGER NOT NULL DEFAULT 0,
    active_calories_kcal DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    resting_bpm INTEGER NULL,
    avg_bpm INTEGER NULL,
    min_bpm INTEGER NULL,
    max_bpm INTEGER NULL,
    hrv_rmssd_ms DOUBLE PRECISION NULL,
    total_monitored_minutes INTEGER NOT NULL DEFAULT 0,
    relaxed_zone_minutes INTEGER NOT NULL DEFAULT 0,
    normal_zone_minutes INTEGER NOT NULL DEFAULT 0,
    elevated_tension_minutes INTEGER NOT NULL DEFAULT 0,
    high_load_minutes INTEGER NOT NULL DEFAULT 0,
    tension_episode_count INTEGER NOT NULL DEFAULT 0,
    tension_minutes INTEGER NOT NULL DEFAULT 0,
    peak_tension_bpm INTEGER NULL,
    rapid_delta_spike_count INTEGER NOT NULL DEFAULT 0,
    max_delta_bpm_per_min INTEGER NULL,
    activity_overload_count INTEGER NOT NULL DEFAULT 0,
    avg_recovery_drop_bpm INTEGER NULL,
    sleep_awakening_count INTEGER NOT NULL DEFAULT 0,
    recovery_score INTEGER NULL,
    hourly_bpm_buckets_json TEXT NOT NULL DEFAULT '[]',
    source_devices_json TEXT NOT NULL DEFAULT '[]',
    client_captured_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_biometric_daily_summaries PRIMARY KEY (id),
    CONSTRAINT uq_biometric_daily_summaries_user_date UNIQUE (user_id, record_date),
    CONSTRAINT ck_biometric_daily_summaries_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_biometric_daily_summaries_zone_id_nonempty CHECK (length(trim(zone_id)) >= 1),
    CONSTRAINT ck_biometric_daily_summaries_total_steps_range CHECK (total_steps >= 0 AND total_steps <= 200000),
    CONSTRAINT ck_biometric_daily_summaries_active_calories_range CHECK (active_calories_kcal >= 0.0 AND active_calories_kcal <= 20000.0),
    CONSTRAINT ck_biometric_daily_summaries_resting_bpm_range CHECK (resting_bpm IS NULL OR (resting_bpm >= 30 AND resting_bpm <= 220)),
    CONSTRAINT ck_biometric_daily_summaries_avg_bpm_range CHECK (avg_bpm IS NULL OR (avg_bpm >= 30 AND avg_bpm <= 240)),
    CONSTRAINT ck_biometric_daily_summaries_min_bpm_range CHECK (min_bpm IS NULL OR (min_bpm >= 25 AND min_bpm <= 240)),
    CONSTRAINT ck_biometric_daily_summaries_max_bpm_range CHECK (max_bpm IS NULL OR (max_bpm >= 30 AND max_bpm <= 260)),
    CONSTRAINT ck_biometric_daily_summaries_hrv_rmssd_range CHECK (hrv_rmssd_ms IS NULL OR (hrv_rmssd_ms >= 0.0 AND hrv_rmssd_ms <= 500.0)),
    CONSTRAINT ck_biometric_daily_summaries_monitored_min_range CHECK (total_monitored_minutes >= 0 AND total_monitored_minutes <= 1440),
    CONSTRAINT ck_biometric_daily_summaries_relaxed_min_range CHECK (relaxed_zone_minutes >= 0 AND relaxed_zone_minutes <= 1440),
    CONSTRAINT ck_biometric_daily_summaries_normal_min_range CHECK (normal_zone_minutes >= 0 AND normal_zone_minutes <= 1440),
    CONSTRAINT ck_biometric_daily_summaries_elevated_min_range CHECK (elevated_tension_minutes >= 0 AND elevated_tension_minutes <= 1440),
    CONSTRAINT ck_biometric_daily_summaries_high_load_min_range CHECK (high_load_minutes >= 0 AND high_load_minutes <= 1440),
    CONSTRAINT ck_biometric_daily_summaries_tension_count_range CHECK (tension_episode_count >= 0 AND tension_episode_count <= 500),
    CONSTRAINT ck_biometric_daily_summaries_tension_min_range CHECK (tension_minutes >= 0 AND tension_minutes <= 1440),
    CONSTRAINT ck_biometric_daily_summaries_peak_tension_bpm_range CHECK (peak_tension_bpm IS NULL OR (peak_tension_bpm >= 40 AND peak_tension_bpm <= 260)),
    CONSTRAINT ck_biometric_daily_summaries_rapid_spike_count_range CHECK (rapid_delta_spike_count >= 0 AND rapid_delta_spike_count <= 500),
    CONSTRAINT ck_biometric_daily_summaries_max_delta_bpm_range CHECK (max_delta_bpm_per_min IS NULL OR (max_delta_bpm_per_min >= 0 AND max_delta_bpm_per_min <= 180)),
    CONSTRAINT ck_biometric_daily_summaries_overload_count_range CHECK (activity_overload_count >= 0 AND activity_overload_count <= 500),
    CONSTRAINT ck_biometric_daily_summaries_recovery_drop_range CHECK (avg_recovery_drop_bpm IS NULL OR (avg_recovery_drop_bpm >= -100 AND avg_recovery_drop_bpm <= 180)),
    CONSTRAINT ck_biometric_daily_summaries_sleep_awakening_range CHECK (sleep_awakening_count >= 0 AND sleep_awakening_count <= 500),
    CONSTRAINT ck_biometric_daily_summaries_recovery_score_range CHECK (recovery_score IS NULL OR (recovery_score >= 0 AND recovery_score <= 100)),
    CONSTRAINT ck_biometric_daily_summaries_hourly_json_nonempty CHECK (length(trim(hourly_bpm_buckets_json)) >= 2),
    CONSTRAINT ck_biometric_daily_summaries_devices_json_nonempty CHECK (length(trim(source_devices_json)) >= 2)
);

CREATE INDEX ix_biometric_daily_summaries_user_date
    ON public.biometric_daily_summaries (user_id, record_date DESC);

-- ----------------------------------------------------------------------------
-- 10. data_reset_audits (Idempotent Hard-Reset & Cutover Audit Ledger)
-- ----------------------------------------------------------------------------
CREATE TABLE public.data_reset_audits (
    reset_id VARCHAR(128) NOT NULL,
    image_sha VARCHAR(64) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    row_counts_json TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_data_reset_audits PRIMARY KEY (reset_id),
    CONSTRAINT ck_data_reset_audits_reset_id_nonempty CHECK (length(trim(reset_id)) >= 1),
    CONSTRAINT ck_data_reset_audits_image_sha_nonempty CHECK (length(trim(image_sha)) >= 1),
    CONSTRAINT ck_data_reset_audits_execution_id_nonempty CHECK (length(trim(execution_id)) >= 1),
    CONSTRAINT ck_data_reset_audits_row_counts_json_nonempty CHECK (length(trim(row_counts_json)) >= 2)
);
