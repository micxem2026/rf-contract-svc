-- Создание таблицы для отсутствующих прав
CREATE TABLE IF NOT EXISTS MISSING_RIGHT (
 ID BIGSERIAL PRIMARY KEY,
 ID_ORG INTEGER NOT NULL REFERENCES SYNC__KLF_ORGANIZATION(ID) ON DELETE CASCADE,
 ID_OIP INTEGER NOT NULL REFERENCES SYNC__KLF_OIP(ID) ON DELETE CASCADE,
 ID_CONTRACT BIGINT NOT NULL REFERENCES CONTRACT(ID) ON DELETE CASCADE,
 ID_LICENSE BIGINT NOT NULL REFERENCES LICENSE(ID) ON DELETE CASCADE,
 ID_LIC_RIGHTS BIGINT NOT NULL REFERENCES LICENSE_RIGHTS(ID) ON DELETE CASCADE,
 ID_LIC_RIGHTS_RT BIGINT NOT NULL REFERENCES LICENSE_RIGHTS_RT(ID) ON DELETE CASCADE,
 ID_RIGHT_TYPE INTEGER NOT NULL REFERENCES SYNC__KLF_RIGHT_TYPE ON DELETE CASCADE,
 FEATURES_HASH VARCHAR(255) NOT NULL,
 BEG_DATE DATE NOT NULL,
 END_DATE DATE NOT NULL,
 IS_EXCLUSIVE BOOLEAN NOT NULL,
 RIGHT_KEY VARCHAR(255) NOT NULL,
 MISSING_FLAG INTEGER NOT NULL DEFAULT -1,
 MISSING_RIGHT_INFO TEXT,
 DROP_FLAG BOOLEAN NOT NULL DEFAULT FALSE,
 CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UPDATED_AT TIMESTAMPTZ,
 CONSTRAINT CHK_MISSING_FLAG CHECK(MISSING_FLAG = ANY(ARRAY[1,-1]::INTEGER[]))
);

COMMENT ON TABLE MISSING_RIGHT IS 'Таблица отсутствующих прав';
COMMENT ON COLUMN MISSING_RIGHT.MISSING_FLAG IS '1 - право отсутствует, -1 - право проверяется';

CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_ORG ON MISSING_RIGHT(ID_ORG);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_OIP ON MISSING_RIGHT(ID_OIP);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_CONTRACT ON MISSING_RIGHT(ID_CONTRACT);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_LICENSE ON MISSING_RIGHT(ID_LICENSE);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_LIC_RIGHTS ON MISSING_RIGHT(ID_LIC_RIGHTS);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_LIC_RIGHTS_RT ON MISSING_RIGHT(ID_LIC_RIGHTS_RT);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_RT ON MISSING_RIGHT(ID_RIGHT_TYPE);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_KEY ON MISSING_RIGHT(RIGHT_KEY);
CREATE UNIQUE INDEX IF NOT EXISTS UNQ_MISSING_RIGHT_KEY ON MISSING_RIGHT(ID_LIC_RIGHTS, RIGHT_KEY);

INSERT INTO KAFKA_BINDINGS_CONTROL (BINDING_NAME, BINDING_STATE)
VALUES
    ('missingRightProcessor-in-0','PAUSE')
ON CONFLICT (BINDING_NAME) DO NOTHING;

INSERT INTO KAFKA_BINDINGS_CONTROL (BINDING_NAME, BINDING_STATE)
VALUES
    ('checkRightOutboxProcessor-in-0','PAUSE')
ON CONFLICT (BINDING_NAME) DO NOTHING;

INSERT INTO KAFKA_BINDINGS_CONTROL (BINDING_NAME, BINDING_STATE)
VALUES
    ('searchRightParamProcessor-in-0','PAUSE')
ON CONFLICT (BINDING_NAME) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_contract_guid_trgm
    ON contract USING gin ((guid::text) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_klf_cpart_code1c_trgm
    ON sync__klf_counterparty USING gin ((code_1c::text) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_contract_cpart_id_contract
    ON contract_counterparty (id_contract, id_cpart);

CREATE INDEX IF NOT EXISTS idx_contract_cpart_id_cpart
    ON contract_counterparty (id_cpart, id_contract);

DROP FUNCTION IF EXISTS pkg_contract.get_features_set_hash(bigint, bigint, integer, character varying, boolean, boolean);

create or replace function pkg_contract.get_features_set_hash(
    p_id_lic_rights bigint,
    p_id_feature_set bigint,
    p_id_right_type integer,
    p_username character varying,
    p_use_format boolean,
    p_use_cache boolean default false,
    p_use_plain_ids boolean default false
)
    returns text[]
    security definer
    set search_path = rightsflow
    language plpgsql
as
$$
declare
    v_result   text[] := array[]::text[];   -- финальный результат
    v_result1  text[] := array[]::text[];   -- from vw_rt_cat_down
    v_result2  text[] := array[]::text[];   -- from vw_rt_cat_up
    v_sql      text;
    v_categories int[];        -- список категорий для первой группы
    v_categories2 int[];       -- список категорий для второй группы
    v_cat_parts  text[];       -- вспомогательно для cat_list expression
    v_cat_list_expr text;
    v_cat      integer;
    v_excl_cnt integer;
    v_total_cnt integer;
    v_available_ids text[];    -- сбор доступных id (в форме 'category:id')
    v_quoted_vals text;
    v_sub        text;
    v_final_sql  text;
    v_val_text   text;
    v_features_table text;
    v_hash_table text;
    v_id_col     text;   -- 'id' (иерархический) либо 'id_feature_plain' (плоский)
begin
    raise notice 'id_feature_set: %, id_right_type: %, use_plain_ids: %',
        p_id_feature_set, p_id_right_type, p_use_plain_ids;

    if p_use_format = false then
        v_features_table := 'license_rt_features';
        v_hash_table := 'license_rt_features_hash';
    else
        v_features_table := 'format_rt_features';
        v_hash_table := 'format_rt_features_hash';
    end if;

    v_id_col := case when p_use_plain_ids then 'id_feature_plain' else 'id' end;

    -- 1) попытка взять из кеша (только для иерархического режима)
    if p_use_cache and not p_use_plain_ids then
        v_final_sql := format(
                'select array_agg(hash_value order by hash_value) from %s
                 where id_lic_rights = $1 and id_feature_set = $2 and id_right_type = $3',
                v_hash_table);
        execute v_final_sql into v_result using p_id_lic_rights, p_id_feature_set, p_id_right_type;

        if v_result is not null and array_length(v_result, 1) is not null then
            return v_result;
        end if;
    end if;

    --------------------------------------------------------------------------------
    -- 2) первая группа: vw_rt_cat_down
    --------------------------------------------------------------------------------
    select array_agg(distinct id_feature_category order by id_feature_category)
    into v_categories
    from vw_rt_cat_down
    where id_right_type = p_id_right_type;

    if v_categories is not null and array_length(v_categories, 1) is not null then
        -- сформировать выражение для сборки хэша: cat1.id || ',' || cat2.id || ...
        select array_agg('cat' || cid || '.id' order by cid) into v_cat_parts
        from unnest(v_categories) as c(cid);

        if v_cat_parts is not null then
            -- используем dollar-quoted delimiter, чтобы не путаться с кавычками
            v_cat_list_expr := array_to_string(v_cat_parts, $x$ || ',' || $x$);
        else
            v_cat_list_expr := null;
        end if;

        v_sql := null;

        -- пройти по каждой категории и подготовить подзапрос (v_sub) для cross join
        foreach v_cat in array v_categories loop
                -- один запрос вместо двух: сразу считаем и число исключений, и общее число записей
                v_final_sql := format(
                        'select count(*) filter (where is_included = false), count(*)
                         from %s where id_feature_set = $1 and id_feature_category = $2',
                        v_features_table);
                execute v_final_sql into v_excl_cnt, v_total_cnt using p_id_feature_set, v_cat;

                if v_excl_cnt = 0 then
                    if v_total_cnt > 0 then
                        if p_use_plain_ids then
                            -- нужен join к дереву характеристик за id_feature_plain;
                            -- DISTINCT — т.к. разные узлы дерева могут иметь один и тот же plain id
                            v_final_sql := format(
                                    'select array_agg(distinct (f.id_feature_category::text || '':'' || t.%I::text))
                                     from %s f
                                     join sync__klf_feature_tree t on t.id = f.id_feature
                                     where f.id_feature_set = $1 and f.id_feature_category = $2',
                                    v_id_col, v_features_table);
                            execute v_final_sql into v_available_ids using p_id_feature_set, v_cat;
                        else
                            -- иерархический режим: значение id_feature уже лежит в самой таблице связей,
                            -- join не нужен (как и в исходной версии функции)
                            v_final_sql := format(
                                    'select array_agg((id_feature_category::text || '':'' || id_feature::text) order by id_feature)
                                     from %s where id_feature_set = $1 and id_feature_category = $2',
                                    v_features_table);
                            execute v_final_sql into v_available_ids using p_id_feature_set, v_cat;
                        end if;
                    else
                        -- нет записей в license_rt_features: берем дефолт (root feature parent is null limit 1)
                        v_final_sql := format(
                                'select id_feature_category::text || '':'' || %I::text
                                 from sync__klf_feature_tree
                                 where id_feature_category = $1 and id_parent is null
                                 limit 1',
                                v_id_col);
                        execute v_final_sql into v_val_text using v_cat;

                        if v_val_text is not null then
                            v_available_ids := array[v_val_text];
                        else
                            v_available_ids := array[]::text[];
                        end if;
                    end if;

                else
                    -- есть исключения: обход дерева предков всегда по иерархическим id,
                    -- итоговое представление (first_set/second_set) — нужной колонкой.
                    -- EXCEPT сам по себе дедуплицирует, доп. DISTINCT не требуется.
                    v_final_sql := format($sql$
with recursive excluded_nodes as (
  select id_feature from %s
   where id_feature_set = $1 and id_feature_category = $2 and is_included = false
),
ancestors as (
  select k.id, k.id_parent from sync__klf_feature_tree k where k.id in (select id_feature from excluded_nodes)
  union all
  select k2.id, k2.id_parent from sync__klf_feature_tree k2 join ancestors a on k2.id = a.id_parent
),
first_set as (
  select id_feature_category::text || ':' || o1.%2$I::text as id
    from sync__klf_feature_tree o1
   where o1.id_parent in (select id_parent from ancestors where id_parent is not null)
     and id_feature_category = $3
),
second_set as (
  select id_feature_category::text || ':' || t2.%2$I::text as id
    from sync__klf_feature_tree t2 where t2.id in (select id from ancestors)
)
select array_agg(id order by id) from (select id from first_set except select id from second_set) t;
$sql$, v_features_table, v_id_col);

                    execute v_final_sql into v_available_ids using p_id_feature_set, v_cat, v_cat;
                end if;

                -- fallback: если v_available_ids пуст или null -> пробуем дефолт (parent is null limit 1)
                if v_available_ids is null or array_length(v_available_ids, 1) is null then
                    v_final_sql := format(
                            'select id_feature_category::text || '':'' || %I::text
                             from sync__klf_feature_tree
                             where id_feature_category = $1 and id_parent is null
                             limit 1',
                            v_id_col);
                    execute v_final_sql into v_val_text using v_cat;

                    if v_val_text is not null then
                        v_available_ids := array[v_val_text];
                    else
                        v_available_ids := array[]::text[];
                    end if;
                end if;

                -- подготовить подзапрос вида: (select unnest(array['c:1','c:2']::text[]) as id) cat<cat>
                if array_length(v_available_ids, 1) > 0 then
                    select array_to_string(array_agg(quote_literal(x)), ',') into v_quoted_vals
                    from unnest(v_available_ids) as x;
                    v_sub := format('(select unnest(array[%s]::text[]) as id) cat%s', v_quoted_vals, v_cat);
                else
                    -- если нечего — подставим пустую таблицу (чтобы не ломать cross join)
                    v_sub := format('(select null::text as id) cat%s', v_cat);
                end if;

                -- собрать v_sql: первый элемент — select distinct <cat_list_expr> as hash from <first_sub>
                if v_sql is null then
                    v_sql := format('select distinct %s as hash from %s', v_cat_list_expr, v_sub);
                else
                    v_sql := v_sql || ' cross join ' || v_sub;
                end if;

                -- сброс временных переменных
                v_available_ids := null;
                v_val_text := null;
            end loop; -- v_categories loop

        -- выполнить l_sql и собрать в массив
        if v_sql is not null then
            v_final_sql := 'select array_agg(hash order by hash) from (' || v_sql || ') t';
            execute v_final_sql into v_result1;
            if v_result1 is null then
                v_result1 := array[]::text[];
            end if;
        end if;
    end if; -- end first group

    -- отладочный вывод (аналог dbms_output)
    if v_sql is not null then
        raise debug 'last dynamic sql (result1): %', left(coalesce(v_sql,''), 2000);
    end if;

    --------------------------------------------------------------------------------
    -- 3) вторая группа: vw_rt_cat_up (та же логика, та же замена id -> v_id_col)
    --------------------------------------------------------------------------------
    select array_agg(distinct id_feature_category order by id_feature_category)
    into v_categories2
    from vw_rt_cat_up
    where id_right_type = p_id_right_type;

    if v_categories2 is not null and array_length(v_categories2, 1) is not null then
        select array_agg('cat' || cid || '.id' order by cid) into v_cat_parts
        from unnest(v_categories2) as c(cid);

        if v_cat_parts is not null then
            v_cat_list_expr := array_to_string(v_cat_parts, $x$ || ',' || $x$);
        else
            v_cat_list_expr := null;
        end if;

        v_sql := null;

        foreach v_cat in array v_categories2 loop
                v_final_sql := format(
                        'select count(*) filter (where is_included = false), count(*)
                         from %s where id_feature_set = $1 and id_feature_category = $2',
                        v_features_table);
                execute v_final_sql into v_excl_cnt, v_total_cnt using p_id_feature_set, v_cat;

                if v_excl_cnt = 0 then
                    if v_total_cnt > 0 then
                        if p_use_plain_ids then
                            v_final_sql := format(
                                    'select array_agg(distinct (f.id_feature_category::text || '':'' || t.%I::text))
                                     from %s f
                                     join sync__klf_feature_tree t on t.id = f.id_feature
                                     where f.id_feature_set = $1 and f.id_feature_category = $2',
                                    v_id_col, v_features_table);
                            execute v_final_sql into v_available_ids using p_id_feature_set, v_cat;
                        else
                            v_final_sql := format(
                                    'select array_agg((id_feature_category::text || '':'' || id_feature::text) order by id_feature)
                                     from %s where id_feature_set = $1 and id_feature_category = $2',
                                    v_features_table);
                            execute v_final_sql into v_available_ids using p_id_feature_set, v_cat;
                        end if;
                    else
                        v_final_sql := format(
                                'select id_feature_category::text || '':'' || %I::text
                                 from sync__klf_feature_tree
                                 where id_feature_category = $1 and id_parent is null
                                 limit 1',
                                v_id_col);
                        execute v_final_sql into v_val_text using v_cat;

                        if v_val_text is not null then
                            v_available_ids := array[v_val_text];
                        else
                            v_available_ids := array[]::text[];
                        end if;
                    end if;

                else
                    -- рекурсивный вариант для исключений
                    v_final_sql := format($sql$
with recursive excluded_nodes as (
  select id_feature from %s
   where id_feature_set = $1 and id_feature_category = $2 and is_included = false
),
ancestors as (
  select k.id, k.id_parent from sync__klf_feature_tree k where k.id in (select id_feature from excluded_nodes)
  union all
  select k2.id, k2.id_parent from sync__klf_feature_tree k2 join ancestors a on k2.id = a.id_parent
),
first_set as (
  select id_feature_category::text || ':' || o1.%2$I::text as id
    from sync__klf_feature_tree o1
   where o1.id_parent in (select id_parent from ancestors where id_parent is not null)
     and id_feature_category = $3
),
second_set as (
  select id_feature_category::text || ':' || t2.%2$I::text as id
    from sync__klf_feature_tree t2 where t2.id in (select id from ancestors)
)
select array_agg(id order by id) from (select id from first_set except select id from second_set) t;
$sql$, v_features_table, v_id_col);

                    execute v_final_sql into v_available_ids using p_id_feature_set, v_cat, v_cat;
                end if;

                if v_available_ids is null or array_length(v_available_ids, 1) is null then
                    v_final_sql := format(
                            'select id_feature_category::text || '':'' || %I::text
                             from sync__klf_feature_tree
                             where id_feature_category = $1 and id_parent is null
                             limit 1',
                            v_id_col);
                    execute v_final_sql into v_val_text using v_cat;

                    if v_val_text is not null then
                        v_available_ids := array[v_val_text];
                    else
                        v_available_ids := array[]::text[];
                    end if;
                end if;

                if array_length(v_available_ids, 1) > 0 then
                    select array_to_string(array_agg(quote_literal(x)), ',') into v_quoted_vals
                    from unnest(v_available_ids) as x;
                    v_sub := format('(select unnest(array[%s]::text[]) as id) cat%s', v_quoted_vals, v_cat);
                else
                    v_sub := format('(select null::text as id) cat%s', v_cat);
                end if;

                if v_sql is null then
                    v_sql := format('select distinct %s as hash from %s', v_cat_list_expr, v_sub);
                else
                    v_sql := v_sql || ' cross join ' || v_sub;
                end if;

                v_available_ids := null;
                v_val_text := null;
            end loop;

        if v_sql is not null then
            v_final_sql := 'select array_agg(hash order by hash) from (' || v_sql || ') t';
            execute v_final_sql into v_result2;
            if v_result2 is null then
                v_result2 := array[]::text[];
            end if;
        end if;
    end if; -- end second group

    if v_sql is not null then
        raise debug 'last dynamic sql (result2): %', left(coalesce(v_sql,''), 2000);
    end if;

    --------------------------------------------------------------------------------
    -- 4) комбинация результатов
    --------------------------------------------------------------------------------
    if array_length(v_result1, 1) is not null and array_length(v_result2, 1) is null then
        v_result := v_result1;
    elsif array_length(v_result2, 1) is not null and array_length(v_result1, 1) is null then
        -- второй набор получает префикс ';' при единственном использовании
        v_result := array(select ';' || x from unnest(v_result2) as x);
    elsif array_length(v_result1, 1) is not null and array_length(v_result2, 1) is not null then
        -- cartesian product r1 || ';' || r2
        v_result := array(
                select r1 || ';' || r2 from unnest(v_result1) as r1 cross join unnest(v_result2) as r2
                    );
    else
        v_result := array[]::text[];
    end if;

    --------------------------------------------------------------------------------
    -- 5) кеширование результата (только для иерархического режима)
    --------------------------------------------------------------------------------
    begin
        if p_use_cache and not p_use_plain_ids and array_length(v_result, 1) is not null then
            for v_val_text in select unnest(v_result) loop
                    v_final_sql := format(
                            'insert into %s (id_lic_rights, id_feature_set, id_right_type, hash_value, created_by)
                             values ($1, $2, $3, $4, $5)',
                            v_hash_table);
                    execute v_final_sql using p_id_lic_rights, p_id_feature_set, p_id_right_type, v_val_text, p_username;
                end loop;
        end if;
    exception
        when others then
            null; -- ничего не делаем, если не удалось сохранить кеш
    end;

    return v_result;
end;
$$;

comment on function pkg_contract.get_features_set_hash(bigint, bigint, integer, varchar, boolean, boolean, boolean)
    is 'Вычисляет хэш набора характеристик прав. p_use_plain_ids=false (по умолчанию) — хэш на иерархических id из sync__klf_feature_tree, с кешированием. p_use_plain_ids=true — хэш на плоских id (sync__klf_feature_tree.id_feature_plain), без кеширования.';

alter function pkg_contract.get_features_set_hash(bigint, bigint, integer, varchar, boolean, boolean, boolean)
    owner to rightsflow;

DROP VIEW rightsflow.vw_lic_rt;

CREATE OR REPLACE VIEW rightsflow.vw_lic_rt
AS
SELECT fs.validity_period,
       l.id_contract,
       c.id_org,
       c.id_org_party,
       c.in_out,
       lower(c.validity_period) AS sign_date,
       lr.id_license,
       lo.id_oip,
       lr.id AS id_lic_rights,
       false AS use_format,
       lrt.id AS id_lic_rights_rt,
       lrt.id_right_type,
       fs.id_feature_set,
       fs.is_exclusive,
       fs.is_use_right,
       fs.is_sub_license,
       c.id_contract_type,
       c.id_contract_status,
       cs.mode AS status_mode
FROM license l
         JOIN license_oip lo ON l.id = lo.id_license
         JOIN contract c ON l.id_contract = c.id
         JOIN lov_contract_status cs ON c.id_contract_status = cs.id
         JOIN license_rights lr ON lr.id_license = l.id
         JOIN license_rights_rt lrt ON lrt.id_lic_rights = lr.id
         JOIN license_rt_feature_set_ext fs ON fs.id_lic_rights = lr.id
WHERE l.id_lic_format IS NULL
UNION ALL
SELECT fs.validity_period,
       l.id_contract,
       c.id_org,
       c.id_org_party,
       c.in_out,
       lower(c.validity_period) AS sign_date,
       l.id AS id_license,
       lo.id_oip,
       fr.id AS id_lic_rights,
       true AS use_format,
       frt.id,
       frt.id_right_type,
       fs.id AS id_feature_set,
       fs.is_exclusive,
       fs.is_use_right,
       fs.is_sub_license,
       c.id_contract_type,
       c.id_contract_status,
       cs.mode AS status_mode
FROM license l
         JOIN license_oip lo ON l.id = lo.id_license
         JOIN contract c ON l.id_contract = c.id
         JOIN lov_contract_status cs ON c.id_contract_status = cs.id
         JOIN format_rights fr ON fr.id_lic_format = l.id_lic_format
         JOIN format_rights_rt frt ON frt.id_fmt_rights = fr.id
         JOIN format_rt_feature_set fs ON fs.id_fmt_rights = fr.id
WHERE l.id_lic_format IS NOT NULL;

ALTER TABLE rightsflow.vw_lic_rt OWNER TO rightsflow;
COMMENT ON VIEW rightsflow.vw_lic_rt
    IS 'Объединённое представление прав лицензий из license_rights* и format_rights* таблиц';

GRANT SELECT ON TABLE rightsflow.vw_lic_rt TO debezium;
GRANT ALL ON TABLE rightsflow.vw_lic_rt TO rightsflow;

CREATE TABLE IF NOT EXISTS check_right_outbox (
  ID BIGSERIAL PRIMARY KEY,
  ID_ORG INTEGER NOT NULL,
  ID_OIP INTEGER NOT NULL,
  ID_CONTRACT BIGINT NOT NULL,
  ID_LICENSE BIGINT NOT NULL,
  ID_LIC_RIGHTS BIGINT NOT NULL,
  ID_LIC_RIGHTS_RT BIGINT NOT NULL,
  ID_RIGHT_TYPE INTEGER NOT NULL,
  FEATURES_HASH VARCHAR(255) NOT NULL,
  BEG_DATE DATE NOT NULL,
  END_DATE DATE NOT NULL,
  IS_EXCLUSIVE BOOLEAN NOT NULL,
  RIGHT_KEY VARCHAR(255) NOT NULL,
  MISSING_RIGHT_INFO TEXT,
  CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE CHECK_RIGHT_OUTBOX IS 'Сообщения для проверки прав';

CREATE INDEX IF NOT EXISTS idx_check_right_outbox_created_at ON CHECK_RIGHT_OUTBOX (CREATED_AT);

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM pg_publication_tables
            WHERE pubname = 'dbz_publication'
              AND schemaname = 'rightsflow'
              AND tablename = 'check_right_outbox'
        ) THEN
            ALTER PUBLICATION dbz_publication ADD TABLE rightsflow.check_right_outbox;
        END IF;
    END
$$;

-- =============================================================================
-- Используется один set-based запрос, который:
--   1) считает хэши характеристик для всех прав контракта разом ;
--   2) парсит все хэши в характеристики и строит человекочитаемый текст одним
--      проходом;
--   3) вставляет все строки в missing_right ОДНИМ INSERT (data-modifying CTE),
--      и по RETURNING из него — все строки в check_right_outbox ВТОРЫМ INSERT.
--
-- Дополнительно:
--   - добавлен DISTINCT ON (id_lic_rights, right_key) перед вставкой —
--     защита от ошибки "ON CONFLICT DO UPDATE command cannot affect row
--     a second time", если в рамках одного вызова встретятся два совпадающих
--     right_key для одного id_lic_rights.
-- =============================================================================

CREATE OR REPLACE FUNCTION pkg_contract.check_rights_for_contract(
    p_id_contract bigint
)
    returns integer
    security definer
    set search_path = rightsflow
    volatile
    parallel unsafe
    language plpgsql
as
$$
DECLARE
    v_is_valid boolean;
    v_contract contract%rowtype;
BEGIN

    -- Проверка входных параметров
    if p_id_contract is null then
        raise notice 'Не указан контракт!';
        return 0;
    end if;

    -- один запрос вместо exists(...) + select * into (...)
    SELECT * INTO v_contract FROM contract WHERE id = p_id_contract;
    if not found then
        raise notice 'Контракт [ID=%] не существует!', p_id_contract;
        return -1;
    end if;

    if v_contract.in_out = 'eP' then
        raise notice 'Пропуск внешнего контракта покупки [ID=%]..', p_id_contract;
        return -3;
    end if;

    -- Проверка валидности контракта
    select pkg_contract.is_contract_valid(
                   p_id_contract => p_id_contract,
                   p_username => 'system'
           ) into v_is_valid;

    if not v_is_valid then
        raise notice 'Контракт [ID=%] не валиден!', p_id_contract;
        return -2;
    end if;

    -- Подготовка к удалению удалённых прав
    update missing_right
    set drop_flag = true
    where id_contract = p_id_contract;

    -- =========================================================================
    -- Set-based расчёт и вставка вместо построчного цикла
    -- =========================================================================
    WITH lic AS (
        select lrt.*, o.name as oip_name, rt.name as rt_name
        from vw_lic_rt lrt
                 join sync__klf_oip o on o.id = lrt.id_oip
                 join sync__klf_right_type rt on rt.id = lrt.id_right_type
        where lrt.id_contract = p_id_contract
          and lrt.status_mode in (0,1) --  только черновые/архивные контракты
    ),
         rights_raw AS (
             -- rn — суррогатный идентификатор строки: гарантирует, что даже при
             -- совпадении (id_lic_rights, features_hash) у двух разных исходных
             -- строк текст характеристик считается для каждой строки отдельно,
             -- как и было в цикле (там каждая итерация обрабатывалась независимо)
             select
                         row_number() over () as rn,
                         lower(lic.validity_period) as beg_date,
                         upper(lic.validity_period)-1 as end_date,
                         lic.id_org,
                         lic.id_oip,
                         lic.oip_name,
                         lic.id_contract,
                         lic.id_license,
                         lic.id_lic_rights,
                         lic.id_lic_rights_rt,
                         lic.id_right_type,
                         lic.rt_name,
                         lic.is_exclusive,
                         ntab.features_hash
             from lic
                     cross join lateral unnest(
                     pkg_contract.get_features_set_hash(lic.id_lic_rights, lic.id_feature_set,
                                                        lic.id_right_type, 'system',
                                                        lic.use_format, false, true)
                                         ) as ntab(features_hash)
         ),
         hash_pairs AS (
             select
                 rr.rn,
                 g.grp_idx,
                 split_part(p.pair, ':', 1)::int AS id_feature_category,
                 split_part(p.pair, ':', 2)::int AS id_feature_plain
             from rights_raw rr
                      cross join lateral unnest(string_to_array(rr.features_hash, ';')) WITH ORDINALITY AS g(grp, grp_idx)
                      cross join lateral unnest(string_to_array(g.grp, ',')) WITH ORDINALITY AS p(pair, pair_idx)
         ),
         hash_text AS (
             select
                 hp.rn,
                 string_agg(vf.name, ', ' ORDER BY hp.grp_idx, hp.id_feature_category) AS features_text
             from hash_pairs hp
                      join lateral (select distinct id_feature_category, id_feature_plain, name from vw_features
                                      where id_feature_category = hp.id_feature_category
                                        and id_feature_plain = hp.id_feature_plain) vf
                           ON true
             group by hp.rn
         ),
         rights_final AS (
             select
                 rr.id_org,
                 rr.id_oip,
                 rr.id_contract,
                 rr.id_license,
                 rr.id_lic_rights,
                 rr.id_lic_rights_rt,
                 rr.id_right_type,
                 rr.features_hash,
                 rr.beg_date,
                 rr.end_date,
                 rr.is_exclusive,
                 rr.id_right_type || '#' || rr.features_hash || '#1#'
                     || to_char(rr.beg_date, 'YYYY-MM-DD') || ':' || to_char(rr.end_date, 'YYYY-MM-DD')
                     || ':' || rr.is_exclusive AS right_key,
                 'ОИС: ' || rr.oip_name || E'\n'
                     || 'Способ использования: ' || rr.rt_name || E'\n'
                     || 'Характеристики: ' || coalesce(ht.features_text, '') || E'\n'
                     || 'Период: [' || to_char(rr.beg_date, 'YYYY-MM-DD') || '..' || to_char(rr.end_date, 'YYYY-MM-DD')
                     || '(' || case when rr.is_exclusive then 'E' else 'N' end || ')]' AS right_info
             from rights_raw rr
                      left join hash_text ht on ht.rn = rr.rn
         ),
         rights_dedup AS (
             -- защита от повторного изменения одной и той же строки в рамках
             -- ON CONFLICT DO UPDATE в пределах одного INSERT-а
             select distinct on (id_lic_rights, right_key) *
             from rights_final
             order by id_lic_rights, right_key
         ),
         upserted AS (
             INSERT INTO missing_right (id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                                        features_hash, beg_date, end_date, is_exclusive, right_key, missing_flag, missing_right_info,
                                        drop_flag
                 )
                 select id_org, id_oip, id_contract, id_license, id_lic_rights,
                        id_lic_rights_rt, id_right_type, features_hash, beg_date,
                        end_date, is_exclusive, right_key, -1, right_info,
                        false
                 from rights_dedup
                 ON CONFLICT (id_lic_rights, right_key) DO UPDATE
                     SET missing_flag = EXCLUDED.missing_flag,
                         drop_flag  = false,
                         updated_at = current_timestamp
                 RETURNING id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                     features_hash, beg_date, end_date, is_exclusive, right_key, missing_right_info
         )
    INSERT INTO check_right_outbox (id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                                    features_hash, beg_date, end_date, is_exclusive, right_key, missing_right_info
    )
    select id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
           features_hash, beg_date, end_date, is_exclusive, right_key, missing_right_info
    from upserted;

    -- удаляем удалённые права
    delete from missing_right
        where id_contract = p_id_contract
          and drop_flag;

    return 1; -- нормальное завершение
END;
$$;

-- =============================================================================
--
-- Аналогично check_rights_for_contract:
--   1) один set-based запрос: расчёт хэшей,
--      парсинг характеристик и формирование текста — одним проходом для всех
--      найденных прав, вставка в обе таблицы — двумя INSERT (второй читает
--      результат первого через RETURNING в data-modifying CTE);
--   2) добавлен DISTINCT ON (id_lic_rights, right_key) перед вставкой — защита
--      от ошибки "ON CONFLICT DO UPDATE command cannot affect row a second
--      time" в пределах одного INSERT-а;
-- =============================================================================

CREATE OR REPLACE FUNCTION pkg_contract.check_dependant_rights(
    p_id_org integer,
    p_id_oip integer,
    p_id_right_type integer
)
    returns integer
    security definer
    set search_path = rightsflow
    volatile
    parallel unsafe
    language plpgsql
as
$$
BEGIN

    -- Проверка входных параметров
    if p_id_org is null then
        raise notice 'Не указана организация!';
        return 0;
    end if;
    if p_id_oip is null then
        raise notice 'Не указан ОИС!';
        return 0;
    end if;
    if p_id_right_type is null then
        raise notice 'Не указан способ использования!';
        return 0;
    end if;

    -- =========================================================================
    -- Set-based расчёт и вставка
    -- =========================================================================
    WITH lic AS (
        select lrt.*, o.name as oip_name, rt.name as rt_name
        from vw_lic_rt lrt
                 join sync__klf_oip o on o.id = lrt.id_oip
                 join sync__klf_right_type rt on rt.id = lrt.id_right_type
        where lrt.id_oip = p_id_oip
          and (lrt.id_org = p_id_org or lrt.id_org_party = p_id_org)
          and lrt.id_right_type in (
            with recursive rt(id, id_parent) as (
                select id, id_parent from sync__klf_right_type where id = p_id_right_type
                union all
                select r.id, r.id_parent from sync__klf_right_type r
                                                  join rt rt on r.id_parent = rt.id
            )
            select id from rt
        )
          and lrt.status_mode in (0,1) --  только черновые/архивные контракты
          and lrt.in_out in ('eS','iS','iP')
    ),
         rights_raw AS (
             -- rn — суррогатный идентификатор строки: гарантирует, что текст
             -- характеристик считается для каждой исходной строки независимо,
             -- даже если у двух строк совпадут (id_lic_rights, features_hash)
             select
                         row_number() over () as rn,
                         lower(lic.validity_period) as beg_date,
                         upper(lic.validity_period)-1 as end_date,
                         lic.id_org,
                         lic.id_oip,
                         lic.oip_name,
                         lic.id_contract,
                         lic.id_license,
                         lic.id_lic_rights,
                         lic.id_lic_rights_rt,
                         lic.id_right_type,
                         lic.rt_name,
                         lic.is_exclusive,
                         ntab.features_hash
             from lic
                      cross join lateral unnest(
                     pkg_contract.get_features_set_hash(lic.id_lic_rights, lic.id_feature_set,
                                                        lic.id_right_type, 'system',
                                                        lic.use_format, false, true)
                                         ) as ntab(features_hash)
         ),
         hash_pairs AS (
             select
                 rr.rn,
                 g.grp_idx,
                 split_part(p.pair, ':', 1)::int AS id_feature_category,
                 split_part(p.pair, ':', 2)::int AS id_feature_plain
             from rights_raw rr
                      cross join lateral unnest(string_to_array(rr.features_hash, ';')) WITH ORDINALITY AS g(grp, grp_idx)
                      cross join lateral unnest(string_to_array(g.grp, ',')) WITH ORDINALITY AS p(pair, pair_idx)
         ),
         hash_text AS (
             select
                 hp.rn,
                 string_agg(vf.name, ', ' ORDER BY hp.grp_idx, hp.id_feature_category) AS features_text
             from hash_pairs hp
                      join lateral (select distinct id_feature_category, id_feature_plain, name from vw_features
                                    where id_feature_category = hp.id_feature_category
                                      and id_feature_plain = hp.id_feature_plain) vf
                           ON true
             group by hp.rn
         ),
         rights_final AS (
             select
                 rr.id_org,
                 rr.id_oip,
                 rr.id_contract,
                 rr.id_license,
                 rr.id_lic_rights,
                 rr.id_lic_rights_rt,
                 rr.id_right_type,
                 rr.features_hash,
                 rr.beg_date,
                 rr.end_date,
                 rr.is_exclusive,
                 rr.id_right_type || '#' || rr.features_hash || '#1#'
                     || to_char(rr.beg_date, 'YYYY-MM-DD') || ':' || to_char(rr.end_date, 'YYYY-MM-DD')
                     || ':' || rr.is_exclusive AS right_key,
                 'ОИС: ' || rr.oip_name || E'\n'
                     || 'Способ использования: ' || rr.rt_name || E'\n'
                     || 'Характеристики: ' || coalesce(ht.features_text, '') || E'\n'
                     || 'Период: [' || to_char(rr.beg_date, 'YYYY-MM-DD') || '..' || to_char(rr.end_date, 'YYYY-MM-DD')
                     || '(' || case when rr.is_exclusive then 'E' else 'N' end || ')]' AS right_info
             from rights_raw rr
                      left join hash_text ht on ht.rn = rr.rn
         ),
         rights_dedup AS (
             -- защита от повторного изменения одной и той же строки в рамках
             -- ON CONFLICT DO UPDATE в пределах одного INSERT-а
             select distinct on (id_lic_rights, right_key) *
             from rights_final
             order by id_lic_rights, right_key
         ),
         upserted AS (
             INSERT INTO missing_right (id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                                        features_hash, beg_date, end_date, is_exclusive, right_key, missing_flag, missing_right_info
                 )
                 select id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                        features_hash, beg_date, end_date, is_exclusive, right_key, -1, right_info
                 from rights_dedup
                 ON CONFLICT (id_lic_rights, right_key) DO UPDATE
                     SET missing_flag = EXCLUDED.missing_flag,
                         updated_at = current_timestamp
                 RETURNING id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                     features_hash, beg_date, end_date, is_exclusive, right_key, missing_right_info
         )
    INSERT INTO check_right_outbox (id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                                    features_hash, beg_date, end_date, is_exclusive, right_key, missing_right_info
    )
    select id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
           features_hash, beg_date, end_date, is_exclusive, right_key, missing_right_info
    from upserted;

    return 1; -- нормальное завершение
END;
$$;

DROP FUNCTION IF EXISTS pkg_sync.sync_missing_right(bigint, integer, integer, integer, character varying, date, date, boolean,
                                                    character varying, text, integer);

CREATE OR REPLACE FUNCTION pkg_sync.sync_missing_right(
    p_id_lic_rights bigint,
    p_id_org integer,
    p_id_oip integer,
    p_id_right_type integer,
    p_feature_hash character varying,
    p_beg_date date,
    p_end_date date,
    p_is_exclusive boolean,
    p_right_key character varying,
    p_missing_right_info text,
    p_missing_flag integer
)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
DECLARE
    v_id_contract bigint;
    v_id_license bigint;
    v_id_lic_rights_rt bigint;
    v_oip_name character varying;
    v_missing_info text;
BEGIN
    IF p_missing_flag = 0 THEN
        DELETE FROM missing_right WHERE id_lic_rights = p_id_lic_rights and right_key = p_right_key;
    ELSE

        select name into v_oip_name from sync__klf_oip where id = p_id_oip;
        if v_oip_name is null then
            raise notice 'ОИС не найден.. id_oip=%', p_id_oip;
            return 0; -- невозможно сохранить -> ОИС не найден
        end if;

        v_missing_info := 'ОИС: ' || v_oip_name || chr(10) || p_missing_right_info;

        select id_contract, id_license, id_lic_rights_rt
        into v_id_contract, v_id_license, v_id_lic_rights_rt
        from vw_lic_rt
        where id_oip = p_id_oip
          and ((id_org = p_id_org and in_out in ('eS','iS'))
            or (id_org_party = p_id_org and in_out = 'iP'))
          and id_lic_rights = p_id_lic_rights
          and id_right_type = p_id_right_type
          and status_mode = 2
        group by id_contract, id_license, id_lic_rights_rt;

        if v_id_contract is null or v_id_license is null or v_id_lic_rights_rt is null then
            raise notice 'данные контракта не найдены.. id_oip=%, id_org=%, id_lic_rights=%, id_right_type=%',
                p_id_oip, p_id_org, p_id_lic_rights, p_id_right_type;
            return -1; -- невозможно сохранить -> данные контракта не найдены
        end if;

        INSERT INTO missing_right (id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                                   features_hash, beg_date, end_date, is_exclusive, right_key, missing_flag, missing_right_info
        ) VALUES (p_id_org, p_id_oip, v_id_contract, v_id_license, p_id_lic_rights,
                  v_id_lic_rights_rt, p_id_right_type, p_feature_hash, p_beg_date,
                  p_end_date, p_is_exclusive, p_right_key, p_missing_flag,
                  v_missing_info
                 )
        ON CONFLICT (id_lic_rights, right_key) DO UPDATE
            SET missing_flag = EXCLUDED.missing_flag,
                updated_at = current_timestamp;

    END IF;
    RETURN 1;
END;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_sync.sync_checking_right(
    p_id_org integer,
    p_id_oip integer,
    p_id_contract bigint,
    p_id_license bigint,
    p_id_lic_rights bigint,
    p_id_lic_rights_rt bigint,
    p_id_right_type integer,
    p_feature_hash character varying,
    p_beg_date date,
    p_end_date date,
    p_is_exclusive boolean,
    p_right_key character varying,
    p_missing_right_info text,
    p_missing_flag integer
)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_missing_flag = 0 THEN
        DELETE FROM missing_right WHERE id_lic_rights = p_id_lic_rights and right_key = p_right_key;
    ELSE

        INSERT INTO missing_right (id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                                   features_hash, beg_date, end_date, is_exclusive, right_key, missing_flag, missing_right_info
        ) VALUES (p_id_org, p_id_oip, p_id_contract, p_id_license, p_id_lic_rights,
                  p_id_lic_rights_rt, p_id_right_type, p_feature_hash, p_beg_date,
                  p_end_date, p_is_exclusive, p_right_key, p_missing_flag,
                  p_missing_right_info
                 )
        ON CONFLICT (id_lic_rights, right_key) DO UPDATE
            SET missing_flag = EXCLUDED.missing_flag,
                updated_at = current_timestamp;

    END IF;
    RETURN 1;
END;
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.make_change_buffer(
    IN p_action character varying,
    IN p_username character varying,
    IN p_id_contract bigint DEFAULT NULL::bigint,
    IN p_id_license bigint DEFAULT NULL::bigint,
    IN p_id_lic_oip bigint DEFAULT NULL::bigint,
    IN p_id_lic_rights bigint DEFAULT NULL::bigint,
    IN p_id_feature_set bigint DEFAULT NULL::bigint,
    IN p_id_rt_feature bigint DEFAULT NULL::bigint)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
declare
    v_id_contract bigint;
    v_id_license bigint;
    v_entity_name varchar;
    v_id_org integer;
    v_id_entity bigint;
    v_data jsonb;
begin
    -- Обработка контракта
    if p_id_contract is not null then
        select id into v_id_contract from contract where id = p_id_contract;

        if v_id_contract is null then
            raise exception 'Контракт [ID=%] не найден!', p_id_contract
                using errcode = '20120';
        end if;

        v_entity_name := 'CONTRACT';
        v_id_entity := v_id_contract;

        select jsonb_agg(jsonb_build_object('id_contract', c.id, 'id_org', c.id_org, 'id_oip', lo.id_oip))
        into v_data
        from contract c
                 join license l on l.id_contract = c.id
                 join license_oip lo on lo.id_license = l.id
        where c.id = v_id_contract;

        -- Обработка лицензии и связанных сущностей
    ELSE
        -- Определение лицензии и типа сущности
        if p_id_license is not null then
            v_id_license := p_id_license;
            v_entity_name := 'LICENSE';
            v_id_entity := p_id_license;

        elsif p_id_lic_oip is not null then
            select id_license into v_id_license from license_oip where id = p_id_lic_oip;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_OIP';
                v_id_entity := p_id_lic_oip;
            end if;

        elsif p_id_lic_rights is not null then
            select id_license into v_id_license from license_rights where id = p_id_lic_rights;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_RT';
                v_id_entity := p_id_lic_rights;
            end if;

        elsif p_id_feature_set is not null then
            select lr.id_license into v_id_license
            from license_rt_feature_set fs
                     join license_rights lr on lr.id = fs.id_lic_rights
            where fs.id = p_id_feature_set;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_RT_FEATURE_SET';
                v_id_entity := p_id_feature_set;
            end if;
        elsif p_id_rt_feature is not null then
            select lr.id_license into v_id_license
            from license_rt_features lf
                     join license_rights lr on lr.id = lf.id_lic_rights
            where lf.id = p_id_rt_feature;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_RT_FEATURE';
                v_id_entity := p_id_rt_feature;
            end if;
        end if;

        -- Валидация и получение данных лицензии
        if v_id_license is not null then
            -- проверка существования лицензии
            if not exists (select 1 from license where id = v_id_license) then
                raise exception 'Лицензия [ID=%] не найдена!', v_id_license
                    using errcode = 20120;
            end if;

            -- получение id_org
            select c.id_org, c.id into v_id_org, v_id_contract
            from contract c
                     join license l on l.id_contract = c.id
            where l.id = v_id_license;

            -- Формирование данных в зависимости от типа сущности
            if v_entity_name = 'LICENSE_OIP' then
                select jsonb_agg(jsonb_build_object('id_contract', v_id_contract, 'id_org', v_id_org, 'id_oip', id_oip))
                into v_data
                from license_oip
                where id = p_id_lic_oip;
            else
                select jsonb_agg(jsonb_build_object('id_contract', v_id_contract, 'id_org', v_id_org, 'id_oip', lo.id_oip))
                into v_data
                from license_oip lo
                where lo.id_license = v_id_license;
            end if;
        end if;
    end if;

    -- Выполняем проверку прав контракта для вставок и обновлений
    if p_action != 'DELETE' then
        perform pkg_contract.check_rights_for_contract(
                p_id_contract => v_id_contract
        );
    end if;

    -- Вставка/обновление буфера изменений
    if v_data is not null then
        insert into contract_change_buffer (name_entity, id_entity, action, data, status, created_by)
        values (v_entity_name, v_id_entity, p_action, v_data, 'NEW', p_username)
        on conflict (name_entity, id_entity, action)
            do update set
                          data = EXCLUDED.data,
                          status = 'NEW',
                          updated_by = p_username,
                          updated_at = current_timestamp;
    end if;
end;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_contract.upd_contract(
    p_id bigint,
    p_guid character varying,
    p_num character varying,
    p_id_org character varying,
    p_id_org_party character varying,
    p_beg_date date,
    p_end_date date,
    p_contract_date date,
    p_id_contract_type integer,
    p_in_out character,
    p_description character varying,
    p_id_currency integer,
    p_id_currency_payment integer,
    p_unf_price numeric,
    p_unf_vat_rate numeric,
    p_unf_vat_amount numeric,
    p_unf_total_amount numeric,
    p_id_contract_vp integer,
    p_username character varying,
    p_bypass boolean DEFAULT false)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_validity_period daterange;
    v_id_contract_type integer;
    v_old contract%rowtype;
    v_id_org_party integer;
    v_id_org integer;
BEGIN

    -- Проверка доступа к контракту перед изменением
    PERFORM pkg_contract.check_contract_org_access(p_id, p_username, p_bypass);

    select * into v_old from contract where id = p_id for update;

    v_validity_period := v_old.validity_period;
    if p_beg_date is not null or p_end_date is not null then
        v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    end if;

    v_id_contract_type := pkg_contract.get_old_contract_type(p_id_contract_type, v_old.id_contract_type);

    if coalesce(p_in_out, v_old.in_out) in ('eP', 'eS') then
        v_id_org_party := null;
    else
        v_id_org_party := null;
        if p_id_org_party is not null then
            v_id_org_party := pkg_contract.get_org_id(p_id_org_party);
        end if;
    end if;

    if p_id_org is not null then
        v_id_org := pkg_contract.get_org_id(p_id_org);
    end if;

    update contract
    set
        guid = p_guid,
        num = coalesce(p_num, num),
        id_org = coalesce(v_id_org, id_org),
        id_org_party = v_id_org_party,
        validity_period = v_validity_period,
        contract_date = p_contract_date,
        id_contract_type = v_id_contract_type,
        in_out = coalesce(p_in_out, in_out),
        description = p_description,
        id_currency = p_id_currency,
        id_currency_payment = p_id_currency_payment,
        unf_price = p_unf_price,
        unf_vat_rate = p_unf_vat_rate,
        unf_vat_amount = p_unf_vat_amount,
        unf_total_amount = p_unf_total_amount,
        id_contract_vp = p_id_contract_vp,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    if v_old.id_org != coalesce(v_id_org, v_old.id_org) or coalesce(v_old.id_org_party, 1) != coalesce(v_id_org_party, 1) then
        call pkg_contract.make_change_buffer(p_action => 'UPDATE', p_username => p_username, p_id_contract => p_id);
    end if;

    return p_id;
END;
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.del_license_rt_features(
    IN p_id bigint,
    IN p_username character varying,
    IN p_bypass boolean DEFAULT false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_rec RECORD;
    v_id_contract bigint;
BEGIN
    select * into v_rec from license_rt_features where id = p_id;

    -- Проверка доступа
    if FOUND then
        PERFORM pkg_contract.check_feature_set_org_access(v_rec.id_feature_set, p_username, p_bypass);
    else
        raise exception 'Характеристики набора не существует: [ID=%]', p_id;
    end if;

    select l.id_contract into v_id_contract from license l
    join license_rights lr on lr.id_license = l.id
    where lr.id = v_rec.id_lic_rights;

    call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_rt_feature => p_id);
    delete from license_rt_features where id = p_id;
    perform pkg_contract.check_rights_for_contract(p_id_contract => v_id_contract);
END;
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.del_license_oip(
    IN p_id bigint,
    IN p_username character varying,
    IN p_bypass boolean DEFAULT false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_rec RECORD;
    v_id_contract bigint;
BEGIN
    select * into v_rec from license_oip where id = p_id;

    -- Проверка доступа
    if FOUND then
        PERFORM pkg_contract.check_license_org_access(v_rec.id_license, p_username, p_bypass);
    else
        raise exception 'ОИС лицензии не существует: [ID=%]', p_id;
    end if;

    select l.id_contract into v_id_contract from license l where l.id = v_rec.id_license;

    call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => p_id);
    delete from license_oip where id = p_id;
    perform pkg_contract.check_rights_for_contract(p_id_contract => v_id_contract);

END;
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.del_license_oip_by_lic(
    IN p_id_license bigint,
    IN p_username character varying,
    IN p_bypass boolean DEFAULT false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_rec record;
    v_id_contract bigint;
BEGIN
    if not exists (select 1 from license where id = p_id_license) then
        raise exception 'Лицензия не найдена! [ID = %]', p_id_license using errcode = '20120';
    end if;

    -- Проверка доступа
    PERFORM pkg_contract.check_license_org_access(p_id_license, p_username, p_bypass);

    for v_rec in select * from license_oip where id_license = p_id_license loop
            call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => v_rec.id);
        end loop;

    delete from license_oip where id_license = p_id_license;

    select l.id_contract into v_id_contract from license l where l.id = p_id_license;
    perform pkg_contract.check_rights_for_contract(p_id_contract => v_id_contract);
END;
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.del_license_oip_by_root(
    IN p_id_license bigint,
    IN p_id_root_oip bigint,
    IN p_username character varying,
    IN p_bypass boolean DEFAULT false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_rec record;
    v_id_contract bigint;
BEGIN
    if not exists (select 1 from license where id = p_id_license) then
        raise exception 'Лицензия не найдена! [id = %]', p_id_license using errcode = 20120;
    end if;

    -- Проверка доступа
    PERFORM pkg_contract.check_license_org_access(p_id_license, p_username, p_bypass);

    if not exists (select 1 from sync__klf_oip where id = p_id_root_oip) then
        raise exception 'Корневой ОИС не найден! [id = %]', p_id_root_oip using errcode = 20120;
    end if;
    for v_rec in select * from license_oip where id_license = p_id_license and id_root_oip = p_id_root_oip loop
            call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => v_rec.id);
        end loop;

    delete from license_oip where id_license = p_id_license and id_root_oip = p_id_root_oip;

    select l.id_contract into v_id_contract from license l where l.id = p_id_license;
    perform pkg_contract.check_rights_for_contract(p_id_contract => v_id_contract);
END;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_contract.upd_license(
    p_id bigint,
    p_guid character varying,
    p_num character varying,
    p_name character varying,
    p_id_lic_format bigint,
    p_price numeric,
    p_vat_rate numeric,
    p_vat_amount numeric,
    p_total_amount numeric,
    p_beg_date date,
    p_end_date date,
    p_description character varying,
    p_username character varying,
    p_bypass boolean DEFAULT false)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

DECLARE
    v_contract contract%rowtype;
    v_license license%rowtype;
    v_validity_period daterange;
BEGIN

    -- Проверка доступа через цепочку license → contract
    PERFORM pkg_contract.check_license_org_access(p_id, p_username, p_bypass);

    select c.* into v_contract from contract c, license l where l.id_contract = c.id and l.id = p_id;
    select l.* into v_license from license l where l.id = p_id for update;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');

    if isempty(v_contract.validity_period * v_validity_period) then
        raise exception 'Лицензия не пересекается с периодом договора!'
            using errcode = '20105';
        -- Отключил по требованию заказчика
        --else
        --    v_validity_period := v_contract.validity_period * v_validity_period;
    end if;

    if coalesce(v_license.id_lic_format, -1) != coalesce(p_id_lic_format, -1) then
        call pkg_contract.make_change_buffer(p_action => 'UPDATE', p_username => p_username, p_id_license => p_id);
    end if;

    update license
    set
        id_lic_format = p_id_lic_format,
        guid = p_guid,
        num = coalesce(p_num, num),
        name = p_name,
        price = coalesce(p_price, price),
        vat_rate = coalesce(p_vat_rate, vat_rate),
        vat_amount = coalesce(p_vat_amount, vat_amount),
        total_amount = coalesce(p_total_amount, total_amount),
        validity_period = v_validity_period,
        description = p_description,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;
    /* Отключил по требованию заказчика
    update license_rt_feature_set fs
    set validity_period = case
        -- Для пересекающихся feature_set - пересечение с новым периодом лицензии
                              when not (fs.validity_period <@ ul.validity_period)
                                  and (fs.validity_period && ul.validity_period) then
                                  fs.validity_period * ul.validity_period
        -- Для остальных случаев - просто новый период лицензии
                              else ul.validity_period
        end
    from license ul
             join license_rights lr on lr.id_license = ul.id
    where fs.id_lic_rights = lr.id
      and ul.id = p_id;
    */
    if v_license.validity_period != v_validity_period then
       perform pkg_contract.check_rights_for_contract(p_id_contract => v_contract.id);
    end if;

    return p_id;
END;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_contract.upd_license_rt_feature_set(
    p_id bigint,
    p_id_lic_rights bigint,
    p_is_exclusive boolean,
    p_is_use_right boolean,
    p_is_sub_license boolean,
    p_beg_date date,
    p_end_date date,
    p_username character varying,
    p_bypass boolean DEFAULT false)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$



DECLARE
    v_validity_period daterange;
    v_license license%rowtype;
    v_lic_rights  license_rights%rowtype;
    v_feature_set license_rt_feature_set%rowtype;
BEGIN

    if p_id_lic_rights is null then
        raise exception 'Ошибка обновления набора характеристик! Не указан идентификатор права лицензии (p_id_lic_rights)!'
            using errcode = '20111';
    end if;

    -- Проверка доступа через цепочку feature_set → license_rights → license → contract
    PERFORM pkg_contract.check_feature_set_org_access(p_id, p_username, p_bypass);

    select * into v_lic_rights from license_rights where id = p_id_lic_rights;
    select * into v_license from license where id = v_lic_rights.id_license;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    if isempty(v_license.validity_period * v_validity_period) then
        raise exception 'Период набора характеристик не пересекается с периодом лицензии!'
            using errcode = '20110';
        -- Отключил по требованию заказчика
        --else
        --    v_validity_period := v_license.validity_period * v_validity_period;
    end if;

    update license_rt_feature_set
    set
        is_exclusive = coalesce(p_is_exclusive, is_exclusive),
        is_use_right = coalesce(p_is_use_right, is_use_right),
        is_sub_license = coalesce(p_is_sub_license, is_sub_license),
        validity_period = v_validity_period,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    select * into v_feature_set from license_rt_feature_set where id = p_id for update;
    if v_feature_set.is_exclusive != coalesce(p_is_exclusive, v_feature_set.is_exclusive) or
       v_feature_set.validity_period != v_validity_period then
        call pkg_contract.make_change_buffer(p_action => 'UPDATE', p_username => p_username, p_id_feature_set => p_id);
    end if;

    return p_id;
END;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_contract.upd_contract_status(
    p_id_contract bigint,
    p_status_code character varying,
    p_status_1c character varying,
    p_username character varying,
    p_bypass boolean DEFAULT false)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_contract           contract%rowtype;
    v_curr_status        lov_contract_status%rowtype;   -- текущий статус договора
    v_target_status      lov_contract_status%rowtype;   -- итоговый статус, в который переводим
    v_target_code        lov_contract_status.code%type; -- вычисленный целевой код
    v_input_code         lov_contract_status.code%type := upper(p_status_code);
    v_warning            contract.warning%type;
    v_need_disable_check boolean := false;
BEGIN
    -- Проверка доступа
    PERFORM pkg_contract.check_contract_org_access(p_id_contract, p_username, p_bypass);

    -- 1) Получаем договор + его текущий статус и сразу блокируем строку
    select c.* into v_contract from contract c
    where c.id = p_id_contract
        for update;

    if not found then
        raise exception 'Контракт [ID=%] не найден!', p_id_contract
            using errcode = '20120';
    end if;

    select cs.* into v_curr_status from contract c
      join lov_contract_status cs on cs.id = c.id_contract_status
    where c.id = p_id_contract;

    -- 2) Проверяем, что запрошенный статус существует для данного типа договора
    if v_input_code is not null then
        select cs.*
        into v_target_status
        from lov_contract_status cs
        where cs.code = v_input_code
          and cs.id_contract_type = v_contract.id_contract_type;

        if not found then
            raise exception 'Статус [code=%] не найден!', p_status_code
                using errcode = '20120';
        end if;
    end if;

    -- 3) Бизнес-логика переходов: определяем конечный код статуса и спец флаги
    if v_input_code = 'DRAFT' and v_curr_status.code in ('ARCHIVE', 'APPROVED') then
        v_target_code := 'DRAFT';
        v_need_disable_check := true;

    elsif v_input_code = 'ARCHIVE' and v_curr_status.code = 'DRAFT' then
        if pkg_contract.is_contract_valid(p_id_contract => p_id_contract, p_username => p_username) then
            v_target_code := 'ARCHIVE';
        else
            select warning into v_warning from contract where id = p_id_contract;
            raise exception 'Невозможно изменить статус контракта [ID=%] на ARCHIVE. Контракт не валиден! %', p_id_contract, v_warning
                using errcode = '20121';
        end if;

    elsif v_input_code = 'APPROVED' and v_curr_status.code = 'DRAFT' then
        if pkg_contract.is_contract_valid(p_id_contract => p_id_contract, p_username => p_username) then
            -- Сделка с «родственником» не может быть утверждена -> в ARCHIVE
            if v_contract.id_contract_type = 1 and v_contract.id_sibling is not null then
                v_target_code := 'ARCHIVE';
            else
                v_target_code := 'APPROVED';
            end if;
        else
            select warning into v_warning from contract where id = p_id_contract;
            raise exception 'Невозможно изменить статус контракта [ID=%] на APPROVED. Контракт не валиден! %', p_id_contract, v_warning
                using errcode = '20121';
        end if;

    else
        -- Любые иные запросы: либо уже в нужном статусе, либо переход запрещён
        if v_curr_status.code = coalesce(v_input_code, v_curr_status.code) then
            perform set_config('rf.disable_status_check', 'true', true);
            update contract
            set status_1c  = p_status_1c,
                updated_at = current_timestamp,
                updated_by = p_username
            where id = p_id_contract;
            perform set_config('rf.disable_status_check', 'false', true);
            return p_id_contract;
        else
            raise exception 'Контракт [ID=%] находится в состоянии [%], операция невозможна!',
                p_id_contract, v_curr_status.name
                using errcode = '20121';
        end if;
    end if;

    -- 4) Если целевой код отличается от текущего — выполняем переход
    if v_target_code is not null and v_target_code <> v_curr_status.code then
        -- Обновляем объект v_target_status под финальный код (мог измениться с APPROVED -> ARCHIVE)
        if v_target_code <> v_target_status.code then
            select cs.*
            into v_target_status
            from lov_contract_status cs
            where cs.code = v_target_code
              and cs.id_contract_type = v_contract.id_contract_type;
        end if;

        -- Если отменили/применили APPROVE, нужно пересчитать права для всех ОИС контракта
        if (v_curr_status.mode = 2 and v_target_status.mode = 0) or
           (v_curr_status.mode = 0 and v_target_status.mode = 2) then
           call pkg_contract.make_change_buffer('UPDATE', p_username, p_id_contract);
        end if;

        -- Создаем событие
        call pkg_contract.make_contract_outbox_event(
                p_id_contract => p_id_contract,
                p_status_mode => case when (v_curr_status.mode = 2 and v_target_status.mode = 0) then 2 else v_target_status.mode end,
                p_username    => p_username
             );

        -- Для отката в DRAFT из ARCHIVE/APPROVED временно отключаем проверку
        if v_need_disable_check then
            perform set_config('rf.disable_status_check', 'true', true);
        end if;

        update contract
        set id_contract_status = pkg_contract.get_def_contract_status(
                p_id_contract_type   => v_contract.id_contract_type,
                p_contract_status_code => v_target_code),
            status_1c         = p_status_1c,
            updated_at        = current_timestamp,
            updated_by        = p_username
        where id = p_id_contract;

        if v_need_disable_check then
            perform set_config('rf.disable_status_check', 'false', true);
        end if;
    end if;

    return p_id_contract;
END;
$BODY$;
