package me.rightsflow.acl.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
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

