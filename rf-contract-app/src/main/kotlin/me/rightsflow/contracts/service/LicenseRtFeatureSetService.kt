package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.common.util.realLower
import me.rightsflow.common.util.realUpper
import me.rightsflow.contracts.dto.request.LicenseRtFeatureSetCreateRequest
import me.rightsflow.contracts.dto.request.LicenseRtFeatureSetUpdateRequest
import me.rightsflow.contracts.dto.response.LicenseRtFeatureSetDto
import me.rightsflow.contracts.entity.LicenseRights
import me.rightsflow.contracts.entity.LicenseRtFeatureSet
import me.rightsflow.contracts.repository.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LicenseRtFeatureSetService(
    private val repo: LicenseRtFeatureSetRepository,
    private val licenseRtRepo: LicenseRightsRepository,
    private val pgeService: PgeService,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): LicenseRtFeatureSetDto =
        repo.findByIdForUser(id, buildUsername())
            .orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatureSet::class.java) }
            .toDto()

    fun findByLicenseRt(id: Long, pageable: Pageable): Page<LicenseRtFeatureSetDto> {
        licenseRtRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRights::class.java) }
        return repo.findByIdLicRightsForUser(id, buildUsername(), pageable).map { it.toDto() }
    }

    @Transactional
    fun create(req: LicenseRtFeatureSetCreateRequest): LicenseRtFeatureSetDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license_rt_feature_set(" +
                    ":pIdLicRights, " +
                    ":pIsExclusive, " +
                    ":pIsUseRight, " +
                    ":pIsSubLicense, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pIdLicRights", req.idLicRights)
        query.setParameter("pIsExclusive", req.isExclusive)
        query.setParameter("pIsUseRight", req.isUseRight)
        query.setParameter("pIsSubLicense", req.isSubLicense)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatureSet::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: LicenseRtFeatureSetUpdateRequest): LicenseRtFeatureSetDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatureSet::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_license_rt_feature_set(" +
                    ":pId, " +
                    ":pIdLicRights, " +
                    ":pIsExclusive, " +
                    ":pIsUseRight, " +
                    ":pIsSubLicense, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pUpdatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pIdLicRights", req.idLicRights)
        query.setParameter("pIsExclusive", req.isExclusive)
        query.setParameter("pIsUseRight", req.isUseRight)
        query.setParameter("pIsSubLicense", req.isSubLicense)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pUpdatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatureSet::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license_rt_feature_set")

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

    private fun LicenseRtFeatureSet.toDto() = LicenseRtFeatureSetDto(
        id = this.id!!,
        idLicRights = this.idLicRights,
        licenseRightsRt = this.licenseRights?.getLicenseRightsRt(pgeService, subProvider) ?: emptyList(),
        isExclusive = this.isExclusive,
        isUseRight = this.isUseRight,
        isSubLicense = this.isSubLicense,
        validityPeriodStart = this.validityPeriod.realLower(),
        validityPeriodEnd = this.validityPeriod.realUpper(),
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}