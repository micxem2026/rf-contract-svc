create schema if not exists pkg_acl;
alter schema pkg_acl owner to rightsflow;

CREATE OR REPLACE FUNCTION pkg_pge.is_numeric(
    str text)
    RETURNS boolean
    LANGUAGE 'plpgsql'
    COST 100
    IMMUTABLE STRICT SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

begin
    -- Быстрая проверка через регулярное выражение
    if str !~ '^-?(0|[1-9][0-9]*)(\.[0-9]+)?$' then
        return false;
    end if;

    -- Точная проверка
    perform str::numeric;
    return true;
exception
    when others then
        return false;
end;
$BODY$;

ALTER TABLE IF EXISTS contract
    ALTER COLUMN description TYPE text;

ALTER TABLE IF EXISTS contract
    ADD COLUMN IF NOT EXISTS id_target_cs integer;
ALTER TABLE IF EXISTS contract
    ADD COLUMN IF NOT EXISTS id_contract_vp integer;

ALTER TABLE IF EXISTS license
    ALTER COLUMN description TYPE text;

-----

create or replace function pkg_acl.sync_contract(p_id bigint,
                                                 p_guid character varying,
                                                 p_num character varying,
                                                 p_id_org integer,
                                                 p_id_org_party integer,
                                                 p_id_cparty integer,
                                                 p_beg_date date,
                                                 p_end_date date,
                                                 p_contract_date date,
                                                 p_id_contract_type integer,
                                                 p_id_contract_status integer,
                                                 p_id_target_cs integer,
                                                 p_id_contract_vp integer,
                                                 p_in_out char(2),
                                                 p_description text,
                                                 p_id_currency integer,
                                                 p_id_currency_payment integer,
                                                 p_drop_flag boolean
) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
declare
    v_id bigint;
    v_cur_mode integer;
    v_contract contract%rowtype;
    v_validity_period daterange;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    -- Если такой контракт существует, то нужно его перевести в режим черновика
    if exists(select 1 from contract where id = p_id) then
        select c.* into v_contract from contract c where c.id = p_id for update;
        select cs.mode into v_cur_mode from contract c
          join lov_contract_status cs on cs.id = c.id_contract_status
            where c.id = p_id;
        if v_cur_mode <> 0 then
            select pkg_contract.upd_contract_status(p_id, 'DRAFT', 'system') into v_id;
            select cs.mode into v_cur_mode from contract c
              join lov_contract_status cs on cs.id = c.id_contract_status
                where c.id = p_id;
            if v_cur_mode <> 0 then
                raise exception 'Сбой перевода контракта в режим черновика, id=%', p_id;
            end if;
        end if;
    end if;

    if p_drop_flag then
        v_id := -1;
        call pkg_contract.del_contract(p_id, 'system', true);
    else
        v_validity_period := daterange(p_beg_date, p_end_date, '[]');
        -- добавляем или обновляем
        insert into contract (id, guid, num, id_org, id_org_party, in_out, validity_period, contract_date, id_contract_type,
                              id_contract_status, id_target_cs, id_contract_vp, description, id_currency, id_currency_payment,
                              created_by)
        values (p_id, p_guid, p_num, p_id_org, p_id_org_party, p_in_out,
                v_validity_period, p_contract_date, p_id_contract_type,
                p_id_contract_status, p_id_target_cs, p_id_contract_vp,
                p_description, p_id_currency, p_id_currency_payment, 'system')
        on conflict (id) do update set
           guid = excluded.guid,
           num = excluded.num,
           id_org = excluded.id_org,
           id_org_party = excluded.id_org_party,
           in_out = excluded.in_out,
           validity_period = excluded.validity_period,
           contract_date = excluded.contract_date,
           id_contract_type = excluded.id_contract_type,
           id_contract_status = excluded.id_contract_status,
           id_target_cs = excluded.id_target_cs,
           id_contract_vp = excluded.id_contract_vp,
           description = excluded.description,
           id_currency = excluded.id_currency,
           id_currency_payment = excluded.id_currency_payment,
           updated_by = 'system',
           updated_at = current_timestamp
        returning id into v_id;
        -- для VP всегда один контрагент, поэтому можно смело удалять по id контракта
        if p_id_cparty is not null then
           delete from contract_counterparty where id_contract = p_id;
           insert into contract_counterparty (id_contract, id_cpart, created_by)
           values (p_id, p_id_cparty, 'system');
        end if;
    end if;
    return v_id;

exception
    when lock_not_available then
        raise exception 'Сделка % заблокирована другим процессом', p_num;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

create or replace function pkg_acl.comp_contract(p_id bigint) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
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
        call pkg_contract.del_contract(p_id, 'system', true);
    end if;

    return v_id;

exception
    when lock_not_available then
        raise exception 'Сделка ID=% заблокирована другим процессом', p_id;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

create or replace function pkg_acl.sync_license(p_id bigint,
                                                p_id_contract bigint,
                                                p_id_lic_format bigint,
                                                p_guid character varying,
                                                p_num character varying,
                                                p_name character varying,
                                                p_price numeric,
                                                p_vat_rate numeric(10,2),
                                                p_vat_amount numeric,
                                                p_total_amount numeric,
                                                p_description text,
                                                p_beg_date date,
                                                p_end_date date,
                                                p_drop_flag boolean
) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
declare
    v_id bigint;
    v_license license%rowtype;
    v_validity_period daterange;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    if exists(select 1 from license where id = p_id) then
        select l.* into v_license from license l where l.id = p_id for update;
    end if;

    if p_drop_flag then
        v_id := -1;
        call pkg_contract.del_license(p_id, 'system', true);
    else
        v_validity_period := daterange(p_beg_date, p_end_date, '[]');
        -- добавляем или обновляем
        insert into license (id, id_contract, id_lic_format, guid, num, name, price, vat_rate, vat_amount, total_amount,
                             validity_period, description, created_by)
        values (p_id, p_id_contract,  p_id_lic_format, p_guid, p_num, p_name,
                p_price, p_vat_rate, p_vat_amount, p_total_amount,v_validity_period,
                p_description, 'system')
        on conflict (id) do update set
           id_contract = excluded.id_contract,
           id_lic_format = excluded.id_lic_format,
           guid = excluded.guid,
           num = excluded.num,
           name = excluded.name,
           price = excluded.price,
           vat_rate = excluded.vat_rate,
           vat_amount = excluded.vat_amount,
           total_amount = excluded.total_amount,
           validity_period = excluded.validity_period,
           description = excluded.description,
           updated_by = 'system',
           updated_at = current_timestamp
        returning id into v_id;
    end if;
    return v_id;

exception
    when lock_not_available then
        raise exception 'Лицензия % заблокирована другим процессом', p_num;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

create or replace function pkg_acl.sync_license_oip(p_id bigint,
                                                    p_id_license bigint,
                                                    p_id_oip integer,
                                                    p_id_root_oip integer,
                                                    p_parents text,
                                                    p_drop_flag boolean
) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
declare
    v_id bigint;
    v_license_oip license_oip%rowtype;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    if exists(select 1 from license_oip where id = p_id) then
        select l.* into v_license_oip from license_oip l where l.id = p_id for update;
    end if;

    if p_drop_flag then
        v_id := -1;
        call pkg_contract.del_license_oip(p_id, 'system');
    else
        -- добавляем или обновляем
        insert into license_oip (id, id_license, id_oip, id_root_oip, parents, created_by)
        values (p_id, p_id_license, p_id_oip, p_id_root_oip, p_parents, 'system')
        on conflict (id) do update set
           id_license = excluded.id_license,
           id_oip = excluded.id_oip,
           id_root_oip = excluded.id_root_oip,
           parents = excluded.parents,
           updated_by = 'system',
           updated_at = current_timestamp
        returning id into v_id;
    end if;
    return v_id;

exception
    when lock_not_available then
        raise exception 'ОИС лицензии [ID=%] заблокирован другим процессом', p_id;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

create or replace function pkg_acl.sync_license_rights(p_id bigint,
                                                       p_id_license bigint,
                                                       p_hb_start_date date,
                                                       p_hb_days integer,
                                                       p_drop_flag boolean
) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
declare
    v_id bigint;
    v_license_rights license_rights%rowtype;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    if exists(select 1 from license_rights where id = p_id) then
        select l.* into v_license_rights from license_rights l where l.id = p_id for update;
    end if;

    if p_drop_flag then
        v_id := -1;
        call pkg_contract.del_license_rights(p_id, 'system', true);
    else
        -- добавляем или обновляем
        insert into license_rights (id, id_license, hb_start_date, hb_days, created_by)
        values (p_id, p_id_license, p_hb_start_date, p_hb_days, 'system')
        on conflict (id) do update set
           id_license = excluded.id_license,
           hb_start_date = excluded.hb_start_date,
           hb_days = excluded.hb_days,
           updated_by = 'system',
           updated_at = current_timestamp
        returning id into v_id;
    end if;
    return v_id;

exception
    when lock_not_available then
        raise exception 'Право лицензии [ID=%] заблокировано другим процессом', p_id;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

create or replace function pkg_acl.sync_license_rights_rt(p_id bigint,
                                                          p_id_lic_rights bigint,
                                                          p_id_right_type integer,
                                                          p_showings integer,
                                                          p_id_tech_repeat integer,
                                                          p_catch_forward smallint,
                                                          p_catch_up smallint,
                                                          p_id_report_period integer,
                                                          p_id_report_currency integer,
                                                          p_id_distrib_channel integer,
                                                          p_id_goods_country integer,
                                                          p_id_cont_quality integer,
                                                          p_id_lang_subtitle integer,
                                                          p_id_lang_voiceover integer,
                                                          p_id_lang_of_use integer,
                                                          p_drop_flag boolean
) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
declare
    v_id bigint;
    v_updates text;
    v_rt record;
    v_license_rights_rt license_rights_rt%rowtype;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    if exists(select 1 from license_rights_rt where id = p_id) then
        select l.* into v_license_rights_rt from license_rights_rt l where l.id = p_id for update;
    end if;

    if p_drop_flag then
        v_id := -1;
        delete from license_rights_rt where id = p_id;
    else
        -- добавляем или обновляем
        insert into license_rights_rt (id, id_lic_rights, id_right_type, created_by)
        values (p_id, p_id_lic_rights, p_id_right_type, 'system')
        on conflict (id) do update set
           id_lic_rights = excluded.id_lic_rights,
           id_right_type = excluded.id_right_type,
           updated_by = 'system',
           updated_at = current_timestamp
        returning id into v_id;

        if v_id is not null then

            select * into v_rt from sync__klf_right_type where id = p_id_right_type;
            SELECT json_agg(t) INTO v_updates
            FROM (
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'shows' as property,
                            p_showings::character varying as value where p_showings is not null and v_rt.id_right_group = 1
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'techReplyProfile' as property,
                            p_id_tech_repeat::character varying as value where p_id_tech_repeat is not null and v_rt.id_right_group = 1
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'catchForward' as property,
                            'true' as value where p_catch_forward is not null and p_catch_forward > 0 and v_rt.id_right_group = 2
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'quantityCatchForward' as property,
                            p_catch_forward::character varying as value where p_catch_forward is not null and p_catch_forward > 0 and v_rt.id_right_group = 2
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'catchForward' as property,
                            'false' as value where p_catch_forward is not null and p_catch_forward < 1 and v_rt.id_right_group = 2
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'catchUp' as property,
                            'true' as value where p_catch_up is not null and p_catch_up > 0 and v_rt.id_right_group = 2
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'quantityCatchUp' as property,
                            p_catch_up::character varying as value where p_catch_up is not null and p_catch_up > 0 and v_rt.id_right_group = 2
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'catchUp' as property,
                            'false' as value where p_catch_up is not null and p_catch_up < 1 and v_rt.id_right_group = 2
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'distributionChannel' as property,
                            p_id_distrib_channel::character varying as value where p_id_distrib_channel is not null and v_rt.id_right_group = 3
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'goodsOriginCountry' as property,
                            p_id_goods_country::character varying as value where p_id_goods_country is not null and v_rt.id_right_group = 3
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'contentQuality' as property,
                            p_id_cont_quality::character varying as value where p_id_cont_quality is not null and v_rt.id_right_group in (1,2)
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'subtitleLanguage' as property,
                            p_id_lang_subtitle::character varying as value where p_id_lang_subtitle is not null and v_rt.id_right_group in (1,2)
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'voiceLanguage' as property,
                            p_id_lang_voiceover::character varying as value where p_id_lang_voiceover is not null and v_rt.id_right_group in (1,2)
                     union all
                     select v_id as id_entity, 'PG_RT_COND' as code_pg, 'allowedUseLanguage' as property,
                            p_id_lang_of_use::character varying as value where p_id_lang_of_use is not null and v_rt.id_right_group = 3
                     union all
                     select v_id as id_entity, 'PG_FIN_COND' as code_pg, 'usageReportFrequency' as property,
                            p_id_report_period::character varying as value where p_id_report_period is not null
                     union all
                     select v_id as id_entity, 'PG_FIN_COND' as code_pg, 'reportCurrency' as property,
                            p_id_report_currency::character varying as value where p_id_report_currency is not null
                 ) t;
                 perform pkg_pge.update_properties_batch(v_updates, 'system');
        end if;

    end if;
    return v_id;

exception
    when lock_not_available then
        raise exception 'Способ использования права лицензии [ID=%] заблокирован другим процессом', p_id;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

create or replace function pkg_acl.sync_license_rt_feature_set(p_id bigint,
                                                               p_id_lic_rights bigint,
                                                               p_is_exclusive boolean,
                                                               p_is_use_right boolean,
                                                               p_is_sub_license boolean,
                                                               p_beg_date date,
                                                               p_end_date date,
                                                               p_drop_flag boolean
) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
declare
    v_id bigint;
    v_validity_period daterange;
    v_license_rt_fs license_rt_feature_set%rowtype;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    if exists(select 1 from license_rt_feature_set where id = p_id) then
        select l.* into v_license_rt_fs from license_rt_feature_set l where l.id = p_id for update;
    end if;

    if p_drop_flag then
        v_id := -1;
        call pkg_contract.del_license_rt_feature_set(p_id, 'system', true);
    else
        -- добавляем или обновляем
        v_validity_period := daterange(p_beg_date, p_end_date, '[]');
        insert into license_rt_feature_set (id, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
        values (p_id, p_id_lic_rights, p_is_exclusive, p_is_use_right, p_is_sub_license,
                v_validity_period, 'system')
        on conflict (id) do update set
           id_lic_rights = excluded.id_lic_rights,
           is_exclusive = excluded.is_exclusive,
           is_use_right = excluded.is_use_right,
           is_sub_license = excluded.is_sub_license,
           validity_period = excluded.validity_period,
           updated_by = 'system',
           updated_at = current_timestamp
        returning id into v_id;
    end if;
    return v_id;

exception
    when lock_not_available then
        raise exception 'Набор характеристик [ID=%] заблокирован другим процессом', p_id;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

create or replace function pkg_acl.sync_license_rt_features(p_id bigint,
                                                            p_id_lic_rights bigint,
                                                            p_id_feature_set bigint,
                                                            p_id_feature_category integer,
                                                            p_id_feature integer,
                                                            p_is_included boolean,
                                                            p_is_native boolean,
                                                            p_drop_flag boolean
) returns bigint
    security definer
    set search_path = rightsflow
    language plpgsql
    volatile parallel unsafe
as
$$
declare
    v_id bigint;
    v_license_rt_ft license_rt_features%rowtype;
begin
    -- Установить таймаут только для этой функции
    SET LOCAL lock_timeout = '30s';
    if exists(select 1 from license_rt_features where id = p_id) then
        select l.* into v_license_rt_ft from license_rt_features l where l.id = p_id for update;
    end if;

    -- отключаем триггер
    perform set_config('rf.disable_features_check', 'true', true);

    if p_drop_flag then
        v_id := -1;
        call pkg_contract.del_license_rt_features(p_id, 'system');
    else
        -- добавляем или обновляем
        insert into license_rt_features (id, id_lic_rights, id_feature_set, id_feature_category, id_feature, is_included,
                                         is_native, created_by)
                    values (p_id, p_id_lic_rights, p_id_feature_set, p_id_feature_category,
                            p_id_feature, p_is_included, p_is_native, 'system')
        on conflict (id) do update set
           id_lic_rights = excluded.id_lic_rights,
           id_feature_set = excluded.id_feature_set,
           id_feature_category = excluded.id_feature_category,
           id_feature = excluded.id_feature,
           is_included = excluded.is_included,
           is_native = excluded.is_native,
           updated_by = 'system',
           updated_at = current_timestamp
        returning id into v_id;
    end if;
    return v_id;

exception
    when lock_not_available then
        raise exception 'Характеристика [ID=%] заблокирована другим процессом', p_id;

    when others then
        raise exception 'Ошибка: % (код: %)', SQLERRM, SQLSTATE;

end;
$$;

CREATE OR REPLACE FUNCTION pkg_contract.a2_check_license_rt_features()
    RETURNS trigger
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE NOT LEAKPROOF SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

declare
    new_subtree_ids int[];
    new_ancestor_ids int[];
    conflict_count int;
    v_cnt int;
begin

    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_features_check', true), ''), 'false') = 'true' then
       return NEW; -- пропустить проверку
    end if;

    -- собрать id узла + всех его потомков
    with recursive subtree as (
        select id, id_parent
        from sync__klf_feature_tree
        where id = NEW.id_feature
        union all
        select t.id, t.id_parent
        from sync__klf_feature_tree t
                 join subtree s on t.id_parent = s.id
    )
    select array_agg(id) into new_subtree_ids from subtree;

    -- собрать id узла + всех его предков
    with recursive ancestors as (
        select id, id_parent
        from sync__klf_feature_tree
        where id = NEW.id_feature
        union all
        select p.id, p.id_parent
        from sync__klf_feature_tree p
                 join ancestors a on a.id_parent = p.id
    )
    select array_agg(id) into new_ancestor_ids from ancestors;

    if NEW.is_included then
        -- Проверка: новый включённый узел не должен пересекаться с другими включёнными
        select count(*) into conflict_count
        from license_rt_features f
        where f.id_feature_set = NEW.id_feature_set
          and f.id <> NEW.id
          and f.is_included = true
          and (
            f.id_feature = any(new_subtree_ids) or
            f.id_feature = any(new_ancestor_ids)
            );

        if conflict_count > 0 then
            raise exception
                'Ошибка: включаемая характеристика [id_feature=%] пересекается с уже включённой в наборе [id_feature_set=%]',
                NEW.id_feature, NEW.id_feature_set
                using errcode = 20116;
        end if;

    else
        -- Проверка: новый исключённый узел должен пересекаться хотя бы с одной включённой
        select count(*) into conflict_count
        from license_rt_features f
        where f.id_feature_set = NEW.id_feature_set
          and f.id <> NEW.id
          and f.is_included = true
          and f.id_feature = any(new_ancestor_ids);

        if conflict_count = 0 then
            raise exception
                'Ошибка: исключаемая характеристика [id_feature=%] не пересекается ни с одной включённой в наборе [id_feature_set=%]',
                NEW.id_feature, NEW.id_feature_set
                using errcode = 20117;
        end if;

        select count(*) into v_cnt
        from license_rt_features f
        where f.id_feature_set = NEW.id_feature_set
          and f.id <> NEW.id
          and f.id_feature = NEW.id_feature
          and f.is_included = false;

        if v_cnt > 0 then
            raise exception
                'Ошибка: исключаемая характеристика [id_feature=%] уже исключена в наборе [id_feature_set=%]',
                NEW.id_feature, NEW.id_feature_set
                using errcode = 20118;
        end if;

    end if;

    return NEW;
end;
$BODY$;

CREATE OR REPLACE FUNCTION pkg_pge.update_property(
    p_code_pg character varying,
    p_property character varying,
    p_id_entity bigint,
    p_value character varying,
    p_username character varying DEFAULT 'admin'::character varying)
    RETURNS TABLE(id_entity bigint, id integer, id_pgl integer, id_property integer, pg_order integer, code_prop character varying, name_prop character varying, id_prop_type integer, name_prop_type character varying, property_value character varying, use_multi_select boolean, display_value text)
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    ROWS 1000

    SET search_path=rightsflow
AS $BODY$

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
exception
  when others then
    raise exception 'Ошибка в функции update_property(p_code_pg => %, p_property => %, p_id_entity => %, p_value => %): %',
        p_code_pg, p_property, p_id_entity, p_value, SQLERRM;
end;
$BODY$;

----
ALTER TABLE IF EXISTS LICENSE_RIGHTS ADD COLUMN IF NOT EXISTS PRICE DECIMAL NOT NULL DEFAULT 0.0;
ALTER TABLE IF EXISTS LICENSE_RIGHTS ADD COLUMN IF NOT EXISTS VAT_AMOUNT DECIMAL NOT NULL DEFAULT 0.0;
ALTER TABLE IF EXISTS LICENSE_RIGHTS ADD COLUMN IF NOT EXISTS TOTAL_AMOUNT DECIMAL NOT NULL DEFAULT 0.0;
ALTER TABLE IF EXISTS LICENSE_RIGHTS ADD COLUMN IF NOT EXISTS DESCRIPTION TEXT;

DROP FUNCTION IF EXISTS pkg_contract.ins_license_rights(bigint, text, date, integer, character varying);

CREATE OR REPLACE FUNCTION pkg_contract.ins_license_rights(
    p_id_license bigint,
    p_id_right_types text,
    p_hb_start_date date,
    p_hb_days integer,
    p_price numeric,
    p_vat_amount numeric,
    p_total_amount numeric,
    p_description text,
    p_username character varying)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$


DECLARE
    r_result bigint;
    v_license license%rowtype;
    v_array integer[];
    v_cnt integer;
BEGIN

    begin
        v_array := string_to_array(p_id_right_types, ',')::integer[];
    exception
        when invalid_text_representation then
            raise notice 'невалидное значение в строке';
            v_array := null;
    end;

    if v_array is null then
        raise exception 'Ошибка создания права! Не обнаружено ни одного типа права!'
            using errcode = 20100;
    end if;

    select count(*) into v_cnt from sync__klf_right_type as rt
      right join unnest(v_array) as rt0(id) on rt.id = rt0.id
    where rt.id is null;

    if v_cnt > 0 then
        raise exception 'Ошибка создания права! Передано некорректное значение типа права!'
            using errcode = 20100;
    end if;
/*
    select count(distinct coalesce(rt.id_parent, -1)) into v_cnt from sync__klf_right_type as rt
       join unnest(v_array) as rt0(id) on rt.id = rt0.id;

    if v_cnt > 1 then
        raise exception 'Ошибка создания права! Типы прав из разных поддеревьев дерева прав в одной привязке права к лицензии не поддерживается!'
            using errcode = 20100;
    end if;
*/
    if p_id_license is null then
        raise exception 'Ошибка создания права! Не указан идентификатор лицензии (p_id_license)!'
            using errcode = 20106;
    end if;

    if p_hb_start_date is not null and coalesce(p_hb_days, 0) <= 0 then
        raise exception 'Количество дней холдбэка должно быть больше 0!'
            using errcode = 20107;
    end if;

    select * into v_license from license where id = p_id_license;

    if p_hb_start_date is not null and not (p_hb_start_date <@ v_license.validity_period) then
        raise exception 'Дата начала холдбэка [%] не попадает в период действия лицензии [%]', p_hb_start_date, v_license.validity_period
            using errcode = 20108;
    end if;

    insert into license_rights (id_license, hb_start_date, hb_days, price, vat_amount, total_amount, description, created_by)
    values (p_id_license, p_hb_start_date,
            case when p_hb_start_date is not null then p_hb_days end, p_price, p_vat_amount,
            p_total_amount, p_description, p_username)
    returning id into r_result;

    insert into license_rights_rt (id_lic_rights, id_right_type, created_by)
    select r_result,rt.id, p_username from unnest(v_array) as rt(id);

    return r_result;
END;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.upd_license_rights(bigint, bigint, text, date, integer, character varying);

CREATE OR REPLACE FUNCTION pkg_contract.upd_license_rights(
    p_id bigint,
    p_id_license bigint,
    p_id_right_types text,
    p_hb_start_date date,
    p_hb_days integer,
    p_price numeric,
    p_vat_amount numeric,
    p_total_amount numeric,
    p_description text,
    p_username character varying)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

DECLARE
    v_license license%rowtype;
    v_license_rights license_rights%rowtype;
    v_array integer[];
    v_cnt integer;
BEGIN

    begin
        v_array := string_to_array(p_id_right_types, ',')::integer[];
    exception
        when invalid_text_representation then
            raise notice 'невалидное значение в строке';
            v_array := null;
    end;

    if v_array is not null then
        select count(*) into v_cnt from sync__klf_right_type as rt
          right join unnest(v_array) as rt0(id) on rt.id = rt0.id
        where rt.id is null;
        if v_cnt > 0 then
            raise exception 'Ошибка обновления права! Передано некорректное значение типа права!'
                using errcode = 20100;
        end if;
        /*
        select count(distinct coalesce(rt.id_parent, -1)) into v_cnt from sync__klf_right_type as rt
          join unnest(v_array) as rt0(id) on rt.id = rt0.id;
        if v_cnt > 1 then
            raise exception 'Ошибка создания права! Типы прав из разных поддеревьев дерева прав в одной привязке права к лицензии не поддерживается!'
                using errcode = 20100;
        end if;
        */
    end if;

    if p_id_license is null then
        raise exception 'Ошибка обновления права! Не указан идентификатор лицензии (p_id_license)!'
            using errcode = 20109;
    end if;

    if p_hb_start_date is not null and coalesce(p_hb_days, 0) <= 0 then
        raise exception 'Количество дней холдбэка должно быть больше 0!'
            using errcode = 20107;
    end if;

    select * into v_license from license where id = p_id_license;

    if p_hb_start_date is not null and not (p_hb_start_date <@ v_license.validity_period) then
        raise exception 'Дата начала холдбэка [%] не попадает в период действия лицензии [%]', p_hb_start_date, v_license.validity_period
            using errcode = 20108;
    end if;

    select * into v_license_rights from license_rights where id = p_id for update;

    if coalesce(v_license_rights.hb_start_date, '1900-01-01'::date) != coalesce(p_hb_start_date, '1900-01-01'::date) or
       coalesce(v_license_rights.hb_days, 0) != coalesce(p_hb_days, 0) then
        call pkg_contract.make_change_buffer(p_action => 'UPDATE', p_username => p_username, p_id_lic_rights => p_id);
    end if;

    update license_rights
    set
        hb_start_date = p_hb_start_date,
        hb_days = case when p_hb_start_date is null then null else p_hb_days end,
        price = coalesce(p_price, price),
        vat_amount = coalesce(p_vat_amount, vat_amount),
        total_amount = coalesce(p_total_amount, total_amount),
        description = p_description,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    if v_array is not null then
        delete from license_rights_rt
        where id_lic_rights = p_id;
        insert into license_rights_rt (id_lic_rights, id_right_type, created_by)
        select p_id, rt.id, p_username from unnest(v_array) as rt(id);
    end if;

    return p_id;
END;
$BODY$;
