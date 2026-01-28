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
    v_simple_arr := strpos(p_id_oip_str, ';') = 0;
    if v_simple_arr = false then
        v_array := string_to_array(p_id_oip_str,';');
        insert into temp_res (parents, id_oip)
        select split_part(a1.val, ':', 1)::text as parents,
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