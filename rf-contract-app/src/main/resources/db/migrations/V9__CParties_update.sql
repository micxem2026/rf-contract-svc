ALTER TABLE SYNC__KLF_COUNTERPARTY ADD COLUMN IF NOT EXISTS CODE_1C VARCHAR(50) UNIQUE;
ALTER TABLE SYNC__KLF_ORGANIZATION ADD COLUMN IF NOT EXISTS CODE_1C VARCHAR(50) UNIQUE;

drop function if exists pkg_sync.sync_klf_counterparty(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_name character varying,
    p_id_org_ref integer,
    p_created_by character varying,
    p_created_at timestamptz,
    p_updated_by character varying,
    p_updated_at timestamptz);

-- SYNC__KLF_COUNTERPARTY
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_counterparty(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_code_1c character varying,
    p_name character varying,
    p_id_org_ref integer,
    p_created_by character varying,
    p_created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
    p_updated_by character varying DEFAULT NULL::character varying,
    p_updated_at timestamptz DEFAULT NULL::timestamptz)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__klf_counterparty WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_counterparty (
            id, guid, code_1c, name, id_org_ref, created_by,
            created_at, updated_by, updated_at
        )
        VALUES (
                 p_sync_id, p_guid, p_code_1c, p_name, p_id_org_ref, p_created_by,
                 p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
           guid = EXCLUDED.guid,
           code_1c = EXCLUDED.code_1c,
           name = EXCLUDED.name,
           id_org_ref = EXCLUDED.id_org_ref,
           created_by = EXCLUDED.created_by,
           updated_by = EXCLUDED.updated_by,
           updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_counterparty.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

drop function if exists pkg_sync.sync_klf_organization(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_name character varying,
    p_created_by character varying,
    p_created_at timestamptz,
    p_updated_by character varying,
    p_updated_at timestamptz);

-- SYNC__KLF_ORGANIZATION
CREATE OR REPLACE FUNCTION pkg_sync.sync_klf_organization(
    p_sync_id integer,
    p_id integer,
    p_guid character varying,
    p_code_1c character varying,
    p_name character varying,
    p_created_by character varying,
    p_created_at timestamptz DEFAULT CURRENT_TIMESTAMP,
    p_updated_by character varying DEFAULT NULL::character varying,
    p_updated_at timestamptz DEFAULT NULL::timestamptz)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
BEGIN
    IF p_id IS NULL THEN
        DELETE FROM sync__klf_organization WHERE id = p_sync_id;
    ELSE
        INSERT INTO sync__klf_organization (
            id, guid, code_1c, name, created_by,
            created_at, updated_by, updated_at
        )
        VALUES (
                   p_sync_id, p_guid, p_code_1c, p_name, p_created_by,
                   p_created_at, p_updated_by, p_updated_at
               )
        ON CONFLICT (id) DO UPDATE SET
           guid = EXCLUDED.guid,
           code_1c = EXCLUDED.code_1c,
           name = EXCLUDED.name,
           created_by = EXCLUDED.created_by,
           updated_by = EXCLUDED.updated_by,
           updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at > COALESCE(sync__klf_organization.updated_at, '1970-01-01'::TIMESTAMPTZ);
    END IF;
    RETURN p_sync_id;
END;
$BODY$;

create or replace function pkg_contract.get_org_id(p_id_org character varying)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_id integer;
BEGIN
  if pkg_pge.is_numeric(p_id_org) then
      return p_id_org::integer;
  else
      select id into v_id from sync__klf_organization where code_1c = upper(p_id_org);
      if v_id is null then
          raise exception 'Код [%] не найден в справочнике организаций', p_id_org using errcode = 20404;
      end if;
      return v_id;
  end if;
END;
$$ language plpgsql;

create or replace function pkg_contract.get_cparty_id(p_id_cpart character varying)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_id integer;
BEGIN
    if pkg_pge.is_numeric(p_id_cpart) then
        return p_id_cpart::integer;
    else
        select id into v_id from sync__klf_counterparty where code_1c = upper(p_id_cpart);
        if v_id is null then
            raise exception 'Код [%] не найден в справочнике контрагентов', p_id_cpart using errcode = 20404;
        end if;
        return v_id;
    end if;
END;
$$ language plpgsql;

drop function if exists pkg_contract.ins_contract(p_guid character varying,
                                                  p_num character varying,
                                                  p_id_org integer,
                                                  p_id_org_party integer,
                                                  p_beg_date date,
                                                  p_end_date date,
                                                  p_contract_date date,
                                                  p_id_contract_type integer,
                                                  p_in_out char(2),
                                                  p_description character varying,
                                                  p_id_currency integer,
                                                  p_id_currency_payment integer,
                                                  p_username character varying);

create or replace function pkg_contract.ins_contract(p_guid character varying,
                                                     p_num character varying,
                                                     p_id_org character varying,
                                                     p_id_org_party integer,
                                                     p_beg_date date,
                                                     p_end_date date,
                                                     p_contract_date date,
                                                     p_id_contract_type integer,
                                                     p_in_out char(2),
                                                     p_description character varying,
                                                     p_id_currency integer,
                                                     p_id_currency_payment integer,
                                                     p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
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

    v_id_contract_type := pkg_contract.get_def_contract_type(p_id_contract_type);
    v_id_contract_status := pkg_contract.get_def_contract_status(v_id_contract_type, 'DRAFT');
    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    v_num := coalesce(nullif(p_num, ''), pkg_contract.get_next_contract_num(v_id_contract_type));

    if p_in_out in ('eP', 'eS') then
        v_id_org_party := null;
    else
        v_id_org_party := p_id_org_party;
    end if;

    v_id_currency := pkg_contract.get_def_currency(p_id_currency);
    v_id_currency_payment := pkg_contract.get_def_currency(p_id_currency_payment);
    v_id_org := pkg_contract.get_org_id(p_id_org);

    insert into contract (guid, num, id_org, id_org_party, validity_period,contract_date, id_contract_type, id_contract_status, in_out, description, id_currency, id_currency_payment, created_by)
    values (p_guid, v_num, v_id_org, v_id_org_party, v_validity_period, p_contract_date, v_id_contract_type,
            v_id_contract_status, p_in_out, p_description, v_id_currency, v_id_currency_payment, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

drop function if exists pkg_contract.upd_contract(p_id bigint,
                                                  p_guid character varying,
                                                  p_num character varying,
                                                  p_id_org integer,
                                                  p_id_org_party integer,
                                                  p_beg_date date,
                                                  p_end_date date,
                                                  p_contract_date date,
                                                  p_id_contract_type integer,
                                                  p_in_out char(2),
                                                  p_description character varying,
                                                  p_id_currency integer,
                                                  p_id_currency_payment integer,
                                                  p_username character varying);

create or replace function pkg_contract.upd_contract(p_id bigint,
                                                     p_guid character varying,
                                                     p_num character varying,
                                                     p_id_org character varying,
                                                     p_id_org_party integer,
                                                     p_beg_date date,
                                                     p_end_date date,
                                                     p_contract_date date,
                                                     p_id_contract_type integer,
                                                     p_in_out char(2),
                                                     p_description character varying,
                                                     p_id_currency integer,
                                                     p_id_currency_payment integer,
                                                     p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_validity_period daterange;
    v_id_contract_type integer;
    v_old contract%rowtype;
    v_id_org_party integer;
    v_id_org integer;
BEGIN

    select * into v_old from contract
    where id = p_id
        for update;

    v_validity_period := v_old.validity_period;
    if p_beg_date is not null or p_end_date is not null then
        v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    end if;

    v_id_contract_type := pkg_contract.get_old_contract_type(p_id_contract_type, v_old.id_contract_type);

    if coalesce(p_in_out, v_old.in_out) in ('eP', 'eS') then
        v_id_org_party := null;
    else
        v_id_org_party := p_id_org_party;
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
$$ language plpgsql;

drop function if exists pkg_contract.ins_contract_counterparty(p_id_contract bigint,
                                                               p_id_cpart integer,
                                                               p_username character varying);

create or replace function pkg_contract.ins_contract_counterparty(p_id_contract bigint,
                                                                  p_id_cpart character varying,
                                                                  p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
    v_id_cpart integer;
BEGIN

    v_id_cpart := pkg_contract.get_cparty_id(p_id_cpart);

    insert into contract_counterparty (id_contract, id_cpart, created_by)
    values (p_id_contract, v_id_cpart, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

ALTER TABLE LICENSE ADD COLUMN IF NOT EXISTS NAME VARCHAR(255);

drop function if exists pkg_contract.ins_license(p_guid character varying,
                                                 p_num character varying,
                                                 p_id_contract bigint,
                                                 p_id_lic_format bigint,
                                                 p_price numeric,
                                                 p_vat_rate numeric,
                                                 p_vat_amount numeric,
                                                 p_total_amount numeric,
                                                 p_beg_date date,
                                                 p_end_date date,
                                                 p_description character varying,
                                                 p_username character varying);

create or replace function pkg_contract.ins_license(p_guid character varying,
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
                                                    p_username character varying)
returns bigint
    security definer
    SET search_path = rightsflow
    language plpgsql
as
$$
DECLARE
    r_result bigint;
    v_contract contract%rowtype;
    v_validity_period daterange;
    v_num contract.num%type;
BEGIN

    if p_id_contract is null then
        raise exception 'Ошибка создания лицензии! Не указан идентификатор договора (p_id_contract)!'
            using errcode = 20104;
    end if;

    select * into v_contract from contract where id = p_id_contract;

    v_num := coalesce(nullif(p_num, ''), pkg_contract.get_next_license_num());
    v_validity_period := daterange(p_beg_date, p_end_date, '[]');

    if isempty(v_contract.validity_period * v_validity_period) then
        raise exception 'Лицензия не пересекается с периодом договора!'
            using errcode = 20105;
    else
        v_validity_period := v_contract.validity_period * v_validity_period;
    end if;

    insert into license (id_contract, id_lic_format, guid, num, name, price, vat_rate, vat_amount, total_amount, validity_period, description, created_by)
    values (p_id_contract, p_id_lic_format, p_guid, v_num, p_name, p_price, p_vat_rate,
            p_vat_amount, p_total_amount, v_validity_period, p_description, p_username)
    returning id into r_result;

    return r_result;
END;
$$;

create or replace function pkg_contract.ins_license_rt_features_bulk(p_id_lic_rights bigint,
                                                                     p_id_feature_set bigint,
                                                                     p_id_features text,
                                                                     p_is_included boolean,
                                                                     p_username character varying) returns text
    security definer
    SET search_path = rightsflow
    language plpgsql
as
$$
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
            using errcode = 20100;
    end if;

    for v_id_feature in select a.id from unnest(v_array) as a(id) loop
        v_id := pkg_contract.ins_license_rt_features(p_id_lic_rights, p_id_feature_set, v_id_feature, p_is_included, p_username);
        v_ids := v_ids || v_id;
    end loop;

    return array_to_string(v_ids, ',') ;
end;
$$;