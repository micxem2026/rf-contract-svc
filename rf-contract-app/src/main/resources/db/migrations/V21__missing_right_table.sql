-- Создание таблицы для отсутствующих прав
CREATE TABLE IF NOT EXISTS MISSING_RIGHT (
 ID BIGSERIAL PRIMARY KEY,
 ID_ORG INTEGER NOT NULL REFERENCES SYNC__KLF_ORGANIZATION(ID) ON DELETE CASCADE,
 ID_OIP INTEGER NOT NULL REFERENCES SYNC__KLF_OIP(ID) ON DELETE CASCADE,
 ID_CONTRACT BIGINT NOT NULL REFERENCES CONTRACT(ID) ON DELETE CASCADE,
 ID_LICENSE BIGINT NOT NULL REFERENCES LICENSE(ID) ON DELETE CASCADE,
 ID_LIC_RIGHTS BIGINT NOT NULL REFERENCES LICENSE_RIGHTS(ID) ON DELETE CASCADE,
 ID_LIC_RIGHTS_RT BIGINT NOT NULL REFERENCES LICENSE_RIGHTS_RT(ID) ON DELETE CASCADE,
 ID_RIGHT_TYPE INTEGER NOT NULL REFERENCES SYNC__KLF_RIGHT_TYPE ON DELETE CASCADE,
 FEATURES_HASH VARCHAR(255) NOT NULL,
 BEG_DATE DATE NOT NULL,
 END_DATE DATE NOT NULL,
 IS_EXCLUSIVE BOOLEAN NOT NULL,
 RIGHT_KEY VARCHAR(255) NOT NULL,
 MISSING_FLAG INTEGER NOT NULL DEFAULT -1,
 MISSING_RIGHT_INFO TEXT,
 CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UPDATED_AT TIMESTAMPTZ,
 CONSTRAINT CHK_MISSING_FLAG CHECK(MISSING_FLAG = ANY(ARRAY[1,-1]::INTEGER[]))
);

COMMENT ON TABLE MISSING_RIGHT IS 'Таблица отсутствующих прав';
COMMENT ON COLUMN MISSING_RIGHT.MISSING_FLAG IS '1 - право отсутствует, -1 - право проверяется';

CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_ORG ON MISSING_RIGHT(ID_ORG);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_OIP ON MISSING_RIGHT(ID_OIP);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_CONTRACT ON MISSING_RIGHT(ID_CONTRACT);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_LICENSE ON MISSING_RIGHT(ID_LICENSE);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_LIC_RIGHTS ON MISSING_RIGHT(ID_LIC_RIGHTS);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_LIC_RIGHTS_RT ON MISSING_RIGHT(ID_LIC_RIGHTS_RT);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_RT ON MISSING_RIGHT(ID_RIGHT_TYPE);
CREATE INDEX IF NOT EXISTS IDX_MISSING_RIGHT_KEY ON MISSING_RIGHT(RIGHT_KEY);
CREATE UNIQUE INDEX IF NOT EXISTS UNQ_MISSING_RIGHT_KEY ON MISSING_RIGHT(ID_LIC_RIGHTS, RIGHT_KEY);

CREATE OR REPLACE FUNCTION pkg_sync.sync_missing_right(
    p_id_lic_rights bigint,
    p_id_org integer,
    p_id_oip integer,
    p_id_right_type integer,
    p_feature_hash character varying,
    p_beg_date date,
    p_end_date date,
    p_is_exclusive boolean,
    p_right_key character varying,
    p_missing_info text,
    p_missing_flag integer default 1
)
    RETURNS integer
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    SECURITY DEFINER
    SET search_path = rightsflow
AS $BODY$
DECLARE
    v_id_contract bigint;
    v_id_license bigint;
    v_id_lic_rights_rt bigint;
    v_oip_name character varying;
    v_missing_info text;
BEGIN
    IF p_missing_info = 'null' THEN
        DELETE FROM missing_right WHERE id_lic_rights = p_id_lic_rights and right_key = p_right_key;
    ELSE

        select name into v_oip_name from sync__klf_oip where id = p_id_oip;
        if v_oip_name is null then
           return 0; -- невозможно сохранить -> ОИС не найден
        end if;

        v_missing_info := 'ОИС: ' || v_oip_name || chr(10) || p_missing_info;

        select l.id_contract, lr.id_license, lrr.id as id_lic_rights_rt
        into v_id_contract, v_id_license, v_id_lic_rights_rt
        from contract c
         join license l on l.id_contract = c.id
         join license_rights lr on lr.id_license = l.id
         join license_rights_rt lrr on lrr.id_lic_rights = lr.id
        where lrr.id_lic_rights = p_id_lic_rights
          and lrr.id_right_type = p_id_right_type;

        if v_id_contract is null or v_id_license is null or v_id_lic_rights_rt is null then
           return -1; -- невозможно сохранить -> данные контракта не найдены
        end if;

        INSERT INTO missing_right (id_org, id_oip, id_contract, id_license, id_lic_rights, id_lic_rights_rt, id_right_type,
                                   features_hash, beg_date, end_date, is_exclusive, right_key, missing_flag, missing_right_info
        ) VALUES (p_id_org, p_id_oip, v_id_contract, v_id_license, p_id_lic_rights,
                  v_id_lic_rights_rt, p_id_right_type, p_feature_hash, p_beg_date,
                  p_end_date, p_is_exclusive, p_right_key, p_missing_flag,
                  v_missing_info
               )
        ON CONFLICT (id_lic_rights, right_key) DO UPDATE
           SET missing_flag = EXCLUDED.missing_flag,
               updated_at = current_timestamp;

    END IF;
    RETURN 1;
END;
$BODY$;

INSERT INTO KAFKA_BINDINGS_CONTROL (BINDING_NAME, BINDING_STATE)
VALUES
    ('missingRightProcessor-in-0','PAUSE')
ON CONFLICT (BINDING_NAME) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_contract_guid_trgm
    ON contract USING gin ((guid::text) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_klf_cpart_code1c_trgm
    ON sync__klf_counterparty USING gin ((code_1c::text) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_contract_cpart_id_contract
    ON contract_counterparty (id_contract, id_cpart);

CREATE INDEX IF NOT EXISTS idx_contract_cpart_id_cpart
    ON contract_counterparty (id_cpart, id_contract);
