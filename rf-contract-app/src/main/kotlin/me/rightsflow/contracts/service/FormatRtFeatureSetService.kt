package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.common.util.realLower
import me.rightsflow.common.util.realUpper
import me.rightsflow.contracts.dto.request.FormatRtFeatureSetCreateRequest
import me.rightsflow.contracts.dto.request.FormatRtFeatureSetUpdateRequest
import me.rightsflow.contracts.dto.response.FormatRtFeatureSetDto
import me.rightsflow.contracts.entity.FormatRt
import me.rightsflow.contracts.entity.FormatRtFeatureSet
import me.rightsflow.contracts.repository.FormatRtFeatureSetRepository
import me.rightsflow.contracts.repository.FormatRtRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FormatRtFeatureSetService(
    private val repo: FormatRtFeatureSetRepository,
    private val formatRtRepo: FormatRtRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): FormatRtFeatureSetDto {
        return repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatureSet::class.java) }.toDto()
    }

    fun findByFormatRt(id: Long, pageable: Pageable): Page<FormatRtFeatureSetDto> {
        formatRtRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRt::class.java) }
        return repo.findByIdFmtRt(id, pageable).map { it.toDto() }
    }

    @Transactional
    fun create(req: FormatRtFeatureSetCreateRequest): FormatRtFeatureSetDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_format_rt_feature_set(" +
                    ":pIdFmtRt, " +
                    ":pIsExclusive, " +
                    ":pIsUseRight, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pIdFmtRt", req.idFmtRt)
        query.setParameter("pIsExclusive", req.isExclusive)
        query.setParameter("pIsUseRight", req.isUseRight)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatureSet::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: FormatRtFeatureSetUpdateRequest): FormatRtFeatureSetDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatureSet::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_format_rt_feature_set(" +
                    ":pId, " +
                    ":pIdFmtRt, " +
                    ":pIsExclusive, " +
                    ":pIsUseRight, " +
                    ":pBegDate, " +
                    ":pEndDate, " +
                    ":pUpdatedBy" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pIdFmtRt", req.idFmtRt)
        query.setParameter("pIsExclusive", req.isExclusive)
        query.setParameter("pIsUseRight", req.isUseRight)
        query.setParameter("pBegDate", req.validityPeriodStart)
        query.setParameter("pEndDate", req.validityPeriodEnd)
        query.setParameter("pUpdatedBy", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRtFeatureSet::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_format_rt_feature_set")

        sp.registerStoredProcedureParameter("p_id", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())

        sp.execute()
    }

    private fun FormatRtFeatureSet.toDto() = FormatRtFeatureSetDto(
        id = this.id!!,
        idFmtRt = this.idFmtRt,
        rightTypeName = this.formatRt?.rightType?.name ?: "",
        isExclusive = this.isExclusive,
        isUseRight = this.isUseRight,
        validityPeriodStart = this.validityPeriod.realLower(),
        validityPeriodEnd = this.validityPeriod.realUpper(),
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}