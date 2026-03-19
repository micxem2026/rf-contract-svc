package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.common.util.realLower
import me.rightsflow.common.util.realUpper
import me.rightsflow.contracts.dto.request.ContractCreateRequest
import me.rightsflow.contracts.dto.request.ContractStatusUpdateRequest
import me.rightsflow.contracts.dto.request.ContractUpdateRequest
import me.rightsflow.contracts.dto.response.ContractChangeStatusDto
import me.rightsflow.contracts.dto.response.ContractCounterpartyShortDto
import me.rightsflow.contracts.dto.response.ContractDto
import me.rightsflow.contracts.dto.response.ContractWithTotalsProjection
import me.rightsflow.contracts.entity.Contract
import me.rightsflow.contracts.entity.ContractCounterparty
import me.rightsflow.contracts.entity.ContractStatus
import me.rightsflow.contracts.entity.ContractType
import me.rightsflow.contracts.entity.Currency
import me.rightsflow.contracts.entity.Organization
import me.rightsflow.contracts.repository.ContractCounterpartyRepository
import me.rightsflow.contracts.repository.ContractRepository
import me.rightsflow.contracts.repository.ContractStatusRepository
import me.rightsflow.contracts.repository.ContractTypeRepository
import me.rightsflow.contracts.repository.CurrencyRepository
import me.rightsflow.contracts.repository.OrganizationRepository
import me.rightsflow.contracts.toOffsetDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.ZoneId

@Service
class ContractService(
    private val repo: ContractRepository,
    private val orgRepo: OrganizationRepository,
    private val currencyRepo: CurrencyRepository,
    private val contractTypeRepo: ContractTypeRepository,
    private val contractStatusRepo: ContractStatusRepository,
    private val contractCPartyRepo: ContractCounterpartyRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    companion object {
        private val MOSCOW_ZONE = ZoneId.of("Europe/Moscow")
    }

    fun getById(id: Long): ContractDto =
        repo.getContractById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }.toContractDto()

    fun findByFilter(
        idContractType: Int?,
        idContractStatus: Int?,
        idOrg: String?,
        numFilter: String?,
        inOut: String?,
        pageable: Pageable
    ): Page<ContractDto> = run {
        //val contractKind = inOut?.let { Contract.ContractKind.valueOf(it) }
        var idOrgInt: Int? = null
        if (idOrg != null) {
            idOrgInt = repo.getIdOrg(idOrg)
        }

        repo.findByFilter(idContractType, idContractStatus, idOrgInt, numFilter, inOut, pageable).toContractDtoPage()
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
                    ":pContractDate, " +
                    ":pIdContractType, " +
                    ":pInOut, " +
                    ":pDescription, " +
                    ":pIdCurrency, " +
                    ":pIdCurrencyPayment, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pIdOrg", req.idOrg)
        query.setParameter("pIdOrgParty", req.idOrgParty)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pContractDate", req.contractDate)
        query.setParameter("pIdContractType", req.idContractType)
        query.setParameter("pInOut", req.inOut)
        query.setParameter("pDescription", req.description)
        query.setParameter("pIdCurrency", req.idCurrency)
        query.setParameter("pIdCurrencyPayment", req.idCurrencyPayment)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        em.refresh(e)
        return getById(id)
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
                    ":pContractDate, " +
                    ":pIdContractType, " +
                    ":pInOut, " +
                    ":pDescription, " +
                    ":pIdCurrency, " +
                    ":pIdCurrencyPayment, " +
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
        query.setParameter("pContractDate", req.contractDate)
        query.setParameter("pIdContractType", req.idContractType)
        query.setParameter("pInOut", req.inOut)
        query.setParameter("pDescription", req.description)
        query.setParameter("pIdCurrency", req.idCurrency)
        query.setParameter("pIdCurrencyPayment", req.idCurrencyPayment)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)
        return getById(id)

    }

    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_contract")

        sp.registerStoredProcedureParameter("p_id", java.lang.Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_use_cascade", Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_use_cascade", useCascade)

        sp.execute()
    }

    @Transactional
    fun updateStatus(id: Long, req: ContractStatusUpdateRequest): ContractChangeStatusDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_contract_status(" +
                    ":pIdContract, " +
                    ":pStatusCode, " +
                    ":pUsername" +
                    ")"
        )

        query.setParameter("pIdContract", id)
        query.setParameter("pStatusCode", req.statusCode)
        query.setParameter("pUsername", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)

        val success = e.contractStatus?.code == req.statusCode.uppercase()
        val info = if (success) "Статус договора успешно изменен" else e.warning ?: ""
        val contract = getById(id)

        return ContractChangeStatusDto(success, info, id, contract)

    }

    /**
     * Отправка события на пересчёт прав для сделки
     */
    @Transactional
    fun makeOutboxEvent(): Int {
        val sql = "SELECT pkg_contract.make_contract_outbox()"

        val result = em.createNativeQuery(sql)
            .singleResult

        return (result as Number).toInt()
    }

    @Deprecated("Use projection version")
    private fun Contract.toDto() = ContractDto(
        id = this.id!!,
        guid = this.guid,
        num = this.num,
        idOrg = this.idOrg,
        code1c = this.organization?.code_1c ?:  "",
        nameOrg = this.organization?.name ?: "",
        idOrgParty = this.idOrgParty,
        nameOrgParty = this.organizationParty?.name ?: "",
        validityPeriodStart = this.validityPeriod.realLower(),
        validityPeriodEnd = this.validityPeriod.realUpper(),
        contractDate = this.contractDate,
        idContractType = this.idContractType,
        contractTypeName = this.contractType?.name ?: "",
        idContractStatus = this.idContractStatus,
        contractStatusName = this.contractStatus?.name ?: "",
        inOut = this.inOut.name,
        description = this.description,
        warning = this.warning,
        idSibling = this.idSibling,
        guidSibling = this.siblingContract?.guid ?: "",
        numSibling = this.siblingContract?.num ?: "",
        idParent = this.idParent,
        guidParent = this.parentContract?.guid ?: "",
        numParent = this.parentContract?.num ?: "",
        idCurrency = this.idCurrency,
        currencyCode = this.currency?.isoCharCode ?: "",
        currencyName = this.currency?.name ?: "",
        idCurrencyPayment = this.idCurrencyPayment,
        currencyCodePayment = this.currencyPayment?.isoCharCode ?: "",
        currencyNamePayment = this.currencyPayment?.name ?: "",
        idContractVp = this.idContractVp,
        contractPrice        =  BigDecimal.ZERO,
        contractVatAmount    =  BigDecimal.ZERO,
        contractTotalAmount  =  BigDecimal.ZERO,
        cParties             =  emptyList(),
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )

    private fun Page<ContractWithTotalsProjection>.toContractDtoPage(): Page<ContractDto> {
        val content = this.content
        if (content.isEmpty()) return Page.empty()

        // Собираем все нужные ID одним проходом
        val orgIds      = content.flatMap { listOfNotNull(it.getIdOrg(), it.getIdOrgParty()) }.toSet()
        val currencyIds = content.flatMap { listOfNotNull(it.getIdCurrency(), it.getIdCurrencyPayment()) }.toSet()
        val typeIds     = content.map { it.getIdContractType() }.toSet()
        val statusIds   = content.map { it.getIdContractStatus() }.toSet()
        val contractIds = content.map { it.getId() }.toSet()
        val siblingIds  = content.mapNotNull { it.getIdSibling() }.toSet()
        val parentIds   = content.mapNotNull { it.getIdParent() }.toSet()

        // Батч-запросы (по одному запросу на тип справочника)
        val orgs       = orgRepo.findAllById(orgIds).associateBy { it.id }
        val currencies = currencyRepo.findAllById(currencyIds).associateBy { it.id }
        val types      = contractTypeRepo.findAllById(typeIds).associateBy { it.id }
        val statuses   = contractStatusRepo.findAllById(statusIds).associateBy { it.id }
        val siblings   = repo.findAllById(siblingIds).filter { it.id != null }.associateBy { it.id!! }
        val parents    = repo.findAllById(parentIds).filter { it.id != null }.associateBy { it.id!! }
        val parties    = contractCPartyRepo.findByIdContractIn(contractIds).groupBy { it.idContract }

        return this.map { p ->
            p.toDto(orgs, currencies, types, statuses, siblings, parents, parties)
        }
    }

    private fun ContractWithTotalsProjection.toContractDto(): ContractDto {


        // Собираем все нужные ID одним проходом
        val orgIds      = mutableListOf( this.getIdOrg(), this.getIdOrgParty()).toSet()
        val currencyIds = mutableListOf(this.getIdCurrency(), this.getIdCurrencyPayment()).toSet()
        val typeIds     = mutableListOf (this.getIdContractType()).toSet()
        val statusIds   = mutableListOf(this.getIdContractStatus()).toSet()
        val contractIds = mutableListOf(this.getId()).toSet()
        val siblingIds  = mutableListOf(this.getIdSibling()).filter { it != null }.toSet()
        val parentIds   = mutableListOf(this.getIdParent()).filter { it != null }.toSet()

        // Батч-запросы (по одному запросу на тип справочника)
        val orgs       = orgRepo.findAllById(orgIds).associateBy { it.id }
        val currencies = currencyRepo.findAllById(currencyIds).associateBy { it.id }
        val types      = contractTypeRepo.findAllById(typeIds).associateBy { it.id }
        val statuses   = contractStatusRepo.findAllById(statusIds).associateBy { it.id }
        val siblings   = repo.findAllById(siblingIds).filter { it.id != null }.associateBy { it.id!! }
        val parents    = repo.findAllById(parentIds).filter { it.id != null }.associateBy { it.id!! }
        val parties    = contractCPartyRepo.findByIdContractIn(contractIds).groupBy { it.idContract }

        return this.toDto(orgs, currencies, types, statuses, siblings, parents, parties)

    }

    private fun ContractWithTotalsProjection.toDto(
        orgs: Map<Int, Organization>,
        currencies: Map<Int, Currency>,
        types: Map<Int, ContractType>,
        statuses: Map<Int, ContractStatus>,
        siblings: Map<Long, Contract>,
        parents: Map<Long, Contract>,
        parties: Map<Long, List<ContractCounterparty>>
    ) = ContractDto(
        id                   = getId(),
        guid                 = getGuid(),
        num                  = getNum(),
        idOrg                = getIdOrg(),
        code1c               = orgs[getIdOrg()]?.code_1c,
        nameOrg              = orgs[getIdOrg()]?.name ?: "",
        idOrgParty           = getIdOrgParty(),
        nameOrgParty         = getIdOrgParty()?.let { orgs[it]?.name },
        validityPeriodStart  = getValidityPeriodStart(),
        validityPeriodEnd    = getValidityPeriodEnd(),
        contractDate         = getContractDate(),
        idContractType       = getIdContractType(),
        contractTypeName     = types[getIdContractType()]?.name ?: "",
        idContractStatus     = getIdContractStatus(),
        contractStatusName   = statuses[getIdContractStatus()]?.name ?: "",
        inOut                = getInOut(),
        description          = getDescription(),
        warning              = getWarning(),
        idSibling            = getIdSibling(),
        guidSibling          = getIdSibling()?.let { siblings[it]?.guid },
        numSibling           = getIdSibling()?.let { siblings[it]?.num },
        idParent             = getIdParent(),
        guidParent           = getIdParent()?.let { parents[it]?.guid },
        numParent            = getIdParent()?.let { parents[it]?.num },
        idCurrency           = getIdCurrency(),
        currencyCode         = getIdCurrency()?.let { currencies[it]?.isoCharCode },
        currencyName         = getIdCurrency()?.let { currencies[it]?.name },
        idCurrencyPayment    = getIdCurrencyPayment(),
        currencyCodePayment  = getIdCurrencyPayment()?.let { currencies[it]?.isoCharCode },
        currencyNamePayment  = getIdCurrencyPayment()?.let { currencies[it]?.name },
        idContractVp         = getIdContractVp(),
        contractPrice        = getContractPrice() ?: BigDecimal.ZERO,
        contractVatAmount    = getContractVatAmount() ?: BigDecimal.ZERO,
        contractTotalAmount  = getContractTotalAmount() ?: BigDecimal.ZERO,
        cParties             = parties[getId()]?.map { it.toDto() } ?: emptyList(),
        createdBy            = getCreatedBy(),
        createdAt            = getCreatedAt().toOffsetDateTime(MOSCOW_ZONE),
        updatedBy            = getUpdatedBy(),
        updatedAt            = getUpdatedAt()?.toOffsetDateTime(MOSCOW_ZONE)
    )

    private fun ContractCounterparty.toDto() = ContractCounterpartyShortDto(
        id = this.id!!,
        idCpart = this.idCpart,
        code1c = this.counterparty?.code_1c ?: "",
        cpartName = this.counterparty?.name ?: ""
    )
}