package me.rightsflow.contracts.service

import jakarta.persistence.EntityManager
import jakarta.persistence.ParameterMode
import jakarta.persistence.PersistenceContext
import me.rightsflow.common.config.SecuritySubjectProvider
import me.rightsflow.common.exception.EntityNotFoundWithClsException
import me.rightsflow.contracts.dto.request.LicenseOipRequest
import me.rightsflow.contracts.dto.response.LicenseOipDto
import me.rightsflow.contracts.dto.response.ParentInfo
import me.rightsflow.contracts.entity.License
import me.rightsflow.contracts.entity.LicenseOip
import me.rightsflow.contracts.repository.LicenseOipRepository
import me.rightsflow.contracts.repository.LicenseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.BigInteger

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

        val result = repo.findByIdLicense(id)

        // Собрать все ID_OIP
        val oipIds = result.map { it.idOip }

        // Получить родителей одним запросом для всех OIP на странице
        val parentsMap = getParentsMapForOips(oipIds)

        return result.map { oip ->
            val parents = parentsMap[oip.idOip] ?: emptyList()
            oip.toDto(parents)
        }

    }

    // Получить Map<OipId, List<ParentInfo>> для множества OIP одним запросом
    private fun getParentsMapForOips(oipIds: List<Int>): Map<Int, List<ParentInfo>> {
        if (oipIds.isEmpty()) return emptyMap()

        @Suppress("UNCHECKED_CAST")
        val results = em.createQuery(
            """
            select h.idOip, p.id, p.name
            from OipHierarchy h
            join Oip p on p.id = h.idParent
            where h.idOip in :oipIds
            order by h.idOip, h.idParent
            """
        )
            .setParameter("oipIds", oipIds)
            .resultList as List<Array<Any>>

        return results.groupBy(
            keySelector = { it[0] as Int },
            valueTransform = { ParentInfo(id = it[1] as Int, name = it[2] as String) }
        )
    }

    @Transactional
    fun create(req: LicenseOipRequest): List<LicenseOipDto> {

        val query = em.createNativeQuery(
            "SELECT pkg_contract.ins_license_oip(" +
                    ":pIdLicense, " +
                    ":pIdOipStr, " +
                    ":pCreatedBy" +
                    ")"
        )

        query.setParameter("pIdLicense", req.idLicense)
        query.setParameter("pIdOipStr", req.listIdOip.joinToString(","))
        query.setParameter("pCreatedBy", subProvider.currentSub())

        @Suppress("UNCHECKED_CAST")
        val result = query.singleResult as Array<Long>
        val ids = result.toList()
        val entities = repo.findAllById(ids)
        entities.forEach { em.refresh(it) }

        return entities.map { it.toDto() }
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

    @Transactional
    fun deleteByRoot(idLicense: Long, idRoot: Long) {
        val sp = em.createStoredProcedureQuery("pkg_contract.del_license_oip_by_root")

        sp.registerStoredProcedureParameter("p_id_license", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_id_root_oip", Long::class.java, ParameterMode.IN)
        sp.registerStoredProcedureParameter("p_username", String::class.java, ParameterMode.IN)

        sp.setParameter("p_id_license", idLicense)
        sp.setParameter("p_id_root_oip", idRoot)
        sp.setParameter("p_username", subProvider.currentSub())

        sp.execute()
    }

    private fun LicenseOip.toDto(parents: List<ParentInfo> = emptyList()) = LicenseOipDto(
        id = this.id!!,
        idLicense = this.idLicense,
        licenseNum = this.license?.num ?: "",
        idOip = this.idOip,
        oipName = this.oip?.name ?: "",
        parents = parents,
        idRootOip = this.idRootOip,
        rootOipName = this.rootOip?.name ?: "",
        createdBy = this.createdBy,
        createdAt = this.createdAt,
        updatedBy = this.updatedBy,
        updatedAt = this.updatedAt
    )
}