CREATE OR REPLACE FUNCTION pkg_contract.ins_license_rt_features(
    p_id_lic_rights bigint,
    p_id_feature_set bigint,
    p_id_feature integer,
    p_is_included boolean,
    p_username character varying)
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