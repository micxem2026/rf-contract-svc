package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.LicenseFormatCreateRequest
import me.rightsflow.contracts.dto.request.LicenseFormatUpdateRequest
import me.rightsflow.contracts.dto.response.LicenseFormatDto
import me.rightsflow.contracts.entity.LicenseFormat
import me.rightsflow.contracts.repository.LicenseFormatRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LicenseFormatService(
    private val repo: LicenseFormatRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {

    fun getById(id: Long): LicenseFormatDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseFormat::class.java) }.toDto()

    fun findByFilter(
        nameFilter: String?,
        pageable: Pageable
    ): Page<LicenseFormatDto> =
        repo.findByFilter(nameFilter, pageable).map { it.toDto() }

    @Transactional
    fun create(req: LicenseFormatCreateRequest): LicenseFormatDto {

        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license_format(" +
                    ":pName, " +
                    ":pDescription, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pName", req.name)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseFormat::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun update(id: Long, req: LicenseFormatUpdateRequest): LicenseFormatDto {

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseFormat::class.java) }
        val query = em.createNativeQuery(
            "SELECT pkg_contract.upd_license_format(" +
                    ":pId, " +
                    ":pName, " +
                    ":pDescription, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pId", id)
        query.setParameter("pName", req.name)
        query.setParameter("pDescription", req.description)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        query.singleResult as Long

        em.refresh(e)
        return e.toDto()

    }

    @Transactional
    fun delete(id: Long, useCascade: Boolean) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseFormat::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license_format")

        sp.registerStoredProcedureParameter("p_id", java.lang.Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_use_cascade", Boolean::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())
        sp.setParameter("p_use_cascade", useCascade)

        sp.execute()
    }

    private fun LicenseFormat.toDto() = LicenseFormatDto(
        id = this.id!!,
        name = this.name,
        description = this.description,
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}