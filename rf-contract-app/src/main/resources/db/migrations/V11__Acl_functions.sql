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