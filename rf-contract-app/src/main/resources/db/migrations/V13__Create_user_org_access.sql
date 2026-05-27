-- ============================================================
-- V13: Привязка пользователей к организациям и функции контроля доступа
--
-- Добавляет:
--   user_org_access                        — таблица привязки пользователь → организация
--   pkg_contract.check_contract_org_access — проверка доступа к контракту (DML)
--   pkg_contract.check_license_org_access  — проверка через license → contract
--   pkg_contract.check_license_rights_org_access — через license_rights → license → contract
--   pkg_contract.check_feature_set_org_access    — через feature_set → ... → contract
-- ============================================================
SET search_path = rightsflow;

CREATE OR REPLACE FUNCTION pkg_contract.get_contract_id(
    p_id_contract_cparty bigint DEFAULT NULL::bigint,
    p_id_license bigint DEFAULT NULL::bigint,
    p_id_lic_oip bigint DEFAULT NULL::bigint,
    p_id_lic_rights bigint DEFAULT NULL::bigint,
    p_id_feature_set bigint DEFAULT NULL::bigint)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    STABLE SECURITY DEFINER PARALLEL SAFE
    SET search_path=rightsflow
AS $BODY$

DECLARE
    r_result bigint := null;
BEGIN

    if p_id_contract_cparty is not null then
        select id_contract from contract_counterparty cc
        where cc.id = p_id_contract_cparty
        into r_result;
    end if;

    if p_id_license is not null and r_result is null then
        select id_contract from license l
        where l.id = p_id_license
        into r_result;
    end if;

    if p_id_lic_oip is not null and r_result is null then
        select l.id_contract from license_oip lo
        join license l on l.id = lo.id_license
        where lo.id = p_id_lic_oip
        into r_result;
    end if;

    if p_id_lic_rights is not null and r_result is null then
        select l.id_contract from license_rights lr
        join license l on l.id = lr.id_license
        where lr.id = p_id_lic_rights
        into r_result;
    end if;

    if p_id_feature_set is not null and r_result is null then
        select l.id_contract from license_rt_feature_set fs
        join license_rights lr on lr.id = fs.id_lic_rights
        join license l on l.id = lr.id_license
        where fs.id = p_id_feature_set
        into r_result;
    end if;

    if r_result is null then
        raise exception 'Невозможно определить контракт по указанным параметрам [p_id_contract_cparty=%], [p_id_license=%], [p_id_lic_oip=%], [p_id_lic_rights=%], [p_id_feature_set=%]',
            p_id_contract_cparty, p_id_license, p_id_lic_oip, p_id_lic_rights, p_id_feature_set
            using errcode = '20119';
    end if;

    return r_result;
END;
$BODY$;


-- ============================================================
-- Таблица привязки пользователей к организациям
-- ============================================================
CREATE TABLE IF NOT EXISTS user_org_access (
   id          BIGSERIAL    NOT NULL PRIMARY KEY,
   username    VARCHAR(50)  NOT NULL,
   id_org      INTEGER      NOT NULL REFERENCES sync__klf_organization(id) ON DELETE CASCADE,
   created_by  VARCHAR(50)  NOT NULL,
   created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
   CONSTRAINT  unq_user_org_access UNIQUE (username, id_org)
);

COMMENT ON TABLE  user_org_access            IS 'Привязка пользователей к организациям для разграничения доступа к контрактам';
COMMENT ON COLUMN user_org_access.username   IS 'Username из rf-auth-svc (соответствует JWT sub claim)';
COMMENT ON COLUMN user_org_access.id_org     IS 'ID организации из sync__klf_organization';
COMMENT ON COLUMN user_org_access.created_by IS 'Username администратора, создавшего привязку';

CREATE INDEX IF NOT EXISTS idx_user_org_access_username ON user_org_access(username);
CREATE INDEX IF NOT EXISTS idx_user_org_access_id_org   ON user_org_access(id_org);

-- ============================================================
-- Функция: check_contract_org_access
--
-- Проверяет доступ пользователя к контракту через JOIN с user_org_access.
-- Доступ разрешён если contract.id_org ИЛИ contract.id_org_party
-- совпадает с одной из записей пользователя в user_org_access.
--
-- При p_bypass = TRUE — проверка пропускается (ADMIN, SERVICE).
-- При отказе бросает SQLSTATE '42501' → GlobalExceptionHandler → HTTP 403.
-- ============================================================
CREATE OR REPLACE FUNCTION pkg_contract.check_contract_org_access(
    p_id_contract BIGINT,
    p_username    VARCHAR,
    p_bypass      BOOLEAN
)
    RETURNS VOID
    LANGUAGE plpgsql
    STABLE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $$
BEGIN
    IF p_bypass THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM contract c
        JOIN user_org_access uoa ON  uoa.username = p_username
                                AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
        WHERE  c.id = p_id_contract
    ) THEN
        -- Различаем "не найден" и "нет доступа"
        IF NOT EXISTS (SELECT 1 FROM contract WHERE id = p_id_contract) THEN
            RAISE EXCEPTION 'Контракт [ID=%] не найден!', p_id_contract
                USING ERRCODE = '20120';
        END IF;

        RAISE EXCEPTION
            'Доступ запрещён: контракт [ID=%] не принадлежит организациям пользователя "%".',
            p_id_contract, p_username
            USING ERRCODE = '42501';
    END IF;
END;
$$;

COMMENT ON FUNCTION pkg_contract.check_contract_org_access(BIGINT, VARCHAR, BOOLEAN) IS
    'Проверяет доступ пользователя к контракту по user_org_access. '
        'p_bypass = TRUE пропускает проверку (ADMIN/SERVICE). '
        'Бросает SQLSTATE 42501 (→ HTTP 403) при отказе.';


-- ============================================================
-- Функция: check_license_org_access
-- Проверяет доступ через цепочку license → contract.
-- ============================================================
CREATE OR REPLACE FUNCTION pkg_contract.check_license_org_access(
    p_id_license BIGINT,
    p_username   VARCHAR,
    p_bypass     BOOLEAN
)
    RETURNS VOID
    LANGUAGE plpgsql
    STABLE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $$
DECLARE
    v_id_contract BIGINT;
BEGIN
    IF p_bypass THEN RETURN; END IF;

    SELECT id_contract INTO v_id_contract
    FROM   license
    WHERE  id = p_id_license;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Лицензия [ID=%] не найдена!', p_id_license
            USING ERRCODE = '20120';
    END IF;

    PERFORM pkg_contract.check_contract_org_access(v_id_contract, p_username, FALSE);
END;
$$;


-- ============================================================
-- Функция: check_license_rights_org_access
-- Проверяет доступ через цепочку license_rights → license → contract.
-- ============================================================
CREATE OR REPLACE FUNCTION pkg_contract.check_license_rights_org_access(
    p_id_lic_rights BIGINT,
    p_username      VARCHAR,
    p_bypass        BOOLEAN
)
    RETURNS VOID
    LANGUAGE plpgsql
    STABLE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $$
DECLARE
    v_id_license BIGINT;
BEGIN
    IF p_bypass THEN RETURN; END IF;

    SELECT id_license INTO v_id_license
    FROM   license_rights
    WHERE  id = p_id_lic_rights;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Право лицензии [ID=%] не найдено!', p_id_lic_rights
            USING ERRCODE = '20120';
    END IF;

    PERFORM pkg_contract.check_license_org_access(v_id_license, p_username, FALSE);
END;
$$;


-- ============================================================
-- Функция: check_feature_set_org_access
-- Проверяет доступ через цепочку feature_set → license_rights → license → contract.
-- ============================================================
CREATE OR REPLACE FUNCTION pkg_contract.check_feature_set_org_access(
    p_id_feature_set BIGINT,
    p_username       VARCHAR,
    p_bypass         BOOLEAN
)
    RETURNS VOID
    LANGUAGE plpgsql
    STABLE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $$
DECLARE
    v_id_lic_rights BIGINT;
BEGIN
    IF p_bypass THEN RETURN; END IF;

    SELECT id_lic_rights INTO v_id_lic_rights
    FROM   license_rt_feature_set
    WHERE  id = p_id_feature_set;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Набор характеристик [ID=%] не найден!', p_id_feature_set
            USING ERRCODE = '20120';
    END IF;

    PERFORM pkg_contract.check_license_rights_org_access(v_id_lic_rights, p_username, FALSE);
END;
$$;

-- ============================================================
-- Доработка ins_contract: проверка org доступа при создании
-- ============================================================
DROP FUNCTION IF EXISTS pkg_contract.ins_contract(character varying, character varying, character varying, integer,
                                                  date, date, date, integer, character, character varying, integer,
                                                  integer, character varying);

CREATE OR REPLACE FUNCTION pkg_contract.ins_contract(
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
    p_username character varying,
    p_bypass   boolean default false)
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

    insert into contract (guid, num, id_org, id_org_party, validity_period,contract_date, id_contract_type,
                          id_contract_status, in_out, description, id_currency, id_currency_payment, created_by)
    values (p_guid, v_num, v_id_org, v_id_org_party, v_validity_period,
            p_contract_date, v_id_contract_type, v_id_contract_status,
            p_in_out, p_description, v_id_currency, v_id_currency_payment,
            p_username)
    returning id into r_result;

    return r_result;
END;
$BODY$;

-- ============================================================
-- Доработка upd_contract: проверка доступа перед обновлением
-- ============================================================
DROP FUNCTION IF EXISTS pkg_contract.upd_contract(bigint, character varying, character varying, character varying,
                                                  integer, date, date, date, integer, character, character varying,
                                                  integer, integer, character varying);

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
    p_username character varying,
    p_bypass boolean default false)
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
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$BODY$;

-- ============================================================
-- Доработка del_contract: проверка доступа перед удалением
-- ============================================================
DROP PROCEDURE IF EXISTS pkg_contract.del_contract(bigint, character varying, boolean);

CREATE OR REPLACE PROCEDURE pkg_contract.del_contract(
    IN p_id bigint,
    IN p_username character varying,
    IN p_use_cascade boolean default false,
    IN p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

DECLARE
    rec record;
BEGIN
    -- Проверка доступа к контракту перед удалением
    PERFORM pkg_contract.check_contract_org_access(p_id, p_username, p_bypass);

    if p_use_cascade then
        for rec in select * from license where id_contract = p_id loop
                call pkg_contract.del_license(rec.id, p_username, true);
            end loop;
        for rec in select * from contract_counterparty where id_contract = p_id loop
                call pkg_contract.del_contract_counterparty(rec.id, p_username);
            end loop;
    end if;
    delete from contract where id = p_id;

    call pkg_contract.make_contract_outbox_event(
            p_id_contract => p_id,
            p_status_mode => 2,
            p_username => p_username
         );

END;
$BODY$;

-- ============================================================
-- Доработка upd_contract_status: проверка доступа
-- ============================================================
DROP FUNCTION IF EXISTS pkg_contract.upd_contract_status(bigint, character varying, character varying);

CREATE OR REPLACE FUNCTION pkg_contract.upd_contract_status(
    p_id_contract bigint,
    p_status_code character varying,
    p_username character varying,
    IN p_bypass boolean default false)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

declare
    v_contract           contract%rowtype;
    v_curr_status        lov_contract_status%rowtype;   -- текущий статус договора
    v_target_status      lov_contract_status%rowtype;   -- итоговый статус, в который переводим
    v_target_code        lov_contract_status.code%type; -- вычисленный целевой код
    v_input_code         lov_contract_status.code%type := upper(p_status_code);
    v_need_disable_check boolean := false;
begin
    -- Проверка доступа
    PERFORM pkg_contract.check_contract_org_access(p_id_contract, p_username, p_bypass);

    -- 1) Получаем договор + его текущий статус и сразу блокируем строку
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
    select cs.*
    into v_target_status
    from lov_contract_status cs
    where cs.code = v_input_code
      and cs.id_contract_type = v_contract.id_contract_type;

    if not found then
        raise exception 'Статус [code=%] не найден!', p_status_code
            using errcode = '20120';
    end if;

    -- 3) Бизнес-логика переходов: определяем конечный код статуса и спец флаги
    if v_input_code = 'DRAFT' and v_curr_status.code in ('ARCHIVE', 'APPROVED') then
        v_target_code := 'DRAFT';
        v_need_disable_check := true;

    elsif v_input_code = 'ARCHIVE' and v_curr_status.code = 'DRAFT' then
        if pkg_contract.is_contract_valid(p_id_contract => p_id_contract, p_username => p_username) then
            v_target_code := 'ARCHIVE';
        else
            raise notice 'Невозможно изменить статус контракта [ID=%] на ARCHIVE. Контракт не валиден!', p_id_contract;
            return p_id_contract;
        end if;

    elsif v_input_code = 'APPROVED' and v_curr_status.code = 'DRAFT' then
        if pkg_contract.is_contract_valid(p_id_contract => p_id_contract, p_username => p_username) then
            -- Сделка с «родственником» не может быть утверждена -> в ARCHIVE
            if v_contract.id_contract_type = 1 and v_contract.id_sibling is not null then
                v_target_code := 'ARCHIVE';
            else
                v_target_code := 'APPROVED';
            end if;
        else
            raise notice 'Невозможно изменить статус контракта [ID=%] на APPROVED. Контракт не валиден!', p_id_contract;
            return p_id_contract;
        end if;

    else
        -- Любые иные запросы: либо уже в нужном статусе, либо переход запрещён
        if v_curr_status.code = v_input_code then
            return p_id_contract; -- ничего делать не нужно
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

        -- Создаем событие (там используется mode из целевого статуса)
        call pkg_contract.make_contract_outbox_event(
                p_id_contract => p_id_contract,
                p_status_mode => v_target_status.mode,
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
            updated_at        = current_timestamp,
            updated_by        = p_username
        where id = p_id_contract;

        if v_need_disable_check then
            perform set_config('rf.disable_status_check', 'false', true);
        end if;
    end if;

    return p_id_contract;
end;
$BODY$;

-- ============================================================
-- Доработка ins_license: проверка доступа к контракту при создании лицензии
-- (сигнатура не меняется — доступ проверяется через контракт)
-- ============================================================
DROP FUNCTION IF EXISTS pkg_contract.ins_license(character varying, character varying, character varying, bigint, bigint,
                                                 numeric, numeric, numeric, numeric, date, date, character varying, character varying);

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
    p_bypass boolean default false)
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
    else
        v_validity_period := v_contract.validity_period * v_validity_period;
    end if;

    insert into license (id_contract, id_lic_format, guid, num, name, price, vat_rate, vat_amount, total_amount, validity_period, description, created_by)
    values (p_id_contract, p_id_lic_format, p_guid, v_num, p_name, p_price, p_vat_rate,
            p_vat_amount, p_total_amount, v_validity_period, p_description, p_username)
    returning id into r_result;

    return r_result;
END;
$BODY$;


DROP FUNCTION IF EXISTS pkg_contract.upd_license(bigint, character varying, character varying, character varying, bigint,
                                                 numeric, numeric, numeric, numeric, date, date, character varying, character varying);
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
    p_bypass boolean default false)
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
$BODY$;

DROP PROCEDURE IF EXISTS pkg_contract.del_license(bigint, character varying, boolean);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license(
    IN p_id bigint,
    IN p_username character varying,
    IN p_use_cascade boolean default false,
    in p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

DECLARE
    rec record;
BEGIN
    -- Проверка доступа через цепочку license → contract
    PERFORM pkg_contract.check_license_org_access(p_id, p_username, p_bypass);

    if p_use_cascade then
        for rec in select * from license_rights where id_license = p_id loop
                call pkg_contract.del_license_rights(rec.id, p_username, true);
            end loop;
        for rec in select * from license_oip where id_license = p_id loop
                call pkg_contract.del_license_oip(rec.id, p_username);
            end loop;
    end if;
    delete from license where id = p_id;
END;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.ins_license_rights(bigint, text, date, integer, numeric, numeric, numeric, text, character varying);
CREATE OR REPLACE FUNCTION pkg_contract.ins_license_rights(
    p_id_license bigint,
    p_id_right_types text,
    p_hb_start_date date,
    p_hb_days integer,
    p_price numeric,
    p_vat_amount numeric,
    p_total_amount numeric,
    p_description text,
    p_username character varying,
    p_bypass boolean default false)
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
    -- Проверка доступа через цепочку license → contract
    PERFORM pkg_contract.check_license_org_access(p_id_license, p_username, p_bypass);

    begin
        v_array := string_to_array(p_id_right_types, ',')::integer[];
    exception
        when invalid_text_representation then
            raise notice 'невалидное значение в строке';
            v_array := null;
    end;

    if v_array is null then
        raise exception 'Ошибка создания права! Не обнаружено ни одного типа права!'
            using errcode = '20100';
    end if;

    select count(*) into v_cnt from sync__klf_right_type as rt
                                        right join unnest(v_array) as rt0(id) on rt.id = rt0.id
    where rt.id is null;

    if v_cnt > 0 then
        raise exception 'Ошибка создания права! Передано некорректное значение типа права!'
            using errcode = '20100';
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
            using errcode = '20106';
    end if;

    if p_hb_start_date is not null and coalesce(p_hb_days, 0) <= 0 then
        raise exception 'Количество дней холдбэка должно быть больше 0!'
            using errcode = '20107';
    end if;

    select * into v_license from license where id = p_id_license;

    if p_hb_start_date is not null and not (p_hb_start_date <@ v_license.validity_period) then
        raise exception 'Дата начала холдбэка [%] не попадает в период действия лицензии [%]', p_hb_start_date, v_license.validity_period
            using errcode = '20108';
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

DROP FUNCTION IF EXISTS pkg_contract.upd_license_rights(bigint, bigint, text, date, integer, numeric, numeric, numeric, text, character varying);
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
    p_username character varying,
    p_bypass boolean default false)
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

    -- Проверка доступа через цепочку license_rights → license → contract
    PERFORM pkg_contract.check_license_rights_org_access(p_id, p_username, p_bypass);

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
                using errcode = '20100';
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
            using errcode = '20109';
    end if;

    if p_hb_start_date is not null and coalesce(p_hb_days, 0) <= 0 then
        raise exception 'Количество дней холдбэка должно быть больше 0!'
            using errcode = '20107';
    end if;

    select * into v_license from license where id = p_id_license;

    if p_hb_start_date is not null and not (p_hb_start_date <@ v_license.validity_period) then
        raise exception 'Дата начала холдбэка [%] не попадает в период действия лицензии [%]', p_hb_start_date, v_license.validity_period
            using errcode = '20108';
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

DROP PROCEDURE IF EXISTS pkg_contract.del_license_rights(bigint, character varying, boolean);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license_rights(
    IN p_id bigint,
    IN p_username character varying,
    IN p_use_cascade boolean default false,
    IN p_bypass boolean default false)
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
                call pkg_contract.del_license_rt_feature_set(rec.id, p_username, true);
            end loop;
        delete from license_rights_rt where id_lic_rights = p_id;
    end if;
    delete from license_rights where id = p_id;
END;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.ins_license_rt_feature_set(bigint, boolean, boolean, boolean, date, date, character varying);
CREATE OR REPLACE FUNCTION pkg_contract.ins_license_rt_feature_set(
    p_id_lic_rights bigint,
    p_is_exclusive boolean,
    p_is_use_right boolean,
    p_is_sub_license boolean,
    p_beg_date date,
    p_end_date date,
    p_username character varying,
    p_bypass boolean default false)
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
    else
        v_validity_period := v_license.validity_period * v_validity_period;
    end if;

    insert into license_rt_feature_set (id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
    values (p_id_lic_rights, coalesce(p_is_exclusive, false), coalesce(p_is_use_right, false),
            coalesce(p_is_sub_license, false), v_validity_period, p_username)
    returning id into r_result;

    return r_result;
END;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.upd_license_rt_feature_set(bigint, bigint, boolean, boolean, boolean, date, date, character varying);
CREATE OR REPLACE FUNCTION pkg_contract.upd_license_rt_feature_set(
    p_id bigint,
    p_id_lic_rights bigint,
    p_is_exclusive boolean,
    p_is_use_right boolean,
    p_is_sub_license boolean,
    p_beg_date date,
    p_end_date date,
    p_username character varying,
    p_bypass boolean default false)
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
    else
        v_validity_period := v_license.validity_period * v_validity_period;
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

DROP PROCEDURE IF EXISTS pkg_contract.del_license_rt_feature_set(bigint, character varying, boolean);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license_rt_feature_set(
    IN p_id bigint,
    IN p_username character varying,
    IN p_use_cascade boolean default false,
    in p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

BEGIN
    -- Проверка доступа через цепочку feature_set → license_rights → license → contract
    PERFORM pkg_contract.check_feature_set_org_access(p_id, p_username, p_bypass);

    if p_use_cascade then
        delete from license_rt_features where id_feature_set = p_id;
    end if;
    delete from license_rt_feature_set where id = p_id;
END;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.ins_contract_counterparty(bigint, character varying, character varying);
CREATE OR REPLACE FUNCTION pkg_contract.ins_contract_counterparty(
    p_id_contract bigint,
    p_id_cpart character varying,
    p_username character varying,
    p_bypass boolean default false)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

DECLARE
    r_result bigint;
    v_id_cpart integer;
BEGIN

    -- Проверка доступа
    PERFORM pkg_contract.check_contract_org_access(p_id_contract, p_username, p_Bypass);

    v_id_cpart := pkg_contract.get_cparty_id(p_id_cpart);

    insert into contract_counterparty (id_contract, id_cpart, created_by)
    values (p_id_contract, v_id_cpart, p_username)
    returning id into r_result;

    return r_result;
END;
$BODY$;

DROP PROCEDURE IF EXISTS pkg_contract.del_contract_counterparty(bigint, character varying);
CREATE OR REPLACE PROCEDURE pkg_contract.del_contract_counterparty(
    IN p_id bigint,
    IN p_username character varying,
    IN p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$
DECLARE
    v_rec RECORD;
BEGIN
    -- Проверка доступа
    select * into v_rec from contract_counterparty where id = p_id;
    if FOUND then
       PERFORM pkg_contract.check_contract_org_access(v_rec.id_contract, p_username, p_bypass);
    else
       raise exception 'Контрагент контракта не существует: [ID=%]', p_id;
    end if;

    delete from contract_counterparty where id = p_id;
END;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.ins_license_oip(bigint, text, character varying);
CREATE OR REPLACE FUNCTION pkg_contract.ins_license_oip(
    p_id_license bigint,
    p_id_oip_str text,
    p_username character varying,
    p_bypass boolean default false)
    RETURNS bigint[]
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

DECLARE
    v_simple_arr boolean;
    v_array text[];
    v_id_oip_str  text;
    inserted_ids bigint[];
    v_errors text;
    v_rec record;
BEGIN

    -- Проверка доступа через цепочку license → contract
    PERFORM pkg_contract.check_license_org_access(p_id_license, p_username, p_bypass);

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
$BODY$;

DROP PROCEDURE IF EXISTS pkg_contract.del_license_oip(bigint, character varying);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license_oip(
    IN p_id bigint,
    IN p_username character varying,
    IN p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

DECLARE
   v_rec RECORD;
BEGIN
    select * into v_rec from license_oip where id = p_id;

    -- Проверка доступа
    if FOUND then
        PERFORM pkg_contract.check_license_org_access(v_rec.id_license, p_username, p_bypass);
    else
        raise exception 'ОИС лицензии не существует: [ID=%]', p_id;
    end if;

    call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => p_id);
    delete from license_oip where id = p_id;

END;
$BODY$;

DROP PROCEDURE IF EXISTS pkg_contract.del_license_oip_by_lic(bigint, character varying);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license_oip_by_lic(
    IN p_id_license bigint,
    IN p_username character varying,
    IN p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

DECLARE
    v_rec record;
BEGIN
    if not exists (select 1 from license where id = p_id_license) then
        raise exception 'Лицензия не найдена! [ID = %]', p_id_license using errcode = '20120';
    end if;

    -- Проверка доступа
    PERFORM pkg_contract.check_license_org_access(p_id_license, p_username, p_bypass);

    for v_rec in select * from license_oip where id_license = p_id_license loop
            call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => v_rec.id);
        end loop;
    delete from license_oip where id_license = p_id_license;
END;
$BODY$;

DROP PROCEDURE IF EXISTS pkg_contract.del_license_oip_by_root(bigint, bigint, character varying);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license_oip_by_root(
    IN p_id_license bigint,
    IN p_id_root_oip bigint,
    IN p_username character varying,
    IN p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

DECLARE
    v_rec record;
BEGIN
    if not exists (select 1 from license where id = p_id_license) then
        raise exception 'Лицензия не найдена! [id = %]', p_id_license using errcode = 20120;
    end if;

    -- Проверка доступа
    PERFORM pkg_contract.check_license_org_access(p_id_license, p_username, p_bypass);

    if not exists (select 1 from sync__klf_oip where id = p_id_root_oip) then
        raise exception 'Корневой ОИС не найден! [id = %]', p_id_root_oip using errcode = 20120;
    end if;
    for v_rec in select * from license_oip where id_license = p_id_license and id_root_oip = p_id_root_oip loop
            call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => v_rec.id);
        end loop;
    delete from license_oip where id_license = p_id_license and id_root_oip = p_id_root_oip;
END;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.ins_license_rt_features(bigint, bigint, integer, boolean, character varying);
CREATE OR REPLACE FUNCTION pkg_contract.ins_license_rt_features(
    p_id_lic_rights bigint,
    p_id_feature_set bigint,
    p_id_feature integer,
    p_is_included boolean,
    p_username character varying,
    p_bypass boolean default false)
    RETURNS bigint
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

declare
    v_rt_name varchar;
    v_fc_name varchar;
    v_is_native boolean;
    r_result bigint;
    v_feature_category bigint;
begin

    -- Проверка прав доступа
    PERFORM pkg_contract.check_feature_set_org_access(p_id_feature_set, p_username, p_bypass);

    if p_id_feature is null then
        raise exception 'Ошибка создания характеристики! Не указан идентификатор характеристики (p_id_feature)!'
            using errcode = '20114';
    end if;

    -- извлекаем категорию признака
    select id_feature_category into v_feature_category from sync__klf_feature_tree where id = p_id_feature;

    if not found then
        raise exception 'Ошибка создания характеристики! Указан не существующий идентификатор характеристики [p_id_feature=%]!', p_id_feature
            using errcode = '20115';
    end if;

    -- проверка категорий: сначала "down", потом "up"
    if exists (
        select 1
        from license_rights lr
                 join license_rights_rt lrt on lrt.id_lic_rights = lr.id
                 join vw_rt_cat_down cd on cd.id_right_type = lrt.id_right_type
        where lr.id = p_id_lic_rights
          and cd.id_feature_category = v_feature_category
    ) then
        v_is_native := true;
    elsif exists (
        select 1
        from license_rights lr
                 join license_rights_rt lrt on lrt.id_lic_rights = lr.id
                 join vw_rt_cat_up cu on cu.id_right_type = lrt.id_right_type
        where lr.id = p_id_lic_rights
          and cu.id_feature_category = v_feature_category
    ) then
        v_is_native := false;
    else
        v_is_native := false; -- добавил для исключения ошибки
    /* отключил для исключения ошибки при сохранении сайтов и платформ и категорий товаров
    select rt.name, fc.name
    into v_rt_name, v_fc_name
    from license_rights lr
             join license_rights_rt lrt on lrt.id_lic_rights = lr.id
             join sync__klf_right_type rt on rt.id = lrt.id_right_type
             join sync__klf_feature_category fc on fc.id = v_feature_category
    where lr.id = p_id_lic_rights limit 1;

    raise exception 'Ошибка создания характеристики! Характеристика категории "%" не может использоваться для права "%"!',
        v_fc_name, v_rt_name
        using errcode = 20119;*/
    end if;

    -- вставка
    insert into license_rt_features (id_lic_rights, id_feature_set, id_feature_category, id_feature, is_included, is_native, created_by)
    values (p_id_lic_rights, p_id_feature_set, v_feature_category, p_id_feature,
            coalesce(p_is_included, true), v_is_native, p_username)
    returning id into r_result;

    call pkg_contract.make_change_buffer(p_action => 'INSERT', p_username => p_username, p_id_rt_feature => r_result);

    return r_result;
end;
$BODY$;

DROP FUNCTION IF EXISTS pkg_contract.ins_license_rt_features_bulk(bigint, bigint, text, boolean, character varying);
CREATE OR REPLACE FUNCTION pkg_contract.ins_license_rt_features_bulk(
    p_id_lic_rights bigint,
    p_id_feature_set bigint,
    p_id_features text,
    p_is_included boolean,
    p_username character varying,
    p_bypass boolean default false)
    RETURNS text
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE SECURITY DEFINER PARALLEL UNSAFE
    SET search_path=rightsflow
AS $BODY$

declare
    v_array integer[];
    v_id_feature integer;
    v_id integer;
    v_ids integer[];
begin

    begin
        v_array := string_to_array(p_id_features, ',')::integer[];
    exception
        when invalid_text_representation then
            raise notice 'невалидное значение в строке';
            v_array := null;
    end;

    if v_array is null then
        raise exception 'Ошибка создания характеристики! Не обнаружено ни одной характеристики!'
            using errcode = '20100';
    end if;

    for v_id_feature in select a.id from unnest(v_array) as a(id) loop
            v_id := pkg_contract.ins_license_rt_features(p_id_lic_rights, p_id_feature_set,
                                                         v_id_feature, p_is_included,
                                                         p_username, p_bypass);
            v_ids := v_ids || v_id;
        end loop;

    return array_to_string(v_ids, ',') ;
end;
$BODY$;

DROP PROCEDURE IF EXISTS pkg_contract.del_license_rt_features(bigint, character varying);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license_rt_features(
    IN p_id bigint,
    IN p_username character varying,
    IN p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

DECLARE
    v_rec RECORD;
BEGIN
    select * into v_rec from license_rt_features where id = p_id;

    -- Проверка доступа
    if FOUND then
        PERFORM pkg_contract.check_feature_set_org_access(v_rec.id_feature_set, p_username, p_bypass);
    else
        raise exception 'Характеристики набора не существует: [ID=%]', p_id;
    end if;

    call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_rt_feature => p_id);
    delete from license_rt_features where id = p_id;
END;
$BODY$;

DROP PROCEDURE IF EXISTS pkg_contract.del_license_rt_features_bulk(text, character varying);
CREATE OR REPLACE PROCEDURE pkg_contract.del_license_rt_features_bulk(
    IN p_ids text,
    IN p_username character varying,
    IN p_bypass boolean default false)
    LANGUAGE 'plpgsql'
    SECURITY DEFINER
    SET search_path=rightsflow
AS $BODY$

declare
    v_array integer[];
    v_id_feature integer;
begin

    begin
        v_array := string_to_array(p_ids, ',')::integer[];
    exception
        when invalid_text_representation then
            raise notice 'невалидное значение в строке';
            v_array := null;
    end;

    if v_array is null then
        raise exception 'Ошибка удаления характеристик! Не обнаружено ни одной характеристики!'
            using errcode = '20100';
    end if;

    for v_id_feature in select a.id from unnest(v_array) as a(id) loop
        call pkg_contract.del_license_rt_features(v_id_feature, p_username, p_bypass);
    end loop;

end;
$BODY$;
