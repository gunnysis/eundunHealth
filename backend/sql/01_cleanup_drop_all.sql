-- ============================================================================
-- File: backend/sql/01_cleanup_drop_all.sql
-- Purpose: Hard Reset Step 1 — Complete DROP CASCADE of all user objects in
--          schema 'public' while preserving Azure PostgreSQL Flexible Server
--          ('azure_pg_admin') schema ownership and managed extensions.
-- Target:  Azure Database for PostgreSQL 18 (healthapp) & Docker PostgreSQL 18
-- ============================================================================

SET lock_timeout = '15s';
SET statement_timeout = '60s';
SET client_min_messages = warning;

-- 1. Explicit DROP of known views, child tables, parent tables, and legacy objects
DROP VIEW IF EXISTS public.v_user_weekly_summary CASCADE;
DROP VIEW IF EXISTS public.v_user_latest_metrics CASCADE;

DROP TABLE IF EXISTS public.meal_plan_items CASCADE;
DROP TABLE IF EXISTS public.meal_plans CASCADE;
DROP TABLE IF EXISTS public.smile_training_sessions CASCADE;
DROP TABLE IF EXISTS public.badges CASCADE;
DROP TABLE IF EXISTS public.goals CASCADE;
DROP TABLE IF EXISTS public.weekly_plans CASCADE;
DROP TABLE IF EXISTS public.user_profile_history CASCADE;
DROP TABLE IF EXISTS public.user_profiles CASCADE;
DROP TABLE IF EXISTS public.data_reset_audits CASCADE;
DROP TABLE IF EXISTS public.alembic_version CASCADE;

DROP TYPE IF EXISTS public.gender_enum CASCADE;
DROP TYPE IF EXISTS public.workout_plan_mode_enum CASCADE;
DROP TYPE IF EXISTS public.goal_type_enum CASCADE;
DROP TYPE IF EXISTS public.meal_type_enum CASCADE;

-- 2. Dynamic catalog sweep for any remaining non-extension objects in 'public'
DO $$
DECLARE
    obj RECORD;
    remaining_count INTEGER;
BEGIN
    -- 2-A. Drop remaining materialized views & views in 'public' (excluding extension objects)
    FOR obj IN
        SELECT c.relname, c.relkind
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('m', 'v')
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_class'::regclass
                AND d.objid = c.oid
                AND d.deptype = 'e'
          )
    LOOP
        IF obj.relkind = 'm' THEN
            EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS public.%I CASCADE', obj.relname);
        ELSE
            EXECUTE format('DROP VIEW IF EXISTS public.%I CASCADE', obj.relname);
        END IF;
    END LOOP;

    -- 2-B. Drop remaining tables & partitioned tables in 'public' (excluding extension objects)
    FOR obj IN
        SELECT c.relname
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_class'::regclass
                AND d.objid = c.oid
                AND d.deptype = 'e'
          )
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', obj.relname);
    END LOOP;

    -- 2-C. Drop remaining standalone sequences in 'public' (excluding extension objects)
    FOR obj IN
        SELECT c.relname
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'S'
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_class'::regclass
                AND d.objid = c.oid
                AND d.deptype = 'e'
          )
    LOOP
        EXECUTE format('DROP SEQUENCE IF EXISTS public.%I CASCADE', obj.relname);
    END LOOP;

    -- 2-D. Drop remaining custom types (enum, domain, range) in 'public'
    FOR obj IN
        SELECT t.typname
        FROM pg_catalog.pg_type t
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'public'
          AND t.typtype IN ('e', 'd', 'r')
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_type'::regclass
                AND d.objid = t.oid
                AND d.deptype = 'e'
          )
    LOOP
        EXECUTE format('DROP TYPE IF EXISTS public.%I CASCADE', obj.typname);
    END LOOP;

    -- 3. Post-Cleanup Zero-Object Verification Assertion
    SELECT count(*)
    INTO remaining_count
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
      AND NOT EXISTS (
          SELECT 1 FROM pg_catalog.pg_depend d
          WHERE d.classid = 'pg_catalog.pg_class'::regclass
            AND d.objid = c.oid
            AND d.deptype = 'e'
      );

    IF remaining_count > 0 THEN
        RAISE EXCEPTION 'Cleanup verification failed: % non-extension relation(s) still remain in public schema.', remaining_count;
    END IF;
END $$;
