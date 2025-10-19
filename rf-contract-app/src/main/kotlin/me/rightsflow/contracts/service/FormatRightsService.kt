package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.FormatRightsCreateRequest
import me.rightsflow.contracts.dto.request.FormatRightsUpdateRequest
import me.rightsflow.contracts.dto.response.FormatRightsDto
import me.rightsflow.contracts.dto.response.FormatRightsRtDto
import me.rightsflow.contracts.entity.FormatRights
import me.rightsflow.contracts.entity.LicenseFormat
import me.rightsflow.contracts.repository.FormatRightsRepository
import me.rightsflow.contracts.repository.LicenseFormatRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FormatRtService(
    private val repo: FormatRightsRepository,
    private val licenseFormatRepo: LicenseFormatRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): FormatRightsDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRights::class.java) }.toDto()

    fun findByLicFormat(id: Long, pageable: Pageable): Page<FormatRightsDto> {
        licenseFormatRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseFormat::class.java) }
        return repo.findByIdLicFormat(id, pageable).map { it.toDto() }
    }

    @Transactional
    fun create(req: FormatRightsCreateRequest): FormatRightsDto {
        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_format_rights(" +
                    ":pIdLicFormat, " +
                    ":pIdRightTypes, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pIdRightTypes", req.listIdRightTypes.joinToString(","))
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRights::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: FormatRightsUpdateRequest): FormatRightsDto {
        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRights::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_format_rights(" +
                    ":pId, " +
                    ":pIdLicFormat, " +
                    ":pIdRightTypes, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pIdLicFormat", req.idLicFormat)
        query.setParameter("pIdRightTypes", req.listIdRightTypes.joinToString(","))
        query.setParameter("pCreatedBy", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, FormatRights::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_format_rights")

        sp.registerStoredProcedureParameter("p_id", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_use_cascade", Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_use_cascade", useCascade)

        sp.execute()
    }

    private fun FormatRights.toDto() = FormatRightsDto(
        id = this.id!!,
        idLicFormat = this.idLicFormat,
        licFormatName = this.licenseFormat?.name ?: "",
        formatRightsRt = this.getFormatRightsRt(),
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}

internal fun FormatRights.getFormatRightsRt(): List<FormatRightsRtDto> {
    return this.rights.map { FormatRightsRtDto(
        id = it.id!!,
        idFmtRights = it.idFmtRights,
        idRightType = it.idRightType,
        nameRightType = it.rightType?.name ?: "",
        createdBy = it.createdBy,
        createdAt = it.createdAt)
    }
}