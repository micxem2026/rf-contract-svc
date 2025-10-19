create schema if not exists pkg_constraint;
alter schema pkg_constraint owner to rightsflow;

create or replace function pkg_constraint.check_oip_use(p_id_oip integer)
    returns boolean
    security definer
    set search_path = rightsflow
as
$$
BEGIN
   return exists(
            with recursive oip as (
                    select
                        id_parent,
                        id_oip,
                        array[id_oip] as path
                    from sync__klf_oip_hierarchy
                    where id_parent = p_id_oip
                    union all
                    select
                        t.id_parent,
                        t.id_oip,
                        r.path || t.id_oip
                    from sync__klf_oip_hierarchy t
                             join oip r on r.id_oip = t.id_parent
                    where t.id_oip <> all(r.path)
            )
            select 1 from license_oip
             where id_oip in (select distinct id_oip from oip
                              union
                              select p_id_oip as id_oip)
            );
END;
$$ language plpgsql;

create or replace function pkg_constraint.check_right_type_use(p_id_right_type integer)
    returns boolean
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    return exists(
        with recursive rt as (
            select
                id,
                id_parent,
                array[id] as path
            from sync__klf_right_type
            where id = p_id_right_type
            union all
            select
                t.id,
                t.id_parent,
                r.path || t.id
            from sync__klf_right_type t
                     join rt r on r.id = t.id_parent
            where t.id <> all(r.path)
        )
        select 1 from license_rights_rt
        where id_right_type in (select id from rt)
        union
        select 1 from format_rights_rt
        where id_right_type in (select id from rt)
    );
END;
$$ language plpgsql;

create or replace function pkg_constraint.check_feature_category_use(p_id_feature_category integer)
    returns boolean
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    return exists(select 1 from license_rt_features where id_feature_category = p_id_feature_category) or
           exists(select 1 from format_rt_features where id_feature_category = p_id_feature_category);
END;
$$ language plpgsql;

create or replace function pkg_constraint.check_feature_use(p_id_feature integer)
    returns boolean
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    return exists(
        with recursive features as (
            select
                id,
                id_parent,
                id_feature_category,
                id_feature_plain,
                array[id] as path
            from sync__klf_feature_tree
            where id = p_id_feature
            union all
            select
                t.id,
                t.id_parent,
                t.id_feature_category,
                t.id_feature_plain,
                r.path || t.id
            from sync__klf_feature_tree t
                     join features r on r.id = t.id_parent
            where t.id <> all(r.path)
        )
        select 1 from license_rt_features
        where id_feature in (select id from features)
        union
        select 1 from format_rt_features
        where id_feature in (select id from features)
    );
END;
$$ language plpgsql;

create or replace function pkg_constraint.check_counterparty_use(p_id_cpart integer)
    returns boolean
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    return exists(select 1 from contract_counterparty where id_cpart = p_id_cpart);
END;
$$ language plpgsql;

create or replace function pkg_constraint.check_organization_use(p_id_org integer)
    returns boolean
    security definer
    set search_path = rightsflow
as
$$
BEGIN
    return exists(select 1 from contract where id_org = p_id_org) or
           exists(select 1 from contract where id_org_party = p_id_org);
END;
$$ language plpgsql;