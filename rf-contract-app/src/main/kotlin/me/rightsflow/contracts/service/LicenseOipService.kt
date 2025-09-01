package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.LicenseOipRequest
import me.rightsflow.contracts.dto.response.LicenseOipDto
import me.rightsflow.contracts.entity.License
import me.rightsflow.contracts.entity.LicenseOip
import me.rightsflow.contracts.repository.LicenseOipRepository
import me.rightsflow.contracts.repository.LicenseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LicenseOipService(
    private val repo: LicenseOipRepository,
    private val licenseRepo: LicenseRepository,
    private val subProvider: SecuritySubjectProvider,
    @PersistenceContext private val em: EntityManager
) {
    fun getById(id: Long): LicenseOipDto =
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseOip::class.java) }.toDto()

    fun findByLicense(id: Long): List<LicenseOipDto> {
        licenseRepo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, License::class.java) }
        return repo.findByIdLicense(id).map { it.toDto() }
    }

    @Transactional
    fun create(req: LicenseOipRequest): LicenseOipDto {

        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license_oip(" +
                    ":pIdLicense, " +
                    ":pIdOip, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pIdLicense", req.idLicense)
        query.setParameter("pIdOip", req.idOip)
        query.setParameter("pCreatedBy", subProvider.currentSub())

        val id = query.singleResult as Long

        val e = repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseOip::class.java) }
        em.refresh(e)
        return e.toDto()
    }

    @Transactional
    fun delete(id: Long) {
        repo.findById(id).orElseThrow { EntityNotFoundWithClsException(id, LicenseOip::class.java) }
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license_oip")

        sp.registerStoredProcedureParameter("p_id", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)

        sp.setParameter("p_id", id)
        sp.setParameter("p_username", subProvider.currentSub())

        sp.execute()
    }

    private fun LicenseOip.toDto() = LicenseOipDto(
        id = this.id!!,
        idLicense = this.idLicense,
        licenseNum = this.license?.num ?: "",
        idOip = this.idOip,
        oipName = this.oip?.name ?: "",
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}