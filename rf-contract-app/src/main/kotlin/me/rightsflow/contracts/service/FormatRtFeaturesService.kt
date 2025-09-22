package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.FormatRtFeaturesCreateRequest
import me.rightsflow.contracts.dto.response.FormatRtFeaturesDto
import me.rightsflow.contracts.entity.FormatRtFeatureSet
import me.rightsflow.contracts.entity.FormatRtFeatures
import me.rightsflow.contracts.repository.FormatRtFeatureSetRepository
import me.rightsflow.contracts.repository.FormatRtFeaturesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FormatRtFeaturesService(
    private val repo: FormatRtFeaturesRepository,
    private val formatRtFeatureSetRepo: FormatRtFeatureSetRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): FormatRtFeaturesDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatures::class.java) }.toDto()

    fun findByFeatureSet(id: Long): List<FormatRtFeaturesDto> {
        formatRtFeatureSetRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatureSet::class.java) }
        return repo.findByIdFeatureSet(id).map { it.toDto() }
    }

    @Transactional
    fun create(req: FormatRtFeaturesCreateRequest): FormatRtFeaturesDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_format_rt_features(" +
                    ":pIdFmtRt, " +
                    ":pIdFeatureSet, " +
                    ":pIdFeature, " +
                    ":pIncluded, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pIdFmtRt", req.idFmtRt)
        query.setParameter("pIdFeatureSet", req.idFeatureSet)
        query.setParameter("pIdFeature", req.idFeature)
        query.setParameter("pIncluded", req.isIncluded)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatures::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatures::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_format_rt_features")

        sp.registerStoredProcedureParameter("p_id", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())

        sp.execute()
    }

    private fun FormatRtFeatures.toDto() = FormatRtFeaturesDto(
        id = this.id!!,
        idFmtRt = this.idFmtRt,
        rightTypeName = this.formatRt?.rightType?.name ?: "",
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