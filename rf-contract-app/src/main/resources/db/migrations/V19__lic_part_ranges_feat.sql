CREATE OR REPLACE FUNCTION pkg_acl.compress_part_num_ranges(p_id_license bigint)
    RETURNS text
    STABLE
    PARALLEL SAFE
    SECURITY DEFINER
    SET search_path=rightsflow
    LANGUAGE plpgsql
AS $$
DECLARE
    v_result text;
BEGIN
    WITH nums AS (
        SELECT DISTINCT o.part_num::bigint AS num
        FROM license_oip lo
                 JOIN sync__klf_oip o ON o.id = lo.id_oip
        WHERE lo.id_license = p_id_license
    ),
         grouped AS (
             SELECT
                 num,
                 num - ROW_NUMBER() OVER (ORDER BY num) AS grp
             FROM nums
         ),
         ranges AS (
             SELECT
                 MIN(num) AS start_num,
                 MAX(num) AS end_num
             FROM grouped
             GROUP BY grp
         )
    SELECT string_agg(
                   CASE
                       WHEN start_num = end_num THEN start_num::text
                       WHEN end_num = start_num + 1 THEN start_num::TEXT || ',' || end_num::TEXT
                       ELSE start_num::text || '-' || end_num::text
                       END,
                   ','
                   ORDER BY start_num
           )
    INTO v_result
    FROM ranges;

    RETURN v_result;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_klf_oip_id_part_num
    ON sync__klf_oip (id) INCLUDE (part_num);
