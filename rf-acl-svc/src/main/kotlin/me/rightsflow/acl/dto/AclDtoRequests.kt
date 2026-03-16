package me.rightsflow.acl.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class ContractRequest(

    @field:NotNull
    var id: Long,

    var guid: String?,

    @field:NotEmpty
    val num: String,

    @field:NotNull
    var idOrg: Int,

    val idOrgParty: Int?,

    val idCParty: Int?,

    @field:NotNull
    var begDate: LocalDate,

    @field:NotNull
    var endDate: LocalDate,

    var contractDate: LocalDate?,

    @field:NotNull
    var idContractType: Int,

    @field:NotNull
    var idContractStatus: Int,

    val idTargetCs: Int?,

    val idContractVp: Int?,

    @field:NotEmpty
    val inOut: String,

    val description: String?,

    @field:NotNull
    var idCurrency: Int,

    @field:NotNull
    var idCurrencyPayment: Int,

    @field:NotNull
    var dropFlag: Boolean
)

data class LicenseRequest(

    @field:NotNull
    var id: Long,

    @field:NotNull
    var idContract: Long,

    val idLicFormat: Long?,

    var guid: String?,

    @field:NotEmpty
    val num: String,

    val name: String?,

    val price: BigDecimal,
    val vatRate: BigDecimal,
    val vatAmount: BigDecimal,
    val totalAmount: BigDecimal,

    val description: String?,

    @field:NotNull
    var begDate: LocalDate,
    @field:NotNull
    var endDate: LocalDate,

    @field:NotNull
    var dropFlag: Boolean

)

data class LicenseOipRequest(

    @field:NotNull
    var id: Long,

    @field:NotNull
    var idLicense: Long,

    @field:NotNull
    var idOip: Int,

    @field:NotNull
    var idRootOip: Int,

    val parents: String?,

    @field:NotNull
    var dropFlag: Boolean
)

data class LicenseRightsRequest(

    @field:NotNull
    var id: Long,

    @field:NotNull
    var idLicense: Long,

    val hbStartDate: LocalDate?,

    val hbDays: Int?,

    @field:NotNull
    var dropFlag: Boolean
)

data class LicenseRightsRtRequest(

    @field:NotNull
    var id: Long,

    @field:NotNull
    var idLicRights: Long,

    @field:NotNull
    var idRightType: Int,

    val showings: Int? ,
    val idTechRepeat: Int?,
    val catchForward: Short?,
    val catchUp: Short?,
    val idReportPeriod: Int?,
    val idReportCurrency: Int?,
    val idDistribChannel: Int?,
    val idGoodsCountry: Int?,
    val idContQuality: Int?,
    val idLangSubtitle: Int?,
    val idLangVoiceover: Int?,
    val idLangOfUse: Int?,

    @field:NotNull
    var dropFlag: Boolean
)

data class LicenseRtFeatureSetRequest(

    @field:NotNull
    var id: Long,

    @field:NotNull
    var idLicRights: Long,

    var isExclusive: Boolean,
    var isUseRight: Boolean,
    var isSubLicense: Boolean,

    @field:NotNull
    var begDate: LocalDate,
    @field:NotNull
    var endDate: LocalDate,

    @field:NotNull
    var dropFlag: Boolean
)

data class LicenseRtFeaturesRequest(

    @field:NotNull
    var id: Long,

    @field:NotNull
    var idLicRights: Long,

    @field:NotNull
    var idFeatureSet: Long,

    @field:NotNull
    var idFeatureCategory: Int,

    @field:NotNull
    var idFeature: Int,

    @field:NotNull
    var isIncluded: Boolean,

    @field:NotNull
    var isNative: Boolean,

    @field:NotNull
    var dropFlag: Boolean
)

