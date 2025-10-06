package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "Запрос на создание контракта")
data class ContractCreateRequest(
    @field:Schema(description = "GUID", example = "guid")
    @field:Size(max = 255)
    val guid: String?,

    @field:Schema(description = "Номер контракта", example = "123")
    @field:Size(max = 255)
    val num: String?,

    @field:Schema(description = "ID организации владельца контракта", example = "1")
    @field:NotNull
    var idOrg: Int,

    @field:Schema(description = "ID организации партнёра для внутригруппового контракта", example = "1")
    val idOrgParty: Int?,

    @field:Schema(description = "Период действия контракта (начало)", example = "2022-01-01")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия контракта (конец)", example = "2022-01-31")
    val validityPeriodEnd: LocalDate?,

    @field:Schema(description = "Дата подписания контракта", example = "2022-01-01")
    val signDate: LocalDate?,

    @field:Schema(description = "ID типа контракта (сделка/договор)", example = "1")
    @field:NotNull
    var idContractType: Int,

    @field:Schema(description = "Вид контракта (внешняя покупка/продажа, внутренняя покупка/продажа)", example = "eS", allowableValues = ["eP", "eS", "iP", "iS"])
    @field:NotNull
    var inOut: String,

    @field:Schema(description = "Описание", example = "Описание")
    @field:Size(max = 511)
    val description: String?

)