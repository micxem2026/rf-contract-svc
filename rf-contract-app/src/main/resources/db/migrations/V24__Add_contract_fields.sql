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
$BODY$;