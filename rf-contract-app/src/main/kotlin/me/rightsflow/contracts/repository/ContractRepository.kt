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
/*
    @Query(
        value = """
            select
                c.id,
                c.guid,
                c.num,
                c.id_org                     as idOrg,
                c.id_org_party               as idOrgParty,
                lower(c.validity_period)     as validityPeriodStart,
                upper(c.validity_period) - interval '1 day' as validityPeriodEnd,
                c.contract_date              as contractDate,
                c.id_contract_type           as idContractType,
                c.id_contract_status         as idContractStatus,
                c.status_1c                  AS status1c,                
                c.in_out                     as inOut,
                c.description,
                c.warning,
                c.id_sibling                 as idSibling,
                c.id_parent                  as idParent,
                c.id_currency                as idCurrency,
                c.id_currency_payment        as idCurrencyPayment,
                c.id_contract_vp             as idContractVp,
                c.unf_price                  as unfContractPrice,
                c.unf_vat_rate               as unfContractVatRate,
                c.unf_vat_amount             as unfContractVatAmount,
                c.unf_total_amount           as unfContractTotalAmount,
                c.created_by                 as createdBy,
                c.created_at                 as createdAt,
                c.updated_by                 as updatedBy,
                c.updated_at                 as updatedAt,
                coalesce(l.price,        0)  as contractPrice,
                coalesce(l.vat_amount,   0)  as contractVatAmount,
                coalesce(l.total_amount, 0)  as contractTotalAmount,
                l.vat_rate                   as contractVatRate,
                coalesce(m.missing_flag, 0)  as missingFlag
            from contract c
            left join (
                select id_contract,
                       sum(price)        as price,
                       sum(vat_amount)   as vat_amount,
                       sum(total_amount) as total_amount,
                       CASE
                           WHEN MIN(vat_rate) = MAX(vat_rate) THEN MIN(vat_rate)
                           ELSE 99
                       END AS vat_rate                       
                from license
                group by id_contract
            ) l on l.id_contract = c.id
            left join (
                select id_contract, 
                       max(missing_flag) as missing_flag
                from missing_right
                group by id_contract       
            ) m on m.id_contract = c.id
            where c.id = :id
        """,
        nativeQuery = true
    )
    fun getContractById(
        @Param("id") id: Long
    ): Optional<ContractWithTotalsProjection>
*/
/*
    @Query(
        value = """
            select
                c.id,
                c.guid,
                c.num,
                c.id_org                     as idOrg,
                c.id_org_party               as idOrgParty,
                lower(c.validity_period)     as validityPeriodStart,
                upper(c.validity_period) - interval '1 day' as validityPeriodEnd,
                c.contract_date              as contractDate,
                c.id_contract_type           as idContractType,
                c.id_contract_status         as idContractStatus,
                c.status_1c                  AS status1c,
                c.in_out                     as inOut,
                c.description,
                c.warning,
                c.id_sibling                 as idSibling,
                c.id_parent                  as idParent,
                c.id_currency                as idCurrency,
                c.id_currency_payment        as idCurrencyPayment,
                c.id_contract_vp             as idContractVp,
                c.unf_price                  as unfContractPrice,
                c.unf_vat_rate               as unfContractVatRate,
                c.unf_vat_amount             as unfContractVatAmount,
                c.unf_total_amount           as unfContractTotalAmount,                
                c.created_by                 as createdBy,
                c.created_at                 as createdAt,
                c.updated_by                 as updatedBy,
                c.updated_at                 as updatedAt,
                coalesce(l.price,        0)  as contractPrice,
                coalesce(l.vat_amount,   0)  as contractVatAmount,
                coalesce(l.total_amount, 0)  as contractTotalAmount,
                l.vat_rate                   as contractVatRate,
                coalesce(m.missing_flag, 0)  as missingFlag
            from contract c
            left join (
                select id_contract,
                       sum(price)        as price,
                       sum(vat_amount)   as vat_amount,
                       sum(total_amount) as total_amount,
                       CASE
                           WHEN MIN(vat_rate) = MAX(vat_rate) THEN MIN(vat_rate)
                           ELSE 99
                       END AS vat_rate                       
                from license
                group by id_contract
            ) l on l.id_contract = c.id
            left join (
                select id_contract, 
                       max(missing_flag) as missing_flag
                from missing_right
                group by id_contract       
            ) m on m.id_contract = c.id            
            left join contract_counterparty cc on cc.id_contract = c.id
            left join sync__klf_counterparty cp on cp.id = cc.id_cpart
            where (:idType        is null or c.id_contract_type   = :idType)
              and (:idStatus      is null or c.id_contract_status = :idStatus)
              and (:idOrg         is null or c.id_org             = :idOrg)
              and (:inOut         is null or c.in_out             = :inOut)
              and (
                    :numFilter is null
                    or lower(c.num)  like lower(concat('%', :numFilter, '%'))
                    or lower(cp.name)    like lower(concat('%', :numFilter, '%'))
                    or lower(cp.code_1c) like lower(concat('%', :numFilter, '%'))
                  )
            group by
                c.id, c.guid, c.num, c.id_org, c.id_org_party,
                c.validity_period, c.contract_date,
                c.id_contract_type, c.id_contract_status, c.in_out,
                c.description, c.warning, c.id_sibling, c.id_parent,
                c.id_currency, c.id_currency_payment, c.id_contract_vp,
                c.created_by, c.created_at, c.updated_by, c.updated_at,
                l.price, l.vat_amount, l.total_amount
        """,
        countQuery = """
            select count(distinct c.id)
            from contract c
            left join contract_counterparty cc on cc.id_contract = c.id
            left join sync__klf_counterparty cp on cp.id = cc.id_cpart
            where (:idType        is null or c.id_contract_type   = :idType)
              and (:idStatus      is null or c.id_contract_status = :idStatus)
              and (:idOrg         is null or c.id_org             = :idOrg)
              and (:inOut         is null or c.in_out             = :inOut)
              and (
                    :numFilter is null
                    or lower(c.num)      like lower(concat('%', :numFilter, '%'))
                    or lower(cp.name)    like lower(concat('%', :numFilter, '%'))
                    or lower(cp.code_1c) like lower(concat('%', :numFilter, '%'))
                  )
        """,
        nativeQuery = true
    )
    fun findByFilter(
        @Param("idType")    idType:    Int?,
        @Param("idStatus")  idStatus:  Int?,
        @Param("idOrg")     idOrg:     Int?,
        @Param("numFilter") numFilter: String?,
        @Param("inOut")     inOut:     String?,   // String, не enum — нативный запрос
        pageable: Pageable
    ): Page<ContractWithTotalsProjection>
*/
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
                u1.display_name as createdByDisplayName,
                u2.display_name as updatedByDisplayName,
                coalesce(u3.display_name, '<Не назначен>') as managedByDisplayName
            FROM contract c
            LEFT JOIN sync__users u1 ON u1.username = c.created_by
            LEFT JOIN sync__users u2 ON u2.username = c.updated_by
            LEFT JOIN sync__users u3 ON u3.username = c.managed_by
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
                u1.display_name as createdByDisplayName,
                u2.display_name as updatedByDisplayName,
                coalesce(u3.display_name, '<Не назначен>') as managedByDisplayName                                  
            FROM contract c
            LEFT JOIN sync__users u1 ON u1.username = c.created_by
            LEFT JOIN sync__users u2 ON u2.username = c.updated_by
            LEFT JOIN sync__users u3 ON u3.username = c.managed_by            
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
