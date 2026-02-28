package me.rightsflow.acl.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import me.rightsflow.acl.dto.ContractRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AclSyncService(
    @PersistenceContext private val entityManager: EntityManager,
) {

    private val log = LoggerFactory.getLogger(AclSyncService::class.java)

    @Transactional
    fun syncContract(
        pod: ContractRequest
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.sync_contract(:pId, :pGuid, :pNum, :pIdOrg, :pIdOrgParty, :pIdCparty, :pBegDate, " +
                  ":pEndDate, :pContractDate, :pIdContractType, :pIdContractStatus, :pIdTargetCs, :pIdContractVp, :pInOut, " +
                  ":pDescription, :pIdCurrency, :pIdCurrencyPayment, :pDropFlag)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", pod.id)
            .setParameter("pGuid", pod.guid)
            .setParameter("pNum", pod.num)
            .setParameter("pIdOrg", pod.idOrg)
            .setParameter("pIdOrgParty", pod.idOrgParty)
            .setParameter("pIdCparty", pod.idCParty)
            .setParameter("pBegDate", pod.begDate)
            .setParameter("pEndDate", pod.endDate)
            .setParameter("pContractDate", pod.contractDate)
            .setParameter("pIdContractType", pod.idContractType)
            .setParameter("pIdContractStatus", pod.idContractStatus)
            .setParameter("pIdTargetCs", pod.idTargetCs)
            .setParameter("pIdContractVp", pod.idContractVp)
            .setParameter("pInOut", pod.inOut)
            .setParameter("pDescription", pod.description)
            .setParameter("pIdCurrency", pod.idCurrency)
            .setParameter("pIdCurrencyPayment", pod.idCurrencyPayment)
            .setParameter("pDropFlag", pod.dropFlag)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

}