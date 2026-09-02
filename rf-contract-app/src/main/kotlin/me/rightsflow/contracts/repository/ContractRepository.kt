package me.rightsflow.contracts.repository

import me.rightsflow.contracts.dto.response.ContractWithTotalsProjection
import me.rightsflow.contracts.entity.Contract
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ContractRepository : JpaRepository<Contract, Long> {

    /**
     * Получить контракт по ID с фильтрацией по организациям пользователя.
     *
     * @param id       ID контракта
     * @param username JWT sub пользователя, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT
                c.id,
                c.guid,
                c.num,
                c.id_org                     AS idOrg,
                c.id_org_party               AS idOrgParty,
                lower(c.validity_period)     AS validityPeriodStart,
                upper(c.validity_period) - interval '1 day' AS validityPeriodEnd,
                c.contract_date              AS contractDate,
                c.id_contract_type           AS idContractType,
                c.id_contract_status         AS idContractStatus,
                c.status_1c                  AS status1c,
                c.in_out                     AS inOut,
                c.description,
                c.warning,
                c.id_sibling                 AS idSibling,
                c.id_parent                  AS idParent,
                c.id_currency                AS idCurrency,
                c.id_currency_payment        AS idCurrencyPayment,
                c.id_contract_vp             AS idContractVp,
                c.unf_price                  as unfContractPrice,
                c.unf_vat_rate               as unfContractVatRate,
                c.unf_vat_amount             as unfContractVatAmount,
                c.unf_total_amount           as unfContractTotalAmount,                
                c.created_by                 AS createdBy,
                c.created_at                 AS createdAt,
                c.updated_by                 AS updatedBy,
                c.updated_at                 AS updatedAt,
                COALESCE(l.price,        0)  AS contractPrice,
                COALESCE(l.vat_amount,   0)  AS contractVatAmount,
                COALESCE(l.total_amount, 0)  AS contractTotalAmount,
                l.vat_rate                   as contractVatRate,
                case when c.warning is null then coalesce(m.missing_flag, 0) else -1 end as missingFlag,
                l1.numParts,
                coalesce(c.managed_by, 'unknown') as managedBy,
                coalesce(c.coordinated_by, 'unknown') as coordinatedBy,
                u1.display_name as createdByDisplayName,
                u2.display_name as updatedByDisplayName,
                coalesce(u3.display_name, '<Не назначен>') as managedByDisplayName,
                coalesce(u4.display_name, '<Не назначен>') as coordinatedByDisplayName 
            FROM contract c
            LEFT JOIN sync__users u1 ON u1.username = c.created_by
            LEFT JOIN sync__users u2 ON u2.username = c.updated_by
            LEFT JOIN sync__users u3 ON u3.username = c.managed_by
            LEFT JOIN sync__users u4 ON u4.username = c.coordinated_by             
            LEFT JOIN LATERAL (
                        SELECT
                            COALESCE(SUM(li.price), 0)        AS price,
                            COALESCE(SUM(li.vat_amount), 0)   AS vat_amount,
                            COALESCE(SUM(li.total_amount), 0) AS total_amount,
                            CASE
                                WHEN COUNT(*) = 0 THEN NULL
                                WHEN MIN(li.vat_rate) = MAX(li.vat_rate) THEN MIN(li.vat_rate)
                                ELSE 99
                            END AS vat_rate
                        FROM license li
                        WHERE li.id_contract = c.id
            ) l ON true
            LEFT JOIN LATERAL (
                        SELECT
                            COUNT(lo.id_oip) AS numParts
                        FROM license li
                        JOIN license_oip lo on lo.id_license = li.id
                        WHERE li.id_contract = c.id
            ) l1 ON true            
            LEFT JOIN LATERAL (
                    SELECT
                        MAX(mr.missing_flag) AS missing_flag
                    FROM missing_right mr
                    WHERE mr.id_contract = c.id      
            ) m on true            
            WHERE c.id = :id
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )
        """,
        nativeQuery = true
    )
    fun getContractByIdForUser(
        @Param("id")       id:       Long,
        @Param("username") username: String?   // null = bypass для ADMIN/SERVICE
    ): Optional<ContractWithTotalsProjection>

    /**
     * Получить контракты по ID_SIBLING с фильтрацией по организациям пользователя.
     *
     * @param idSibling ID_SIBLING контрактов
     * @param username JWT sub пользователя, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT
                c.id,
                c.guid,
                c.num,
                c.id_org                     AS idOrg,
                c.id_org_party               AS idOrgParty,
                lower(c.validity_period)     AS validityPeriodStart,
                upper(c.validity_period) - interval '1 day' AS validityPeriodEnd,
                c.contract_date              AS contractDate,
                c.id_contract_type           AS idContractType,
                c.id_contract_status         AS idContractStatus,
                c.status_1c                  AS status1c,
                c.in_out                     AS inOut,
                c.description,
                c.warning,
                c.id_sibling                 AS idSibling,
                c.id_parent                  AS idParent,
                c.id_currency                AS idCurrency,
                c.id_currency_payment        AS idCurrencyPayment,
                c.id_contract_vp             AS idContractVp,
                c.unf_price                  as unfContractPrice,
                c.unf_vat_rate               as unfContractVatRate,
                c.unf_vat_amount             as unfContractVatAmount,
                c.unf_total_amount           as unfContractTotalAmount,                
                c.created_by                 AS createdBy,
                c.created_at                 AS createdAt,
                c.updated_by                 AS updatedBy,
                c.updated_at                 AS updatedAt,
                COALESCE(l.price,        0)  AS contractPrice,
                COALESCE(l.vat_amount,   0)  AS contractVatAmount,
                COALESCE(l.total_amount, 0)  AS contractTotalAmount,
                l.vat_rate                   as contractVatRate,
                case when c.warning is null then coalesce(m.missing_flag, 0) else -1 end as missingFlag,
                l1.numParts,
                coalesce(c.managed_by, 'unknown') as managedBy,
                coalesce(c.coordinated_by, 'unknown') as coordinatedBy,
                u1.display_name as createdByDisplayName,
                u2.display_name as updatedByDisplayName,
                coalesce(u3.display_name, '<Не назначен>') as managedByDisplayName,
                coalesce(u4.display_name, '<Не назначен>') as coordinatedByDisplayName 
            FROM contract c
            LEFT JOIN sync__users u1 ON u1.username = c.created_by
            LEFT JOIN sync__users u2 ON u2.username = c.updated_by
            LEFT JOIN sync__users u3 ON u3.username = c.managed_by
            LEFT JOIN sync__users u4 ON u4.username = c.coordinated_by             
            LEFT JOIN LATERAL (
                        SELECT
                            COALESCE(SUM(li.price), 0)        AS price,
                            COALESCE(SUM(li.vat_amount), 0)   AS vat_amount,
                            COALESCE(SUM(li.total_amount), 0) AS total_amount,
                            CASE
                                WHEN COUNT(*) = 0 THEN NULL
                                WHEN MIN(li.vat_rate) = MAX(li.vat_rate) THEN MIN(li.vat_rate)
                                ELSE 99
                            END AS vat_rate
                        FROM license li
                        WHERE li.id_contract = c.id
            ) l ON true
            LEFT JOIN LATERAL (
                        SELECT
                            COUNT(lo.id_oip) AS numParts
                        FROM license li
                        JOIN license_oip lo on lo.id_license = li.id
                        WHERE li.id_contract = c.id
            ) l1 ON true            
            LEFT JOIN LATERAL (
                    SELECT
                        MAX(mr.missing_flag) AS missing_flag
                    FROM missing_right mr
                    WHERE mr.id_contract = c.id      
            ) m on true            
            WHERE c.id_sibling = :idSibling
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )
        """,
        countQuery = """
            SELECT COUNT(DISTINCT c.id)
            FROM contract c
            WHERE c.id_sibling = :idSibling
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )            
        """,
        nativeQuery = true
    )
    fun getContractByIdSiblingForUser(
        @Param("idSibling") idSibling: Long,
        @Param("username") username: String?, // null = bypass для ADMIN/SERVICE
        pageable: Pageable
    ): Page<ContractWithTotalsProjection>


    /**
     * Получить контракты по ID_PARENT с фильтрацией по организациям пользователя.
     *
     * @param idParent ID_PARENT контрактов
     * @param username JWT sub пользователя, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT
                c.id,
                c.guid,
                c.num,
                c.id_org                     AS idOrg,
                c.id_org_party               AS idOrgParty,
                lower(c.validity_period)     AS validityPeriodStart,
                upper(c.validity_period) - interval '1 day' AS validityPeriodEnd,
                c.contract_date              AS contractDate,
                c.id_contract_type           AS idContractType,
                c.id_contract_status         AS idContractStatus,
                c.status_1c                  AS status1c,
                c.in_out                     AS inOut,
                c.description,
                c.warning,
                c.id_sibling                 AS idSibling,
                c.id_parent                  AS idParent,
                c.id_currency                AS idCurrency,
                c.id_currency_payment        AS idCurrencyPayment,
                c.id_contract_vp             AS idContractVp,
                c.unf_price                  as unfContractPrice,
                c.unf_vat_rate               as unfContractVatRate,
                c.unf_vat_amount             as unfContractVatAmount,
                c.unf_total_amount           as unfContractTotalAmount,                
                c.created_by                 AS createdBy,
                c.created_at                 AS createdAt,
                c.updated_by                 AS updatedBy,
                c.updated_at                 AS updatedAt,
                COALESCE(l.price,        0)  AS contractPrice,
                COALESCE(l.vat_amount,   0)  AS contractVatAmount,
                COALESCE(l.total_amount, 0)  AS contractTotalAmount,
                l.vat_rate                   as contractVatRate,
                case when c.warning is null then coalesce(m.missing_flag, 0) else -1 end as missingFlag,
                l1.numParts,
                coalesce(c.managed_by, 'unknown') as managedBy,
                coalesce(c.coordinated_by, 'unknown') as coordinatedBy,
                u1.display_name as createdByDisplayName,
                u2.display_name as updatedByDisplayName,
                coalesce(u3.display_name, '<Не назначен>') as managedByDisplayName,
                coalesce(u4.display_name, '<Не назначен>') as coordinatedByDisplayName 
            FROM contract c
            LEFT JOIN sync__users u1 ON u1.username = c.created_by
            LEFT JOIN sync__users u2 ON u2.username = c.updated_by
            LEFT JOIN sync__users u3 ON u3.username = c.managed_by
            LEFT JOIN sync__users u4 ON u4.username = c.coordinated_by             
            LEFT JOIN LATERAL (
                        SELECT
                            COALESCE(SUM(li.price), 0)        AS price,
                            COALESCE(SUM(li.vat_amount), 0)   AS vat_amount,
                            COALESCE(SUM(li.total_amount), 0) AS total_amount,
                            CASE
                                WHEN COUNT(*) = 0 THEN NULL
                                WHEN MIN(li.vat_rate) = MAX(li.vat_rate) THEN MIN(li.vat_rate)
                                ELSE 99
                            END AS vat_rate
                        FROM license li
                        WHERE li.id_contract = c.id
            ) l ON true
            LEFT JOIN LATERAL (
                        SELECT
                            COUNT(lo.id_oip) AS numParts
                        FROM license li
                        JOIN license_oip lo on lo.id_license = li.id
                        WHERE li.id_contract = c.id
            ) l1 ON true            
            LEFT JOIN LATERAL (
                    SELECT
                        MAX(mr.missing_flag) AS missing_flag
                    FROM missing_right mr
                    WHERE mr.id_contract = c.id      
            ) m on true            
            WHERE c.id_parent = :idParent
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )
        """,
        countQuery = """
            SELECT COUNT(DISTINCT c.id)
            FROM contract c
            WHERE c.id_parent = :idParent
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )            
        """,
        nativeQuery = true
    )
    fun getContractByIdParentForUser(
        @Param("idParent") idParent: Long,
        @Param("username") username: String?, // null = bypass для ADMIN/SERVICE
        pageable: Pageable
    ): Page<ContractWithTotalsProjection>

    /**
     * Поиск контрактов по фильтрам с фильтрацией по организациям пользователя.
     * Сортировка и пагинация работают через стандартный Pageable.
     *
     * @param username JWT sub пользователя, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT 
                c.id,
                c.guid,
                c.num,
                c.id_org                     AS idOrg,
                c.id_org_party               AS idOrgParty,
                lower(c.validity_period)     AS validityPeriodStart,
                upper(c.validity_period) - interval '1 day' AS validityPeriodEnd,
                c.contract_date              AS contractDate,
                c.id_contract_type           AS idContractType,
                c.id_contract_status         AS idContractStatus,
                c.status_1c                  AS status1c,
                c.in_out                     AS inOut,
                c.description,
                c.warning,
                c.id_sibling                 AS idSibling,
                c.id_parent                  AS idParent,
                c.id_currency                AS idCurrency,
                c.id_currency_payment        AS idCurrencyPayment,
                c.id_contract_vp             AS idContractVp,
                c.unf_price                  as unfContractPrice,
                c.unf_vat_rate               as unfContractVatRate,
                c.unf_vat_amount             as unfContractVatAmount,
                c.unf_total_amount           as unfContractTotalAmount,                
                c.created_by                 AS createdBy,
                c.created_at                 AS createdAt,
                c.updated_by                 AS updatedBy,
                c.updated_at                 AS updatedAt,
                COALESCE(l.price,        0)  AS contractPrice,
                COALESCE(l.vat_amount,   0)  AS contractVatAmount,
                COALESCE(l.total_amount, 0)  AS contractTotalAmount,
                l.vat_rate                   as contractVatRate,
                case when c.warning is null then coalesce(m.missing_flag, 0) else -1 end as missingFlag,
                l1.numParts,
                coalesce(c.managed_by, 'unknown') as managedBy,
                coalesce(c.coordinated_by, 'unknown') as coordinatedBy,
                l1.numParts,
                u1.display_name as createdByDisplayName,
                u2.display_name as updatedByDisplayName,
                coalesce(u3.display_name, '<Не назначен>') as managedByDisplayName,
                coalesce(u4.display_name, '<Не назначен>') as coordinatedByDisplayName 
            FROM contract c
            LEFT JOIN sync__users u1 ON u1.username = c.created_by
            LEFT JOIN sync__users u2 ON u2.username = c.updated_by
            LEFT JOIN sync__users u3 ON u3.username = c.managed_by
            LEFT JOIN sync__users u4 ON u4.username = c.coordinated_by 
            LEFT JOIN LATERAL (
                        SELECT
                            COALESCE(SUM(li.price), 0)        AS price,
                            COALESCE(SUM(li.vat_amount), 0)   AS vat_amount,
                            COALESCE(SUM(li.total_amount), 0) AS total_amount,
                            CASE
                                WHEN COUNT(*) = 0 THEN NULL
                                WHEN MIN(li.vat_rate) = MAX(li.vat_rate) THEN MIN(li.vat_rate)
                                ELSE 99
                            END AS vat_rate
                        FROM license li
                        WHERE li.id_contract = c.id
            ) l ON true
            LEFT JOIN LATERAL (
                        SELECT
                            COUNT(lo.id_oip) AS numParts
                        FROM license li
                        JOIN license_oip lo on lo.id_license = li.id
                        WHERE li.id_contract = c.id
            ) l1 ON true            
            LEFT JOIN LATERAL (
                    SELECT
                        MAX(mr.missing_flag) AS missing_flag
                    FROM missing_right mr
                    WHERE mr.id_contract = c.id      
            ) m on true            
            WHERE (:idType IS NULL OR c.id_contract_type = :idType)
              AND (:managedBy IS NULL OR c.managed_by = :managedBy)
              AND (:coordinatedBy IS NULL OR c.coordinated_by = :coordinatedBy)
              AND (
                    NULLIF(:idStatus, '') IS NULL
                    OR c.id_contract_status = ANY(
                           string_to_array(NULLIF(:idStatus, ''), ',')::integer[]
                       )
              )
              AND (
                    NULLIF(:status1c, '') IS NULL
                    OR c.status_1c = ANY(
                           string_to_array(NULLIF(:status1c, ''), ',')::varchar[]
                       )
              )
              AND (:idOrg    IS NULL OR c.id_org             = :idOrg)
              AND (:inOut    IS NULL OR c.in_out             = :inOut)
              AND (
                    :numFilter IS NULL
                    OR c.num ILIKE concat('%', :numFilter, '%')
                    OR c.guid ILIKE concat('%', :numFilter, '%')
                  )
              AND (
                    :cpFilter IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM contract_counterparty cc
                        JOIN sync__klf_counterparty cp
                             ON cp.id = cc.id_cpart
                        WHERE cc.id_contract = c.id
                          AND (
                                cp.name ILIKE concat('%', :cpFilter, '%')
                             OR cp.code_1c ILIKE concat('%', :cpFilter, '%')
                          )
                    )
                  ) 
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )
        """,
        countQuery = """
            SELECT COUNT(DISTINCT c.id)
            FROM contract c
            WHERE (:idType   IS NULL OR c.id_contract_type   = :idType)
              AND (:managedBy IS NULL OR c.managed_by = :managedBy)
              AND (:coordinatedBy IS NULL OR c.coordinated_by = :coordinatedBy)
              AND (
                    NULLIF(:idStatus, '') IS NULL
                    OR c.id_contract_status = ANY(
                           string_to_array(NULLIF(:idStatus, ''), ',')::integer[]
                       )
              )
              AND (
                    NULLIF(:status1c, '') IS NULL
                    OR c.status_1c = ANY(
                           string_to_array(NULLIF(:status1c, ''), ',')::varchar[]
                       )
              )
              AND (:idOrg IS NULL OR c.id_org = :idOrg)
              AND (:inOut IS NULL OR c.in_out = :inOut)
              AND (
                    :numFilter IS NULL
                    OR c.num ILIKE concat('%', :numFilter, '%')
                    OR c.guid ILIKE concat('%', :numFilter, '%')
                  )
              AND (
                    :cpFilter IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM contract_counterparty cc
                        JOIN sync__klf_counterparty cp
                             ON cp.id = cc.id_cpart
                        WHERE cc.id_contract = c.id
                          AND (
                                cp.name ILIKE concat('%', :cpFilter, '%')
                             OR cp.code_1c ILIKE concat('%', :cpFilter, '%')
                          )
                    )
                  )                  
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )
        """,
        nativeQuery = true
    )
    fun findByFilterForUser(
        @Param("idType")    idType:    Int?,
        @Param("idStatus")  idStatus:  String?,
        @Param("status1c")  status1c:  String?,
        @Param("idOrg")     idOrg:     Int?,
        @Param("numFilter") numFilter: String?,
        @Param("cpFilter")  cpFilter:  String?,
        @Param("inOut")     inOut:     String?,
        @Param("managedBy") managedBy: String?,
        @Param("coordinatedBy") coordinatedBy: String?,
        @Param("username")  username:  String?,  // null = bypass для ADMIN/SERVICE
        pageable: Pageable
    ): Page<ContractWithTotalsProjection>

    @Query("select * from pkg_contract.get_org_id(:idOrg)", nativeQuery = true)
    fun getIdOrg(@Param("idOrg") idOrg: String): Int

    @Query(value = """
        select pkg_contract.is_contract_valid(
                   p_id_contract => :id,
                   p_username => 'system'
           );
    """,
    nativeQuery = true)
    fun getContractValidState(@Param("id") id: Long): Boolean
}
