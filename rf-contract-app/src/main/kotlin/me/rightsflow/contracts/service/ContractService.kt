package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.common.util.setTextArrayParam
import me.rightsflow.contracts.dto.request.ContractCreateRequest
import me.rightsflow.contracts.dto.request.ContractStatusUpdateRequest
import me.rightsflow.contracts.dto.request.ContractUpdateRequest
import me.rightsflow.contracts.dto.response.ContractChangeStatusDto
import me.rightsflow.contracts.dto.response.ContractCounterpartyShortDto
import me.rightsflow.contracts.dto.response.ContractDto
import me.rightsflow.contracts.dto.response.ContractWithTotalsProjection
import me.rightsflow.contracts.entity.*
import me.rightsflow.contracts.repository.*
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
        repo.getContractByIdForUser(id, buildUsername())
            .orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
            .toContractDto()

    fun findByFilter(
        idContractType: Int?,
        idContractStatus: List<Int>?,
        status1c: List<String>?,
        idOrg: String?,
        numFilter: String?,
        inOut: String?,
        pageable: Pageable
    ): Page<ContractDto> {
        val idOrgInt = idOrg?.let { repo.getIdOrg(it) }
        val normalizedStatus: String? = idContractStatus
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(",")
        val normalizedStatus1c: String? = status1c
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(",")
        return repo.findByFilterForUser(
            idContractType, normalizedStatus, normalizedStatus1c, idOrgInt, numFilter, inOut,
            buildUsername(),
            pageable
        ).toContractDtoPage()
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
                    ":pStatus1c, " +
                    ":pIdContractType, " +
                    ":pInOut, " +
                    ":pDescription, " +
                    ":pIdCurrency, " +
                    ":pIdCurrencyPayment, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pIdOrg", req.idOrg)
        query.setParameter("pIdOrgParty", req.idOrgParty)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pContractDate", req.contractDate)
        query.setParameter("pStatus1c", req.status1c)
        query.setParameter("pIdContractType", req.idContractType)
        query.setParameter("pInOut", req.inOut)
        query.setParameter("pDescription", req.description)
        query.setParameter("pIdCurrency", req.idCurrency)
        query.setParameter("pIdCurrencyPayment", req.idCurrencyPayment)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        val id = query.singleResult as Long
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        em.refresh(e)
        return getById(id)
    }

    @Transactional
    fun update(id: Long, req: ContractUpdateRequest): ContractDto {

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
                    ":pCreatedBy, " +
                    ":pBypass " +
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
        query.setParameter("pBypass", subProvider.isBypassRole())

        query.singleResult as Long
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        em.refresh(e)
        return getById(id)

    }

    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_contract")

        sp.registerStoredProcedureParameter("p_id",          java.lang.Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username",    String::class.java,         ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_use_cascade", Boolean::class.java,        ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_bypass",      Boolean::class.java,        ParameterMode.IN)

        sp.setParameter("p_id",          id)
        sp.setParameter("p_username",    subProvider.currentSub())
        sp.setParameter("p_use_cascade", useCascade)
        sp.setParameter("p_bypass",      subProvider.isBypassRole())

        sp.execute()
    }

    @Transactional
    fun updateStatus(id: Long, req: ContractStatusUpdateRequest): ContractDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, Contract::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_contract_status(" +
                    ":pIdContract, " +
                    ":pStatusCode, " +
                    ":pStatus1c, " +
                    ":pUsername, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pIdContract", id)
        query.setParameter("pStatusCode", req.statusCode)
        query.setParameter("pStatus1c", req.status1c)
        query.setParameter("pUsername", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        query.singleResult as Long
        em.refresh(e)

        return getById(id)

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

    /**
     * Возвращает username для передачи в org-фильтр репозитория.
     *
     * NULL — для ролей ADMIN и SERVICE (bypass: JOIN не даст ограничений,
     *        условие ":username IS NULL OR ..." всегда true).
     *
     * username — для всех остальных ролей: JOIN с user_org_access
     *            вернёт только контракты доступных организаций.
     */
    private fun buildUsername(): String? {
        val roles = subProvider.currentRoles()
        if (roles.any { it in listOf("ADMIN", "SERVICE") }) return null
        return subProvider.currentSub()
    }

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
        status1c             = getStatus1c(),
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