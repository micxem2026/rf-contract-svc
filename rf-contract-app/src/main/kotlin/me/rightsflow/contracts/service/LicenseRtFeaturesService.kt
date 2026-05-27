package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.LicenseRtFeaturesCreateBulkRequest
import me.rightsflow.contracts.dto.request.LicenseRtFeaturesCreateRequest
import me.rightsflow.contracts.dto.response.LicenseRtFeaturesDto
import me.rightsflow.contracts.entity.LicenseRtFeatureSet
import me.rightsflow.contracts.entity.LicenseRtFeatures
import me.rightsflow.contracts.repository.LicenseRtFeatureSetRepository
import me.rightsflow.contracts.repository.LicenseRtFeaturesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.collections.joinToString

@Service
class LicenseRtFeaturesService(
    private val repo: LicenseRtFeaturesRepository,
    private val licenseRtFeatureSetRepo: LicenseRtFeatureSetRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): LicenseRtFeaturesDto =
        repo.findByIdForUser(id, buildUsername()).orElseThrow {
            EntityNotFoundWithClsException(id, LicenseRtFeatures::class.java)
        }.toDto()

    fun findByFeatureSet(id: Long): List<LicenseRtFeaturesDto> {
        licenseRtFeatureSetRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatureSet::class.java) }
        return repo.findByIdFeatureSetForUser(id, buildUsername()).map { it.toDto() }
    }

    @Transactional
    fun create(req: LicenseRtFeaturesCreateRequest): LicenseRtFeaturesDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license_rt_features(" +
                    ":pIdLicRights, " +
                    ":pIdFeatureSet, " +
                    ":pIdFeature, " +
                    ":pIncluded, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pIdLicRights", req.idLicRights)
        query.setParameter("pIdFeatureSet", req.idFeatureSet)
        query.setParameter("pIdFeature", req.idFeature)
        query.setParameter("pIncluded", req.isIncluded)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatures::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun createBulk(req: LicenseRtFeaturesCreateBulkRequest): List<LicenseRtFeaturesDto> {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license_rt_features_bulk(" +
                    ":pIdLicRights, " +
                    ":pIdFeatureSet, " +
                    ":pIdFeatures, " +
                    ":pIncluded, " +
                    ":pCreatedBy, " +
                    ":pBypass" +
                    ")"
        )

        query.setParameter("pIdLicRights", req.idLicRights)
        query.setParameter("pIdFeatureSet", req.idFeatureSet)
        query.setParameter("pIdFeatures", req.idFeatures.joinToString(","))
        query.setParameter("pIncluded", req.isIncluded)
        query.setParameter("pCreatedBy", subProvider.currentSub())
        query.setParameter("pBypass", subProvider.isBypassRole())

        val ids = query.singleResult as String

        return repo.findByIdFeatureSet(req.idFeatureSet).map { it.toDto() }
    }

    @Transactional
    fun delete(id: Long) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatures::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license_rt_features")

        sp.registerStoredProcedureParameter("p_id", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_bypass", Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_bypass", subProvider.isBypassRole())

        sp.execute()
    }

    @Transactional
    fun deleteBulk(ids: List<Long>) {

        for (id in ids) {
            repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseRtFeatures::class.java) }
        }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license_rt_features_bulk")

        sp.registerStoredProcedureParameter("p_ids", String::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_bypass", Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_ids", ids.joinToString(","))
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_bypass", subProvider.isBypassRole())

        sp.execute()
    }

    private fun buildUsername(): String? =
        if (subProvider.isBypassRole()) null else subProvider.currentSub()

    private fun LicenseRtFeatures.toDto() = LicenseRtFeaturesDto(
        id = this.id!!,
        idLicRights = this.idLicRights,
        idFeatureSet = this.idFeatureSet,
        idFeatureCategory = this.idFeatureCategory,
        featureCategoryName = this.featureCategory?.name ?: "",
        idFeature = this.idFeature,
        featureName = this.featureTree?.featurePlain?.name ?: "",
        isIncluded = this.isIncluded,
        isNative = this.isNative,
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}