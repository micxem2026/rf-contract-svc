create schema if not exists pkg_contract;
alter schema pkg_contract owner to rightsflow;

create sequence if not exists pkg_contract.contract_num_seq;
alter sequence pkg_contract.contract_num_seq owner to rightsflow;

create sequence if not exists pkg_contract.license_num_seq;
alter sequence pkg_contract.license_num_seq owner to rightsflow;

create or replace function pkg_contract.get_next_contract_num(p_id_contract_type integer)
    returns character varying
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_num_part varchar(10);
BEGIN
    v_num_part := trim(to_char(nextval('pkg_contract.contract_num_seq'), '99990000'));
    return case when p_id_contract_type = 1 then 'С-'||v_num_part
                when p_id_contract_type = 2 then 'Д-'||v_num_part
                else v_num_part
        end;
END;
$$ language plpgsql;

create or replace function pkg_contract.get_next_license_num()
    returns character varying
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_num_part varchar(10);
BEGIN
    v_num_part := trim(to_char(nextval('pkg_contract.license_num_seq'), '99990000'));
    return 'Л-'||v_num_part;
END;
$$ language plpgsql;

create or replace function pkg_contract.get_def_currency(p_id_currency integer)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result integer;
BEGIN
    if p_id_currency is not null and
       exists(select id from lov_currency where id = p_id_currency) then
        r_result := p_id_currency;
    else
        select id into r_result from lov_currency
        where def = true limit 1;
    end if;
    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.get_def_contract_type(p_id_contract_type integer)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result integer;
BEGIN
    if p_id_contract_type is not null and
       exists(select id from lov_contract_type where id = p_id_contract_type) then
        r_result := p_id_contract_type;
    else
        select id into r_result from lov_contract_type
        where def = true limit 1;
    end if;
    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.get_old_contract_type(p_id_contract_type integer, p_id_contract_type_old integer)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result integer;
BEGIN
    if p_id_contract_type is not null and
       exists(select id from lov_contract_type where id = p_id_contract_type) then
        r_result := p_id_contract_type;
    else
        r_result := p_id_contract_type_old;
    end if;
    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.get_def_contract_status(p_id_contract_type integer,
                                                                p_id_contract_status integer)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result integer;
BEGIN
    if p_id_contract_status is not null and
       exists(select id from lov_contract_status
              where id = p_id_contract_status and
                  id_contract_type = p_id_contract_type) then
        r_result := p_id_contract_status;
    else
        select id into r_result from lov_contract_status
        where id_contract_type = p_id_contract_type and
            def = true
        limit 1;
    end if;
    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.get_old_contract_status(p_id_contract_type integer,
                                                                p_id_contract_status integer,
                                                                p_id_contract_status_old integer)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result integer;
BEGIN
    if p_id_contract_status is not null and
       exists(select id from lov_contract_status
              where id = p_id_contract_status and
                  id_contract_type = p_id_contract_type) then
        r_result := p_id_contract_status;
    else
        r_result := p_id_contract_status_old;
    end if;
    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_contract(p_guid character varying,
                                                     p_num character varying,
                                                     p_id_org integer,
                                                     p_beg_date date,
                                                     p_end_date date,
                                                     p_sign_date date,
                                                     p_id_contract_type integer,
                                                     p_id_contract_status integer,
                                                     p_in_out char(1),
                                                     p_description character varying,
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
BEGIN

    v_id_contract_type := pkg_contract.get_def_contract_type(p_id_contract_type);
    v_id_contract_status := pkg_contract.get_def_contract_status(v_id_contract_type, p_id_contract_status);
    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    v_num := coalesce(p_num, pkg_contract.get_next_contract_num(v_id_contract_type));

    insert into contract (guid, num, id_org, validity_period, sign_date, id_contract_type, id_contract_status, in_out, description, created_by)
    values (p_guid, v_num, p_id_org, v_validity_period, p_sign_date, v_id_contract_type,
            v_id_contract_status, p_in_out, p_description, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_contract(p_id bigint,
                                                     p_guid character varying,
                                                     p_num character varying,
                                                     p_id_org integer,
                                                     p_beg_date date,
                                                     p_end_date date,
                                                     p_sign_date date,
                                                     p_id_contract_type integer,
                                                     p_id_contract_status integer,
                                                     p_in_out char(1),
                                                     p_description character varying,
                                                     p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_validity_period daterange;
    v_id_contract_type integer;
    v_id_contract_status integer;
    v_old contract%rowtype;
BEGIN

    select * into v_old from contract where id = p_id;

    v_validity_period := v_old.validity_period;
    if p_beg_date is not null or p_end_date is not null then
        v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    end if;

    v_id_contract_type := pkg_contract.get_old_contract_type(p_id_contract_type, v_old.id_contract_type);
    v_id_contract_status := pkg_contract.get_old_contract_status(v_id_contract_type, p_id_contract_status, v_old.id_contract_status);

    update contract
    set
        guid = p_guid,
        num = coalesce(p_num, num),
        id_org = coalesce(p_id_org, id_org),
        validity_period = v_validity_period,
        sign_date = p_sign_date,
        id_contract_type = v_id_contract_type,
        id_contract_status = v_id_contract_status,
        in_out = coalesce(p_in_out, in_out),
        description = p_description,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_contract(p_id bigint,
                                                      p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from contract where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_license(p_guid character varying,
                                                    p_num character varying,
                                                    p_id_contract bigint,
                                                    p_id_lic_format bigint,
                                                    p_price numeric,
                                                    p_id_currency integer,
                                                    p_beg_date date,
                                                    p_end_date date,
                                                    p_description character varying,
                                                    p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
    v_contract contract%rowtype;
    v_validity_period daterange;
    v_num contract.num%type;
    v_id_currency integer;
BEGIN

    if p_id_contract is null then
        raise exception 'Ошибка создания лицензии! Не указан идентификатор договора (p_id_contract)!'
            using errcode = 20104;
    end if;

    select * into v_contract from contract where id = p_id_contract;

    v_num := coalesce(p_num, pkg_contract.get_next_license_num());
    v_validity_period := daterange(p_beg_date, p_end_date, '[]');

    if isempty(v_contract.validity_period * v_validity_period) then
        raise exception 'Лицензия не пересекается с периодом договора!'
            using errcode = 20105;
    else
        v_validity_period := v_contract.validity_period * v_validity_period;
    end if;

    v_id_currency := pkg_contract.get_def_currency(p_id_currency);

    insert into license (id_contract, id_lic_format, guid, num, price, id_currency, validity_period, description, created_by)
    values (p_id_contract, p_id_lic_format, p_guid, v_num, p_price, v_id_currency,
            v_validity_period, p_description, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_license(p_id bigint,
                                                    p_guid character varying,
                                                    p_num character varying,
                                                    p_id_lic_format bigint,
                                                    p_price numeric,
                                                    p_id_currency integer,
                                                    p_beg_date date,
                                                    p_end_date date,
                                                    p_description character varying,
                                                    p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_contract contract%rowtype;
    v_validity_period daterange;
BEGIN

    select c.* into v_contract from contract c, license l
    where l.id_contract = c.id and
        l.id = p_id;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');

    if isempty(v_contract.validity_period * v_validity_period) then
        raise exception 'Лицензия не пересекается с периодом договора!'
            using errcode = 20105;
    else
        v_validity_period := v_contract.validity_period * v_validity_period;
    end if;

    update license
    set
        id_lic_format = p_id_lic_format,
        guid = p_guid,
        num = coalesce(p_num, num),
        price = coalesce(p_price, price),
        id_currency = coalesce(p_id_currency, id_currency),
        validity_period = v_validity_period,
        description = p_description,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license(p_id bigint,
                                                     p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from license where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_license_oip(p_id_license bigint,
                                                        p_id_oip integer,
                                                        p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
BEGIN

    insert into license_oip (id_license, id_oip, created_by)
    values (p_id_license, p_id_oip, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_oip(p_id bigint,
                                                         p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from license_oip where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_contract_counterparty(p_id_contract bigint,
                                                                  p_id_cpart integer,
                                                                  p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
BEGIN

    insert into contract_counterparty (id_contract, id_cpart, created_by)
    values (p_id_contract, p_id_cpart, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_contract_counterparty(p_id bigint,
                                                                   p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from contract_counterparty where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_license_rt(p_id_license bigint,
                                                       p_id_right_type integer,
                                                       p_hb_start_date date,
                                                       p_hb_days integer,
                                                       p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
    v_license license%rowtype;
BEGIN

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

    insert into license_rt (id_license, id_right_type, hb_start_date, hb_days, created_by)
    values (p_id_license, p_id_right_type, p_hb_start_date, p_hb_days, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_license_rt(p_id bigint,
                                                       p_id_license bigint,
                                                       p_id_right_type integer,
                                                       p_hb_start_date date,
                                                       p_hb_days integer,
                                                       p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_license license%rowtype;
BEGIN

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

    update license_rt
    set
        id_right_type = coalesce(p_id_right_type, id_right_type),
        hb_start_date = p_hb_start_date,
        hb_days = case when p_hb_start_date is null then null else p_hb_days end,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_rt(p_id bigint,
                                                        p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from license_rt where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_license_rt_feature_set(p_id_lic_rt bigint,
                                                                   p_is_exclusive boolean,
                                                                   p_is_use_right boolean,
                                                                   p_beg_date date,
                                                                   p_end_date date,
                                                                   p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
    v_validity_period daterange;
    v_license license%rowtype;
    v_lic_rt  license_rt%rowtype;
BEGIN

    if p_id_lic_rt is null then
        raise exception 'Ошибка создания набора характеристик! Не указан идентификатор права лицензии (p_id_lic_rt)!'
            using errcode = 20109;
    end if;

    select * into v_lic_rt from license_rt where id = p_id_lic_rt;
    select * into v_license from license where id = v_lic_rt.id_license;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    if isempty(v_license.validity_period * v_validity_period) then
        raise exception 'Период набора характеристик не пересекается с периодом лицензии!'
            using errcode = 20110;
    else
        v_validity_period := v_license.validity_period * v_validity_period;
    end if;

    insert into license_rt_feature_set (id_lic_rt, is_exclusive, is_use_right, validity_period, created_by)
    values (p_id_lic_rt, coalesce(p_is_exclusive, false), coalesce(p_is_use_right, false),
            v_validity_period, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_license_rt_feature_set(p_id bigint,
                                                                   p_id_lic_rt bigint,
                                                                   p_is_exclusive boolean,
                                                                   p_is_use_right boolean,
                                                                   p_beg_date date,
                                                                   p_end_date date,
                                                                   p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_validity_period daterange;
    v_license license%rowtype;
    v_lic_rt  license_rt%rowtype;
BEGIN

    if p_id_lic_rt is null then
        raise exception 'Ошибка обновления набора характеристик! Не указан идентификатор права лицензии (p_id_lic_rt)!'
            using errcode = 20111;
    end if;

    select * into v_lic_rt from license_rt where id = p_id_lic_rt;
    select * into v_license from license where id = v_lic_rt.id_license;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    if isempty(v_license.validity_period * v_validity_period) then
        raise exception 'Период набора характеристик не пересекается с периодом лицензии!'
            using errcode = 20110;
    else
        v_validity_period := v_license.validity_period * v_validity_period;
    end if;

    update license_rt_feature_set
    set
        is_exclusive = coalesce(p_is_exclusive, is_exclusive),
        is_use_right = coalesce(p_is_use_right, is_use_right),
        validity_period = v_validity_period,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_rt_feature_set(p_id bigint,
                                                                    p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from license_rt_feature_set where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.make_license_rt_feature_set_ext()
    returns trigger
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_lic_rt license_rt%rowtype;
    v_hb_end_date date;
    v_period daterange;
BEGIN
    if tg_op not in ('INSERT', 'UPDATE') then
        return null;
    end if;

    if tg_op = 'UPDATE' then
        delete from license_rt_feature_set_ext where id_feature_set = NEW.id;
    end if;

    select * into v_lic_rt from license_rt where id = NEW.id_lic_rt;
    if not found then
        return null;
    end if;

    -- Простой случай: эксклюзивность или отсутствие holdback
    if v_lic_rt.hb_start_date is null or NEW.is_exclusive then
        insert into license_rt_feature_set_ext
        (id_feature_set, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by)
        values
            (NEW.id, NEW.id_lic_rt, NEW.is_exclusive, NEW.is_use_right, NEW.validity_period, NEW.created_by);
        return null;
    end if;

    -- Предварительные вычисления для holdback
    v_hb_end_date := v_lic_rt.hb_start_date + v_lic_rt.hb_days;
    v_period := daterange(least(v_hb_end_date, upper(NEW.validity_period)-1), upper(NEW.validity_period), '[)');

    -- Holdback совпадает с началом периода
    if not lower_inf(NEW.validity_period) and lower(NEW.validity_period) = v_lic_rt.hb_start_date then
        -- Первый период (holdback) - всегда эксклюзивный
        insert into license_rt_feature_set_ext
        (id_feature_set, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by)
        select NEW.id, NEW.id_lic_rt, true, NEW.is_use_right,
               daterange(v_lic_rt.hb_start_date, least(v_hb_end_date, upper(NEW.validity_period)), '[)'),
               NEW.created_by
        union all
        -- Второй период (после holdback) - с исходной эксклюзивностью
        select NEW.id, NEW.id_lic_rt, NEW.is_exclusive, NEW.is_use_right, v_period, NEW.created_by
        where not isempty(v_period);

        -- Holdback внутри периода
    elsif v_lic_rt.hb_start_date <@ NEW.validity_period then
        insert into license_rt_feature_set_ext
        (id_feature_set, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by)
        select NEW.id, NEW.id_lic_rt, NEW.is_exclusive, NEW.is_use_right,
               daterange(lower(NEW.validity_period), v_lic_rt.hb_start_date, '[)'), NEW.created_by
        where not isempty(daterange(lower(NEW.validity_period), v_lic_rt.hb_start_date, '[)'))
        union all
        -- Holdback период - эксклюзивный
        select NEW.id, NEW.id_lic_rt, true, NEW.is_use_right,
               daterange(v_lic_rt.hb_start_date, least(v_hb_end_date, upper(NEW.validity_period)), '[)'),
               NEW.created_by
        union all
        -- После holdback - исходная эксклюзивность
        select NEW.id, NEW.id_lic_rt, NEW.is_exclusive, NEW.is_use_right, v_period, NEW.created_by
        where not isempty(v_period);
    end if;

    return null;
END;
$$ language plpgsql;

create or replace trigger tr_make_license_rt_feature_set_ext
    after insert or update
    on rightsflow.license_rt_feature_set
    for each row
execute function pkg_contract.make_license_rt_feature_set_ext();

create or replace function pkg_contract.remake_license_rt_feature_set_ext()
    returns trigger
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    hb_start date := NEW.hb_start_date;
    hb_days  int  := COALESCE(NEW.hb_days, 0);
    hb_end   date;
BEGIN
    -- Только обновление
    IF TG_OP <> 'UPDATE' THEN
        RETURN NULL;
    END IF;

    -- Ничего не изменилось -> выход
    IF (OLD.hb_start_date IS NULL AND hb_start IS NULL)
        OR (OLD.hb_start_date = hb_start AND OLD.hb_days = hb_days) THEN
        RETURN NULL;
    END IF;

    -- Очищаем старые ext строки для этого lic_rt
    DELETE FROM license_rt_feature_set_ext WHERE id_lic_rt = NEW.id;

    -- Если нет holdback-а -> просто копируем все feature-sets
    IF hb_start IS NULL THEN
        INSERT INTO license_rt_feature_set_ext
        (id_feature_set, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by)
        SELECT id, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by
        FROM license_rt_feature_set
        WHERE id_lic_rt = NEW.id;

        RETURN NULL;
    END IF;

    hb_end := hb_start + hb_days;

    -- 1) Копируем feature-sets, которые уже являются эксклюзивными, без изменений
    INSERT INTO license_rt_feature_set_ext
    (id_feature_set, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by)
    SELECT id, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by
    FROM license_rt_feature_set
    WHERE id_lic_rt = NEW.id
      AND is_exclusive;

    -- 2) Для не эксклюзивных feature-sets распределяем их по частям: before / hold / after
    INSERT INTO license_rt_feature_set_ext
    (id_feature_set, id_lic_rt, is_exclusive, is_use_right, validity_period, created_by)
    SELECT
        fs.id,
        fs.id_lic_rt,
        CASE parts.part WHEN 'hold' THEN true ELSE fs.is_exclusive END AS is_exclusive,
        fs.is_use_right,
        daterange(parts.part_lower, parts.part_upper, '[)') AS validity_period,
        fs.created_by
    FROM license_rt_feature_set fs
             CROSS JOIN LATERAL (
        VALUES
            ( lower(fs.validity_period), least(hb_start, upper(fs.validity_period)), 'before' ),
            ( greatest(hb_start, lower(fs.validity_period)), least(hb_end, upper(fs.validity_period)), 'hold' ),
            ( greatest(hb_end, lower(fs.validity_period)), upper(fs.validity_period), 'after' )
        ) AS parts(part_lower, part_upper, part)
    WHERE fs.id_lic_rt = NEW.id
      AND NOT fs.is_exclusive
      -- исключить сегменты с NULL границами или пустыми диапазонами
      AND parts.part_lower IS NOT NULL
      AND parts.part_upper IS NOT NULL
      AND parts.part_lower < parts.part_upper;

    RETURN NULL;
END;
$$ language plpgsql;

create or replace trigger tr_remake_license_rt_feature_set_ext
    after update
    on rightsflow.license_rt
    for each row
execute function pkg_contract.remake_license_rt_feature_set_ext();

create or replace function pkg_contract.ins_license_rt_features(p_id_lic_rt bigint,
                                                                p_id_feature_set bigint,
                                                                p_id_feature integer,
                                                                p_is_included boolean,
                                                                p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
    v_klf_feature  sync__klf_feature_tree%rowtype;
BEGIN

    if p_id_lic_rt is null then
        raise exception 'Ошибка создания характеристики! Не указан идентификатор права лицензии (p_id_lic_rt)!'
            using errcode = 20112;
    end if;

    if p_id_feature_set is null then
        raise exception 'Ошибка создания характеристики! Не указан идентификатор набора характеристик (p_id_feature_set)!'
            using errcode = 20113;
    end if;

    if p_id_feature is null then
        raise exception 'Ошибка создания характеристики! Не указан идентификатор характеристики (p_id_feature)!'
            using errcode = 20114;
    end if;

    select * into v_klf_feature from sync__klf_feature_tree where id = p_id_feature;
    if not found then
        raise exception 'Ошибка создания характеристики! Указан не существующий идентификатор характеристики [p_id_feature=%]!', p_id_feature
            using errcode = 20115;
    end if;

    insert into license_rt_features (id_lic_rt, id_feature_set, id_feature_category, id_feature, is_included, created_by)
    values (p_id_lic_rt, p_id_feature_set, v_klf_feature.id_feature_category, p_id_feature,
            coalesce(p_is_included, true), p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_rt_features(p_id bigint,
                                                                 p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from license_rt_features where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_license_format(p_name character varying,
                                                           p_description character varying,
                                                           p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
BEGIN

    insert into license_format (name, description, created_by)
    values (p_name, p_description, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_license_format(p_id bigint,
                                                           p_name character varying,
                                                           p_description character varying,
                                                           p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
BEGIN

    update license_format
    set
        name = coalesce(p_name, name),
        description = p_description,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_format(p_id bigint,
                                                            p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from license_format where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_format_rt(p_id_lic_format bigint,
                                                      p_id_right_type integer,
                                                      p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
BEGIN

    insert into format_rt (id_lic_format, id_right_type, created_by)
    values (p_id_lic_format, p_id_right_type, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_format_rt(p_id bigint,
                                                      p_id_lic_format bigint,
                                                      p_id_right_type integer,
                                                      p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
BEGIN

    update format_rt
    set
        id_right_type = coalesce(p_id_right_type, id_right_type),
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_format_rt(p_id bigint,
                                                       p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from format_rt where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_format_rt_feature_set(p_id_fmt_rt bigint,
                                                                  p_is_exclusive boolean,
                                                                  p_is_use_right boolean,
                                                                  p_beg_date date,
                                                                  p_end_date date,
                                                                  p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
    v_validity_period daterange;
BEGIN

    v_validity_period = daterange(p_beg_date, p_end_date, '[]');
    insert into format_rt_feature_set (id_fmt_rt, is_exclusive, is_use_right, validity_period, created_by)
    values (p_id_fmt_rt, coalesce(p_is_exclusive, false), coalesce(p_is_use_right, false),
            v_validity_period, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_format_rt_feature_set(p_id bigint,
                                                                  p_id_fmt_rt bigint,
                                                                  p_is_exclusive boolean,
                                                                  p_is_use_right boolean,
                                                                  p_beg_date date,
                                                                  p_end_date date,
                                                                  p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_validity_period daterange;
BEGIN

    v_validity_period = daterange(p_beg_date, p_end_date, '[]');
    update format_rt_feature_set
    set
        is_exclusive = coalesce(p_is_exclusive, is_exclusive),
        is_use_right = coalesce(p_is_use_right, is_use_right),
        validity_period = v_validity_period,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_format_rt_feature_set(p_id bigint,
                                                                   p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from format_rt_feature_set where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_format_rt_features(p_id_fmt_rt bigint,
                                                               p_id_feature_set bigint,
                                                               p_id_feature integer,
                                                               p_is_included boolean,
                                                               p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
    v_klf_feature  sync__klf_feature_tree%rowtype;
BEGIN

    if p_id_feature is null then
        raise exception 'Ошибка создания характеристики! Не указан идентификатор характеристики (p_id_feature)!'
            using errcode = 20114;
    end if;

    select * into v_klf_feature from sync__klf_feature_tree where id = p_id_feature;
    if not found then
        raise exception 'Ошибка создания характеристики! Указан не существующий идентификатор характеристики [p_id_feature=%]!', p_id_feature
            using errcode = 20115;
    end if;

    insert into format_rt_features (id_fmt_rt, id_feature_set, id_feature_category, id_feature, is_included, created_by)
    values (p_id_fmt_rt, p_id_feature_set, v_klf_feature.id_feature_category, p_id_feature,
            coalesce(p_is_included, true), p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_format_rt_features(p_id bigint,
                                                                p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    delete from format_rt_features where id = p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.correct_license_periods()
    returns trigger
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    -- только для сделки
    if NEW.id_contract_type != 1 then
        return null;
    end if;

    -- Обновляем все лицензии одним запросом
    with updated_licenses as (
        update license
            set validity_period = case
                -- Пересекающиеся, но не полностью содержащиеся периоды
                                      when not (validity_period <@ NEW.validity_period)
                                          and (validity_period && NEW.validity_period) then
                                          NEW.validity_period * validity_period
                -- Периоды полностью вне контракта
                                      when validity_period << NEW.validity_period
                                          or validity_period >> NEW.validity_period then
                                          NEW.validity_period
                -- Остальные случаи не изменяем
                                      else validity_period
                end
            where id_contract = NEW.id
            returning id, validity_period
    )
    -- Обновляем связанные feature_set одним запросом
    update license_rt_feature_set fs
    set validity_period = case
        -- Для пересекающихся feature_set - пересечение с новым периодом лицензии
                              when not (fs.validity_period <@ ul.validity_period)
                                  and (fs.validity_period && ul.validity_period) then
                                  fs.validity_period * ul.validity_period
        -- Для остальных случаев - просто новый период лицензии
                              else ul.validity_period
        end
    from updated_licenses ul
             join license_rt lr on lr.id_license = ul.id
    where fs.id_lic_rt = lr.id;

    return null; -- AFTER-триггер: возвращаем NULL
END;
$$ language plpgsql;

create or replace trigger tr_correct_license_periods
    after update
    on rightsflow.contract
    for each row
execute function pkg_contract.correct_license_periods();

create or replace function pkg_contract.check_license_rt_features()
    returns trigger
    language plpgsql
as
$$
declare
    new_subtree_ids int[];
    new_ancestor_ids int[];
    conflict_count int;
    v_cnt int;
begin
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
          and f.is_included = true
          and  f.id_feature = any(new_ancestor_ids);

        if conflict_count = 0 then
            raise exception
                'Ошибка: исключаемая характеристика [id_feature=%] не пересекается ни с одной включённой в наборе [id_feature_set=%]',
                NEW.id_feature, NEW.id_feature_set
                using errcode = 20117;
        end if;

        select count(*) into v_cnt
        from license_rt_features f
        where f.id_feature_set = NEW.id_feature_set
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
$$;

create or replace trigger tr_check_license_rt_features
    before insert on rightsflow.license_rt_features
    for each row
execute function pkg_contract.check_license_rt_features();

create or replace function pkg_contract.check_format_rt_features()
    returns trigger
    language plpgsql
as
$$
declare
    new_subtree_ids int[];
    new_ancestor_ids int[];
    conflict_count int;
    v_cnt int;
begin
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
        from format_rt_features f
        where f.id_feature_set = NEW.id_feature_set
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
        from format_rt_features f
        where f.id_feature_set = NEW.id_feature_set
          and f.is_included = true
          and f.id_feature = any(new_ancestor_ids);

        if conflict_count = 0 then
            raise exception
                'Ошибка: исключаемая характеристика [id_feature=%] не пересекается ни с одной включённой в наборе [id_feature_set=%]',
                NEW.id_feature, NEW.id_feature_set
                using errcode = 20117;
        end if;

        select count(*) into v_cnt
        from format_rt_features f
        where f.id_feature_set = NEW.id_feature_set
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
$$;

create or replace trigger tr_check_format_rt_features
    before insert on rightsflow.format_rt_features
    for each row
execute function pkg_contract.check_format_rt_features();