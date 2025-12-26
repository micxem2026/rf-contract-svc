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
                                                                p_contract_status_code varchar)
    returns integer
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result integer;
BEGIN
    if p_contract_status_code is not null and
       exists(select id from lov_contract_status
              where code = p_contract_status_code and
                  id_contract_type = p_id_contract_type) then
       select id into r_result from lov_contract_status
       where code = p_contract_status_code
         and id_contract_type = p_id_contract_type;
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

create or replace function pkg_contract.validate_oip_with_hierarchy(
    p_id_oip_str    text
)
    returns table (
                      id_root       int,
                      id_oip        int,
                      cnt           bigint,
                      roots         text,
                      name_oip      varchar(512)
                  )
    security definer
    set search_path = rightsflow
as
$$
declare
    v_array integer[];
begin
    begin
        v_array := string_to_array(p_id_oip_str, ',')::integer[];
    exception
        when invalid_text_representation then
            raise notice 'невалидное значение в строке';
            v_array := null;
    end;
    return query
        with recursive oip_tree(id_parent, id_oip, id_root, path) as (
            -- Начальные узлы
            select oh.id_parent, oh.id_oip, oh.id_parent as id_root,
                   array[oh.id_parent, oh.id_oip] as path
            from sync__klf_oip_hierarchy oh
            where oh.id_parent = any(v_array)

            union

            -- Рекурсивная часть
            select r.id_parent, r.id_oip, s.id_root,
                   s.path || r.id_oip
            from sync__klf_oip_hierarchy r
                     inner join oip_tree s on s.id_oip = r.id_parent
            where not (r.id_oip = any(s.path))  -- Защита от циклов
        ),
           leaves as (
               select distinct t.id_root, t.id_oip
               from oip_tree t
               where not exists (
                   select 1
                   from sync__klf_oip_hierarchy h
                   where h.id_parent = t.id_oip
                     and not (h.id_oip = any(t.path))
               )

               union all

               select o.id as id_root, o.id as id_oip
               from sync__klf_oip o
               where o.id = any(v_array)
                 and not exists (select 1 from sync__klf_oip_hierarchy h
                                 where h.id_parent = o.id)
           )
        select l.id_root, l.id_oip, count(*) over(partition by l.id_oip) as cnt,
               string_agg(coalesce(op.name, '<< NO_ROOT >>'), ', ') over(partition by l.id_oip) as roots,
               oc.name as name_oip
        from leaves l
                 join sync__klf_oip oc on oc.id = l.id_oip
                 left join sync__klf_oip op on op.id = l.id_root;
end;
$$ language plpgsql;

-- создание и изменение контракта

create or replace function pkg_contract.ins_contract(p_guid character varying,
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

    insert into contract (guid, num, id_org, id_org_party, validity_period,contract_date, id_contract_type, id_contract_status, in_out, description, id_currency, id_currency_payment, created_by)
    values (p_guid, v_num, p_id_org, v_id_org_party, v_validity_period, p_contract_date, v_id_contract_type,
            v_id_contract_status, p_in_out, p_description, v_id_currency, v_id_currency_payment, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_contract(p_id bigint,
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

    update contract
    set
        guid = p_guid,
        num = coalesce(p_num, num),
        id_org = coalesce(p_id_org, id_org),
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

create or replace function pkg_contract.ins_license(p_guid character varying,
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

    insert into license (id_contract, id_lic_format, guid, num, price, vat_rate, vat_amount, total_amount, validity_period, description, created_by)
    values (p_id_contract, p_id_lic_format, p_guid, v_num, p_price, p_vat_rate, p_vat_amount,
            p_total_amount, v_validity_period, p_description, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_license(p_id bigint,
                                                    p_guid character varying,
                                                    p_num character varying,
                                                    p_id_lic_format bigint,
                                                    p_price numeric,
                                                    p_vat_rate numeric,
                                                    p_vat_amount numeric,
                                                    p_total_amount numeric,
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
    v_license license%rowtype;
    v_validity_period daterange;
BEGIN

    select c.* into v_contract from contract c, license l
    where l.id_contract = c.id
      and l.id = p_id;

    select l.* into v_license from license l
    where l.id = p_id
    for update;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');

    if isempty(v_contract.validity_period * v_validity_period) then
        raise exception 'Лицензия не пересекается с периодом договора!'
            using errcode = 20105;
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
$$ language plpgsql;

create or replace function pkg_contract.ins_license_oip(p_id_license bigint,
                                                        p_id_oip_str text,
                                                        p_username character varying
) returns bigint[]
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    inserted_ids bigint[];
    v_errors text;
    v_rec record;
BEGIN
    -- Вызываем функцию валидации ОДИН РАЗ и сохраняем результат
    create temp table temp_validation_results on commit drop as
    select * from pkg_contract.validate_oip_with_hierarchy(p_id_oip_str);

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
        insert into license_oip (id_license, id_oip, id_root_oip, created_by)
            select p_id_license, id_oip, id_root, p_username
            from temp_validation_results
            returning id
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

create or replace function pkg_contract.ins_license_rights(p_id_license bigint,
                                                           p_id_right_types text,
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

    select count(distinct coalesce(rt.id_parent, -1)) into v_cnt from sync__klf_right_type as rt
       join unnest(v_array) as rt0(id) on rt.id = rt0.id;

    if v_cnt > 1 then
        raise exception 'Ошибка создания права! Типы прав из разных поддеревьев дерева прав в одной привязке права к лицензии не поддерживается!'
            using errcode = 20100;
    end if;

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

    insert into license_rights (id_license, hb_start_date, hb_days, created_by)
    values (p_id_license, p_hb_start_date,
            case when p_hb_start_date is not null then p_hb_days else null end, p_username)
    returning id into r_result;

    insert into license_rights_rt (id_lic_rights, id_right_type, created_by)
    select r_result,rt.id, p_username from unnest(v_array) as rt(id);

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_license_rights(p_id bigint,
                                                           p_id_license bigint,
                                                           p_id_right_types text,
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
        select count(distinct coalesce(rt.id_parent, -1)) into v_cnt from sync__klf_right_type as rt
            join unnest(v_array) as rt0(id) on rt.id = rt0.id;
        if v_cnt > 1 then
            raise exception 'Ошибка создания права! Типы прав из разных поддеревьев дерева прав в одной привязке права к лицензии не поддерживается!'
                using errcode = 20100;
        end if;
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
$$ language plpgsql;

drop function if exists pkg_contract.ins_license_rt_feature_set(p_id_fmt_rights bigint,
                                                               p_is_exclusive boolean,
                                                               p_is_use_right boolean,
                                                               p_beg_date date,
                                                               p_end_date date,
                                                               p_username character varying);

create or replace function pkg_contract.ins_license_rt_feature_set(p_id_lic_rights bigint,
                                                                   p_is_exclusive boolean,
                                                                   p_is_use_right boolean,
                                                                   p_is_sub_license boolean,
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
    v_lic_rights  license_rights%rowtype;
BEGIN

    if p_id_lic_rights is null then
        raise exception 'Ошибка создания набора характеристик! Не указан идентификатор права лицензии (p_id_lic_rights)!'
            using errcode = 20109;
    end if;

    select * into v_lic_rights from license_rights where id = p_id_lic_rights;
    select * into v_license from license where id = v_lic_rights.id_license;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    if isempty(v_license.validity_period * v_validity_period) then
        raise exception 'Период набора характеристик не пересекается с периодом лицензии!'
            using errcode = 20110;
    else
        v_validity_period := v_license.validity_period * v_validity_period;
    end if;

    insert into license_rt_feature_set (id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
    values (p_id_lic_rights, coalesce(p_is_exclusive, false), coalesce(p_is_use_right, false),
            coalesce(p_is_sub_license, false), v_validity_period, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

drop function if exists pkg_contract.upd_license_rt_feature_set(p_id bigint,
                                                                p_id_lic_rights bigint,
                                                                p_is_exclusive boolean,
                                                                p_is_use_right boolean,
                                                                p_beg_date date,
                                                                p_end_date date,
                                                                p_username character varying);

create or replace function pkg_contract.upd_license_rt_feature_set(p_id bigint,
                                                                   p_id_lic_rights bigint,
                                                                   p_is_exclusive boolean,
                                                                   p_is_use_right boolean,
                                                                   p_is_sub_license boolean,
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
    v_lic_rights  license_rights%rowtype;
    v_feature_set license_rt_feature_set%rowtype;
BEGIN

    if p_id_lic_rights is null then
        raise exception 'Ошибка обновления набора характеристик! Не указан идентификатор права лицензии (p_id_lic_rights)!'
            using errcode = 20111;
    end if;

    select * into v_lic_rights from license_rights where id = p_id_lic_rights;
    select * into v_license from license where id = v_lic_rights.id_license;

    v_validity_period := daterange(p_beg_date, p_end_date, '[]');
    if isempty(v_license.validity_period * v_validity_period) then
        raise exception 'Период набора характеристик не пересекается с периодом лицензии!'
            using errcode = 20110;
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
$$ language plpgsql;

create or replace function pkg_contract.ins_license_rt_features(
    p_id_lic_rights bigint,
    p_id_feature_set bigint,
    p_id_feature integer,
    p_is_included boolean,
    p_username varchar
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
declare
    v_rt_name varchar;
    v_fc_name varchar;
    v_is_native boolean;
    r_result bigint;
    v_feature_category bigint;
begin
    if p_id_feature is null then
        raise exception 'Ошибка создания характеристики! Не указан идентификатор характеристики (p_id_feature)!'
            using errcode = 20114;
    end if;

    -- извлекаем категорию признака
    select id_feature_category
    into v_feature_category
    from sync__klf_feature_tree
    where id = p_id_feature;

    if not found then
        raise exception 'Ошибка создания характеристики! Указан не существующий идентификатор характеристики [p_id_feature=%]!', p_id_feature
            using errcode = 20115;
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
        select rt.name, fc.name
        into v_rt_name, v_fc_name
        from license_rights lr
            join license_rights_rt lrt on lrt.id_lic_rights = lr.id
            join sync__klf_right_type rt on rt.id = lrt.id_right_type
            join sync__klf_feature_category fc on fc.id = v_feature_category
        where lr.id = p_id_lic_rights limit 1;

        raise exception 'Ошибка создания характеристики! Характеристика категории "%" не может использоваться для права "%"!',
            v_fc_name, v_rt_name
            using errcode = 20119;
    end if;

    -- вставка
    insert into license_rt_features (id_lic_rights, id_feature_set, id_feature_category, id_feature, is_included, is_native, created_by)
    values (p_id_lic_rights, p_id_feature_set, v_feature_category, p_id_feature,
            coalesce(p_is_included, true), v_is_native, p_username)
    returning id into r_result;

    call pkg_contract.make_change_buffer(p_action => 'INSERT', p_username => p_username, p_id_rt_feature => r_result);

    return r_result;
end;
$$ language plpgsql;

-- создание/изменение формата

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

create or replace function pkg_contract.ins_format_rights(p_id_lic_format bigint,
                                                          p_id_right_types text,
                                                          p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint;
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

    select count(distinct coalesce(rt.id_parent, -1)) into v_cnt from sync__klf_right_type as rt
        join unnest(v_array) as rt0(id) on rt.id = rt0.id;

    if v_cnt > 1 then
        raise exception 'Ошибка создания права! Типы прав из разных поддеревьев дерева прав в одной привязке права к формату не поддерживается!'
            using errcode = 20100;
    end if;

    insert into format_rights (id_lic_format, created_by)
    values (p_id_lic_format, p_username)
    returning id into r_result;

    insert into format_rights_rt (id_fmt_rights, id_right_type, created_by)
    select r_result,rt.id, p_username from unnest(v_array) as rt(id);

    return r_result;
END;
$$ language plpgsql;

create or replace function pkg_contract.upd_format_rights(p_id bigint,
                                                          p_id_lic_format bigint,
                                                          p_id_right_types text,
                                                          p_username character varying
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
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
        select count(distinct coalesce(rt.id_parent, -1)) into v_cnt from sync__klf_right_type as rt
            join unnest(v_array) as rt0(id) on rt.id = rt0.id;
        if v_cnt > 1 then
            raise exception 'Ошибка создания права! Типы прав из разных поддеревьев дерева прав в одной привязке права к формату не поддерживается!'
                using errcode = 20100;
        end if;
    end if;

    update format_rights
        set id_lic_format = p_id_lic_format,
            updated_by = p_username,
            updated_at = current_timestamp
    where id = p_id;

    if v_array is not null then
       delete from format_rights_rt
         where id_fmt_rights = p_id;
       insert into format_rights_rt (id_fmt_rights, id_right_type, created_by)
         select p_id,rt.id, p_username from unnest(v_array) as rt(id);
    end if;

    return p_id;
END;
$$ language plpgsql;

drop function if exists pkg_contract.ins_format_rt_feature_set(p_id_fmt_rights bigint,
                                                               p_is_exclusive boolean,
                                                               p_is_use_right boolean,
                                                               p_beg_date date,
                                                               p_end_date date,
                                                               p_username character varying);

create or replace function pkg_contract.ins_format_rt_feature_set(p_id_fmt_rights bigint,
                                                                  p_is_exclusive boolean,
                                                                  p_is_use_right boolean,
                                                                  p_is_sub_license boolean,
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
    insert into format_rt_feature_set (id_fmt_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
    values (p_id_fmt_rights, coalesce(p_is_exclusive, false), coalesce(p_is_use_right, false),
            coalesce(p_is_sub_license, false), v_validity_period, p_username)
    returning id into r_result;

    return r_result;
END;
$$ language plpgsql;

drop function if exists pkg_contract.upd_format_rt_feature_set(p_id bigint,
                                                               p_is_exclusive boolean,
                                                               p_is_use_right boolean,
                                                               p_beg_date date,
                                                               p_end_date date,
                                                               p_username character varying);

create or replace function pkg_contract.upd_format_rt_feature_set(p_id bigint,
                                                                  p_is_exclusive boolean,
                                                                  p_is_use_right boolean,
                                                                  p_is_sub_license boolean,
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
        is_sub_license = coalesce(p_is_sub_license, is_sub_license),
        validity_period = v_validity_period,
        updated_by = p_username,
        updated_at = current_timestamp
    where
        id = p_id;

    return p_id;
END;
$$ language plpgsql;

create or replace function pkg_contract.ins_format_rt_features(
    p_id_fmt_rights bigint,
    p_id_feature_set bigint,
    p_id_feature integer,
    p_is_included boolean,
    p_username varchar
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
declare
    v_rt_name varchar;
    v_fc_name varchar;
    v_is_native boolean;
    r_result bigint;
    v_feature_category bigint;
begin
    if p_id_feature is null then
        raise exception 'Ошибка создания характеристики! Не указан идентификатор характеристики (p_id_feature)!'
            using errcode = 20114;
    end if;

    -- извлекаем категорию признака
    select id_feature_category
    into v_feature_category
    from sync__klf_feature_tree
    where id = p_id_feature;

    if not found then
        raise exception 'Ошибка создания характеристики! Указан не существующий идентификатор характеристики [p_id_feature=%]!', p_id_feature
            using errcode = 20115;
    end if;

    -- проверка категорий: сначала "down", потом "up"
    if exists (
        select 1
        from format_rights fr
            join format_rights_rt frt on frt.id_fmt_rights = fr.id
            join vw_rt_cat_down cd on cd.id_right_type = frt.id_right_type
        where fr.id = p_id_fmt_rights
          and cd.id_feature_category = v_feature_category
    ) then
        v_is_native := true;
    elsif exists (
        select 1
        from format_rights fr
            join format_rights_rt frt on frt.id_fmt_rights = fr.id
            join vw_rt_cat_up cu on cu.id_right_type = frt.id_right_type
        where fr.id = p_id_fmt_rights
          and cu.id_feature_category = v_feature_category
    ) then
        v_is_native := false;
    else
        select rt.name, fc.name
        into v_rt_name, v_fc_name
        from format_rights fr
            join format_rights_rt frt on frt.id_fmt_rights = fr.id
            join sync__klf_right_type rt on rt.id = frt.id_right_type
            join sync__klf_feature_category fc on fc.id = v_feature_category
        where fr.id = p_id_fmt_rights limit 1;

        raise exception 'Ошибка создания характеристики! Характеристика категории "%" не может использоваться для права "%"!',
            v_fc_name, v_rt_name
            using errcode = 20119;
    end if;

    -- вставка
    insert into format_rt_features (
        id_fmt_rights, id_feature_set, id_feature_category, id_feature, is_included, is_native, created_by
    )
    values (
               p_id_fmt_rights, p_id_feature_set, v_feature_category, p_id_feature,
               coalesce(p_is_included, true), v_is_native, p_username
           )
    returning id into r_result;

    return r_result;
end;
$$ language plpgsql;

-- удаление формата

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

create or replace procedure pkg_contract.del_format_rt_feature_set(p_id bigint,
                                                                   p_username character varying,
                                                                   p_use_cascade boolean default false
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    if p_use_cascade then
        delete from format_rt_features where id_feature_set = p_id;
    end if;
    delete from format_rt_feature_set where id = p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_format_rights(p_id bigint,
                                                           p_username character varying,
                                                           p_use_cascade boolean default false
)
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    rec record;
BEGIN
    if p_use_cascade then
        for rec in select * from format_rt_feature_set where id_fmt_rights = p_id loop
                call pkg_contract.del_format_rt_feature_set(rec.id, p_username, true);
            end loop;
        delete from format_rights_rt where id_fmt_rights = p_id;
    end if;
    delete from format_rights where id = p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_format(p_id bigint,
                                                            p_username character varying,
                                                            p_use_cascade boolean default false
)
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    rec record;
BEGIN
    if p_use_cascade then
        for rec in select * from format_rights where id_lic_format = p_id loop
                call pkg_contract.del_format_rights(rec.id, p_username, true);
            end loop;
    end if;
    delete from license_format where id = p_id;
END;
$$ language plpgsql;

-- удаление контракта

create or replace procedure pkg_contract.del_license_rt_features(p_id bigint,
                                                                 p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_rt_feature => p_id);
    delete from license_rt_features where id = p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_rt_feature_set(p_id bigint,
                                                                    p_username character varying,
                                                                    p_use_cascade boolean default false
)
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    if p_use_cascade then
        delete from license_rt_features where id_feature_set = p_id;
    end if;
    delete from license_rt_feature_set where id = p_id;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_rights(p_id bigint,
                                                            p_username character varying,
                                                            p_use_cascade boolean default false
)
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    rec record;
BEGIN
    if p_use_cascade then
        for rec in select id from license_rt_feature_set where id_lic_rights = p_id loop
                call pkg_contract.del_license_rt_feature_set(rec.id, p_username, true);
            end loop;
        delete from license_rights_rt where id_lic_rights = p_id;
    end if;
    delete from license_rights where id = p_id;
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
    if p_id is not null then
        call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => p_id);
        delete from license_oip where id = p_id;
    end if;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.del_license_oip_by_root(p_id_license bigint,
                                                                 p_id_root_oip bigint,
                                                                 p_username character varying
)
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_rec record;
BEGIN
    if not exists (select 1 from license where id = p_id_license) then
        raise exception 'Лицензия не найдена! [id = %]', p_id_license using errcode = 20120;
    end if;
    if not exists (select 1 from sync__klf_oip where id = p_id_root_oip) then
        raise exception 'Корневой ОИС не найден! [id = %]', p_id_root_oip using errcode = 20120;
    end if;
    for v_rec in select * from license_oip where id_license = p_id_license and id_root_oip = p_id_root_oip loop
            call pkg_contract.make_change_buffer(p_action => 'DELETE', p_username => p_username, p_id_lic_oip => v_rec.id);
    end loop;
    delete from license_oip where id_license = p_id_license and id_root_oip = p_id_root_oip;
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

create or replace procedure pkg_contract.del_license(p_id bigint,
                                                     p_username character varying,
                                                     p_use_cascade boolean default false
)
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    rec record;
BEGIN
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
$$ language plpgsql;

create or replace procedure pkg_contract.del_contract(p_id bigint,
                                                      p_username character varying,
                                                      p_use_cascade boolean default false
)
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    rec record;
BEGIN
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
$$ language plpgsql;

-- функции и процедуры

create or replace function pkg_contract.get_features_set_hash(
    p_id_lic_rights  bigint,
    p_id_feature_set bigint,
    p_id_right_type  integer,
    p_username       varchar,
    p_use_format     boolean,
    p_use_cache      boolean default false
) returns text[]
    security definer
    set search_path = rightsflow
as
$$
declare
    v_result   text[] := array[]::text[];   -- финальный результат
    v_result1  text[] := array[]::text[];   -- from vw_rt_cat_down
    v_result2  text[] := array[]::text[];   -- from vw_rt_cat_up
    v_sql      text;
    v_categories int[];        -- список категорий для первой группы
    v_categories2 int[];       -- список категорий для второй группы
    v_cat_parts  text[];       -- вспомогательно для cat_list expression
    v_cat_list_expr text;
    v_cat      integer;
    v_cnt      integer;
    v_available_ids text[];    -- сбор доступных id (в форме 'category:id')
    v_quoted_vals text;
    v_sub        text;
    v_final_sql  text;
    v_val_text   text;
    v_features_table text;
    v_hash_table text;
begin
    raise notice 'id_feature_set: %, id_right_type: %', p_id_feature_set, p_id_right_type;

    -- устанавливаем имена таблиц в зависимости от типа
    if p_use_format = false then
        v_features_table := 'license_rt_features';
        v_hash_table := 'license_rt_features_hash';
    else
        v_features_table := 'format_rt_features';
        v_hash_table := 'format_rt_features_hash';
    end if;

    -- 1) попытка взять из кеша (если включено)
    if p_use_cache then
        v_final_sql := format(
                'select array_agg(hash_value order by hash_value) from %s
                 where id_lic_rights = $1 and id_feature_set = $2 and id_right_type = $3',
                v_hash_table);
        execute v_final_sql into v_result using p_id_lic_rights, p_id_feature_set, p_id_right_type;

        if v_result is not null and array_length(v_result, 1) is not null then
            return v_result;
        end if;
    end if;

    --------------------------------------------------------------------------------
    -- 2) первая группа: vw_rt_cat_down
    --------------------------------------------------------------------------------
    select array_agg(distinct id_feature_category order by id_feature_category)
    into v_categories
    from vw_rt_cat_down
    where id_right_type = p_id_right_type;

    if v_categories is not null and array_length(v_categories, 1) is not null then
        -- сформировать выражение для сборки хэша: cat1.id || ',' || cat2.id || ...
        select array_agg('cat' || cid || '.id' order by cid) into v_cat_parts
        from unnest(v_categories) as c(cid);

        if v_cat_parts is not null then
            -- используем dollar-quoted delimiter, чтобы не путаться с кавычками
            v_cat_list_expr := array_to_string(v_cat_parts, $x$ || ',' || $x$);
        else
            v_cat_list_expr := null;
        end if;

        v_sql := null;

        -- пройти по каждой категории и подготовить подзапрос (v_sub) для cross join
        foreach v_cat in array v_categories loop
                -- если для категории есть строки с is_include = false (исключения характеристик)
                v_final_sql := format(
                        'select count(*) from %s where id_feature_set = $1 and id_feature_category = $2 and is_included = false',
                        v_features_table);
                execute v_final_sql into v_cnt using p_id_feature_set, v_cat;

                if v_cnt = 0 then
                    -- нет исключений: проверить, есть ли конкретные связи в license_rt_features
                    v_final_sql := format(
                            'select count(*) from %s where id_feature_set = $1 and id_feature_category = $2',
                            v_features_table);
                    execute v_final_sql into v_cnt using p_id_feature_set, v_cat;

                    if v_cnt > 0 then
                        v_final_sql := format(
                                'select array_agg((id_feature_category::text || '':'' || id_feature::text) order by id_feature)
                                 from %s where id_feature_set = $1 and id_feature_category = $2',
                                v_features_table);
                        execute v_final_sql into v_available_ids using p_id_feature_set, v_cat;
                    else
                        -- нет записей в license_rt_features: берем дефолт (root feature parent is null limit 1)
                        select id_feature_category::text || ':' || id::text into v_val_text
                        from sync__klf_feature_tree
                        where id_feature_category = v_cat and id_parent is null
                        limit 1;

                        if v_val_text is not null then
                            v_available_ids := array[v_val_text];
                        else
                            v_available_ids := array[]::text[];
                        end if;
                    end if;

                else
                    -- есть исключения (is_included = false) — используем рекурсивные cte (with recursive)
                    v_final_sql := format($sql$
with recursive excluded_nodes as (
  select id_feature from %s
   where id_feature_set = $1 and id_feature_category = $2 and is_included = false
),
ancestors as (
  select k.id, k.id_parent from sync__klf_feature_tree k where k.id in (select id_feature from excluded_nodes)
  union all
  select k2.id, k2.id_parent from sync__klf_feature_tree k2 join ancestors a on k2.id = a.id_parent
),
first_set as (
  select id_feature_category::text || ':' || id::text as id
    from sync__klf_feature_tree o1
   where o1.id_parent in (select id_parent from ancestors where id_parent is not null)
     and id_feature_category = $3
),
second_set as (
  select id_feature_category::text || ':' || id::text as id
    from sync__klf_feature_tree where id in (select id from ancestors)
)
select array_agg(id order by id) from (select id from first_set except select id from second_set) t;
$sql$, v_features_table);

                    execute v_final_sql into v_available_ids using p_id_feature_set, v_cat, v_cat;
                    -- v_available_ids может быть null, проверим дальше
                end if;

                -- fallback: если v_available_ids пуст или null -> пробуем дефолт (parent is null limit 1)
                if v_available_ids is null or array_length(v_available_ids, 1) is null then
                    select id_feature_category::text || ':' || id::text into v_val_text
                    from sync__klf_feature_tree
                    where id_feature_category = v_cat and id_parent is null
                    limit 1;

                    if v_val_text is not null then
                        v_available_ids := array[v_val_text];
                    else
                        v_available_ids := array[]::text[];
                    end if;
                end if;

                -- подготовить подзапрос вида: (select unnest(array['c:1','c:2']::text[]) as id) cat<cat>
                if array_length(v_available_ids, 1) > 0 then
                    select array_to_string(array_agg(quote_literal(x)), ',') into v_quoted_vals
                    from unnest(v_available_ids) as x;
                    v_sub := format('(select unnest(array[%s]::text[]) as id) cat%s', v_quoted_vals, v_cat);
                else
                    -- если нечего — подставим пустую таблицу (чтобы не ломать cross join)
                    v_sub := format('(select null::text as id) cat%s', v_cat);
                end if;

                -- собрать v_sql: первый элемент — select distinct <cat_list_expr> as hash from <first_sub>
                if v_sql is null then
                    v_sql := format('select distinct %s as hash from %s', v_cat_list_expr, v_sub);
                else
                    v_sql := v_sql || ' cross join ' || v_sub;
                end if;

                -- сброс временных переменных
                v_available_ids := null;
                v_val_text := null;
            end loop; -- v_categories loop

        -- выполнить l_sql и собрать в массив
        if v_sql is not null then
            v_final_sql := 'select array_agg(hash order by hash) from (' || v_sql || ') t';
            execute v_final_sql into v_result1;
            if v_result1 is null then
                v_result1 := array[]::text[];
            end if;
        end if;
    end if; -- end first group

    -- отладочный вывод (аналог dbms_output)
    if v_sql is not null then
        raise debug 'last dynamic sql (result1): %', left(coalesce(v_sql,''), 2000);
    end if;

    --------------------------------------------------------------------------------
    -- 3) вторая группа: vw_rt_cat_up
    --------------------------------------------------------------------------------
    select array_agg(distinct id_feature_category order by id_feature_category)
    into v_categories2
    from vw_rt_cat_up
    where id_right_type = p_id_right_type;

    if v_categories2 is not null and array_length(v_categories2, 1) is not null then
        select array_agg('cat' || cid || '.id' order by cid) into v_cat_parts
        from unnest(v_categories2) as c(cid);

        if v_cat_parts is not null then
            v_cat_list_expr := array_to_string(v_cat_parts, $x$ || ',' || $x$);
        else
            v_cat_list_expr := null;
        end if;

        v_sql := null;

        foreach v_cat in array v_categories2 loop
                v_final_sql := format(
                        'select count(*) from %s where id_feature_set = $1 and id_feature_category = $2 and is_included = false',
                        v_features_table);
                execute v_final_sql into v_cnt using p_id_feature_set, v_cat;

                if v_cnt = 0 then
                    v_final_sql := format(
                            'select count(*) from %s where id_feature_set = $1 and id_feature_category = $2',
                            v_features_table);
                    execute v_final_sql into v_cnt using p_id_feature_set, v_cat;

                    if v_cnt > 0 then
                        v_final_sql := format(
                                'select array_agg((id_feature_category::text || '':'' || id_feature::text) order by id_feature)
                                 from %s where id_feature_set = $1 and id_feature_category = $2',
                                v_features_table);
                        execute v_final_sql into v_available_ids using p_id_feature_set, v_cat;
                    else
                        select id_feature_category::text || ':' || id::text into v_val_text
                        from sync__klf_feature_tree
                        where id_feature_category = v_cat and id_parent is null
                        limit 1;

                        if v_val_text is not null then
                            v_available_ids := array[v_val_text];
                        else
                            v_available_ids := array[]::text[];
                        end if;
                    end if;

                else
                    -- рекурсивный вариант для исключений
                    v_final_sql := format($sql$
with recursive excluded_nodes as (
  select id_feature from %s
   where id_feature_set = $1 and id_feature_category = $2 and is_included = false
),
ancestors as (
  select k.id, k.id_parent from sync__klf_feature_tree k where k.id in (select id_feature from excluded_nodes)
  union all
  select k2.id, k2.id_parent from sync__klf_feature_tree k2 join ancestors a on k2.id = a.id_parent
),
first_set as (
  select id_feature_category::text || ':' || id::text as id
    from sync__klf_feature_tree o1
   where o1.id_parent in (select id_parent from ancestors where id_parent is not null)
     and id_feature_category = $3
),
second_set as (
  select id_feature_category::text || ':' || id::text as id
    from sync__klf_feature_tree where id in (select id from ancestors)
)
select array_agg(id order by id) from (select id from first_set except select id from second_set) t;
$sql$, v_features_table);

                    execute v_final_sql into v_available_ids using p_id_feature_set, v_cat, v_cat;
                end if;

                if v_available_ids is null or array_length(v_available_ids, 1) is null then
                    select id_feature_category::text || ':' || id::text into v_val_text
                    from sync__klf_feature_tree
                    where id_feature_category = v_cat and id_parent is null
                    limit 1;

                    if v_val_text is not null then
                        v_available_ids := array[v_val_text];
                    else
                        v_available_ids := array[]::text[];
                    end if;
                end if;

                if array_length(v_available_ids, 1) > 0 then
                    select array_to_string(array_agg(quote_literal(x)), ',') into v_quoted_vals
                    from unnest(v_available_ids) as x;
                    v_sub := format('(select unnest(array[%s]::text[]) as id) cat%s', v_quoted_vals, v_cat);
                else
                    v_sub := format('(select null::text as id) cat%s', v_cat);
                end if;

                if v_sql is null then
                    v_sql := format('select distinct %s as hash from %s', v_cat_list_expr, v_sub);
                else
                    v_sql := v_sql || ' cross join ' || v_sub;
                end if;

                v_available_ids := null;
                v_val_text := null;
            end loop;

        if v_sql is not null then
            v_final_sql := 'select array_agg(hash order by hash) from (' || v_sql || ') t';
            execute v_final_sql into v_result2;
            if v_result2 is null then
                v_result2 := array[]::text[];
            end if;
        end if;
    end if; -- end second group

    if v_sql is not null then
        raise debug 'last dynamic sql (result2): %', left(coalesce(v_sql,''), 2000);
    end if;

    --------------------------------------------------------------------------------
    -- 4) комбинация результатов
    --------------------------------------------------------------------------------
    if array_length(v_result1, 1) is not null and array_length(v_result2, 1) is null then
        v_result := v_result1;
    elsif array_length(v_result2, 1) is not null and array_length(v_result1, 1) is null then
        -- второй набор получает префикс ';' при единственном использовании
        v_result := array(select ';' || x from unnest(v_result2) as x);
    elsif array_length(v_result1, 1) is not null and array_length(v_result2, 1) is not null then
        -- cartesian product r1 || ';' || r2
        v_result := array(
                select r1 || ';' || r2 from unnest(v_result1) as r1 cross join unnest(v_result2) as r2
                    );
    else
        v_result := array[]::text[];
    end if;

    --------------------------------------------------------------------------------
    -- 5) кеширование результата (вставка в license_rt_features_hash)
    --------------------------------------------------------------------------------
    begin
        if p_use_cache and array_length(v_result, 1) is not null then
            for v_val_text in select unnest(v_result) loop
                    v_final_sql := format(
                            'insert into %s (id_lic_rights, id_feature_set, id_right_type, hash_value, created_by)
                             values ($1, $2, $3, $4, $5)',
                            v_hash_table);
                    execute v_final_sql using p_id_lic_rights, p_id_feature_set, p_id_right_type, v_val_text, p_username;
                end loop;
        end if;
    exception
        when others then
            null; -- ничего не делаем, если не удалось сохранить кеш
    end;

    return v_result;
end;
$$ language plpgsql;

comment on function pkg_contract.get_features_set_hash(bigint, bigint, integer, varchar, boolean, boolean) is
    'Вычисляет хэш набора характеристик прав. Использует соответствующие таблицы для расчёта и кеширования';

drop function if exists pkg_contract.get_elemental_rights(p_id_oip integer, p_id_org integer, p_in_out character, p_beg_date date, p_end_date date, p_username character varying);

create or replace function pkg_contract.get_elemental_rights(
    p_id_oip        int,
    p_id_org        int,
    p_in_out        char(1),
    p_beg_date      date,
    p_end_date      date,
    p_username      character varying
)
    returns table (
                      beg_date        date,
                      end_date        date,
                      id_lic_rights   bigint,
                      id_feature_set  bigint,
                      id_right_type   int,
                      id_org          int,
                      sign_date       date,
                      is_exclusive    boolean,
                      is_use_right    boolean,
                      is_sub_license  boolean,
                      features_hash   text
                  )
    security definer
    set search_path = rightsflow
as
$$
declare
    v_p1 char(2)[] := array[]::char(2)[];
    v_p2 char(2)[] := array[]::char(2)[];
begin

    if p_in_out = 'P' then
       v_p1 := array['eP','iP'];
       v_p2 := array['iS'];
    elsif p_in_out = 'S' then
       v_p1 := array['eS','iS'];
       v_p2 := array['iP'];
    end if;

    return query
        with lic as (
            select lrt.*
            from vw_lic_rt lrt
            where lrt.id_oip = p_id_oip
              and ((lrt.id_org = p_id_org and lrt.in_out = any(v_p1)) or
                   (lrt.id_org_party = p_id_org and lrt.in_out = any(v_p2)))
              and lrt.validity_period && daterange(p_beg_date, p_end_date, '[]')
              and lrt.status_mode = 2 -- контракты для которых разрешён расчёт
        )
        select
            lower(lic.validity_period) as beg_date,
            upper(lic.validity_period)-1 as end_date,
            lic.id_lic_rights,
            lic.id_feature_set,
            lic.id_right_type,
            lic.id_org,
            lic.sign_date,
            lic.is_exclusive,
            lic.is_use_right,
            lic.is_sub_license,
            ntab.features_hash
        from lic
        cross join lateral unnest(
           pkg_contract.get_features_set_hash(lic.id_lic_rights, lic.id_feature_set, lic.id_right_type, p_username, lic.use_format)
        ) as ntab(features_hash);
end;
$$ language plpgsql;

create or replace function pkg_contract.get_contract_id(p_id_contract_cparty bigint default null,
                                                        p_id_license bigint default null,
                                                        p_id_lic_oip bigint default null,
                                                        p_id_lic_rights bigint default null,
                                                        p_id_feature_set bigint default null
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    r_result bigint := null;
BEGIN

    if p_id_contract_cparty is not null then
        select id_contract from contract_counterparty cc
          where cc.id = p_id_contract_cparty into r_result;
    end if;

    if p_id_license is not null and r_result is null then
        select id_contract from license l
          where l.id = p_id_license into r_result;
    end if;

    if p_id_lic_oip is not null and r_result is null then
        select l.id_contract from license_oip lo
          join license l on l.id = lo.id_license
          where lo.id = p_id_lic_oip into r_result;
    end if;

    if p_id_lic_rights is not null and r_result is null then
        select l.id_contract from license_rights lr
          join license l on l.id = lr.id_license
          where lr.id = p_id_lic_rights into r_result;
    end if;

    if p_id_feature_set is not null and r_result is null then
        select l.id_contract from license_rt_feature_set fs
          join license_rights lr on lr.id = fs.id_lic_rights
          join license l on l.id = lr.id_license
          where fs.id = p_id_feature_set into r_result;
    end if;

    if r_result is null then
        raise exception 'Невозможно определить контракт по указанным параметрам [p_id_contract_cparty=%], [p_id_license=%], [id_lic_oip=%], [p_id_lic_rights=%], [p_id_feature_set=%]', p_id_contract_cparty, p_id_license, p_id_lic_oip, p_id_lic_rights, p_id_feature_set
            using errcode = 20119;
    end if;

    return r_result;
END;
$$ language plpgsql;

create or replace procedure pkg_contract.check_contract_status(p_id_contract bigint default null,
                                                               p_id_contract_cparty bigint default null,
                                                               p_id_license bigint default null,
                                                               p_id_lic_oip bigint default null,
                                                               p_id_lic_rights bigint default null,
                                                               p_id_feature_set bigint default null
) security definer
  set search_path = rightsflow
as
$$
DECLARE
    v_id_contract bigint := p_id_contract;
    v_state_mode integer;
    v_state_name varchar;
BEGIN

    if v_id_contract is null then
        v_id_contract := pkg_contract.get_contract_id(p_id_contract_cparty,
                                                      p_id_license,
                                                      p_id_lic_oip,
                                                      p_id_lic_rights,
                                                      p_id_feature_set);
    end if;

    select cs.mode, cs.name from contract c
    join lov_contract_status cs on c.id_contract_status = cs.id
      where c.id = v_id_contract into v_state_mode, v_state_name;

    if v_state_mode is null then
        raise exception 'Контракт [ID=%] не найден!', v_id_contract
            using errcode = 20120;
    end if;

    if v_state_mode in (1,2) then
        raise exception 'Контракт [ID=%] находится в состоянии [%], операция невозможна!', v_id_contract, v_state_name
            using errcode = 20121;
    end if;

END;
$$ language plpgsql;

create or replace procedure pkg_contract.make_change_buffer(
    p_action varchar,
    p_username varchar,
    p_id_contract bigint default null,
    p_id_license bigint default null,
    p_id_lic_oip bigint default null,
    p_id_lic_rights bigint default null,
    p_id_feature_set bigint default null,
    p_id_rt_feature bigint default null
)
    security definer
    set search_path = rightsflow
    language plpgsql
as $$
declare
    v_id_contract bigint;
    v_id_license bigint;
    v_entity_name varchar;
    v_id_org integer;
    v_id_entity bigint;
    v_data jsonb;
begin
    -- Обработка контракта
    if p_id_contract is not null then
        select id into v_id_contract from contract where id = p_id_contract;

        if v_id_contract is null then
            raise exception 'Контракт [ID=%] не найден!', p_id_contract
                using errcode = '20120';
        end if;

        v_entity_name := 'CONTRACT';
        v_id_entity := v_id_contract;

        select jsonb_agg(jsonb_build_object('id_contract', c.id, 'id_org', c.id_org, 'id_oip', lo.id_oip))
        into v_data
        from contract c
          join license l on l.id_contract = c.id
          join license_oip lo on lo.id_license = l.id
        where c.id = v_id_contract;

        -- Обработка лицензии и связанных сущностей
    ELSE
        -- Определение лицензии и типа сущности
        if p_id_license is not null then
            v_id_license := p_id_license;
            v_entity_name := 'LICENSE';
            v_id_entity := p_id_license;

        elsif p_id_lic_oip is not null then
            select id_license into v_id_license from license_oip where id = p_id_lic_oip;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_OIP';
                v_id_entity := p_id_lic_oip;
            end if;

        elsif p_id_lic_rights is not null then
            select id_license into v_id_license from license_rights where id = p_id_lic_rights;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_RT';
                v_id_entity := p_id_lic_rights;
            end if;

        elsif p_id_feature_set is not null then
            select lr.id_license into v_id_license
            from license_rt_feature_set fs
              join license_rights lr on lr.id = fs.id_lic_rights
            where fs.id = p_id_feature_set;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_RT_FEATURE_SET';
                v_id_entity := p_id_feature_set;
            end if;
        elsif p_id_rt_feature is not null then
            select lr.id_license into v_id_license
            from license_rt_features lf
              join license_rights lr on lr.id = lf.id_lic_rights
            where lf.id = p_id_rt_feature;

            if v_id_license is not null then
                v_entity_name := 'LICENSE_RT_FEATURE';
                v_id_entity := p_id_rt_feature;
            end if;
        end if;

        -- Валидация и получение данных лицензии
        if v_id_license is not null then
            -- проверка существования лицензии
            if not exists (select 1 from license where id = v_id_license) then
                raise exception 'Лицензия [ID=%] не найдена!', v_id_license
                    using errcode = 20120;
            end if;

            -- получение id_org
            select c.id_org, c.id into v_id_org, v_id_contract
            from contract c
              join license l on l.id_contract = c.id
            where l.id = v_id_license;

            -- Формирование данных в зависимости от типа сущности
            if v_entity_name = 'LICENSE_OIP' then
                select jsonb_agg(jsonb_build_object('id_contract', v_id_contract, 'id_org', v_id_org, 'id_oip', id_oip))
                into v_data
                from license_oip
                where id = p_id_lic_oip;
            else
                select jsonb_agg(jsonb_build_object('id_contract', v_id_contract, 'id_org', v_id_org, 'id_oip', lo.id_oip))
                into v_data
                from license_oip lo
                where lo.id_license = v_id_license;
            end if;
        end if;
    end if;

    -- Вставка/обновление буфера изменений
    if v_data is not null then
        insert into contract_change_buffer (name_entity, id_entity, action, data, status, created_by)
        values (v_entity_name, v_id_entity, p_action, v_data, 'NEW', p_username)
        on conflict (name_entity, id_entity, action)
           do update set
              data = EXCLUDED.data,
              status = 'NEW',
              updated_by = p_username,
              updated_at = current_timestamp;
    end if;
end;
$$;

create or replace function pkg_contract.is_contract_valid(
    p_id_contract bigint,
    p_username character varying
) returns boolean
    security definer
    set search_path = rightsflow
as
$$
declare
    CRLF constant text := E'\r\n';
    r_result boolean := true;
    v_warning text;
begin
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
             exists(select 1 from license_oip where id_license = l.id) as has_oip,
             exists(select 1 from license_rights lr
                             join license_rights_rt lrt on lrt.id_lic_rights = lr.id
                             where lr.id_license = l.id) as has_lrt,
             exists(select 1 from format_rights fr
                             join format_rights_rt frt on frt.id_fmt_rights = fr.id
                             where fr.id_lic_format = l.id_lic_format) as has_frt
         from license l
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
            exists(select 1 from license_rt_features where id_feature_set = lrtfs.id) as has_features
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
            exists(select 1 from format_rt_features where id_feature_set = frtfs.id) as has_features
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
$$ language plpgsql;

create or replace procedure pkg_contract.make_contract_outbox_event(
    p_id_contract bigint,
    p_status_mode integer,
    p_username varchar
)
    security definer
    set search_path = rightsflow
    language plpgsql
as $$
begin

    if p_status_mode = 2 then
       insert into contract_outbox (id_oip, id_org, created_by)
       select distinct (jsonb_array_elements(data)->'id_oip')::integer AS id_oip,
                       (jsonb_array_elements(data)->'id_org')::integer AS id_org,
                       p_username as created_by
       from contract_change_buffer cb
       where cb.data @> jsonb_build_array(jsonb_build_object('id_contract', p_id_contract))
         and cb.status = 'NEW';
    end if;

    update contract_change_buffer cb
    set status = case when p_status_mode = 2 then 'PROCESSED' else 'ARCHIVED' end,
        updated_by = p_username,
        updated_at = current_timestamp
    where cb.data @> jsonb_build_array(jsonb_build_object('id_contract', p_id_contract))
      and cb.status = 'NEW';

end;
$$;

create or replace function pkg_contract.upd_contract_status(
    p_id_contract bigint,
    p_status_code varchar,
    p_username varchar
) returns bigint
    security definer
    set search_path = rightsflow
as
$$
declare
    v_contract           contract%rowtype;
    v_curr_status        lov_contract_status%rowtype;   -- текущий статус договора
    v_target_status      lov_contract_status%rowtype;   -- итоговый статус, в который переводим
    v_target_code        lov_contract_status.code%type; -- вычисленный целевой код
    v_input_code         lov_contract_status.code%type := upper(p_status_code);
    v_need_disable_check boolean := false;
begin
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
$$ language plpgsql;

-- Функции для триггеров

create or replace function pkg_contract.b2_correct_license_periods()
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
    perform set_config('rf.disable_status_check', 'true', true);
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
             join license_rights lr on lr.id_license = ul.id
    where fs.id_lic_rights = lr.id;
    perform set_config('rf.disable_status_check', 'false', true);

    return null; -- AFTER-триггер: возвращаем NULL
END;
$$ language plpgsql;

create or replace function pkg_contract.b2_remake_license_rt_feature_set_ext()
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
    DELETE FROM license_rt_feature_set_ext WHERE id_lic_rights = NEW.id;

    -- Если нет holdback-а -> просто копируем все feature-sets
    IF hb_start IS NULL THEN
        INSERT INTO license_rt_feature_set_ext
        (id_feature_set, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
        SELECT id, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by
        FROM license_rt_feature_set
        WHERE id_lic_rights = NEW.id;

        RETURN NULL;
    END IF;

    hb_end := hb_start + hb_days;

    -- 1) Копируем feature-sets, которые уже являются эксклюзивными, без изменений
    INSERT INTO license_rt_feature_set_ext
    (id_feature_set, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
    SELECT id, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by
    FROM license_rt_feature_set
    WHERE id_lic_rights = NEW.id
      AND is_exclusive;

    -- 2) Для не эксклюзивных feature-sets распределяем их по частям: before / hold / after
    INSERT INTO license_rt_feature_set_ext
    (id_feature_set, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
    SELECT
        fs.id,
        fs.id_lic_rights,
        CASE parts.part WHEN 'hold' THEN true ELSE fs.is_exclusive END AS is_exclusive,
        fs.is_use_right,
        fs.is_sub_license,
        daterange(parts.part_lower, parts.part_upper, '[)') AS validity_period,
        fs.created_by
    FROM license_rt_feature_set fs
             CROSS JOIN LATERAL (
        VALUES
            ( lower(fs.validity_period), least(hb_start, upper(fs.validity_period)), 'before' ),
            ( greatest(hb_start, lower(fs.validity_period)), least(hb_end, upper(fs.validity_period)), 'hold' ),
            ( greatest(hb_end, lower(fs.validity_period)), upper(fs.validity_period), 'after' )
        ) AS parts(part_lower, part_upper, part)
    WHERE fs.id_lic_rights = NEW.id
      AND NOT fs.is_exclusive
      -- исключить сегменты с NULL границами или пустыми диапазонами
      AND parts.part_lower IS NOT NULL
      AND parts.part_upper IS NOT NULL
      AND parts.part_lower < parts.part_upper;

    RETURN NULL;
END;
$$ language plpgsql;

create or replace function pkg_contract.b2_make_license_rt_feature_set_ext()
    returns trigger
    security definer
    set search_path = rightsflow
as
$$
DECLARE
    v_lic_rights license_rights%rowtype;
    v_hb_end_date date;
    v_period daterange;
BEGIN
    if tg_op not in ('INSERT', 'UPDATE') then
        return null;
    end if;

    if tg_op = 'UPDATE' then
        delete from license_rt_feature_set_ext where id_feature_set = NEW.id;
    end if;

    select * into v_lic_rights from license_rights where id = NEW.id_lic_rights;
    if not found then
        return null;
    end if;

    -- Простой случай: эксклюзивность или отсутствие holdback
    if v_lic_rights.hb_start_date is null or NEW.is_exclusive then
        insert into license_rt_feature_set_ext
        (id_feature_set, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
        values
            (NEW.id, NEW.id_lic_rights, NEW.is_exclusive, NEW.is_use_right, NEW.is_sub_license, NEW.validity_period, NEW.created_by);
        return null;
    end if;

    -- Предварительные вычисления для holdback
    v_hb_end_date := v_lic_rights.hb_start_date + v_lic_rights.hb_days;
    v_period := daterange(least(v_hb_end_date, upper(NEW.validity_period)-1), upper(NEW.validity_period), '[)');

    -- Holdback совпадает с началом периода
    if not lower_inf(NEW.validity_period) and lower(NEW.validity_period) = v_lic_rights.hb_start_date then
        -- Первый период (holdback) - всегда эксклюзивный
        insert into license_rt_feature_set_ext
        (id_feature_set, id_lic_rights, is_exclusive, is_use_right, is_sub_license, validity_period, created_by)
        select NEW.id, NEW.id_lic_rights, true, NEW.is_use_right, NEW.is_sub_license,
               daterange(v_lic_rights.hb_start_date, least(v_hb_end_date, upper(NEW.validity_period)), '[)'),
               NEW.created_by
        union all
        -- Второй период (после holdback) - с исходной эксклюзивностью
        select NEW.id, NEW.id_lic_rights, NEW.is_exclusive, NEW.is_use_right, NEW.is_sub_license, v_period, NEW.created_by
        where not isempty(v_period);

        -- Holdback внутри периода
    elsif v_lic_rights.hb_start_date <@ NEW.validity_period then
        insert into license_rt_feature_set_ext
        (id_feature_set, id_lic_rights, is_exclusive, is_use_right, is_sub_license,  validity_period, created_by)
        select NEW.id, NEW.id_lic_rights, NEW.is_exclusive, NEW.is_use_right, NEW.is_sub_license,
               daterange(lower(NEW.validity_period), v_lic_rights.hb_start_date, '[)'), NEW.created_by
        where not isempty(daterange(lower(NEW.validity_period), v_lic_rights.hb_start_date, '[)'))
        union all
        -- Holdback период - эксклюзивный
        select NEW.id, NEW.id_lic_rights, true, NEW.is_use_right, NEW.is_sub_license,
               daterange(v_lic_rights.hb_start_date, least(v_hb_end_date, upper(NEW.validity_period)), '[)'),
               NEW.created_by
        union all
        -- После holdback - исходная эксклюзивность
        select NEW.id, NEW.id_lic_rights, NEW.is_exclusive, NEW.is_use_right, NEW.is_sub_license, v_period, NEW.created_by
        where not isempty(v_period);
    end if;

    return null;
END;
$$ language plpgsql;

create or replace function pkg_contract.a2_check_license_rt_features()
    returns trigger
    security definer
    set search_path = rightsflow
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

create or replace function pkg_contract.a2_check_format_rt_features()
    returns trigger
    security definer
    set search_path = rightsflow
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

create or replace function pkg_contract.a1_protect_contract()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP = 'UPDATE' then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    call pkg_contract.check_contract_status(p_id_contract => OLD.id);

    if TG_OP = 'UPDATE' then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_contract.a1_protect_contract_cparty()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP in ('INSERT', 'UPDATE') then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    if TG_OP in ('INSERT', 'UPDATE') then
        call pkg_contract.check_contract_status(p_id_contract => NEW.id_contract);
    else
        call pkg_contract.check_contract_status(p_id_contract_cparty => OLD.id);
    end if;

    if TG_OP in ('INSERT', 'UPDATE') then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_contract.a1_protect_license()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP in ('INSERT', 'UPDATE') then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    if TG_OP in ('INSERT', 'UPDATE') then
       call pkg_contract.check_contract_status(p_id_contract => NEW.id_contract);
    else
       call pkg_contract.check_contract_status(p_id_license => OLD.id);
    end if;

    if TG_OP in ('INSERT', 'UPDATE') then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_contract.a1_protect_license_oip()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP in ('INSERT', 'UPDATE') then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    if TG_OP in ('INSERT', 'UPDATE') then
        call pkg_contract.check_contract_status(p_id_license => NEW.id_license);
    else
        call pkg_contract.check_contract_status(p_id_lic_oip => OLD.id);
    end if;

    if TG_OP in ('INSERT', 'UPDATE') then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_contract.a1_protect_license_rights()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP in ('INSERT', 'UPDATE') then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    if TG_OP in ('INSERT', 'UPDATE') then
        call pkg_contract.check_contract_status(p_id_license => NEW.id_license);
    else
        call pkg_contract.check_contract_status(p_id_lic_rights => OLD.id);
    end if;

    if TG_OP in ('INSERT', 'UPDATE') then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_contract.a1_protect_license_rights_rt()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP in ('INSERT', 'UPDATE') then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    if TG_OP in ('INSERT', 'UPDATE') then
        call pkg_contract.check_contract_status(p_id_lic_rights => NEW.id_lic_rights);
    else
        call pkg_contract.check_contract_status(p_id_lic_rights => OLD.id_lic_rights);
    end if;

    if TG_OP in ('INSERT', 'UPDATE') then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_contract.a1_protect_license_rt_feature_set()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP in ('INSERT', 'UPDATE') then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    if TG_OP in ('INSERT', 'UPDATE') then
        call pkg_contract.check_contract_status(p_id_lic_rights => NEW.id_lic_rights);
    else
        call pkg_contract.check_contract_status(p_id_feature_set => OLD.id);
    end if;

    if TG_OP in ('INSERT', 'UPDATE') then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

create or replace function pkg_contract.a1_protect_license_rt_features()
    returns trigger
    security definer
    set search_path = rightsflow
as $$
begin
    -- проверяем, отключена ли проверка
    if coalesce(nullif(current_setting('rf.disable_status_check', true), ''), 'false') = 'true' then
        if TG_OP in ('INSERT', 'UPDATE') then
            return NEW; -- пропустить проверку
        else
            return OLD; -- пропустить проверку
        end if;
    end if;

    -- основная логика защиты
    if TG_OP in ('INSERT', 'UPDATE') then
        call pkg_contract.check_contract_status(p_id_feature_set => NEW.id_feature_set);
    else
        call pkg_contract.check_contract_status(p_id_feature_set => OLD.id_feature_set);
    end if;

    if TG_OP in ('INSERT', 'UPDATE') then
        return NEW;
    else
        return OLD;
    end if;
end;
$$ language plpgsql;

-- Триггеры

create or replace trigger tr_a1_protect_contract
    before update or delete
    on rightsflow.contract
    for each row
execute function pkg_contract.a1_protect_contract();

create or replace trigger tr_a1_protect_contract_cparty
    before insert or delete
    on rightsflow.contract_counterparty
    for each row
execute function pkg_contract.a1_protect_contract_cparty();

create or replace trigger tr_a1_protect_license
    before insert or update or delete
    on rightsflow.license
    for each row
execute function pkg_contract.a1_protect_license();

create or replace trigger tr_a1_protect_license_oip
    before insert or delete
    on rightsflow.license_oip
    for each row
execute function pkg_contract.a1_protect_license_oip();

create or replace trigger tr_a1_protect_license_rights
    before insert or update or delete
    on rightsflow.license_rights
    for each row
execute function pkg_contract.a1_protect_license_rights();

create or replace trigger tr_a1_protect_license_rights_rt
    before insert or update or delete
    on rightsflow.license_rights_rt
    for each row
execute function pkg_contract.a1_protect_license_rights_rt();

create or replace trigger tr_a1_protect_license_rt_feature_set
    before insert or update or delete
    on rightsflow.license_rt_feature_set
    for each row
execute function pkg_contract.a1_protect_license_rt_feature_set();

create or replace trigger tr_a1_protect_license_rt_features
    before insert or delete
    on rightsflow.license_rt_features
    for each row
execute function pkg_contract.a1_protect_license_rt_features();

create or replace trigger tr_a2_check_license_rt_features
    before insert on rightsflow.license_rt_features
    for each row
execute function pkg_contract.a2_check_license_rt_features();

create or replace trigger tr_a2_check_format_rt_features
    before insert on rightsflow.format_rt_features
    for each row
execute function pkg_contract.a2_check_format_rt_features();

create or replace trigger tr_b2_correct_license_periods
    after update
    on rightsflow.contract
    for each row
execute function pkg_contract.b2_correct_license_periods();

create or replace trigger tr_b2_make_license_rt_feature_set_ext
    after insert or update
    on rightsflow.license_rt_feature_set
    for each row
execute function pkg_contract.b2_make_license_rt_feature_set_ext();

create or replace trigger tr_b2_remake_license_rt_feature_set_ext
    after update
    on rightsflow.license_rights
    for each row
execute function pkg_contract.b2_remake_license_rt_feature_set_ext();
