package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.FormatRtCreateRequest
import me.rightsflow.contracts.dto.request.FormatRtUpdateRequest
import me.rightsflow.contracts.dto.response.FormatRtDto
import me.rightsflow.contracts.entity.FormatRt
import me.rightsflow.contracts.entity.LicenseFormat
import me.rightsflow.contracts.repository.FormatRtRepository
import me.rightsflow.contracts.repository.LicenseFormatRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FormatRtService(
    private val repo: FormatRtRepository,
    private val licenseFormatRepo: LicenseFormatRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): FormatRtDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRt::class.java) }.toDto()

    fun findByLicFormat(id: Long, pageable: Pageable): Page<FormatRtDto> {
        licenseFormatRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseFormat::class.java) }
        return repo.findByIdLicFormat(id, pageable).map { it.toDto() }
    }

    @Transactional
    fun create(req: FormatRtCreateRequest): FormatRtDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_format_rt(" +
                    ":pIdLicFormat, " +
                    ":pIdRightType, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pIdRightType", req.idRightType)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRt::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: FormatRtUpdateRequest): FormatRtDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRt::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_format_rt(" +
                    ":pId, " +
                    ":pIdLicFormat, " +
                    ":pIdRightType, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pIdRightType", req.idRightType)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRt::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_format_rt")

        sp.registerStoredProcedureParameter("p_id", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_use_cascade", Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_use_cascade", useCascade)

        sp.execute()
    }

    private fun FormatRt.toDto() = FormatRtDto(
        id = this.id!!,
        idLicFormat = this.idLicFormat,
        licFormatName = this.licenseFormat?.name ?: "",
        idRightType = this.idRightType,
        rightTypeName = this.rightType?.name ?: "",
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}