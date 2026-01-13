create schema if not exists pkg_pge;
alter schema pkg_pge owner to rightsflow;

create or replace function pkg_pge.get_property_groups(
    p_id_obj integer
)
    returns table (
                      id   integer,
                      code character varying,
                      name character varying
                  )
    security definer
    set search_path = rightsflow
as
$$
begin
    return query
        select distinct pg.id, pg.code, pg.name name_pg from sync__lov_pge_property_group pg
          join sync__lov_pge_pg_to_obj pgo on pgo.code_pg = pg.code
            where pgo.id_obj = p_id_obj
              and pg.svc_id = 1;
end;
$$ language plpgsql;

create or replace function pkg_pge.is_numeric(str text)
    returns boolean
    immutable -- ВАЖНО: делаем функцию immutable для оптимизации
    strict -- возвращает NULL если вход NULL
    security definer
    set search_path = rightsflow
as $$
begin
    -- Быстрая проверка через регулярное выражение
    if str !~ '^-?[0-9]+\.?[0-9]*$' then
        return false;
    end if;

    -- Точная проверка
    perform str::numeric;
    return true;
exception
    when others then
        return false;
end;
$$ language plpgsql;

create or replace function pkg_pge.get_pg_data(
    p_code_pg character varying,
    p_id_entities bigint[],
    p_username character varying DEFAULT 'admin'::character varying
)
    returns table(
                     id_entity bigint,
                     id integer,
                     id_pgl integer,
                     id_property integer,
                     pg_order integer,
                     name_prop character varying,
                     code_prop character varying,
                     id_prop_type integer,
                     name_prop_type character varying,
                     property_value character varying,
                     use_multi_select boolean,
                     display_value text
                 )
    security definer
    SET search_path = rightsflow
    language plpgsql
as
$$
declare
    v_def_id_curr integer;
    v_layer_query text;
    v_catalog_sql text;
    v_rec record;
begin
    -- Валидация входных параметров
    if p_id_entities is null or array_length(p_id_entities, 1) = 0 then
        raise exception 'Массив сущностей пуст'
            using errcode = '20155';
    end if;

    -- Получаем валюту по умолчанию
    select c.id into strict v_def_id_curr
    from lov_currency c
    where c.def = true;

    -- Получаем запрос для определения слоев
    select layer_sel_query into strict v_layer_query
    from sync__lov_pge_property_group
    where code = p_code_pg;

    -- Удаляем таблицы если существуют и создаем заново
    drop table if exists temp_entity_layers;
    drop table if exists temp_catalogs;

    -- Создаем временную таблицу для слоев сущностей
    create temp table if not exists temp_entity_layers (id_entity bigint, id_pgl integer) on commit drop;

    -- Заполняем слои для каждой сущности
    for v_rec in
        select unnest(p_id_entities) as id_entity
        loop
            execute format(
                    'insert into temp_entity_layers (id_entity, id_pgl)
                     select $1, l.id
                     from sync__lov_pge_pg_layer l
                     join sync__lov_pge_property_group pg on pg.id = l.id_pg
                     where l.sel_value::varchar[] && (%s)::varchar[]
                     and pg.code = $2', v_layer_query
                    ) using v_rec.id_entity, p_code_pg;
        end loop;

    -- Создаем индекс для оптимизации
    create index if not exists temp_el_entity_idx on temp_entity_layers(id_entity);
    create index if not exists temp_el_pgl_idx on temp_entity_layers(id_pgl);

    -- Создаем временную таблицу для справочников
    create temp table if not exists temp_catalogs (id_obj integer, id integer, name text) on commit drop;

    -- Динамически загружаем все необходимые справочники
    for v_rec in
        select distinct
            pt.id_obj,
            lo.table_name,
            lo.where_filter
        from temp_entity_layers el
                 join sync__lov_pge_pgl_dtl pgld on pgld.id_pgl = el.id_pgl
                 join sync__lov_pge_property p on p.id = pgld.id_property
                 join sync__lov_pge_prop_type pt on pt.id = p.id_prop_type
                 join sync__lov_object lo on lo.id = pt.id_obj
        loop
            v_catalog_sql := format(
                    'insert into temp_catalogs (id_obj, id, name)
                     select %s, id, name from %s %s',
                    v_rec.id_obj,
                    v_rec.table_name,
                    case when v_rec.where_filter is not null
                             then 'where ' || v_rec.where_filter
                         else ''
                        end
                             );
            execute v_catalog_sql;
        end loop;

    -- Создаем индекс для справочников
    create index if not exists temp_cat_obj_id_idx on temp_catalogs(id_obj, id);

    -- Возвращаем результат
    return query
        with property_values as (
            select
                el.id_entity,
                pgld.id,
                pgld.id_pgl,
                pgld.id_property,
                pgld.pg_order,
                p.name as name_prop,
                p.code as code_prop,
                p.id_prop_type,
                pt.name as name_prop_type,
                pt.id_obj,
                pt.use_multi_select,
                case
                    when coalesce(pp.property_value, pgld.default_value) = '{DEF_CURRENCY}'
                        then v_def_id_curr::varchar
                    else coalesce(pp.property_value, pgld.default_value)
                    end as property_value
            from temp_entity_layers el
                     join sync__lov_pge_pgl_dtl pgld on pgld.id_pgl = el.id_pgl
                     join sync__lov_pge_property p on p.id = pgld.id_property
                     join sync__lov_pge_prop_type pt on pt.id = p.id_prop_type
                     left join pge_props pp
                               on pp.id_pgl = el.id_pgl
                                   and pp.id_property = pgld.id_property
                                   and pp.id_entity = el.id_entity
        )
        select
            pv.id_entity,
            pv.id,
            pv.id_pgl,
            pv.id_property,
            pv.pg_order,
            pv.name_prop,
            pv.code_prop,
            pv.id_prop_type,
            pv.name_prop_type,
            pv.property_value,
            pv.use_multi_select,
            case
                when pv.use_multi_select and pv.id_obj is not null then
                    coalesce(
                            (select string_agg(tc.name, ',' order by ordinality)
                             from unnest(pv.property_value::integer[]) with ordinality as val(id, ordinality)
                                      join temp_catalogs tc
                                           on tc.id = val.id
                                               and tc.id_obj = pv.id_obj
                            )::text,
                            pv.property_value::text
                    )
                when not pv.use_multi_select and pv.id_obj is not null then
                    coalesce(
                            (select tc.name
                             from temp_catalogs tc
                             where tc.id_obj = pv.id_obj
                               and pv.property_value ~ '^\d+$'
                               and tc.id = pv.property_value::integer
                            )::text,
                            pv.property_value::text
                    )
                else
                    pv.property_value::text
                end as display_value
        from property_values pv
        order by pv.id_entity, pv.pg_order;

exception
    when no_data_found then
        raise exception 'Группа свойств [%] или валюта по умолчанию не найдена', p_code_pg
            using errcode = '20156';
end;
$$;

create or replace function pkg_pge.get_property(
    p_code_pg character varying,
    p_property varchar,
    p_id_entities bigint[],
    p_username character varying default 'admin'
)
    returns table (
                      id_entity bigint,
                      id integer,
                      id_pgl integer,
                      id_property integer,
                      pg_order integer,
                      code_prop varchar,
                      name_prop varchar,
                      id_prop_type integer,
                      name_prop_type varchar,
                      property_value varchar,
                      use_multi_select boolean,
                      display_value text
                  )
    security definer
    set search_path = rightsflow
as
$$
begin
    if pkg_pge.is_numeric(p_property) then
        return query
            select t.*
            from pkg_pge.get_pg_data(p_code_pg, p_id_entities, p_username) t
            where t.id_property = p_property::integer;
    else
        return query
            select t.*
            from pkg_pge.get_pg_data(p_code_pg, p_id_entities, p_username) t
            where t.code_prop = p_property;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_pge.update_property(
    p_code_pg character varying,
    p_property varchar,
    p_id_entity bigint,
    p_value varchar,
    p_username character varying default 'admin'
)
    returns table (
                      id_entity bigint,
                      id integer,
                      id_pgl integer,
                      id_property integer,
                      pg_order integer,
                      code_prop varchar,
                      name_prop varchar,
                      id_prop_type integer,
                      name_prop_type varchar,
                      property_value varchar,
                      use_multi_select boolean,
                      display_value text
                  )
    security definer
    set search_path = rightsflow
as
$$
declare
    c_RightType constant integer = 2;
    v_value   varchar;
    v_sel     varchar[];
    v_sql     text;
    v_id_pgl  integer;
    v_id_prop integer;
    v_rec     record;
    v_id_obj  integer;
    v_multi   boolean;
    v_obj     record;
    v_cnt     integer;
    v_id      bigint;
begin

    -- находим идентификатор слоя для заданного p_code_pg
    select layer_sel_query into v_sql from sync__lov_pge_property_group where code = p_code_pg;
    execute v_sql into v_sel using p_id_entity;

    select l.id into v_id_pgl from sync__lov_pge_pg_layer l
    join sync__lov_pge_property_group pg on pg.id = l.id_pg
    where l.sel_value::varchar[] && v_sel
      and pg.code = p_code_pg;

    -- находим id_property
    if pkg_pge.is_numeric(p_property) then
        v_id_prop := to_number(p_property, 'FM999999999');
        select p.id into v_id_prop from sync__lov_pge_property p where p.id = v_id_prop;
        if not found then
            raise exception 'Свойство [id=%] не найдено!', v_id_prop
                using errcode = 20150;
        end if;
    else
        select p.id into v_id_prop from sync__lov_pge_property p where p.code = p_property;
        if not found then
            raise exception 'Свойство [code=%] не найдено!', p_property
                using errcode = 20151;
        end if;
    end if;

    select * into v_rec from sync__lov_pge_pgl_dtl pgld
    where pgld.id_pgl = v_id_pgl
      and pgld.id_property = v_id_prop;

    select pt.id_obj, pt.use_multi_select into v_id_obj, v_multi from sync__lov_pge_property p
      join sync__lov_pge_prop_type pt on pt.id = p.id_prop_type
    where p.id = v_id_prop;

    -- приводим список id к строковому формату postgresql массива
    v_value := p_value;
    if p_value is not null and v_id_obj is not null and v_multi then
        if (p_value ~* '^[0-9]+(,[0-9]+){0,}$') then
            v_value := '{' || array_to_string(string_to_array(p_value, ','), ',') || '}';
        end if;
    end if;

    -- проверяем корректность переданного значения свойства
    if v_value is not null and v_rec.property_format is not null then
        if not (select v_value ~* v_rec.property_format) then
            raise exception 'Значение [%] не соответствует заданному формату!', v_value
                using errcode = 20152;
        end if;
    end if;

    -- проверяем наличие значения в справочнике для свойств типа "Справочник"
    if v_value is not null and v_id_obj is not null then

        select * into v_obj from sync__lov_object o where o.id = v_id_obj;
        if not found then
            raise exception 'Объект [id=%] не найден!', v_id_obj
                using errcode = 20153;
        end if;

        if v_multi then
            for v_rec in select t.id from unnest(v_value::text[]) as t(id) loop

                    v_sql := 'select count(*) from '||v_obj.table_name||' t where t.id = $1';
                    execute v_sql into v_cnt using to_number(v_rec.id, 'FM999999999');

                    if v_cnt = 0 then
                        raise exception 'Значение [id=%] отсутствует в справочнике "%"!', v_rec.id, v_obj.NAME
                            using errcode = 20154;
                    end if;

                end loop;
        else
            v_sql := 'select count(*) from '||v_obj.table_name||' t where t.id = $1';
            execute v_sql into v_cnt using to_number(v_value, 'FM999999999');

            if v_cnt = 0 then
                raise exception 'Значение [id=%] отсутствует в справочнике "%"!', v_value, v_obj.NAME
                    using errcode = 20154;
            end if;

        end if;

    end if;

    select count(*) into v_cnt from pge_props pp
    where pp.id_pgl = v_id_pgl
      and pp.id_property = v_id_prop
      and pp.id_entity = p_id_entity;

    if v_cnt = 1 then
        update pge_props pp
        set property_value = v_value,
            updated_by = p_username,
            updated_at = CURRENT_TIMESTAMP
        where pp.id_pgl = v_id_pgl
          and pp.id_property = v_id_prop
          and pp.id_entity = p_id_entity;
    else
        insert into pge_props (id_pgl, id_property, id_entity, property_value, created_by)
        values (v_id_pgl, v_id_prop, p_id_entity, v_value, p_username)
        returning pge_props.id into v_id;

        select pgo.id_obj into v_id_obj from sync__lov_pge_pg_to_obj pgo
        where pgo.code_pg = p_code_pg;

        -- сохранение ограничения для значения свойства
        if v_id_obj = c_RightType then
            insert into pge_props_lic_rights_rt (id, id_lic_rights_rt)
            values (v_id, p_id_entity);
        end if;

    end if;

    return query
        select * from pkg_pge.get_property(p_code_pg, p_property, array[p_id_entity], p_username);
end;
$$ language plpgsql;

create or replace function pkg_pge.update_properties_batch(
    p_updates text, -- [{"id_entity": 1, "code_pg": "PG_COMMON", "property": "code", "value": "123"}, ...]
    p_username character varying default 'admin'
)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
declare
    v_count integer := 0;
    v_rec record;
    v_updates jsonb;
begin

    -- Попытка преобразования
    begin
        v_updates := p_updates::jsonb;
    exception
        when invalid_text_representation then
            raise exception 'Некорректный json в параметре p_updates: %', p_updates
                using errcode = '22022';
    end;

    -- Валидация JSON
    if jsonb_typeof(v_updates) != 'array' then
        raise exception 'p_updates должен быть JSON массивом'
            using errcode = '22023';
    end if;

    -- Batch обновление
    for v_rec in
        select
            (elem->>'id_entity')::bigint as id_entity,
            elem->>'code_pg' as code_pg,
            elem->>'property' as property,
            elem->>'value' as value
        from jsonb_array_elements(v_updates) as elem
        loop
            perform pkg_pge.update_property(
                    v_rec.code_pg,
                    v_rec.property,
                    v_rec.id_entity,
                    v_rec.value,
                    p_username
                    );
            v_count := v_count + 1;
        end loop;

    return v_count;
end;
$$ language plpgsql;

create or replace function pkg_pge.garbage_pge_data()
    returns integer
    security definer
    set search_path = rightsflow
as
$$
declare
    c_RightType constant integer = 2;
    v_deleted_count integer;
begin

    -- уборка для свойств RightType объекта
    delete from pge_props
    where id in (
        select p.id from pge_props p
          join sync__lov_pge_pg_layer pgl on pgl.id = p.id_pgl
          join sync__lov_pge_property_group pg on pg.id = pgl.id_pg
          join sync__lov_pge_pg_to_obj pgo on pgo.code_pg = pg.code
            where pgo.id_obj = c_RightType

        except

        select id from pge_props_lic_rights_rt
    );

    get diagnostics v_deleted_count = row_count;
    raise notice 'Удалено записей: %', v_deleted_count;

    return v_deleted_count;
end;
$$ language plpgsql;
