-- ============================================================
-- Таблицы отчёта
-- ============================================================
CREATE TABLE IF NOT EXISTS report_job (
  id            UUID         NOT NULL PRIMARY KEY,
  id_org        INTEGER      NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  row_count     BIGINT,
  error_message TEXT,
  backend_pid   INTEGER,
  created_by    VARCHAR(50)  NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at    TIMESTAMPTZ,
  finished_at   TIMESTAMPTZ
);
COMMENT ON TABLE report_job IS 'Задачи асинхронного формирования отчёта по проданным правам';
COMMENT ON COLUMN report_job.backend_pid IS
    'PID Postgres, выполняющего generate_sold_rights_report — используется для pg_cancel_backend()';

CREATE INDEX IF NOT EXISTS idx_report_job_created_at ON report_job(created_at);
CREATE INDEX IF NOT EXISTS idx_report_job_active_status
    ON report_job(status) WHERE status IN ('PENDING', 'RUNNING', 'CANCELLING');


-- UNLOGGED — отчёт эфемерный (живёт часы, не переживает падение сервера),
-- зато insert на несколько тысяч строк с json-полезной нагрузкой пишется быстрее.
CREATE UNLOGGED TABLE IF NOT EXISTS report_sold_rights_row (
   id            BIGSERIAL   NOT NULL PRIMARY KEY,
   job_id        UUID        NOT NULL REFERENCES report_job(id) ON DELETE CASCADE,
   id_contract   BIGINT      NOT NULL,
   contract_date DATE,
   payload       JSON        NOT NULL
);
COMMENT ON TABLE report_sold_rights_row IS 'Материализованные готовые строки отчёта (по одной на контракт)';

-- Ключевой индекс для keyset-пагинации: сортировка ровно по нему.
CREATE UNIQUE INDEX IF NOT EXISTS idx_report_row_cursor
    ON report_sold_rights_row(job_id, contract_date, id_contract);

-- Проверка: содержит ли feature_set все заданные территории/каналы/сайты
-- (категории: 3 = территория, 5 = каналы ТВ, 4 = сайты)
CREATE OR REPLACE FUNCTION pkg_contract.feature_set_matches_filters(
    p_id_feature_set        BIGINT,
    p_id_feature_territory  INTEGER[],
    p_id_feature_channel    INTEGER[],
    p_id_feature_site       INTEGER[]
) RETURNS BOOLEAN
    LANGUAGE sql STABLE
    SET search_path = rightsflow
AS $$
SELECT
    (p_id_feature_territory IS NULL OR EXISTS (
        SELECT 1 FROM license_rt_features ft
        WHERE ft.id_feature_set = p_id_feature_set AND ft.is_included
          AND ft.id_feature_category = 3 AND ft.id_feature = ANY(p_id_feature_territory)))
    AND (p_id_feature_channel IS NULL OR EXISTS (
        SELECT 1 FROM license_rt_features ft
        WHERE ft.id_feature_set = p_id_feature_set AND ft.is_included
          AND ft.id_feature_category = 5 AND ft.id_feature = ANY(p_id_feature_channel)))
    AND (p_id_feature_site IS NULL OR EXISTS (
        SELECT 1 FROM license_rt_features ft
        WHERE ft.id_feature_set = p_id_feature_set AND ft.is_included
          AND ft.id_feature_category = 4 AND ft.id_feature = ANY(p_id_feature_site)));
$$;

-- Агрегация имён характеристик (территория/каналы/сайты/платформы/мерч-категории) по feature_set и категории
CREATE OR REPLACE FUNCTION pkg_contract.feature_set_names(
    p_id_feature_set BIGINT,
    p_id_feature_category INTEGER
) RETURNS TEXT
    LANGUAGE sql STABLE
    SET search_path = rightsflow
AS $$
SELECT string_agg(kfp.name, ', ' ORDER BY kfp.name)
FROM license_rt_features ft
         JOIN sync__klf_feature_tree kft ON kft.id = ft.id_feature
         JOIN sync__klf_feature_plain kfp ON kfp.id = kft.id_feature_plain
WHERE ft.id_feature_set = p_id_feature_set
  AND ft.id_feature_category = p_id_feature_category
  AND ft.is_included;
$$;

-- Агрегация мультиселект-свойств pge_props (языки/качество/тех.профили/каналы дистрибуции)
CREATE OR REPLACE FUNCTION pkg_contract.rt_prop_feature_names(
    p_id_entity BIGINT,
    p_id_property INTEGER
) RETURNS TEXT
    LANGUAGE sql STABLE
    SET search_path = rightsflow
AS $$
WITH features AS (
    SELECT unnest(p.property_value::int[]) AS id_feature_tree
    FROM pge_props p
    WHERE p.id_property = p_id_property AND p.id_entity = p_id_entity
)
SELECT string_agg(kfp.name, ', ' ORDER BY kfp.name)
FROM features f
         JOIN sync__klf_feature_tree kft ON kft.id = f.id_feature_tree
         JOIN sync__klf_feature_plain kfp ON kfp.id = kft.id_feature_plain;
$$;

-- Скалярное числовое свойство (показы)
CREATE OR REPLACE FUNCTION pkg_contract.rt_prop_int_max(
    p_id_entity BIGINT,
    p_id_property INTEGER
) RETURNS INTEGER
    LANGUAGE sql STABLE
    SET search_path = rightsflow
AS $$
SELECT coalesce(max(coalesce(nullif(property_value, ''), '0')::int), 0)
FROM pge_props
WHERE id_property = p_id_property AND id_entity = p_id_entity;
$$;

-- Условия catch-up / catch-forward
CREATE OR REPLACE FUNCTION pkg_contract.rt_catch_conditions(
    p_id_entity BIGINT
) RETURNS TEXT
    LANGUAGE sql STABLE
    SET search_path = rightsflow
AS $$
SELECT string_agg(val, ', ')
FROM (
         SELECT 'catch-up: ' || coalesce((SELECT min(property_value) FROM pge_props WHERE id_property = 6 AND id_entity = p_id_entity), 'нет') AS val
         UNION ALL
         SELECT 'Количество (catch-up): ' || coalesce((SELECT min(property_value) FROM pge_props WHERE id_property = 7 AND id_entity = p_id_entity), '0')
         UNION ALL
         SELECT 'catch-forward: ' || coalesce((SELECT min(property_value) FROM pge_props WHERE id_property = 8 AND id_entity = p_id_entity), 'нет')
         UNION ALL
         SELECT 'Количество (catch-forward): ' || coalesce((SELECT min(property_value) FROM pge_props WHERE id_property = 9 AND id_entity = p_id_entity), '0')
     ) x
WHERE val IS NOT NULL;
$$;

-- ============================================================
-- Основная функция генерации отчёта
-- ============================================================
CREATE OR REPLACE FUNCTION pkg_contract.generate_sold_rights_report(
    p_job_id                UUID,
    p_id_org                INTEGER,
    p_id_cparty             INTEGER,
    p_id_oip                INTEGER,
    p_id_oip_type           INTEGER,
    p_id_right_type         INTEGER,
    p_beg_date              DATE,
    p_end_date              DATE,
    p_id_feature_territory  INTEGER[],
    p_id_feature_channel    INTEGER[],
    p_id_feature_site       INTEGER[],
    p_created_by            VARCHAR,
    p_managed_by            VARCHAR,
    p_no_data_filter        BOOLEAN,
    p_username              VARCHAR,
    p_bypass                BOOLEAN
) RETURNS BIGINT
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
DECLARE
    v_row_count BIGINT;
BEGIN
    WITH filtered_contracts AS (
        SELECT DISTINCT c.id AS id_contract, c.contract_date
        FROM contract c
        JOIN lov_contract_status cs ON c.id_contract_status = cs.id
        WHERE ((c.id_org = p_id_org and c.in_out = any(array['eS','iS'])) OR
               (c.id_org_party = p_id_org and c.in_out = any(array['iP']))) -- только контракты продажи
          AND cs.mode = 2 -- только подтверждённые контракты
          AND c.validity_period && daterange(p_beg_date, p_end_date, '[]')
          AND (p_created_by IS NULL OR c.created_by = p_created_by)
          AND (p_managed_by IS NULL OR c.managed_by = p_managed_by)
          -- контроль доступа: те же org access правила, что и в pkg_contract.check_contract_org_access
          AND (p_bypass OR EXISTS (
            SELECT 1 FROM user_org_access uoa
            WHERE uoa.username = p_username
              AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
        ))
          -- контрагент
          AND (p_id_cparty IS NULL OR EXISTS (
            SELECT 1 FROM contract_counterparty cc
            WHERE cc.id_contract = c.id AND cc.id_cpart = p_id_cparty
            UNION ALL
            SELECT 1 FROM sync__klf_counterparty kc
            WHERE kc.id_org_ref = c.id_org_party AND kc.id = p_id_cparty
        ))
          -- ОИС / тип ОИС / способ использования / территория-канал-сайт:
          -- контракт попадает в отчёт, только если у него есть ХОТЯ БЫ ОДНА
          -- лицензия, удовлетворяющая ВСЕМ заданным фильтрам одновременно
          AND (
            (p_id_oip IS NULL AND p_id_oip_type IS NULL AND p_id_right_type IS NULL
                AND p_id_feature_territory IS NULL AND p_id_feature_channel IS NULL AND p_id_feature_site IS NULL)
                OR EXISTS (
                SELECT 1
                FROM license l
                WHERE l.id_contract = c.id
                  AND (
                    (p_id_oip IS NULL AND p_id_oip_type IS NULL)
                        OR EXISTS (
                        SELECT 1 FROM license_oip lo
                                          JOIN sync__klf_oip o ON o.id = lo.id_oip
                        WHERE lo.id_license = l.id
                          AND (p_id_oip IS NULL OR lo.id_oip = p_id_oip)
                          AND (p_id_oip_type IS NULL OR o.id_oip_type = p_id_oip_type)
                    )
                    )
                  AND (
                    p_id_right_type IS NULL
                        OR EXISTS (
                        SELECT 1 FROM license_rights lr
                                          JOIN license_rights_rt lrr ON lrr.id_lic_rights = lr.id
                        WHERE lr.id_license = l.id AND lrr.id_right_type = p_id_right_type
                    )
                    )
                  AND (
                    (p_id_feature_territory IS NULL AND p_id_feature_channel IS NULL AND p_id_feature_site IS NULL)
                        OR EXISTS (
                        SELECT 1 FROM license_rights lr
                                          JOIN license_rt_feature_set fts ON fts.id_lic_rights = lr.id
                        WHERE lr.id_license = l.id
                          AND pkg_contract.feature_set_matches_filters(
                                fts.id, p_id_feature_territory, p_id_feature_channel, p_id_feature_site)
                    )
                    )
            )
            )
    )
    INSERT INTO report_sold_rights_row (job_id, id_contract, contract_date, payload)
    SELECT
        p_job_id,
        c.id,
        c.contract_date,
        json_build_object(
                'idContract', c.id,
                'contractType', ct.name,
                'orgName', org.name,
                'code1c', org.code_1c,
                'num', c.num,
                'contractDate', c.contract_date,
                'preparedBy', u_created.display_name,
                'managedBy', coalesce(u_managed.display_name, 'Не назначен'),
                'licenses', coalesce(licenses.items, '[]'::json)
        )
    FROM filtered_contracts fc
             JOIN contract c ON c.id = fc.id_contract
             JOIN lov_contract_type ct on ct.id = c.id_contract_type
             LEFT JOIN sync__klf_organization org ON org.id = c.id_org
             LEFT JOIN sync__users u_created ON u_created.username = c.created_by
             LEFT JOIN sync__users u_managed ON u_managed.username = c.managed_by
        -- Лицензии
             LEFT JOIN  LATERAL (
        SELECT json_agg(json_build_object(
                'idLicense', lc.id,
                'num', lc.num,
                'licenseName', lc.name,
                'oipItems', coalesce(ois.items, '[]'::json),
                'rightsItems', coalesce(rights.items, '[]'::json)
               )) as items
        FROM license lc
            -- ОИС-данные (только те, что прошли фильтр idOip/idOipType — вариант "б")
                 LEFT JOIN LATERAL (
            SELECT json_agg(json_build_object(
                    'idOip', o.id,
                    'oipName', o.full_name,
                    'seriesName', o.name,
                    'oipTypeName', ot.name,
                    'releaseYear', o.release_year,
                    'contentOwner', NULL,
                    'duration', o.duration,
                    'unf', NULL,
                    'hasRentalCertificate', NULL
                             )) AS items
            FROM license l2
                     JOIN license_oip lo ON lo.id_license = l2.id
                     JOIN sync__klf_oip o ON o.id = lo.id_oip
                     JOIN sync__lov_oip_type ot ON ot.id = o.id_oip_type
            WHERE l2.id = lc.id
              AND (p_no_data_filter OR p_id_oip IS NULL OR lo.id_oip = p_id_oip)
              AND (p_no_data_filter OR p_id_oip_type IS NULL OR o.id_oip_type = p_id_oip_type)
            ) ois ON true
            -- Способы использования (только те, что прошли фильтр idRightType/территория/канал/сайт)
                 LEFT JOIN LATERAL (
            SELECT json_agg(json_build_object(
                    'rightTypeName', rt.name,
                    'begDate', lower(fts.validity_period),
                    'endDate', (upper(fts.validity_period) - interval '1 day')::date,
                    'isExclusive', fts.is_exclusive,
                    'isSubLicense', fts.is_sub_license,
                    'hbStartDate', lr.hb_start_date,
                    'hbDays', lr.hb_days,
                    'territory', pkg_contract.feature_set_names(fts.id, 3),
                    'voiceLanguages', pkg_contract.rt_prop_feature_names(lrr.id, 2),
                    'subtitleLanguages', pkg_contract.rt_prop_feature_names(lrr.id, 1),
                    'quality', pkg_contract.rt_prop_feature_names(lrr.id, 3),
                    'tv', json_build_object(
                            'channels', pkg_contract.feature_set_names(fts.id, 5),
                            'showsCount', pkg_contract.rt_prop_int_max(lrr.id, 4),
                            'techRepeatProfiles', pkg_contract.rt_prop_feature_names(lrr.id, 5)
                          ),
                    'digital', json_build_object(
                            'sites', pkg_contract.feature_set_names(fts.id, 4),
                            'platforms', pkg_contract.feature_set_names(fts.id, 20),
                            'catchConditions', pkg_contract.rt_catch_conditions(lrr.id)
                               ),
                    'merch', json_build_object(
                            'categories', pkg_contract.feature_set_names(fts.id, 21),
                            'distributionChannels', pkg_contract.rt_prop_feature_names(lrr.id, 10)
                             )
                             )) AS items
            FROM license l3
                     JOIN license_rights lr ON lr.id_license = l3.id
                     JOIN license_rights_rt lrr ON lrr.id_lic_rights = lr.id
                     JOIN sync__klf_right_type rt ON rt.id = lrr.id_right_type
                     JOIN license_rt_feature_set fts ON fts.id_lic_rights = lr.id
            WHERE l3.id = lc.id
              AND (p_no_data_filter OR p_id_right_type IS NULL OR lrr.id_right_type = p_id_right_type)
              AND (p_no_data_filter OR pkg_contract.feature_set_matches_filters(
                    fts.id, p_id_feature_territory, p_id_feature_channel, p_id_feature_site))
            ) rights ON true

        WHERE lc.id_contract = c.id
          -- ОИС / тип ОИС / способ использования / территория-канал-сайт:
          -- лицензия попадает в отчёт, только если она удовлетворяет
          -- ВСЕМ заданным фильтрам одновременно
          AND ( p_no_data_filter
                OR (p_id_oip IS NULL AND p_id_oip_type IS NULL AND p_id_right_type IS NULL
                    AND p_id_feature_territory IS NULL AND p_id_feature_channel IS NULL AND p_id_feature_site IS NULL)
                OR EXISTS (
                SELECT 1
                FROM license lc1
                WHERE lc1.id = lc.id
                  AND (
                    (p_id_oip IS NULL AND p_id_oip_type IS NULL)
                        OR EXISTS (
                        SELECT 1 FROM license_oip lo1
                                          JOIN sync__klf_oip o1 ON o1.id = lo1.id_oip
                        WHERE lo1.id_license = lc1.id
                          AND (p_id_oip IS NULL OR lo1.id_oip = p_id_oip)
                          AND (p_id_oip_type IS NULL OR o1.id_oip_type = p_id_oip_type)
                    )
                    )
                  AND (
                    p_id_right_type IS NULL
                        OR EXISTS (
                        SELECT 1 FROM license_rights lr
                                          JOIN license_rights_rt lrr ON lrr.id_lic_rights = lr.id
                        WHERE lr.id_license = lc1.id AND lrr.id_right_type = p_id_right_type
                    )
                    )
                  AND (
                    (p_id_feature_territory IS NULL AND p_id_feature_channel IS NULL AND p_id_feature_site IS NULL)
                        OR EXISTS (
                        SELECT 1 FROM license_rights lr
                                          JOIN license_rt_feature_set fts ON fts.id_lic_rights = lr.id
                        WHERE lr.id_license = lc1.id
                          AND pkg_contract.feature_set_matches_filters(
                                fts.id, p_id_feature_territory, p_id_feature_channel, p_id_feature_site)
                    )
                    )
            )
            )
        ) licenses ON true;

    GET DIAGNOSTICS v_row_count = ROW_COUNT;
    RETURN v_row_count;
END;
$BODY$;

COMMENT ON FUNCTION pkg_contract.generate_sold_rights_report IS
    'Материализует отчёт по проданным правам в report_sold_rights_row для последующей keyset-пагинации';

DROP INDEX IF EXISTS idx_contract_org;
DROP INDEX IF EXISTS idx_license_oip_lic;
DROP INDEX IF EXISTS idx_lic_rt_ftr_lic_rt_ftr_set;
DROP INDEX IF EXISTS idx_lic_rt_ftr_ftr_set;

CREATE INDEX IF NOT EXISTS idx_contract_org_date ON contract(id_org, contract_date);
CREATE INDEX IF NOT EXISTS idx_lrt_features_set_cat_incl ON license_rt_features(id_feature_set, id_feature_category, is_included);