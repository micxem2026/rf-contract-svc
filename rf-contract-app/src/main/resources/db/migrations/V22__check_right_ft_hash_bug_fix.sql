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
                        v_available_ids := array['XXX'];
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

                -- fallback: если v_available_ids пуст или null
                if v_available_ids is null or array_length(v_available_ids, 1) is null then
                    v_available_ids := array['XXX'];
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
                        v_available_ids := array['XXX'];
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
                    v_available_ids := array['XXX'];
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

    return case when (array_to_string(v_result,'|') ~ 'XXX') then null else v_result end;
end;
$$;