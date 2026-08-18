INSERT INTO lov_contract_status(id, id_contract_type, name, def, mode, code)
VALUES (4, 1, 'Отменена', false, 1, 'CANCELED')
ON CONFLICT (ID) DO NOTHING;

INSERT INTO lov_contract_status(id, id_contract_type, name, def, mode, code)
VALUES (14, 2, 'Отменен', false, 1, 'CANCELED')
ON CONFLICT (ID) DO NOTHING;

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
    v_curr_status        lov_contract_status%rowtype;   -- текущий статус контракта
    v_target_status      lov_contract_status%rowtype;   -- итоговый статус, в который переводим
    v_target_code        lov_contract_status.code%type; -- вычисленный целевой код
    v_input_code         lov_contract_status.code%type := upper(p_status_code);
    v_warning            contract.warning%type;
    v_need_disable_check boolean := false;
BEGIN
    -- Проверка доступа
    PERFORM pkg_contract.check_contract_org_access(p_id_contract, p_username, p_bypass);

    -- 1) Получаем контракт + его текущий статус и сразу блокируем строку
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
    if v_input_code = 'DRAFT' and v_curr_status.code in ('ARCHIVE', 'APPROVED','CANCELED') then
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

    elsif v_input_code = 'CANCELED' and v_curr_status.code = 'DRAFT' then
        v_target_code := 'CANCELED';

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

DROP FUNCTION IF EXISTS pkg_contract.ins_contract(character varying, character varying, character varying, character varying,
                                                  date, date, date, character varying, integer, character, character varying,
                                                  integer, integer, numeric, numeric, numeric, numeric, character varying, boolean);

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
    p_id_parent bigint DEFAULT NULL,
    p_id_sibling bigint DEFAULT NULL,
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
                          unf_vat_amount, unf_total_amount, id_parent, id_sibling, created_by)
    values (p_guid, v_num, v_id_org, v_id_org_party, v_validity_period,
            p_contract_date, v_id_contract_type, p_status_1c, v_id_contract_status,
            p_in_out, p_description, v_id_currency, v_id_currency_payment,
            p_unf_price, p_unf_vat_rate, p_unf_vat_amount,
            p_unf_total_amount, p_id_parent, p_id_sibling, p_username)
    returning id into r_result;

    return r_result;
END;
$BODY$;

ALTER FUNCTION pkg_contract.ins_contract(character varying, character varying, character varying, character varying, date,
    date, date, character varying, integer, character, character varying, integer, integer, numeric, numeric, numeric, numeric,
    character varying, bigint, bigint, boolean)
    OWNER TO rightsflow;

DROP FUNCTION IF EXISTS pkg_contract.upd_contract(bigint, character varying, character varying, character varying, character varying,
                                                  date, date, date, integer, character, character varying, integer, integer, numeric,
                                                  numeric, numeric, numeric, integer, character varying, boolean);

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
    p_id_parent bigint DEFAULT NULL,
    p_id_sibling bigint DEFAULT NULL,
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
        id_parent = p_id_parent,
        id_sibling = p_id_sibling,
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

ALTER FUNCTION pkg_contract.upd_contract(bigint, character varying, character varying, character varying, character varying,
    date, date, date, integer, character, character varying, integer, integer, numeric, numeric, numeric, numeric, integer,
    character varying, bigint, bigint, boolean)
    OWNER TO rightsflow;

