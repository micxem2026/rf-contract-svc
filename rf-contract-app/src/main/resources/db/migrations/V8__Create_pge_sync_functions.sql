create or replace function pkg_sync.sync_lov_object(
    p_sync_id integer,
    p_id integer,
    p_name varchar,
    p_table_name varchar,
    p_where_filter varchar,
    p_svc_id integer
) returns integer
language 'plpgsql'
cost 100
volatile parallel unsafe
security definer
set search_path = rightsflow
as $BODY$
begin
  if p_id is null then
    delete from sync__lov_object where id = p_sync_id;
  else
    insert into sync__lov_object (id, name, table_name, where_filter, svc_id)
    values (p_sync_id, p_name, p_table_name, p_where_filter, p_svc_id)
    on conflict (id) do update set
    name = excluded.name,
    table_name = excluded.table_name,
    where_filter = excluded.where_filter,
    svc_id = excluded.svc_id;
  end if;
  return p_sync_id;
end;
$BODY$;

create or replace function pkg_sync.sync_lov_pge_pg_layer(
    p_sync_id integer,
    p_id integer,
    p_id_pg integer,
    p_sel_value varchar
) returns integer
language 'plpgsql'
cost 100
volatile parallel unsafe
security definer
set search_path = rightsflow
as $BODY$
begin
  if p_id is null then
    delete from sync__lov_pge_pg_layer where id = p_sync_id;
  else
    insert into sync__lov_pge_pg_layer (id, id_pg, sel_value)
    values (p_sync_id, p_id_pg, p_sel_value)
    on conflict (id) do update set
    id_pg = excluded.id_pg,
    sel_value = excluded.sel_value;
  end if;
  return p_sync_id;
end;
$BODY$;

create or replace function pkg_sync.sync_lov_pge_pg_to_obj(
    p_sync_id integer,
    p_id integer,
    p_id_obj integer,
    p_code_pg varchar
) returns integer
language 'plpgsql'
cost 100
volatile parallel unsafe
security definer
set search_path = rightsflow
as $BODY$
begin
  if p_id is null then
    delete from sync__lov_pge_pg_to_obj where id = p_sync_id;
  else
    insert into sync__lov_pge_pg_to_obj (id, id_obj, code_pg)
    values (p_sync_id, p_id_obj, p_code_pg)
    on conflict (id) do update set
    id_obj = excluded.id_obj,
    code_pg = excluded.code_pg;
  end if;
  return p_sync_id;
end;
$BODY$;

create or replace function pkg_sync.sync_lov_pge_pgl_dtl(
    p_sync_id integer,
    p_id integer,
    p_id_pgl integer,
    p_id_property integer,
    p_property_format varchar,
    p_default_value varchar,
    p_pg_order integer
) returns integer
language 'plpgsql'
cost 100
volatile parallel unsafe
security definer
set search_path = rightsflow
as $BODY$
begin
  if p_id is null then
    delete from sync__lov_pge_pgl_dtl where id = p_sync_id;
  else
    insert into sync__lov_pge_pgl_dtl (id, id_pgl, id_property, property_format, default_value, pg_order)
    values (p_sync_id, p_id_pgl, p_id_property, p_property_format, p_default_value, p_pg_order)
    on conflict (id) do update set
    id_pgl = excluded.id_pgl,
    id_property = excluded.id_property,
    property_format = excluded.property_format,
    default_value = excluded.default_value,
    pg_order = excluded.pg_order;
  end if;
  return p_sync_id;
end;
$BODY$;

create or replace function pkg_sync.sync_lov_pge_prop_type(
    p_sync_id integer,
    p_id integer,
    p_name varchar,
    p_id_obj integer,
    p_use_multi_select boolean
) returns integer
language 'plpgsql'
cost 100
volatile parallel unsafe
security definer
set search_path = rightsflow
as $BODY$
begin
  if p_id is null then
    delete from sync__lov_pge_prop_type where id = p_sync_id;
  else
    insert into sync__lov_pge_prop_type (id, name, id_obj, use_multi_select)
    values (p_sync_id, p_name, p_id_obj, p_use_multi_select)
    on conflict (id) do update set
    name = excluded.name,
    id_obj = excluded.id_obj,
    use_multi_select = excluded.use_multi_select;
  end if;
  return p_sync_id;
end;
$BODY$;

create or replace function pkg_sync.sync_lov_pge_property(
    p_sync_id integer,
    p_id integer,
    p_code varchar,
    p_name varchar,
    p_id_prop_type integer
) returns integer
language 'plpgsql'
cost 100
volatile parallel unsafe
security definer
set search_path = rightsflow
as $BODY$
begin
  if p_id is null then
    delete from sync__lov_pge_property where id = p_sync_id;
  else
    insert into sync__lov_pge_property (id, code, name, id_prop_type)
    values (p_sync_id, p_code, p_name, p_id_prop_type)
    on conflict (id) do update set
    code = excluded.code,
    name = excluded.name,
    id_prop_type = excluded.id_prop_type;
  end if;
  return p_sync_id;
end;
$BODY$;

create or replace function pkg_sync.sync_lov_pge_property_group(
    p_sync_id integer,
    p_id integer,
    p_code varchar,
    p_name varchar,
    p_layer_sel_query varchar,
    p_svc_id integer
) returns integer
language 'plpgsql'
cost 100
volatile parallel unsafe
security definer
set search_path = rightsflow
as $BODY$
begin
  if p_id is null then
    delete from sync__lov_pge_property_group where id = p_sync_id;
  else
    insert into sync__lov_pge_property_group (id, code, name, layer_sel_query, svc_id)
    values (p_sync_id, p_code, p_name, p_layer_sel_query, p_svc_id)
    on conflict (id) do update set
    code = excluded.code,
    name = excluded.name,
    layer_sel_query = excluded.layer_sel_query,
    svc_id = excluded.svc_id;
  end if;
  return p_sync_id;
end;
$BODY$;
