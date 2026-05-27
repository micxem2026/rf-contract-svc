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

    @Query(
        value = """
            select
                c.id,
                c.guid,
                c.num,
                c.id_org                     as idOrg,
                c.id_org_party               as idOrgParty,
                lower(c.validity_period)     as validityPeriodStart,
                upper(c.validity_period)     as validityPeriodEnd,
                c.contract_date              as contractDate,
                c.id_contract_type           as idContractType,
                c.id_contract_status         as idContractStatus,
                c.in_out                     as inOut,
                c.description,
                c.warning,
                c.id_sibling                 as idSibling,
                c.id_parent                  as idParent,
                c.id_currency                as idCurrency,
                c.id_currency_payment        as idCurrencyPayment,
                c.id_contract_vp             as idContractVp,
                c.created_by                 as createdBy,
                c.created_at                 as createdAt,
                c.updated_by                 as updatedBy,
                c.updated_at                 as updatedAt,
                coalesce(l.price,        0)  as contractPrice,
                coalesce(l.vat_amount,   0)  as contractVatAmount,
                coalesce(l.total_amount, 0)  as contractTotalAmount
            from contract c
            left join (
                select id_contract,
                       sum(price)        as price,
                       sum(vat_amount)   as vat_amount,
                       sum(total_amount) as total_amount
                from license
                group by id_contract
            ) l on l.id_contract = c.id
            where c.id = :id
        """,
        nativeQuery = true
    )
    fun getContractById(
        @Param("id") id: Long
    ): Optional<ContractWithTotalsProjection>

    @Query(
        value = """
            select
                c.id,
                c.guid,
                c.num,
                c.id_org                     as idOrg,
                c.id_org_party               as idOrgParty,
                lower(c.validity_period)     as validityPeriodStart,
                upper(c.validity_period)     as validityPeriodEnd,
                c.contract_date              as contractDate,
                c.id_contract_type           as idContractType,
                c.id_contract_status         as idContractStatus,
                c.in_out                     as inOut,
                c.description,
                c.warning,
                c.id_sibling                 as idSibling,
                c.id_parent                  as idParent,
                c.id_currency                as idCurrency,
                c.id_currency_payment        as idCurrencyPayment,
                c.id_contract_vp             as idContractVp,
                c.created_by                 as createdBy,
                c.created_at                 as createdAt,
                c.updated_by                 as updatedBy,
                c.updated_at                 as updatedAt,
                coalesce(l.price,        0)  as contractPrice,
                coalesce(l.vat_amount,   0)  as contractVatAmount,
                coalesce(l.total_amount, 0)  as contractTotalAmount
            from contract c
            left join (
                select id_contract,
                       sum(price)        as price,
                       sum(vat_amount)   as vat_amount,
                       sum(total_amount) as total_amount
                from license
                group by id_contract
            ) l on l.id_contract = c.id
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
                upper(c.validity_period)     AS validityPeriodEnd,
                c.contract_date              AS contractDate,
                c.id_contract_type           AS idContractType,
                c.id_contract_status         AS idContractStatus,
                c.in_out                     AS inOut,
                c.description,
                c.warning,
                c.id_sibling                 AS idSibling,
                c.id_parent                  AS idParent,
                c.id_currency                AS idCurrency,
                c.id_currency_payment        AS idCurrencyPayment,
                c.id_contract_vp             AS idContractVp,
                c.created_by                 AS createdBy,
                c.created_at                 AS createdAt,
                c.updated_by                 AS updatedBy,
                c.updated_at                 AS updatedAt,
                COALESCE(l.price,        0)  AS contractPrice,
                COALESCE(l.vat_amount,   0)  AS contractVatAmount,
                COALESCE(l.total_amount, 0)  AS contractTotalAmount
            FROM contract c
            LEFT JOIN (
                SELECT id_contract,
                       SUM(price)        AS price,
                       SUM(vat_amount)   AS vat_amount,
                       SUM(total_amount) AS total_amount
                FROM   license
                GROUP  BY id_contract
            ) l ON l.id_contract = c.id
            LEFT JOIN user_org_access uoa
                   ON :username IS NOT NULL
                  AND uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE c.id = :id
              AND (:username IS NULL OR uoa.username IS NOT NULL)
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
            SELECT DISTINCT
                c.id,
                c.guid,
                c.num,
                c.id_org                     AS idOrg,
                c.id_org_party               AS idOrgParty,
                lower(c.validity_period)     AS validityPeriodStart,
                upper(c.validity_period)     AS validityPeriodEnd,
                c.contract_date              AS contractDate,
                c.id_contract_type           AS idContractType,
                c.id_contract_status         AS idContractStatus,
                c.in_out                     AS inOut,
                c.description,
                c.warning,
                c.id_sibling                 AS idSibling,
                c.id_parent                  AS idParent,
                c.id_currency                AS idCurrency,
                c.id_currency_payment        AS idCurrencyPayment,
                c.id_contract_vp             AS idContractVp,
                c.created_by                 AS createdBy,
                c.created_at                 AS createdAt,
                c.updated_by                 AS updatedBy,
                c.updated_at                 AS updatedAt,
                COALESCE(l.price,        0)  AS contractPrice,
                COALESCE(l.vat_amount,   0)  AS contractVatAmount,
                COALESCE(l.total_amount, 0)  AS contractTotalAmount
            FROM contract c
            LEFT JOIN (
                SELECT id_contract,
                       SUM(price)        AS price,
                       SUM(vat_amount)   AS vat_amount,
                       SUM(total_amount) AS total_amount
                FROM   license
                GROUP  BY id_contract
            ) l ON l.id_contract = c.id
            LEFT JOIN contract_counterparty cc ON cc.id_contract = c.id
            LEFT JOIN sync__klf_counterparty cp ON cp.id = cc.id_cpart
            LEFT JOIN user_org_access uoa
                   ON :username IS NOT NULL
                  AND uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE (:idType   IS NULL OR c.id_contract_type   = :idType)
              AND (:idStatus IS NULL OR c.id_contract_status = ANY(string_to_array(:idStatus, ',')::integer[]))
              AND (:idOrg    IS NULL OR c.id_org             = :idOrg)
              AND (:inOut    IS NULL OR c.in_out             = :inOut)
              AND (
                    :numFilter IS NULL
                    OR lower(c.num)      LIKE lower(concat('%', :numFilter, '%'))
                    OR lower(cp.name)    LIKE lower(concat('%', :numFilter, '%'))
                    OR lower(cp.code_1c) LIKE lower(concat('%', :numFilter, '%'))
                  )
              AND (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        countQuery = """
            SELECT COUNT(DISTINCT c.id)
            FROM contract c
            LEFT JOIN contract_counterparty cc ON cc.id_contract = c.id
            LEFT JOIN sync__klf_counterparty cp ON cp.id = cc.id_cpart
            LEFT JOIN user_org_access uoa
                   ON :username IS NOT NULL 
                  AND uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE (:idType   IS NULL OR c.id_contract_type   = :idType)
              AND (:idStatus IS NULL OR c.id_contract_status = ANY(string_to_array(:idStatus, ',')::integer[]))
              AND (:idOrg    IS NULL OR c.id_org             = :idOrg)
              AND (:inOut    IS NULL OR c.in_out             = :inOut)
              AND (
                    :numFilter IS NULL
                    OR lower(c.num)      LIKE lower(concat('%', :numFilter, '%'))
                    OR lower(cp.name)    LIKE lower(concat('%', :numFilter, '%'))
                    OR lower(cp.code_1c) LIKE lower(concat('%', :numFilter, '%'))
                  )
              AND (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByFilterForUser(
        @Param("idType")    idType:    Int?,
        @Param("idStatus")  idStatus:  String?,
        @Param("idOrg")     idOrg:     Int?,
        @Param("numFilter") numFilter: String?,
        @Param("inOut")     inOut:     String?,
        @Param("username")  username:  String?,  // null = bypass для ADMIN/SERVICE
        pageable: Pageable
    ): Page<ContractWithTotalsProjection>

    @Query("select * from pkg_contract.get_org_id(:idOrg)", nativeQuery = true)
    fun getIdOrg(@Param("idOrg") idOrg: String): Int
}
