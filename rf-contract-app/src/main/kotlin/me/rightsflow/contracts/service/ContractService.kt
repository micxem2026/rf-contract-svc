package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.common.util.realLower
import me.rightsflow.common.util.realUpper
import me.rightsflow.contracts.dto.request.ContractCreateRequest
import me.rightsflow.contracts.dto.request.ContractUpdateRequest
import me.rightsflow.contracts.dto.response.ContractDto
import me.rightsflow.contracts.entity.Contract
import me.rightsflow.contracts.repository.ContractRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContractService(
    private val repo: ContractRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {
    fun getById(id: Long): ContractDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }.toDto()

    fun findByFilter(
        idContractType: Int?,
        idContractStatus: Int?,
        idOrg: Int?,
        numFilter: String?,
        inOut: String?,
        pageable: Pageable
    ): Page<ContractDto> = run {
        val contractKind = inOut?.let { Contract.ContractKind.valueOf(it) }
        repo.findByFilter(idContractType, idContractStatus, idOrg, numFilter, contractKind, pageable).map { it.toDto() }
    }

    @Transactional
    fun create(req: ContractCreateRequest): ContractDto {

        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_contract(" +
                    ":pGuid, " +
                    ":pNum, " +
                    ":pIdOrg, " +
                    ":pIdOrgParty, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pSignDate, " +
                    ":pIdContractType, " +
                    ":pIdContractStatus, " +
                    ":pInOut, " +
                    ":pDescription, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pIdOrg", req.idOrg)
        query.setParameter("pIdOrgParty", req.idOrgParty)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pSignDate", req.signDate)
        query.setParameter("pIdContractType", req.idContractType)
        query.setParameter("pIdContractStatus", req.idContractStatus)
        query.setParameter("pInOut", req.inOut)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: ContractUpdateRequest): ContractDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_contract(" +
                    ":pId, " +
                    ":pGuid, " +
                    ":pNum, " +
                    ":pIdOrg, " +
                    ":pIdOrgParty, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pSignDate, " +
                    ":pIdContractType, " +
                    ":pIdContractStatus, " +
                    ":pInOut, " +
                    ":pDescription, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pIdOrg", req.idOrg)
        query.setParameter("pIdOrgParty", req.idOrgParty)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pSignDate", req.signDate)
        query.setParameter("pIdContractType", req.idContractType)
        query.setParameter("pIdContractStatus", req.idContractStatus)
        query.setParameter("pInOut", req.inOut)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()

    }

    @Transactional
    fun delete(id: Long) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_contract")

        sp.registerStoredProcedureParameter("p_id", java.lang.Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())

        sp.execute()
    }

    private fun Contract.toDto() = ContractDto(
        id = this.id!!,
        guid = this.guid,
        num = this.num,
        idOrg = this.idOrg,
        nameOrg = this.organization?.name ?: "",
        idOrgParty = this.idOrgParty,
        nameOrgParty = this.organizationParty?.name ?: "",
        validityPeriodStart = this.validityPeriod.realLower(),
        validityPeriodEnd = this.validityPeriod.realUpper(),
        signDate = this.signDate,
        idContractType = this.idContractType,
        contractTypeName = this.contractType?.name ?: "",
        idContractStatus = this.idContractStatus,
        contractStatusName = this.contractStatus?.name ?: "",
        inOut = this.inOut.name,
        description = this.description,
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}