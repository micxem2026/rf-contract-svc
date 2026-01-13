ALTER TABLE SYNC__KLF_OIP ADD COLUMN IF NOT EXISTS CHILDREN_COUNT INTEGER NOT NULL DEFAULT 0;
ALTER TABLE SYNC__KLF_OIP ADD COLUMN IF NOT EXISTS ROOT_ID INTEGER;
ALTER TABLE SYNC__KLF_OIP ADD COLUMN IF NOT EXISTS NATIVE_NAME VARCHAR(512);
ALTER TABLE SYNC__KLF_OIP ADD COLUMN IF NOT EXISTS RELEASE_YEAR VARCHAR(50);
ALTER TABLE SYNC__KLF_OIP ADD COLUMN IF NOT EXISTS FULL_NAME VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_klf_oip_children_count
    ON SYNC__KLF_OIP(CHILDREN_COUNT) WHERE CHILDREN_COUNT > 0;

CREATE INDEX IF NOT EXISTS idx_klf_oip_root_id ON SYNC__KLF_OIP(ROOT_ID);

-- SYNC__KLF_OIP
drop function if exists pkg_sync.sync_klf_oip(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_id_oip_super_type integer,
    p_id_oip_type integer,
    p_name character varying,
    p_part_num integer,
    p_part_count integer,
    p_duration character varying,
    p_description character varying,
    p_has_parent boolean,
    p_has_children boolean,
    p_children_count integer,
    p_root_id integer,
    p_native_name character varying,
    p_release_year character varying,
    p_created_by character varying,
    p_created_at timestamptz,
    p_updated_by character varying,
    p_updated_at timestamptz);

CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_oip(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_id_oip_super_type integer,
    p_id_oip_type integer,
    p_name character varying,
    p_part_num integer DEFAULT 0,
    p_part_count integer DEFAULT 0,
    p_duration character varying DEFAULT NULL::character varying,
    p_description character varying DEFAULT NULL::character varying,
    p_has_parent boolean DEFAULT false,
    p_has_children boolean DEFAULT false,
    p_children_count integer DEFAULT 0,
    p_root_id integer DEFAULT null,
    p_native_name character varying DEFAULT null,
    p_full_name character varying DEFAULT null,
    p_release_year character varying DEFAULT null,
    p_created_by character varying DEFAULT 'admin',
    p_created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
    p_updated_by character varying DEFAULT NULL::character varying,
    p_updated_at timestamptz DEFAULT NULL::timestamptz)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__klf_oip WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_oip (
            id, guid, id_oip_super_type, id_oip_type, name, part_num, part_count, duration, description,
            has_parent, has_children, children_count, root_id, native_name, full_name, release_year, created_by, created_at,
            updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_guid, p_id_oip_super_type, p_id_oip_type, p_name,
                   p_part_num, p_part_count, p_duration::interval, p_description,
                   p_has_parent, p_has_children, p_children_count, p_root_id,
                   p_native_name, p_full_name, p_release_year, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
                                       guid = EXCLUDED.guid,
                                       id_oip_super_type = EXCLUDED.id_oip_super_type,
                                       id_oip_type = EXCLUDED.id_oip_type,
                                       name = EXCLUDED.name,
                                       part_num = EXCLUDED.part_num,
                                       part_count = EXCLUDED.part_count,
                                       duration = EXCLUDED.duration::interval,
                                       description = EXCLUDED.description,
                                       has_parent = EXCLUDED.has_parent,
                                       has_children = EXCLUDED.has_children,
                                       children_count = EXCLUDED.children_count,
                                       root_id = EXCLUDED.root_id,
                                       native_name = EXCLUDED.native_name,
                                       full_name = EXCLUDED.full_name,
                                       release_year = EXCLUDED.release_year,
                                       created_by = EXCLUDED.created_by,
                                       updated_by = EXCLUDED.updated_by,
                                       updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_oip.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;