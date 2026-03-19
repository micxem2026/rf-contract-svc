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

    @Query("select * from pkg_contract.get_org_id(:idOrg)", nativeQuery = true)
    fun getIdOrg(@Param("idOrg") idOrg: String): Int
}
