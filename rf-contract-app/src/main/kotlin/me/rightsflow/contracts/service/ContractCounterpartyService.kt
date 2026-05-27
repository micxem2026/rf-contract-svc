package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.ContractCounterpartyRequest
import me.rightsflow.contracts.dto.response.ContractCounterpartyDto
import me.rightsflow.contracts.entity.Contract
import me.rightsflow.contracts.entity.ContractCounterparty
import me.rightsflow.contracts.repository.ContractCounterpartyRepository
import me.rightsflow.contracts.repository.ContractRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContractCounterpartyService(
    private val repo: ContractCounterpartyRepository,
    private val contractRepo: ContractRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): ContractCounterpartyDto =
        repo.findByIdForUser(id, buildUsername()).orElseThrow {
            EntityNotFoundWithClsException(id, ContractCounterparty::class.java)
        }.toDto()

    fun findByContract(id: Long): List<ContractCounterpartyDto> {
        contractRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        return repo.findByIdContractForUser(id, buildUsername()).map { it.toDto() }
    }

    @Transactional
    fun create(req: ContractCounterpartyRequest): ContractCounterpartyDto {

        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_contract_counterparty(" +
                    ":pIdContract, " +
                    ":pIdCpart, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pIdContract", req.idContract)
        query.setParameter("pIdCpart", req.idCpart)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, ContractCounterparty::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, ContractCounterparty::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_contract_counterparty")

        sp.registerStoredProcedureParameter("p_id",       Long::class.java,    ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java,  ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_bypass",   Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_bypass", subProvider.isBypassRole())

        sp.execute()
    }

    private fun buildUsername(): String? =
        if (subProvider.isBypassRole()) null else subProvider.currentSub()

    private fun ContractCounterparty.toDto() = ContractCounterpartyDto(
        id = this.id!!,
        idContract = this.idContract,
        contractNum = this.contract?.num ?: "",
        idCpart = this.idCpart,
        code1c = this.counterparty?.code_1c ?: "",
        cpartName = this.counterparty?.name ?: "",
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}