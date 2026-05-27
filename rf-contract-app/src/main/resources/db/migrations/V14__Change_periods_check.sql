ALTER TABLE rightsflow.contract
    DISABLE TRIGGER tr_b2_correct_license_periods;

ALTER TABLE rightsflow.contract
    ALTER COLUMN warning TYPE text;

CREATE OR REPLACE FUNCTION pkg_contract.format_daterange_inclusive(p_range daterange)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS $$
SELECT CASE
           WHEN p_range IS NULL THEN NULL
           WHEN isempty(p_range) THEN '[]'
           WHEN lower(p_range) IS NULL AND upper(p_range) IS NULL THEN '(,)'
           WHEN lower(p_range) IS NULL THEN '(,' || to_char(upper(p_range) - 1, 'YYYY-MM-DD') || ']'
           WHEN upper(p_range) IS NULL THEN '[' || to_char(lower(p_range), 'YYYY-MM-DD') || ',)'
           ELSE '[' || to_char(lower(p_range), 'YYYY-MM-DD') || ',' || to_char(upper(p_range) - 1, 'YYYY-MM-DD') || ']'
           END;
$$;

CREATE OR REPLACE FUNCTION pkg_contract.ins_license(
    p_guid character varying,
    p_num character varying,
    p_name character varying,
    p_id_contract bigint,
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
    r_result bigint;
    v_contract contract%rowtype;
    v_validity_period daterange;
    v_num contract.num%type;
BEGIN

    if p_id_contract is null then
        raise exception 'Ошибка создания лицензии! Не указан идентификатор договора (p_id_contract)!'
            using errcode = '20104';
    end if;

    -- Проверка доступа к родительскому контракту
    PERFORM pkg_contract.check_contract_org_access(p_id_contract, p_username, p_bypass);

    select * into v_contract from contract where id = p_id_contract;

    v_num := coalesce(nullif(p_num, ''), pkg_contract.get_next_license_num());
    v_validity_period := daterange(p_beg_date, p_end_date, '[]');

    if isempty(v_contract.validity_period * v_validity_period) then
        raise exception 'Лицензия не пересекается с периодом договора!'
            using errcode = '20105';
    -- Отключил по требованию заказчика
    --else
    --    v_validity_period := v_contract.validity_period * v_validity_period;
    end if;

    insert into license (id_contract, id_lic_format, guid, num, name, price, vat_rate, vat_amount, total_amount, validity_period, description, created_by)
    values (p_id_contract, p_id_lic_format, p_guid, v_num, p_name, p_price, p_vat_rate,
            p_vat_amount, p_total_amount, v_validity_period, p_description, p_username)
    returning id into r_result;

    return r_result;
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

    return p_id;
END;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_contract.ins_license_rt_feature_set(
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
    r_result bigint;
    v_validity_period daterange;
    v_license license%rowtype;
    v_lic_rights  license_rights%rowtype;
BEGIN
    if p_id_lic_rights is null then
        raise exception 'Ошибка создания набора характеристик! Не указан идентификатор права лицензии (p_id_lic_rights)!'
            using errcode = '20109';
    end if;

    -- Проверка доступа через цепочку license_rights → license → contract
    PERFORM pkg_contract.check_license_rights_org_access(p_id_lic_rights, p_username, p_bypass);

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

    insert into license_rt_feature_set (id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
    values (p_id_lic_rights, coalesce(p_is_exclusive, false), coalesce(p_is_use_right, false),
            coalesce(p_is_sub_license, false), v_validity_period, p_username)
    returning id into r_result;

    return r_result;
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

    select * into v_feature_set from license_rt_feature_set where id = p_id for update;
    if v_feature_set.is_exclusive != coalesce(p_is_exclusive, v_feature_set.is_exclusive) or
       v_feature_set.validity_period != v_validity_period then
        call pkg_contract.make_change_buffer(p_action => 'UPDATE', p_username => p_username, p_id_feature_set => p_id);
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

    return p_id;
END;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_contract.is_contract_valid(
    p_id_contract bigint,
    p_username character varying)
    RETURNS boolean
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

declare
    CRLF constant text := E'\r\n';
    r_result boolean := true;
    v_warning text;
begin
    -- Проверка входных параметров
    if p_id_contract is null then
        raise notice 'Не указан контракт!';
        return false;
    end if;

    if not exists(select 1 from contract where id = p_id_contract) then
        raise exception 'Контракт [ID=%] не найден!', p_id_contract
            using errcode = 20120;
    end if;

    -- Собираем все предупреждения одним запросом
    with contract_checks as (
        select
            c.num,
            exists(select 1 from license where id_contract = c.id) as has_licenses
        from contract c
        where c.id = p_id_contract
    ),
         contract_cparty_checks as (
             select
                 c.id,
                 c.num,
                 c.in_out in ('eP', 'eS') as is_external,
                 exists(select 1 from contract_counterparty where id_contract = c.id) as has_cparties
             from contract c
             where c.id = p_id_contract
         ),
         contract_ip_party_checks as (
             select
                 c.id,
                 c.num,
                 c.in_out = 'iP' as is_internal,
                 c.id_org_party is not null as has_party
             from contract c
             where c.id = p_id_contract
         ),
         contract_is_party_checks as (
             select
                 c.id,
                 c.num,
                 c.in_out = 'iS' as is_internal,
                 c.id_org_party is not null as has_party
             from contract c
             where c.id = p_id_contract
         ),
         license_checks as (
             select
                 l.id,
                 l.num,
                 l.id_lic_format,
                 (l.id_lic_format is not null) as use_format,
                 c.num as contract_num,
                 pkg_contract.format_daterange_inclusive(c.validity_period) as contract_period,
                 pkg_contract.format_daterange_inclusive(l.validity_period) as license_period,
                 exists(select 1 from license_oip where id_license = l.id) as has_oip,
                 exists(select 1 from license_rights lr
                                          join license_rights_rt lrt on lrt.id_lic_rights = lr.id
                        where lr.id_license = l.id) as has_lrt,
                 exists(select 1 from format_rights fr
                                          join format_rights_rt frt on frt.id_fmt_rights = fr.id
                        where fr.id_lic_format = l.id_lic_format) as has_frt,
                 (l.validity_period <@ c.validity_period and l.validity_period && c.validity_period) as valid_period
             from license l
                      join contract c on c.id = l.id_contract
             where l.id_contract = p_id_contract
         ),
         license_rt_checks as (
             select
                 l.num as lic_num,
                 lr.id as lic_rights_id,
                 rt.name as rt_name,
                 exists(select 1 from license_rt_feature_set where id_lic_rights = lr.id) as has_feature_sets
             from license l
                      join license_rights lr on lr.id_license = l.id
                      join license_rights_rt lrt on lrt.id_lic_rights = lr.id
                      join sync__klf_right_type rt on rt.id = lrt.id_right_type
             where l.id_contract = p_id_contract
         ),
         format_rt_checks as (
             select
                 l.num as lic_num,
                 fr.id as lic_rights_id,
                 rt.name as rt_name,
                 lf.name as lic_format_name,
                 l.id_lic_format,
                 exists(select 1 from format_rt_feature_set where id_fmt_rights = fr.id) as has_feature_sets
             from license l
                      join license_format lf on lf.id = l.id_lic_format
                      join format_rights fr on fr.id_lic_format = l.id_lic_format
                      join format_rights_rt frt on frt.id_fmt_rights = fr.id
                      join sync__klf_right_type rt on rt.id = frt.id_right_type
             where l.id_contract = p_id_contract
         ),
         feature_set_checks as (
             select
                 l.num as lic_num,
                 rt.name as rt_name,
                 lrtfs.id as feature_set_id,
                 pkg_contract.format_daterange_inclusive(l.validity_period) as license_period,
                 pkg_contract.format_daterange_inclusive(lrtfs.validity_period) as feature_set_period,
                 exists(select 1 from license_rt_features where id_feature_set = lrtfs.id) as has_features,
                 (lrtfs.validity_period <@ l.validity_period and lrtfs.validity_period && l.validity_period) as valid_period
             from license l
                      join license_rights lr on lr.id_license = l.id
                      join license_rights_rt lrt on lrt.id_lic_rights = lr.id
                      join sync__klf_right_type rt on rt.id = lrt.id_right_type
                      join license_rt_feature_set lrtfs on lrtfs.id_lic_rights = lr.id
             where l.id_contract = p_id_contract
         ),
         fmt_feature_set_checks as (
             select
                 l.num as lic_num,
                 rt.name as rt_name,
                 frtfs.id as feature_set_id,
                 lf.name as lic_format_name,
                 l.id_lic_format,
                 pkg_contract.format_daterange_inclusive(l.validity_period) as license_period,
                 pkg_contract.format_daterange_inclusive(frtfs.validity_period) as fmt_feature_set_period,
                 exists(select 1 from format_rt_features where id_feature_set = frtfs.id) as has_features,
                 (l.validity_period <@ frtfs.validity_period and frtfs.validity_period && l.validity_period) as valid_period
             from license l
                      join license_format lf on lf.id = l.id_lic_format
                      join format_rights fr on fr.id_lic_format = l.id_lic_format
                      join format_rights_rt frt on frt.id_fmt_rights = fr.id
                      join sync__klf_right_type rt on rt.id = frt.id_right_type
                      join format_rt_feature_set frtfs on frtfs.id_fmt_rights = fr.id
             where l.id_contract = p_id_contract
         ),
         features_checks as (
             select
                 l.num,
                 c.name as rt_name,
                 fs.id as feature_set_id,
                 string_to_array(c.cats1, ',')::integer[] - cats1_array_ as cats1_diff,
                 string_to_array(c.cats2, ',')::integer[] - cats2_array_ as cats2_diff
             from license l
                      join license_rights lr on lr.id_license = l.id
                      join license_rights_rt lrt on lrt.id_lic_rights = lr.id
                      join vw_rt_cats c on c.id = lrt.id_right_type
                      join license_rt_feature_set fs on fs.id_lic_rights = lr.id
                      cross join lateral (
                 select coalesce(array_agg(distinct id_feature_category), array[]::integer[]) as cats1_array_
                 from license_rt_features
                 where id_feature_set = fs.id and is_native
                 ) q1
                      cross join lateral (
                 select coalesce(array_agg(distinct id_feature_category), array[]::integer[]) as cats2_array_
                 from license_rt_features
                 where id_feature_set = fs.id and not is_native
                 ) q2
             where l.id_contract = p_id_contract),
         fmt_features_checks as (
             select
                 l.num,
                 c.name as rt_name,
                 fs.id as feature_set_id,
                 lf.name as lic_format_name,
                 l.id_lic_format,
                 string_to_array(c.cats1, ',')::integer[] - cats1_array_ as cats1_diff,
                 string_to_array(c.cats2, ',')::integer[] - cats2_array_ as cats2_diff
             from license l
                      join license_format lf on lf.id = l.id_lic_format
                      join format_rights fr on fr.id_lic_format = l.id_lic_format
                      join format_rights_rt frt on frt.id_fmt_rights = fr.id
                      join vw_rt_cats c on c.id = frt.id_right_type
                      join format_rt_feature_set fs on fs.id_fmt_rights = fr.id
                      cross join lateral (
                 select coalesce(array_agg(distinct id_feature_category), array[]::integer[]) as cats1_array_
                 from format_rt_features
                 where id_feature_set = fs.id and is_native
                 ) q1
                      cross join lateral (
                 select coalesce(array_agg(distinct id_feature_category), array[]::integer[]) as cats2_array_
                 from format_rt_features
                 where id_feature_set = fs.id and not is_native
                 ) q2
             where l.id_contract = p_id_contract),

         all_warnings as (
             -- Предупреждения об отсутствии контрагентов
             select 'Контракт [' || num || '] не содержит контрагентов!' as warning
             from contract_cparty_checks
             where is_external and not has_cparties

             union all

             select 'Контракт [' || num || '] не содержит продавца!' as warning
             from contract_ip_party_checks
             where is_internal and not has_party

             union all

             select 'Контракт [' || num || '] не содержит покупателя!' as warning
             from contract_is_party_checks
             where is_internal and not has_party

             union all

             -- Предупреждения об отсутствии лицензий
             select 'Контракт [' || num || '] не содержит лицензий!' as warning
             from contract_checks
             where not has_licenses

             union all

             -- Предупреждения об отсутствии ОИС
             select 'Лицензия [' || num || '] не содержит ОИС!' as warning
             from license_checks
             where not has_oip

             union all

             -- Предупреждения об отсутствии прав
             select 'Лицензия [' || num || '] не содержит прав!' as warning
             from license_checks
             where not use_format and not has_lrt

             union all

             select 'Лицензия [' || num || '] не содержит прав!' as warning
             from license_checks
             where use_format and not has_frt

             union all

             -- Предупреждения об отсутствии наборов характеристик
             select 'Лицензия [' || lic_num || '] не содержит наборов характеристик для права [' || rt_name || ']!' as warning
             from license_rt_checks
             where not has_feature_sets

             union all

             select 'Формат ['|| lic_format_name ||'], использующийся в лицензии [' || lic_num || '], не содержит наборов характеристик для права [' || rt_name || ']!' as warning
             from format_rt_checks
             where not has_feature_sets

             union all

             -- Предупреждение о некорректном периоде лицензии
             select 'Период действия лицензии [' || num || '] ' || license_period || '  не соответствует периоду действия контракта [' || contract_num || '] ' || contract_period || '!' as warning
             from license_checks
             where not valid_period

             union all

             select 'Период действия набора характеристик [ID=' || feature_set_id || '] ' || feature_set_period || '  не соответствует периоду действия лицензии [' || lic_num || '] ' || license_period || '!' as warning
             from feature_set_checks
             where not valid_period

             union all

             select 'Период действия лицензии [' || lic_num || '] ' || license_period || '  не соответствует периоду действия набора характеристик формата [ID=' || feature_set_id || '] ' || fmt_feature_set_period || '!' as warning
             from fmt_feature_set_checks
             where not valid_period

             union all

             -- Предупреждения об отсутствии характеристик
             select 'Лицензия [' || lic_num || '] не содержит характеристик для набора характеристик [ID=' || feature_set_id || '] на праве [' || rt_name || ']!' as warning
             from feature_set_checks
             where not has_features

             union all

             select 'Формат ['|| lic_format_name ||'], использующийся в лицензии [' || lic_num || '], не содержит характеристик для набора характеристик [ID=' || feature_set_id || '] на праве [' || rt_name || ']!' as warning
             from fmt_feature_set_checks
             where not has_features

             union all

             select 'Лицензия [' || c1.num || '] не содержит обязательной характеристики для категории [' || fc.name || '] на наборе характеристик [id=' || c1.feature_set_id || '] на праве [' || c1.rt_name || ']!' as warning
             from features_checks c1
                      cross join lateral unnest(c1.cats1_diff) as c2d(id_cat)
                      join sync__klf_feature_category fc on fc.id = c2d.id_cat
             where c1.cats1_diff is not null

             union all

             select 'Лицензия [' || c1.num || '] не содержит транзитной характеристики для категории [' || fc.name || '] на наборе характеристик [id=' || c1.feature_set_id || '] на праве [' || c1.rt_name || ']!' as warning
             from features_checks c1
                      cross join lateral unnest(c1.cats2_diff) as c2d(id_cat)
                      join sync__klf_feature_category fc on fc.id = c2d.id_cat
             where c1.cats2_diff is not null

             union all

             select 'Формат ['|| lic_format_name ||'], использующийся в лицензии [' || c1.num || '], не содержит обязательной характеристики для категории [' || fc.name || '] на наборе характеристик [id=' || c1.feature_set_id || '] на праве [' || c1.rt_name || ']!' as warning
             from fmt_features_checks c1
                      cross join lateral unnest(c1.cats1_diff) as c2d(id_cat)
                      join sync__klf_feature_category fc on fc.id = c2d.id_cat
             where c1.cats1_diff is not null

             union all

             select 'Формат ['|| lic_format_name ||'], использующийся в лицензии [' || c1.num || '], не содержит транзитной характеристики для категории [' || fc.name || '] на наборе характеристик [id=' || c1.feature_set_id || '] на праве [' || c1.rt_name || ']!' as warning
             from fmt_features_checks c1
                      cross join lateral unnest(c1.cats2_diff) as c2d(id_cat)
                      join sync__klf_feature_category fc on fc.id = c2d.id_cat
             where c1.cats2_diff is not null
         )
    select
        string_agg(warning, CRLF order by warning),
        count(*) > 0
    into v_warning, r_result
    from all_warnings;

    -- Инвертируем результат (если есть предупреждения, контракт невалиден)
    r_result := not coalesce(r_result, false);

    -- Обновляем контракт
    perform set_config('rf.disable_status_check', 'true', true);
    begin
        update contract
        set warning = v_warning,
            updated_at = current_timestamp,
            updated_by = p_username
        where id = p_id_contract;
    exception
        when others then
            perform set_config('rf.disable_status_check', 'false', true);
    end;
    perform set_config('rf.disable_status_check', 'false', true);

    return r_result;
end;
$BODY$;