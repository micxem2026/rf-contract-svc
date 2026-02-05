CREATE TABLE IF NOT EXISTS CONFIG_DATA
(
    ID integer primary key,
    USE_SCHEDULER boolean not null default false
);

insert into config_data (id, use_scheduler)
values (1, false)
on conflict do nothing;

CREATE TABLE IF NOT EXISTS CONTRACT_OUTBOX_BUFFER
(
    id bigserial primary key,
    id_contract bigint not null,
    id_org integer NOT NULL,
    id_oip integer NOT NULL,
    created_by character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS UNQ_CONTRACT_OUTBOX_BUFFER ON CONTRACT_OUTBOX_BUFFER(ID_CONTRACT,ID_ORG,ID_OIP);

CREATE OR REPLACE PROCEDURE pkg_contract.make_contract_outbox_event(IN p_id_contract bigint, IN p_status_mode integer, IN p_username character varying)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path TO 'rightsflow'
AS $procedure$
declare
    v_use_scheduler boolean;
begin

    select cd.use_scheduler into v_use_scheduler from config_data cd where cd.id = 1;

    if p_status_mode = 2 then
        if v_use_scheduler = true then
            insert into contract_outbox_buffer (id_contract, id_oip, id_org, created_by)
            select distinct (jsonb_array_elements(data)->'id_contract')::bigint AS id_contract,
                            (jsonb_array_elements(data)->'id_oip')::integer AS id_oip,
                            (jsonb_array_elements(data)->'id_org')::integer AS id_org,
                            p_username as created_by
            from contract_change_buffer cb
            where cb.data @> jsonb_build_array(jsonb_build_object('id_contract', p_id_contract))
              and cb.status = 'NEW'
            on conflict (id_contract, id_org, id_oip) do update set
                processed_at = null
            WHERE contract_outbox_buffer.id_contract = EXCLUDED.id_contract
              AND contract_outbox_buffer.id_org = EXCLUDED.id_org
              AND contract_outbox_buffer.id_oip = EXCLUDED.id_oip;
        else
            insert into contract_outbox (id_oip, id_org, created_by)
            select distinct (jsonb_array_elements(data)->'id_oip')::integer AS id_oip,
                            (jsonb_array_elements(data)->'id_org')::integer AS id_org,
                            p_username as created_by
            from contract_change_buffer cb
            where cb.data @> jsonb_build_array(jsonb_build_object('id_contract', p_id_contract))
              and cb.status = 'NEW';
        end if;
    end if;

    update contract_change_buffer cb
    set status = case when p_status_mode = 2 then 'PROCESSED' else 'ARCHIVED' end,
        updated_by = p_username,
        updated_at = current_timestamp
    where cb.data @> jsonb_build_array(jsonb_build_object('id_contract', p_id_contract))
      and cb.status = 'NEW';

end;
$procedure$;

CREATE OR REPLACE FUNCTION pkg_contract.make_contract_outbox()
    RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path TO 'rightsflow'
AS $function$
DECLARE
    v_updated_count integer;
BEGIN
    -- Вставляем уникальные записи в таблицу CONTRACT_OUTBOX
    INSERT INTO contract_outbox (id_oip, id_org, created_by)
    SELECT DISTINCT cob.id_oip, cob.id_org, 'admin' AS created_by
    FROM contract_outbox_buffer cob
    WHERE processed_at IS NULL;

    -- Помечаем записи в буфере как обработанные
    UPDATE contract_outbox_buffer
    SET processed_at = current_timestamp
    WHERE processed_at IS NULL;

    -- Получаем количество обновлённых строк из последнего оператора (UPDATE)
    GET DIAGNOSTICS v_updated_count = ROW_COUNT;

    RETURN v_updated_count;
END;
$function$;

ALTER TABLE LICENSE_OIP ADD COLUMN IF NOT EXISTS PARENTS TEXT;

CREATE OR REPLACE FUNCTION pkg_contract.ins_license_oip(p_id_license bigint, p_id_oip_str text, p_username character varying)
    RETURNS bigint[]
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path TO 'rightsflow'
AS $function$
DECLARE
    v_simple_arr boolean;
    v_array text[];
    v_id_oip_str  text;
    inserted_ids bigint[];
    v_errors text;
    v_rec record;
BEGIN

    create temp table temp_res (parents text, id_oip integer) on commit drop;
    -- Проверяем передали ли простой массив
    v_simple_arr := strpos(p_id_oip_str, ';') = 0 and strpos(p_id_oip_str, ':') = 0;
    if v_simple_arr = false then
        v_array := string_to_array(p_id_oip_str,';');
        insert into temp_res (parents, id_oip)
        select nullif(split_part(a1.val, ':', 1)::text, '') as parents,
               split_part(a1.val, ':', 2)::integer as id_oip
        from unnest(v_array) a1(val);
        select array_to_string(array_agg(id_oip),',') into v_id_oip_str from temp_res;
    else
        v_id_oip_str := p_id_oip_str;
    end if;

    -- Вызываем функцию валидации ОДИН РАЗ и сохраняем результат
    create temp table temp_validation_results on commit drop as
    select * from pkg_contract.validate_oip_with_hierarchy(v_id_oip_str);

    -- Проверка на ошибки
    with errors as (
        select distinct roots, name_oip
        from temp_validation_results
        where cnt > 1
    )
    select string_agg('Для ОИС ['||name_oip||'] задано несколько корневых ОИС: ['||roots||']', E'\r\n')
    into v_errors
    from errors;

    if v_errors is not null then
        raise exception '%', v_errors using errcode = 20122;
    end if;

    with inserted as (
        insert into license_oip (id_license, id_oip, id_root_oip, created_by, parents)
            select p_id_license, vr.id_oip, vr.id_root, p_username, tr.parents
            from temp_validation_results vr
            left join temp_res tr on tr.id_oip = vr.id_oip
            returning license_oip.id
    )
    select array_agg(id) into inserted_ids from inserted;

    -- Обновляем буфер изменений
    for v_rec in select * from unnest(inserted_ids) as ids(id) loop
            call pkg_contract.make_change_buffer(
                    p_action => 'INSERT',
                    p_username => p_username,
                    p_id_lic_oip => v_rec.id
                 );
        end loop;

    return inserted_ids;
END;
$function$
;

ALTER TABLE rightsflow.sync__lov_object
    ALTER COLUMN table_name TYPE character varying(512) COLLATE pg_catalog."default";

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
                            (select string_agg(tc.name, '||' order by ordinality)
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

DROP FUNCTION IF EXISTS pkg_contract.upd_license(int8, varchar, varchar, int8, numeric, numeric, numeric, numeric, date, date, varchar, varchar);

CREATE OR REPLACE FUNCTION pkg_contract.upd_license(p_id bigint,
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
                                                    p_username character varying)
    RETURNS bigint
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path TO 'rightsflow'
AS $function$
DECLARE
    v_contract contract%rowtype;
    v_license license%rowtype;
    v_validity_period daterange;
BEGIN

    select c.* into v_contract from contract c, license l
    where l.id_contract = c.id
      and l.id = p_id;

    select l.* into v_license from license l
    where l.id = p_id
        for update;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');

    if isempty(v_contract.validity_period * v_validity_period) then
        raise exception 'Лицензия не пересекается с периодом договора!'
            using errcode = 20105;
    else
        v_validity_period := v_contract.validity_period * v_validity_period;
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

    return p_id;
END;
$function$
;

CREATE OR REPLACE PROCEDURE pkg_contract.del_license_oip_by_lic(IN p_id_license bigint, IN p_username character varying)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path TO 'rightsflow'
AS $procedure$
DECLARE
    v_rec record;
BEGIN
    if not exists (select 1 from license where id = p_id_license) then
        raise exception 'Лицензия не найдена! [id = %]', p_id_license using errcode = 20120;
    end if;
    for v_rec in select * from license_oip where id_license = p_id_license loop
            call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => v_rec.id);
        end loop;
    delete from license_oip where id_license = p_id_license;
END;
$procedure$
;