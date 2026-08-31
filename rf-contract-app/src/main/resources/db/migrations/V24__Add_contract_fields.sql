CREATE OR REPLACE FUNCTION pkg_contract.check_rights_for_contract(
    p_id_contract bigint)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_is_valid boolean;
    v_contract contract%rowtype;
BEGIN

    if coalesce(nullif(current_setting('rf.disable_check_rights', true), ''), 'false') = 'true' then
       return -4;
    end if;

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
        if coalesce(nullif(current_setting('rf.disable_notice_info', true), ''), 'false') = 'false' then
            raise notice 'Пропуск внешнего контракта покупки [ID=%]..', p_id_contract;
        end if;
        return -3;
    end if;

    -- Проверка валидности контракта
    select pkg_contract.is_contract_valid(
                   p_id_contract => p_id_contract,
                   p_username => 'system'
           ) into v_is_valid;

    if not v_is_valid then
        if coalesce(nullif(current_setting('rf.disable_notice_info', true), ''), 'false') = 'false' then
            raise notice 'Контракт [ID=%] не валиден!', p_id_contract;
        end if;
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
          and lrt.validity_period is not null
          and not lower_inf(lrt.validity_period)
          and not upper_inf(lrt.validity_period)
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
                      join lateral (SELECT cp.id as id_feature_plain, cp.id_feature_category, (fc.name::text || ' : '::text) || cp.name::text AS name
                                    FROM sync__klf_feature_plain cp,
                                         sync__klf_feature_category fc
                                    WHERE cp.id_feature_category = fc.id
                                      and cp.id_feature_category = hp.id_feature_category
                                      and cp.id = hp.id_feature_plain) vf
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
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.del_contract(
    IN p_id bigint,
    IN p_username character varying,
    IN p_use_cascade boolean DEFAULT false,
    IN p_bypass boolean DEFAULT false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

DECLARE
    rec record;
BEGIN
    -- Проверка доступа к контракту перед удалением
    PERFORM pkg_contract.check_contract_org_access(p_id, p_username, p_bypass);

    perform set_config('rf.disable_check_rights', 'true', true);
    perform set_config('rf.disable_validate_contract', 'true', true);

    if p_use_cascade then
        for rec in select * from license where id_contract = p_id loop
                call pkg_contract.del_license(rec.id, p_username, true, p_bypass);
            end loop;
        for rec in select * from contract_counterparty where id_contract = p_id loop
                call pkg_contract.del_contract_counterparty(rec.id, p_username, p_bypass);
            end loop;
    end if;
    delete from contract where id = p_id;

    call pkg_contract.make_contract_outbox_event(
            p_id_contract => p_id,
            p_status_mode => 2,
            p_username => p_username
         );

    perform set_config('rf.disable_check_rights', 'false', true);
    perform set_config('rf.disable_validate_contract', 'false', true);

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

    if coalesce(nullif(current_setting('rf.disable_validate_contract', true), ''), 'false') = 'true' then
        return false;
    end if;

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
                 (l.validity_period <@ c.validity_period and l.validity_period && c.validity_period)  as valid_period,
                 (not lower_inf(l.validity_period) and not upper_inf(l.validity_period)) as no_inf_period_lic,
                 (not lower_inf(c.validity_period) and not upper_inf(c.validity_period)) as no_inf_period_cont
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
                 (lrtfs.validity_period <@ l.validity_period and lrtfs.validity_period && l.validity_period) as valid_period,
                 (not lower_inf(lrtfs.validity_period) and not upper_inf(lrtfs.validity_period)) as no_inf_period
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
                 (l.validity_period <@ frtfs.validity_period and frtfs.validity_period && l.validity_period) as valid_period,
                 (not lower_inf(frtfs.validity_period) and not upper_inf(frtfs.validity_period)) as no_inf_period
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

             -- Предупреждение о некорректном периоде
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

             -- Предупреждение об отсутствии границы в периоде
             select 'Период действия лицензии [' || num || '] ' || license_period || ' не имеет одной из границ!' as warning
             from license_checks
             where not no_inf_period_lic

             union all

             select 'Период действия контракта [' || contract_num || '] ' || contract_period || ' не имеет одной из границ!' as warning
             from license_checks
             where not no_inf_period_cont

             union all

             select 'Период действия набора характеристик [ID=' || feature_set_id || '] ' || feature_set_period || ' не имеет одной из границ!' as warning
             from feature_set_checks
             where not no_inf_period

             union all

             select 'Период действия набора характеристик формата [ID=' || feature_set_id || '] ' || fmt_feature_set_period || ' не имеет одной из границ!' as warning
             from fmt_feature_set_checks
             where not no_inf_period

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

CREATE OR REPLACE FUNCTION pkg_contract.check_dependant_rights(
    p_id_org integer,
    p_id_oip integer,
    p_id_right_type integer)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$
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
          and lrt.validity_period is not null
          and not lower_inf(lrt.validity_period)
          and not upper_inf(lrt.validity_period)
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
                      join lateral (SELECT cp.id as id_feature_plain, cp.id_feature_category, (fc.name::text || ' : '::text) || cp.name::text AS name
                                    FROM sync__klf_feature_plain cp,
                                         sync__klf_feature_category fc
                                    WHERE cp.id_feature_category = fc.id
                                      and cp.id_feature_category = hp.id_feature_category
                                      and cp.id = hp.id_feature_plain) vf
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
$BODY$;

CREATE OR REPLACE FUNCTION pkg_acl.comp_contract(
    p_id bigint)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$
declare
    v_id bigint;
    v_contract contract%rowtype;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    -- Если такой контракт существует, то блокируем его
    if exists(select 1 from contract where id = p_id) then
        select c.* into v_contract from contract c where c.id = p_id for update;
        v_id := -1;
        call pkg_contract.del_contract(p_id, 'system', true, true);
    end if;

    return v_id;

exception
    when lock_not_available then
        raise exception 'Сделка ID=% заблокирована другим процессом', p_id;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.del_license(
    IN p_id bigint,
    IN p_username character varying,
    IN p_use_cascade boolean DEFAULT false,
    IN p_bypass boolean DEFAULT false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
DECLARE
    rec record;
    v_id_contract bigint;
BEGIN
    -- Проверка доступа через цепочку license → contract
    PERFORM pkg_contract.check_license_org_access(p_id, p_username, p_bypass);

    if p_use_cascade then
        for rec in select * from license_rights where id_license = p_id loop
                call pkg_contract.del_license_rights(rec.id, p_username, true, p_bypass);
            end loop;
        for rec in select * from license_oip where id_license = p_id loop
                call pkg_contract.del_license_oip(rec.id, p_username, p_bypass);
            end loop;
    end if;
    select id_contract into v_id_contract from license where id = p_id;
    delete from license where id = p_id;

    -- выставляем флаг невалидности
    perform pkg_contract.is_contract_valid(
            p_id_contract => v_id_contract,
            p_username => 'system'
            );

END;
$BODY$;

CREATE OR REPLACE PROCEDURE pkg_contract.del_license_rights(
    IN p_id bigint,
    IN p_username character varying,
    IN p_use_cascade boolean DEFAULT false,
    IN p_bypass boolean DEFAULT false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
DECLARE
    rec record;
BEGIN
    -- Проверка доступа через цепочку license_rights → license → contract
    PERFORM pkg_contract.check_license_rights_org_access(p_id, p_username, p_bypass);

    if p_use_cascade then
        for rec in select id from license_rt_feature_set where id_lic_rights = p_id loop
                call pkg_contract.del_license_rt_feature_set(rec.id, p_username, true, p_bypass);
            end loop;
        delete from license_rights_rt where id_lic_rights = p_id;
    end if;
    delete from license_rights where id = p_id;
END;
$BODY$;

ALTER TABLE IF EXISTS contract
    ADD COLUMN IF NOT EXISTS managed_by character varying(20);

CREATE INDEX IF NOT EXISTS idx_users_username_incl_dn
    ON sync__users(username) INCLUDE (display_name);

CREATE INDEX IF NOT EXISTS idx_contract_managed_by ON contract(managed_by);
CREATE INDEX IF NOT EXISTS idx_contract_created_by ON contract(created_by);
CREATE INDEX IF NOT EXISTS idx_contract_updated_by ON contract(updated_by);

DROP FUNCTION IF EXISTS pkg_contract.ins_contract(character varying, character varying, character varying, character varying,
                                                  date, date, date, character varying, integer, character, character varying,
                                                  integer, integer, numeric, numeric, numeric, numeric, character varying,
                                                  bigint, bigint, boolean);

CREATE OR REPLACE FUNCTION pkg_contract.ins_contract(
    p_guid character varying,
    p_num character varying,
    p_id_org character varying,
    p_id_org_party character varying,
    p_beg_date date,
    p_end_date date,
    p_contract_date date,
    p_status_1c character varying,
    p_id_contract_type integer,
    p_in_out character,
    p_description character varying,
    p_id_currency integer,
    p_id_currency_payment integer,
    p_unf_price numeric,
    p_unf_vat_rate numeric,
    p_unf_vat_amount numeric,
    p_unf_total_amount numeric,
    p_username character varying,
    p_id_parent bigint DEFAULT NULL::bigint,
    p_id_sibling bigint DEFAULT NULL::bigint,
    p_bypass boolean DEFAULT false,
    p_managed_by character varying DEFAULT NULL::character varying
)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$
DECLARE
    r_result bigint;
    v_validity_period daterange;
    v_num contract.num%type;
    v_id_contract_type integer;
    v_id_contract_status integer;
    v_id_org_party integer;
    v_id_currency integer;
    v_id_currency_payment integer;
    v_id_org integer;
BEGIN

    v_id_org := pkg_contract.get_org_id(p_id_org);

    if p_in_out in ('eP', 'eS') then
        v_id_org_party := null;
    else
        v_id_org_party := null;
        if p_id_org_party is not null then
            v_id_org_party = pkg_contract.get_org_id(p_id_org_party);
        end if;
    end if;

    -- Проверка доступа: пользователь может создать контракт только
    -- если id_org или id_org_party входит в его список организаций
    IF NOT p_bypass THEN
        IF NOT EXISTS (
            SELECT 1 FROM user_org_access
            WHERE  username = p_username
              AND  (id_org = v_id_org
                OR (v_id_org_party IS NOT NULL AND id_org = v_id_org_party))
        ) THEN
            RAISE EXCEPTION
                'Доступ запрещён: нельзя создать контракт для организации [ID=%], '
                    'которая не входит в список организаций пользователя "%".',
                v_id_org, p_username
                USING ERRCODE = '42501';
        END IF;
    END IF;

    v_id_contract_type := pkg_contract.get_def_contract_type(p_id_contract_type);
    v_id_contract_status := pkg_contract.get_def_contract_status(v_id_contract_type, 'DRAFT');
    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    v_num := coalesce(nullif(p_num, ''), pkg_contract.get_next_contract_num(v_id_contract_type));
    v_id_currency := pkg_contract.get_def_currency(p_id_currency);
    v_id_currency_payment := pkg_contract.get_def_currency(p_id_currency_payment);

    insert into contract (guid, num, id_org, id_org_party, validity_period,contract_date, id_contract_type, status_1c,
                          id_contract_status, in_out, description, id_currency, id_currency_payment, unf_price, unf_vat_rate,
                          unf_vat_amount, unf_total_amount, id_parent, id_sibling, created_by, managed_by)
    values (p_guid, v_num, v_id_org, v_id_org_party, v_validity_period,
            p_contract_date, v_id_contract_type, p_status_1c, v_id_contract_status,
            p_in_out, p_description, v_id_currency, v_id_currency_payment,
            p_unf_price, p_unf_vat_rate, p_unf_vat_amount,
            p_unf_total_amount, p_id_parent, p_id_sibling, p_username,
            coalesce(p_managed_by, p_username))
    returning id into r_result;

    -- выставляем флаг невалидности
    perform pkg_contract.is_contract_valid(
            p_id_contract => r_result,
            p_username => p_username
            );

    return r_result;
END;
$BODY$;

ALTER FUNCTION pkg_contract.ins_contract(character varying, character varying, character varying, character varying, date,
                                         date, date, character varying, integer, character, character varying, integer, integer,
                                         numeric, numeric, numeric, numeric, character varying, bigint, bigint, boolean, character varying)
    OWNER TO rightsflow;

DROP FUNCTION IF EXISTS pkg_contract.upd_contract(bigint, character varying, character varying, character varying, character varying,
                                                  date, date, date, integer, character, character varying, integer, integer, numeric,
                                                  numeric, numeric, numeric, integer, character varying, bigint, bigint, boolean);

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
    p_id_parent bigint DEFAULT NULL::bigint,
    p_id_sibling bigint DEFAULT NULL::bigint,
    p_bypass boolean DEFAULT false,
    p_managed_by character varying DEFAULT NULL::character varying
)
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
        id_parent = p_id_parent,
        id_sibling = p_id_sibling,
        updated_by = p_username,
        updated_at = current_timestamp,
        managed_by = coalesce(p_managed_by, managed_by)
    where
        id = p_id;

    if v_old.id_org != coalesce(v_id_org, v_old.id_org) or coalesce(v_old.id_org_party, 1) != coalesce(v_id_org_party, 1) then
        call pkg_contract.make_change_buffer(p_action => 'UPDATE', p_username => p_username, p_id_contract => p_id);
    end if;

    return p_id;
END;
$BODY$;

ALTER FUNCTION pkg_contract.upd_contract(bigint, character varying, character varying, character varying, character varying,
                                         date, date, date, integer, character, character varying, integer, integer, numeric,
                                         numeric, numeric, numeric, integer, character varying, bigint, bigint, boolean, character varying)
    OWNER TO rightsflow;

