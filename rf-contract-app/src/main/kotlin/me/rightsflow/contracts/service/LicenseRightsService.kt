package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.*
import me.rightsflow.contracts.dto.response.LicenseRightsDto
import me.rightsflow.contracts.dto.response.LicenseRightsRtDto
import me.rightsflow.contracts.entity.License
import me.rightsflow.contracts.entity.LicenseRights
import me.rightsflow.contracts.repository.LicenseRepository
import me.rightsflow.contracts.repository.LicenseRightsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LicenseRightsService(
    private val repo: LicenseRightsRepository,
    private val licenseRepo: LicenseRepository,
    private val pgeService: PgeService,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    private val log = LoggerFactory.getLogger(LicenseRightsService::class.java)

    fun getById(id: Long): LicenseRightsDto =
        repo.findByIdForUser(id, buildUsername())
            .orElseThrow { EntityNotFoundWithClsException(id, LicenseRights::class.java) }
            .toDto()

    fun findByLicense(id: Long, pageable: Pageable): Page<LicenseRightsDto> {
        licenseRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
        return repo.findByLicenseIdForUser(id, buildUsername(), pageable).map { it.toDto() }
    }

    @Transactional
    fun create(req: LicenseRightsCreateRequest): LicenseRightsDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license_rights(" +
                    ":pIdLicense, " +
                    ":pIdRightTypes, " +
                    ":phbStartDate, " +
                    ":phbDays, " +
                    ":pPrice, " +
                    ":pVatAmount, " +
                    ":pTotalAmount, " +
                    ":pDescription, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pIdLicense", req.idLicense)
        query.setParameter("pIdRightTypes", req.listIdRightTypes.joinToString(","))
        query.setParameter("phbStartDate", req.hbStartDate)
        query.setParameter("phbDays", req.hbDays)
        query.setParameter("pPrice", req.price)
        query.setParameter("pVatAmount", req.vatAmount)
        query.setParameter("pTotalAmount", req.totalAmount)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRights::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: LicenseRightsUpdateRequest): LicenseRightsDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRights::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_license_rights(" +
                    ":pId, " +
                    ":pIdLicense, " +
                    ":pIdRightTypes, " +
                    ":phbStartDate, " +
                    ":phbDays, " +
                    ":pPrice, " +
                    ":pVatAmount, " +
                    ":pTotalAmount, " +
                    ":pDescription, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pIdLicense", req.idLicense)
        query.setParameter("pIdRightTypes", req.listIdRightTypes?.joinToString(","))
        query.setParameter("phbStartDate", req.hbStartDate)
        query.setParameter("phbDays", req.hbDays)
        query.setParameter("pPrice", req.price)
        query.setParameter("pVatAmount", req.vatAmount)
        query.setParameter("pTotalAmount", req.totalAmount)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()
    }

    fun updateFinCond(id: Long, req: ShortPropertyUpdateBatchRequest): Int {
        val updates = req.updates.map {
            PropertyUpdateBatchDto(
                id,
                "PG_FIN_COND", it.property, it.value
            )
        }
        val res = pgeService.updatePropertiesBatch(PropertyUpdateBatchRequest(updates))
        log.info("Updated: {}", res)
        return res
    }

    fun updateAddCond(id: Long, req: ShortPropertyUpdateBatchRequest): Int {
        val updates = req.updates.map {
            PropertyUpdateBatchDto(
                id,
                "PG_RT_COND", it.property, it.value
            )
        }
        val res = pgeService.updatePropertiesBatch(PropertyUpdateBatchRequest(updates))
        log.info("Updated: {}", res)
        return res
    }


    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRights::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license_rights")

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

    private fun LicenseRights.toDto() = LicenseRightsDto(
        id = this.id!!,
        idLicense = this.idLicense,
        licenseNum = this.license?.num ?: "",
        licenseRightsRt = this.getLicenseRightsRt(pgeService, subProvider, repo),
        hbStartDate = this.hbStartDate,
        hbDays = this.hbDays,
        price = this.price,
        vatAmount = this.vatAmount,
        totalAmount = this.totalAmount,
        description = this.description,
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )

}

internal fun LicenseRights.getLicenseRightsRt(
    pgeService: PgeService,
    subProvider: SecuritySubjectProvider,
    repo: LicenseRightsRepository
): List<LicenseRightsRtDto> {

    val entIds = this.rights.map { it.id!! }
    val fc = pgeService.getPgData("PG_FIN_COND", entIds, subProvider.currentSub())
        .groupBy { it.idEntity }
    val ac = pgeService.getPgData("PG_RT_COND", entIds, subProvider.currentSub())
        .groupBy { it.idEntity }

    val isContractValid = repo.getContractValidStatus(this.id!!)

    return this.rights.map {
        val mri = repo.findMissingRightInfoById(it.id!!)
        LicenseRightsRtDto(
            id = it.id!!,
            idLicRights = it.idLicRights,
            idRightGroup = it.rightType?.idRightGroup,
            idRightType = it.idRightType,
            nameRightType = it.rightType?.name ?: "",
            financeConditions = fc.getOrDefault(it.id!!, emptyList()),
            additionalConditions = ac.getOrDefault(it.id!!, emptyList()),
            missingFlag = if (isContractValid)  mri.getMissingFlag() else -1,
            missingRightInfo = mri.getMissingRightInfo(),
            createdBy = it.createdBy,
            createdAt = it.createdAt
        )

    }

}