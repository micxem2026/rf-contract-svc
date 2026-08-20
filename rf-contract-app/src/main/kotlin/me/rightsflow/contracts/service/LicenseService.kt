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
import me.rightsflow.contracts.dto.response.LicenseProjection
import me.rightsflow.contracts.dto.response.ParentInfo
import me.rightsflow.contracts.entity.License
import me.rightsflow.contracts.repository.LicenseRepository
import me.rightsflow.contracts.toOffsetDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId

@Service
class LicenseService(
    private val repo: LicenseRepository,
    private val subProvider: SecuritySubjectProvider,
    private val licenseOipService: LicenseOipService,
    @PersistenceContext private val em: EntityManager
) {

    companion object {
        private val MOSCOW_ZONE = ZoneId.of("Europe/Moscow")
    }

    fun getById(id: Long): LicenseDto =
        repo.findByIdForUser(id, buildUsername())
            .orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
            .toDto()

    fun findByFilter(
        idContract: Long,
        numFilter: String?,
        pageable: Pageable
    ): Page<LicenseDto> =
        repo.findByFilterForUser(idContract, numFilter, buildUsername(), pageable).map { it.toDto() }

    @Transactional
    fun create(req: LicenseCreateRequest): LicenseDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license(" +
                    ":pGuid, " +
                    ":pNum, " +
                    ":pName, " +
                    ":pIdContract, " +
                    ":pIdLicFormat, " +
                    ":pPrice, " +
                    ":pVatRate, " +
                    ":pVatAmount, " +
                    ":pTotalAmount, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pDescription, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pName", req.name)
        query.setParameter("pIdContract", req.idContract)
        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pPrice", req.price)
        query.setParameter("pVatRate", req.vatRate)
        query.setParameter("pVatAmount", req.vatAmount)
        query.setParameter("pTotalAmount", req.totalAmount)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        val id = query.singleResult as Long
        return getById(id)
    }

    @Transactional
    fun update(id: Long, req: LicenseUpdateRequest): LicenseDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_license(" +
                    ":pId, " +
                    ":pGuid, " +
                    ":pNum, " +
                    ":pName, " +
                    ":pIdLicFormat, " +
                    ":pPrice, " +
                    ":pVatRate, " +
                    ":pVatAmount, " +
                    ":pTotalAmount, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pDescription, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pGuid", req.guid)
        query.setParameter("pNum", req.num)
        query.setParameter("pName", req.name)
        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pPrice", req.price)
        query.setParameter("pVatRate", req.vatRate)
        query.setParameter("pVatAmount", req.vatAmount)
        query.setParameter("pTotalAmount", req.totalAmount)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        query.singleResult as Long
        return getById(id)
    }

    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license")

        sp.registerStoredProcedureParameter("p_id",          Long::class.java,    ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username",    String::class.java,  ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_use_cascade", Boolean::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_bypass",      Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_use_cascade", useCascade)
        sp.setParameter("p_bypass", subProvider.isBypassRole())

        sp.execute()
    }

    private fun buildUsername(): String? =
        if (subProvider.isBypassRole()) null else subProvider.currentSub()

    private fun License.toDto() = run {
        val licOip = licenseOipService.getFirstByIdLicense(this.id!!)
        val licIdOip = if (licOip != null)
                           licOip.parents.filter { it.level == 0 }
                           .getOrNull(0) ?: ParentInfo(licOip.idOip, licOip.oipName ?: "", null)
                       else ParentInfo(0, "", null)
        val partsInfo = repo.findPartsInfoById(this.id!!)
        LicenseDto(
            id = this.id!!,
            idContract = this.idContract,
            contractNum = this.contract?.num ?: "",
            idLicFormat = this.idLicFormat,
            licFormatName = this.licenseFormat?.name ?: "",
            guid = this.guid,
            num = this.num,
            name = this.name,
            price = this.price,
            vatRate = this.vatRate,
            vatAmount = this.vatAmount,
            totalAmount = this.totalAmount,
            validityPeriodStart = this.validityPeriod.realLower(),
            validityPeriodEnd = this.validityPeriod.realUpper(),
            rootOipId = if (licOip != null) licIdOip.id else null,
            rootOipName = if (licOip != null) licIdOip.name else null,
            description = this.description,
            partRanges = partsInfo.getPartRanges(),
            numParts = partsInfo.getNumParts(),
            missingFlag = partsInfo.getMissingFlag(),
            missingRightInfo =  partsInfo.getMissingRightInfo(),
            createdBy = this.createdBy,
            createdAt = this.createdAt,
            updatedBy = this.updatedBy,
            updatedAt = this.updatedAt
        )
    }

    private fun LicenseProjection.toDto(): LicenseDto {
        val licOip = licenseOipService.getFirstByIdLicense(this.getId())
        val licIdOip = if (licOip != null)
            licOip.parents.filter { it.level == 0 }
                .getOrNull(0) ?: ParentInfo(licOip.idOip, licOip.oipName ?: "", null)
        else ParentInfo(0, "", null)
        return LicenseDto(
            id = this.getId(),
            idContract = this.getIdContract(),
            contractNum = this.getContractNum() ?: "",
            idLicFormat = this.getIdLicFormat(),
            licFormatName = this.getLicFormatName() ?: "",
            guid = this.getGuid(),
            num = this.getNum(),
            name = this.getName(),
            price = this.getPrice(),
            vatRate = this.getVatRate(),
            vatAmount = this.getVatAmount(),
            totalAmount = this.getTotalAmount(),
            validityPeriodStart = this.getValidityPeriodStart(),
            validityPeriodEnd = this.getValidityPeriodEnd(),
            rootOipId = if (licOip != null) licIdOip.id else null,
            rootOipName = if (licOip != null) licIdOip.name else null,
            description = this.getDescription(),
            partRanges = this.getPartRanges(),
            numParts = this.getNumParts(),
            missingFlag = this.getMissingFlag(),
            missingRightInfo = this.getMissingRightInfo(),
            createdBy = this.getCreatedBy(),
            createdAt = this.getCreatedAt().toOffsetDateTime(MOSCOW_ZONE),
            updatedBy = this.getUpdatedBy(),
            updatedAt = this.getUpdatedAt()?.toOffsetDateTime(MOSCOW_ZONE)
        )
    }
}