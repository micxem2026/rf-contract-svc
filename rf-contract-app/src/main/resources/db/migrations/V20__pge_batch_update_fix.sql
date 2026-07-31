CREATE OR REPLACE FUNCTION pkg_pge.update_properties_batch(
    p_updates text,
    p_username character varying DEFAULT 'system'::character varying)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

DECLARE
    c_RightType  constant integer = 2;
    v_updates    jsonb;
    v_count      integer := 0;
    v_null_count integer := 0;
    v_rec        record;
    v_errors     text;
BEGIN
    -- ── 1. Парсинг и валидация JSON ────────────────────────────────────────
    BEGIN
        v_updates := p_updates::jsonb;
    EXCEPTION
        WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'Некорректный JSON в параметре p_updates: %', p_updates
                USING ERRCODE = '22022';
    END;

    IF jsonb_typeof(v_updates) != 'array' THEN
        RAISE EXCEPTION 'p_updates должен быть JSON массивом'
            USING ERRCODE = '22023';
    END IF;

    -- ── 2. Разворачиваем входной массив во временную таблицу ──────────────
    DROP TABLE IF EXISTS tmp_batch_input;
    CREATE TEMP TABLE tmp_batch_input (
                                          code_pg   varchar,
                                          id_entity bigint,
                                          property  varchar,
                                          value     varchar
    ) ON COMMIT DROP;

    INSERT INTO tmp_batch_input (code_pg, id_entity, property, value)
    SELECT
        elem->>'code_pg',
        (elem->>'id_entity')::bigint,
        elem->>'property',
        elem->>'value'
    FROM jsonb_array_elements(v_updates) AS elem;

    -- ── 3. Разрешаем property → id_property ───────────────────────────────
    DROP TABLE IF EXISTS tmp_batch_resolved;
    CREATE TEMP TABLE tmp_batch_resolved (
                                             code_pg     varchar,
                                             id_entity   bigint,
                                             property    varchar,   -- исходный идентификатор для сообщений об ошибках
                                             id_property integer,
                                             value       varchar
    ) ON COMMIT DROP;

    INSERT INTO tmp_batch_resolved (code_pg, id_entity, property, id_property, value)
    SELECT
        b.code_pg,
        b.id_entity,
        b.property,
        CASE
            WHEN b.property ~ '^\d+$' THEN b.property::integer
            ELSE p.id
            END AS id_property,
        b.value
    FROM tmp_batch_input b
             LEFT JOIN sync__lov_pge_property p
                       ON p.code = b.property
                           AND b.property !~ '^\d+$';

    -- Проверяем что все property разрешились
    SELECT string_agg(
                   format('code_pg=%s, entity=%s, property=%s',
                          br.code_pg, br.id_entity, br.property),
                   '; '
           )
    INTO v_errors
    FROM tmp_batch_resolved br
    WHERE br.id_property IS NULL;

    IF v_errors IS NOT NULL THEN
        RAISE EXCEPTION 'Свойства не найдены в lov_pge_property: %', v_errors
            USING ERRCODE = '20151';
    END IF;

    -- ── 4. Определяем слои для каждой сущности по каждому code_pg ─────────
    --      layer_sel_query содержит $1 (id_entity) — заменяем текстово на
    --      e.id_entity и выполняем через LATERAL: один EXECUTE на code_pg,
    --      а не на каждую сущность
    DROP TABLE IF EXISTS tmp_batch_layers;
    CREATE TEMP TABLE tmp_batch_layers (
                                           code_pg   varchar,
                                           id_entity bigint,
                                           id_pgl    integer
    ) ON COMMIT DROP;

    FOR v_rec IN
        SELECT DISTINCT pg.code, pg.layer_sel_query
        FROM tmp_batch_resolved br
                 JOIN sync__lov_pge_property_group pg ON pg.code = br.code_pg
        LOOP
            EXECUTE format(
                    'INSERT INTO tmp_batch_layers (code_pg, id_entity, id_pgl)
                     SELECT %L, e.id_entity, l.id
                     FROM unnest($1::bigint[]) AS e(id_entity)
                     CROSS JOIN LATERAL (%s) AS computed(sel_val)
                     JOIN sync__lov_pge_pg_layer l
                          ON l.sel_value::varchar[] && computed.sel_val::varchar[]
                     JOIN sync__lov_pge_property_group pg ON pg.id = l.id_pg
                     WHERE pg.code = %L',
                    v_rec.code,
                    replace(v_rec.layer_sel_query, '$1', 'e.id_entity'),
                    v_rec.code
                    ) USING (
                SELECT array_agg(DISTINCT id_entity)
                FROM tmp_batch_resolved
                WHERE code_pg = v_rec.code
            );
        END LOOP;

    CREATE INDEX ON tmp_batch_layers (code_pg, id_entity);
    CREATE INDEX ON tmp_batch_layers (id_pgl);

    -- ── 5a. Валидация формата single-select ───────────────────────────────
    SELECT string_agg(
                   format('property=%s, entity=%s, value=%s, expected=%s',
                          br.property, br.id_entity, br.value, pgld.property_format),
                   '; ' ORDER BY br.property
           )
    INTO v_errors
    FROM tmp_batch_resolved br
             JOIN tmp_batch_layers bl        ON bl.id_entity = br.id_entity AND bl.code_pg = br.code_pg
             JOIN sync__lov_pge_pgl_dtl pgld ON pgld.id_pgl = bl.id_pgl AND pgld.id_property = br.id_property
             JOIN sync__lov_pge_property  p  ON p.id = br.id_property
             JOIN sync__lov_pge_prop_type pt ON pt.id = p.id_prop_type
    WHERE NOT pt.use_multi_select
      AND br.value IS NOT NULL
      AND pgld.property_format IS NOT NULL
      AND NOT (br.value ~* pgld.property_format);

    IF v_errors IS NOT NULL THEN
        RAISE EXCEPTION 'Значения не соответствуют формату: %', v_errors
            USING ERRCODE = '20152';
    END IF;

    -- ── 5b. Валидация формата multi-select ───────────────────────────────
    SELECT string_agg(
                   format('property=%s, entity=%s, value=%s, expected=%s',
                          br.property, br.id_entity, br.value, pgld.property_format),
                   '; ' ORDER BY br.property
           )
    INTO v_errors
    FROM tmp_batch_resolved br
             JOIN tmp_batch_layers bl        ON bl.id_entity = br.id_entity AND bl.code_pg = br.code_pg
             JOIN sync__lov_pge_pgl_dtl pgld ON pgld.id_pgl = bl.id_pgl AND pgld.id_property = br.id_property
             JOIN sync__lov_pge_property p   ON p.id = br.id_property
             JOIN sync__lov_pge_prop_type pt ON pt.id = p.id_prop_type
    WHERE pt.id_obj IS NOT NULL
      AND pt.use_multi_select
      AND br.value IS NOT NULL
      AND pgld.property_format IS NOT NULL
      AND NOT (CASE WHEN br.value ~* '^[0-9]+(,[0-9]+)*$'
                        THEN ('{' || br.value || '}')
                    ELSE br.value
                   END ~* pgld.property_format);

    IF v_errors IS NOT NULL THEN
        RAISE EXCEPTION 'Значения мультивыбора не соответствуют формату: %', v_errors
            USING ERRCODE = '20152';
    END IF;

    -- ── 6. Загружаем справочники для свойств типа "Справочник" ────────────
    DROP TABLE IF EXISTS tmp_batch_catalogs;
    CREATE TEMP TABLE tmp_batch_catalogs (
                                             id_obj integer,
                                             id     integer
    ) ON COMMIT DROP;

    FOR v_rec IN
        SELECT DISTINCT pt.id_obj, lo.table_name, lo.where_filter
        FROM tmp_batch_resolved br
                 JOIN tmp_batch_layers bl         ON bl.id_entity = br.id_entity AND bl.code_pg = br.code_pg
                 JOIN sync__lov_pge_pgl_dtl  pgld ON pgld.id_pgl = bl.id_pgl AND pgld.id_property = br.id_property
                 JOIN sync__lov_pge_property p    ON p.id = br.id_property
                 JOIN sync__lov_pge_prop_type pt  ON pt.id = p.id_prop_type
                 JOIN sync__lov_object        lo  ON lo.id = pt.id_obj
        WHERE pt.id_obj IS NOT NULL
          AND br.value IS NOT NULL
        LOOP
            EXECUTE format(
                    'INSERT INTO tmp_batch_catalogs (id_obj, id)
                     SELECT %s, id FROM %s %s',
                    v_rec.id_obj,
                    v_rec.table_name,
                    CASE WHEN v_rec.where_filter IS NOT NULL
                             THEN 'WHERE ' || v_rec.where_filter
                         ELSE '' END
                    );
        END LOOP;

    CREATE INDEX ON tmp_batch_catalogs (id_obj, id);

    -- ── 7а. Валидация single-select ────────────────────────────────────────
    SELECT string_agg(
                   format('property=%s, entity=%s, value=%s',
                          br.property, br.id_entity, br.value),
                   '; '
           )
    INTO v_errors
    FROM tmp_batch_resolved br
             JOIN tmp_batch_layers        bl   ON bl.id_entity = br.id_entity AND bl.code_pg = br.code_pg
             JOIN sync__lov_pge_pgl_dtl   pgld ON pgld.id_pgl = bl.id_pgl AND pgld.id_property = br.id_property
             JOIN sync__lov_pge_property  p    ON p.id = br.id_property
             JOIN sync__lov_pge_prop_type pt   ON pt.id = p.id_prop_type
    WHERE pt.id_obj IS NOT NULL
      AND NOT pt.use_multi_select
      AND br.value IS NOT NULL
      AND br.value ~ '^\d+$'
      AND NOT EXISTS (
        SELECT 1 FROM tmp_batch_catalogs tc
        WHERE tc.id_obj = pt.id_obj
          AND tc.id     = br.value::integer
    );

    IF v_errors IS NOT NULL THEN
        RAISE EXCEPTION 'Значения отсутствуют в справочнике: %', v_errors
            USING ERRCODE = '20154';
    END IF;

    -- ── 7б. Валидация multi-select ─────────────────────────────────────────
    SELECT string_agg(
                   format('property=%s, entity=%s, missing_id=%s',
                          br.property, br.id_entity, val.id),
                   '; '
           )
    INTO v_errors
    FROM tmp_batch_resolved br
             JOIN tmp_batch_layers        bl   ON bl.id_entity = br.id_entity AND bl.code_pg = br.code_pg
             JOIN sync__lov_pge_pgl_dtl   pgld ON pgld.id_pgl = bl.id_pgl AND pgld.id_property = br.id_property
             JOIN sync__lov_pge_property  p    ON p.id = br.id_property
             JOIN sync__lov_pge_prop_type pt   ON pt.id = p.id_prop_type
             CROSS JOIN LATERAL unnest(
            CASE
                WHEN br.value ~* '^[0-9]+(,[0-9]+)*$'
                    THEN ('{' || br.value || '}')::integer[]
                ELSE br.value::integer[]
                END
                                ) AS val(id)
    WHERE pt.id_obj IS NOT NULL
      AND pt.use_multi_select
      AND br.value IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM tmp_batch_catalogs tc
        WHERE tc.id_obj = pt.id_obj
          AND tc.id     = val.id
    );

    IF v_errors IS NOT NULL THEN
        RAISE EXCEPTION 'Элементы мультивыбора отсутствуют в справочнике: %', v_errors
            USING ERRCODE = '20154';
    END IF;

    -- ── 8. UPSERT одной операцией ──────────────────────────────────────────
    WITH upserted AS (
        INSERT INTO pge_props (id_pgl, id_property, id_entity, property_value, created_by)
            SELECT
                bl.id_pgl,
                br.id_property,
                br.id_entity,
                CASE
                    WHEN pt.use_multi_select
                        AND pt.id_obj IS NOT NULL
                        AND br.value ~* '^[0-9]+(,[0-9]+)*$'
                        THEN '{' || br.value || '}'
                    ELSE br.value
                    END AS property_value,
                p_username
            FROM tmp_batch_resolved br
                     JOIN tmp_batch_layers        bl   ON bl.id_entity = br.id_entity AND bl.code_pg = br.code_pg
                     JOIN sync__lov_pge_pgl_dtl   pgld ON pgld.id_pgl = bl.id_pgl AND pgld.id_property = br.id_property
                     JOIN sync__lov_pge_property  p    ON p.id = br.id_property
                     JOIN sync__lov_pge_prop_type pt   ON pt.id = p.id_prop_type
            WHERE br.value IS NOT NULL
            ON CONFLICT (id_pgl, id_property, id_entity)
                DO UPDATE SET
                    property_value = EXCLUDED.property_value,
                    updated_by     = p_username,
                    updated_at     = CURRENT_TIMESTAMP
            RETURNING id, id_entity, id_pgl, (xmax = 0) AS is_insert
    ),
         lrt_inserts AS (
             INSERT INTO pge_props_lic_rights_rt (id, id_lic_rights_rt)
                 SELECT u.id, u.id_entity
                 FROM upserted u
                          JOIN tmp_batch_layers  bl  ON bl.id_pgl = u.id_pgl AND bl.id_entity = u.id_entity
                          JOIN sync__lov_pge_pg_to_obj pgo ON pgo.code_pg = bl.code_pg
                 WHERE u.is_insert
                   AND pgo.id_obj = c_RightType
                 ON CONFLICT DO NOTHING
                 RETURNING 1
         )
    SELECT count(*) INTO v_count FROM upserted;

    -- ── 9. Обнуление NULL-значений ─────────────────────────────────────────
    UPDATE pge_props pp
    SET property_value = NULL,
        updated_by     = p_username,
        updated_at     = CURRENT_TIMESTAMP
    FROM tmp_batch_resolved br
             JOIN tmp_batch_layers bl ON bl.id_entity = br.id_entity AND bl.code_pg = br.code_pg
    WHERE pp.id_pgl      = bl.id_pgl
      AND pp.id_property = br.id_property
      AND pp.id_entity   = br.id_entity
      AND br.value IS NULL;

    GET DIAGNOSTICS v_null_count = ROW_COUNT;

    RETURN v_count + v_null_count;
END;
$BODY$;
