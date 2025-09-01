package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.common.util.realLower
import me.rightsflow.common.util.realUpper
import me.rightsflow.contracts.dto.request.LicenseCreateRequest
import me.rightsflow.contracts.dto.request.LicenseUpdateRequest
import me.rightsflow.contracts.dto.response.LicenseDto
import me.rightsflow.contracts.entity.License
import me.rightsflow.contracts.repository.LicenseRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LicenseService(
    private val repo: LicenseRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): LicenseDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }.toDto()

    fun findByFilter(
        idContract: Long,
        numFilter: String?,
        pageable: Pageable
    ): Page<LicenseDto> = repo.findByFilter(idContract, numFilter, pageable).map { it.toDto() }

    @Transactional
    fun create(req: LicenseCreateRequest): LicenseDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license(" +
                    ":pGuid, " +
                    ":pNum, " +
                    ":pIdContract, " +
                    ":pIdLicFormat, " +
                    ":pPrice, " +
                    ":pIdCurrency, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pDescription, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pIdContract", req.idContract)
        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pPrice", req.price)
        query.setParameter("pIdCurrency", req.idCurrency)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: LicenseUpdateRequest): LicenseDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_license(" +
                    ":pId, " +
                    ":pGuid, " +
                    ":pNum, " +
                    ":pIdLicFormat, " +
                    ":pPrice, " +
                    ":pIdCurrency, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pDescription, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pPrice", req.price)
        query.setParameter("pIdCurrency", req.idCurrency)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license")

        sp.registerStoredProcedureParameter("p_id", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())

        sp.execute()
    }

    private fun License.toDto() = LicenseDto(
        id = this.id!!,
        idContract = this.idContract,
        contractNum = this.contract?.num ?: "",
        idLicFormat = this.idLicFormat,
        licFormatName = this.licenseFormat?.name ?: "",
        guid = this.guid,
        num = this.num,
        price = this.price,
        idCurrency = this.idCurrency,
        currencyCode = this.currency?.isoCharCode ?: "",
        currencyName = this.currency?.name ?: "",
        validityPeriodStart = this.validityPeriod.realLower(),
        validityPeriodEnd = this.validityPeriod.realUpper(),
        description = this.description,
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}