ALTER TABLE IF EXISTS SYNC__KLF_FEATURE_CATEGORY ADD COLUMN IF NOT EXISTS ORD INTEGER;

DROP FUNCTION IF EXISTS pkg_sync.sync_klf_feature_category(integer, integer, character varying, character varying,
                                                           timestamp with time zone, character varying, timestamp with time zone);

CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_feature_category(
    p_sync_id integer,
    p_id integer,
    p_name character varying,
    p_ord integer,
    p_created_by character varying,
    p_created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    p_updated_by character varying DEFAULT NULL::character varying,
    p_updated_at timestamp with time zone DEFAULT NULL::timestamp with time zone)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__klf_feature_category WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_feature_category (
            id, name, ord, created_by, created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_name, p_ord, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
           name = EXCLUDED.name,
           ord = EXCLUDED.ord,
           created_by = EXCLUDED.created_by,
           updated_by = EXCLUDED.updated_by,
           updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_feature_category.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

ALTER FUNCTION pkg_sync.sync_klf_feature_category(integer, integer, character varying, integer, character varying,
                                                  timestamp with time zone, character varying, timestamp with time zone)
    OWNER TO rightsflow;