CREATE SCHEMA IF NOT EXISTS pkg_sync;
ALTER SCHEMA pkg_sync OWNER TO rightsflow;

CREATE OR REPLACE FUNCTION pkg_sync.sync_users(
    p_sync_id integer,
    p_id integer,
    p_username character varying,
    p_display_name character varying,
    p_email character varying,
    p_password_hash character varying,
    p_enabled boolean DEFAULT true,
    p_account_non_expired boolean DEFAULT false,
    p_account_non_locked boolean DEFAULT true,
    p_expiration_date timestamp without time zone DEFAULT NULL::timestamp without time zone,
    p_last_logon timestamp without time zone DEFAULT NULL::timestamp without time zone,
    p_created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    p_updated_at timestamp without time zone DEFAULT NULL::timestamp without time zone,
    p_user_type character varying DEFAULT 'USER'::character varying)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    -- Обработка удаления записи
    if p_id is null then
        DELETE FROM sync__users WHERE id = p_sync_id;
        -- Обработка добавления и обновления записи
    else
        INSERT INTO sync__users (
            id, username, display_name, email, password_hash,
            enabled, account_non_expired, account_non_locked,
            expiration_date, last_logon, created_at, updated_at,
            user_type
        )
        VALUES (
                   p_sync_id, p_username, p_display_name, p_email, p_password_hash,
                   p_enabled, p_account_non_expired, p_account_non_locked,
                   p_expiration_date, p_last_logon, p_created_at, p_updated_at,
                   p_user_type
               )
        ON CONFLICT (id) DO UPDATE SET
            display_name = EXCLUDED.display_name,
            email = EXCLUDED.email,
            password_hash = EXCLUDED.password_hash,
            enabled = EXCLUDED.enabled,
            account_non_expired = EXCLUDED.account_non_expired,
            account_non_locked = EXCLUDED.account_non_locked,
            expiration_date = EXCLUDED.expiration_date,
            last_logon = EXCLUDED.last_logon,
            updated_at = EXCLUDED.updated_at,
            user_type = EXCLUDED.user_type
        WHERE
            EXCLUDED.updated_at > COALESCE(sync__users.updated_at, '1970-01-01'::TIMESTAMP);
    end if;

    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_COUNTERPARTY
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_counterparty(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_name character varying,
    p_id_org_ref integer,
    p_created_by character varying,
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
        DELETE FROM sync__klf_counterparty WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_counterparty (
            id, guid, name, id_org_ref, created_by,
            created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_guid, p_name, p_id_org_ref, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
            guid = EXCLUDED.guid,
            name = EXCLUDED.name,
            id_org_ref = EXCLUDED.id_org_ref,
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_counterparty.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_ORGANIZATION
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_organization(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_name character varying,
    p_created_by character varying,
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
        DELETE FROM sync__klf_organization WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_organization (
            id, guid, name, created_by,
            created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_guid, p_name, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
            guid = EXCLUDED.guid,
            name = EXCLUDED.name,
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_organization.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__LOV_OIP_SUPER_TYPE
CREATE OR REPLACE FUNCTION pkg_sync.sync_lov_oip_super_type(
    p_sync_id integer,
    p_id integer,
    p_name character varying)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__lov_oip_super_type WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__lov_oip_super_type (id, name)
        VALUES (p_sync_id, p_name)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name;
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__LOV_OIP_TYPE
CREATE OR REPLACE FUNCTION pkg_sync.sync_lov_oip_type(
    p_sync_id integer,
    p_id integer,
    p_id_oip_super_type integer,
    p_name character varying)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__lov_oip_type WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__lov_oip_type (id, id_oip_super_type, name)
        VALUES (p_sync_id, p_id_oip_super_type, p_name)
        ON CONFLICT (id) DO UPDATE SET
            id_oip_super_type = EXCLUDED.id_oip_super_type,
            name = EXCLUDED.name;
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_OIP
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
            id, guid, id_oip_super_type, id_oip_type,
            name, part_num, part_count, duration, description,
            created_by, created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_guid, p_id_oip_super_type, p_id_oip_type, p_name,
                   p_part_num, p_part_count, p_duration::interval, p_description,
                   p_created_by, p_created_at, p_updated_by, p_updated_at
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
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_oip.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_RIGHT_TYPE
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_right_type(
    p_sync_id integer,
    p_id integer,
    p_id_parent integer,
    p_name character varying,
    p_description character varying,
    p_created_by character varying,
    p_created_at timestamptz DEFAULT NOW(),
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
        DELETE FROM sync__klf_right_type WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_right_type (
            id, id_parent, name, description, created_by,
            created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_id_parent, p_name, p_description, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
            id_parent = EXCLUDED.id_parent,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_right_type.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_FEATURE_CATEGORY
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_feature_category(
    p_sync_id integer,
    p_id integer,
    p_name character varying,
    p_created_by character varying,
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
        DELETE FROM sync__klf_feature_category WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_feature_category (
            id, name, created_by,
            created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_name, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_feature_category.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_FEATURE_PLAIN
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_feature_plain(
    p_sync_id integer,
    p_id integer,
    p_name character varying,
    p_id_feature_category integer,
    p_created_by character varying,
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
        DELETE FROM sync__klf_feature_plain WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_feature_plain (
            id, name, id_feature_category, created_by,
            created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_name, p_id_feature_category, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            id_feature_category = EXCLUDED.id_feature_category,
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_feature_plain.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_FEATURE_TREE
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_feature_tree(
    p_sync_id integer,
    p_id integer,
    p_id_parent integer,
    p_id_feature_category integer,
    p_id_feature_plain integer,
    p_validity_period character varying DEFAULT '(,)',
    p_created_by character varying DEFAULT 'admin',
    p_created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
    p_updated_by character varying DEFAULT NULL::character varying,
    p_updated_at timestamptz DEFAULT NULL::timestamptz)
    RETURNS integer
    LANGUAGE plpgsql
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__klf_feature_tree WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_feature_tree (
            id, id_parent, id_feature_category, id_feature_plain,
            validity_period, created_by, created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_id_parent, p_id_feature_category, p_id_feature_plain,
                   p_validity_period::daterange, p_created_by, p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
            id_parent = EXCLUDED.id_parent,
            id_feature_category = EXCLUDED.id_feature_category,
            id_feature_plain = EXCLUDED.id_feature_plain,
            validity_period = EXCLUDED.validity_period::daterange,
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_feature_tree.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_FEATURE_CAT_TO_RT
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_feature_cat_to_rt(
    p_sync_id integer,
    p_id integer,
    p_id_right_type integer,
    p_id_feature_category integer,
    p_id_def_feature integer,
    p_created_by character varying,
    p_created_at timestamptz DEFAULT NOW(),
    p_updated_by character varying DEFAULT NULL::character varying,
    p_updated_at timestamptz DEFAULT NULL::timestamptz)
    RETURNS integer
    LANGUAGE plpgsql
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__klf_feature_cat_to_rt WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_feature_cat_to_rt (
            id, id_right_type, id_feature_category, id_def_feature, created_by, created_at, updated_by, updated_at
        )
        VALUES (
                p_sync_id, p_id_right_type, p_id_feature_category, p_id_def_feature,
                p_created_by, p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
               id_right_type = EXCLUDED.id_right_type,
               id_feature_category = EXCLUDED.id_feature_category,
               id_def_feature = EXCLUDED.id_def_feature,
               created_by = EXCLUDED.created_by,
               updated_by = EXCLUDED.updated_by,
               updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_feature_cat_to_rt.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

-- SYNC__KLF_OIP_HIERARCHY
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_oip_hierarchy(
    p_sync_id integer,
    p_id integer,
    p_id_parent integer,
    p_id_oip integer,
    p_created_by character varying DEFAULT 'admin',
    p_created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
    p_updated_by character varying DEFAULT NULL::character varying,
    p_updated_at timestamptz DEFAULT NULL::timestamptz)
    RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__klf_oip_hierarchy WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_oip_hierarchy (id, id_parent, id_oip, created_by, created_at, updated_by, updated_at)
        VALUES (p_sync_id, p_id_parent, p_id_oip,
                p_created_by, p_created_at, p_updated_by, p_updated_at)
        ON CONFLICT (id) DO UPDATE SET
           id_parent = EXCLUDED.id_parent,
           id_oip = EXCLUDED.id_oip,
           created_by = EXCLUDED.created_by,
           updated_by = EXCLUDED.updated_by,
           updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_oip_hierarchy.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;