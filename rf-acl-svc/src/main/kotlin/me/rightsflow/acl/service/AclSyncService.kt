package me.rightsflow.acl.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import me.rightsflow.acl.dto.ContractRequest
import me.rightsflow.acl.dto.LicenseOipRequest
import me.rightsflow.acl.dto.LicenseRequest
import me.rightsflow.acl.dto.LicenseRightsRequest
import me.rightsflow.acl.dto.LicenseRightsRtRequest
import me.rightsflow.acl.dto.LicenseRtFeatureSetRequest
import me.rightsflow.acl.dto.LicenseRtFeaturesRequest
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

    @Transactional
    fun compensateContract(
        idContract: Long
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.comp_contract(:pId)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", idContract)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

    @Transactional
    fun syncLicense(
        pod: LicenseRequest
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.sync_license(:pId, :pIdContract, :pIdLicFormat, :pGuid, :pNum, :pName, :pPrice, :pVatRate,"+
                  " :pVatAmount, :pTotalAmount, :pDescription, :pBegDate, :pEndDate, :pDropFlag)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", pod.id)
            .setParameter("pGuid", pod.guid)
            .setParameter("pNum", pod.num)
            .setParameter("pIdContract", pod.idContract)
            .setParameter("pIdLicFormat", pod.idLicFormat)
            .setParameter("pName", pod.name)
            .setParameter("pBegDate", pod.begDate)
            .setParameter("pEndDate", pod.endDate)
            .setParameter("pPrice", pod.price)
            .setParameter("pVatRate", pod.vatRate)
            .setParameter("pVatAmount", pod.vatAmount)
            .setParameter("pTotalAmount", pod.totalAmount)
            .setParameter("pDescription", pod.description)
            .setParameter("pDropFlag", pod.dropFlag)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

    @Transactional
    fun syncLicenseOip(
        pod: LicenseOipRequest
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.sync_license_oip(:pId, :pIdLicense, :pIdOip, :pIdRootOip, :pParents, :pDropFlag)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", pod.id)
            .setParameter("pIdLicense", pod.idLicense)
            .setParameter("pIdOip", pod.idOip)
            .setParameter("pIdRootOip", pod.idRootOip)
            .setParameter("pParents", pod.parents)
            .setParameter("pDropFlag", pod.dropFlag)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

    @Transactional
    fun syncLicenseRights(
        pod: LicenseRightsRequest
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.sync_license_rights(:pId, :pIdLicense, :pHbStartDate, :pHbDays, :pDropFlag)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", pod.id)
            .setParameter("pIdLicense", pod.idLicense)
            .setParameter("pHbStartDate", pod.hbStartDate)
            .setParameter("pHbDays", pod.hbDays)
            .setParameter("pDropFlag", pod.dropFlag)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

    @Transactional
    fun syncLicenseRightsRt(
        pod: LicenseRightsRtRequest
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.sync_license_rights_rt(:pId, :pIdLicRights, :pIdRightType, :pShowings, :pIdTechRepeat," +
                  " :pCatchForward, :pCatchUp, :pIdReportPeriod, :pIdReportCurrency, :pIdDistribChannel, :pIdGoodsCountry," +
                  " :pIdContQuality, :pIdLangSubtitle, :pIdLangVoiceover, :pIdLangOfUse, :pDropFlag)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", pod.id)
            .setParameter("pIdLicRights", pod.idLicRights)
            .setParameter("pIdRightType", pod.idRightType)
            .setParameter("pShowings", pod.showings)
            .setParameter("pIdTechRepeat", pod.idTechRepeat)
            .setParameter("pCatchForward", pod.catchForward)
            .setParameter("pCatchUp", pod.catchUp)
            .setParameter("pIdReportPeriod", pod.idReportPeriod)
            .setParameter("pIdReportCurrency", pod.idReportCurrency)
            .setParameter("pIdDistribChannel", pod.idDistribChannel)
            .setParameter("pIdGoodsCountry", pod.idGoodsCountry)
            .setParameter("pIdContQuality", pod.idContQuality)
            .setParameter("pIdLangSubtitle", pod.idLangSubtitle)
            .setParameter("pIdLangVoiceover", pod.idLangVoiceover)
            .setParameter("pIdLangOfUse", pod.idLangOfUse)
            .setParameter("pDropFlag", pod.dropFlag)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

    @Transactional
    fun syncLicenseRtFeatureSet(
        pod: LicenseRtFeatureSetRequest
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.sync_license_rt_feature_set(:pId, :pIdLicRights, :pIsExclusive, :pIsUseRight," +
                  " :pIsSubLicense, :pBegDate, :pEndDate, :pDropFlag)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", pod.id)
            .setParameter("pIdLicRights", pod.idLicRights)
            .setParameter("pIsExclusive", pod.isExclusive)
            .setParameter("pIsUseRight", pod.isUseRight)
            .setParameter("pIsSubLicense", pod.isSubLicense)
            .setParameter("pBegDate", pod.begDate)
            .setParameter("pEndDate", pod.endDate)
            .setParameter("pDropFlag", pod.dropFlag)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

    @Transactional
    fun syncLicenseRtFeatures(
        pod: LicenseRtFeaturesRequest
    ): Long? {

        val sql = "SELECT * FROM pkg_acl.sync_license_rt_features(:pId, :pIdLicRights, :pIdFeatureSet, :pIdFeatureCategory," +
                " :pIdFeature, :pIsIncluded, :pIsNative, :pDropFlag)"
        val result = entityManager.createNativeQuery(sql)
            .setParameter("pId", pod.id)
            .setParameter("pIdLicRights", pod.idLicRights)
            .setParameter("pIdFeatureSet", pod.idFeatureSet)
            .setParameter("pIdFeatureCategory", pod.idFeatureCategory)
            .setParameter("pIdFeature", pod.idFeature)
            .setParameter("pIsIncluded", pod.isIncluded)
            .setParameter("pIsNative", pod.isNative)
            .setParameter("pDropFlag", pod.dropFlag)
            .singleResult

        return when (result) {
            is Number -> result.toLong()
            else -> null
        }
    }

}