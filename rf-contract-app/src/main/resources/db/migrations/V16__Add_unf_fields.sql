ALTER TABLE IF EXISTS CONTRACT ADD COLUMN IF NOT EXISTS UNF_PRICE DECIMAL;
ALTER TABLE IF EXISTS CONTRACT ADD COLUMN IF NOT EXISTS UNF_VAT_RATE DECIMAL(10,2);
ALTER TABLE IF EXISTS CONTRACT ADD COLUMN IF NOT EXISTS UNF_VAT_AMOUNT DECIMAL;
ALTER TABLE IF EXISTS CONTRACT ADD COLUMN IF NOT EXISTS UNF_TOTAL_AMOUNT DECIMAL;


DROP FUNCTION IF EXISTS pkg_contract.ins_contract(character varying, character varying, character varying, character varying,
                                                  date, date, date, character varying, integer, character, character varying,
                                                  integer, integer, character varying, boolean);

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
    p_unf_price decimal,
    p_unf_vat_rate decimal(10,2),
    p_unf_vat_amount decimal,
    p_unf_total_amount decimal,
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
                          unf_vat_amount, unf_total_amount, created_by)
    values (p_guid, v_num, v_id_org, v_id_org_party, v_validity_period,
            p_contract_date, v_id_contract_type, p_status_1c, v_id_contract_status,
            p_in_out, p_description, v_id_currency, v_id_currency_payment,
            p_unf_price, p_unf_vat_rate, p_unf_vat_amount,
            p_unf_total_amount, p_username)
    returning id into r_result;

    return r_result;
END;
$BODY$;

ALTER FUNCTION pkg_contract.ins_contract(character varying, character varying, character varying, character varying, date, date,
                                         date, character varying, integer, character, character varying, integer, integer,
                                         decimal, decimal, decimal, decimal, character varying, boolean)
    OWNER TO rightsflow;

DROP FUNCTION IF EXISTS pkg_contract.upd_contract(bigint, character varying, character varying, character varying, character varying,
                                                  date, date, date, integer, character, character varying, integer, integer,
                                                  character varying, boolean);

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
    p_unf_price decimal,
    p_unf_vat_rate decimal(10,2),
    p_unf_vat_amount decimal,
    p_unf_total_amount decimal,
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
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$BODY$;

ALTER FUNCTION pkg_contract.upd_contract(bigint, character varying, character varying, character varying, character varying,
                                         date, date, date, integer, character, character varying, integer, integer, decimal,
                                         decimal, decimal, decimal, character varying, boolean)
    OWNER TO rightsflow;
