package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "Запрос на обновление контракта")
data class ContractUpdateRequest(

    @field:Schema(description = "GUID")
    @field:Size(max = 255) val guid: String?,

    @field:Schema(description = "Номер контракта")
    @field:Size(max = 255) val num: String?,

    @field:Schema(description = "ID организации владельца контракта (можно передать 1C код организации)", example = "1")
    @field:Pattern(
        regexp = "^[а-яА-Яa-zA-Z0-9_-]+$",
        message = "idOrg can only contain letters, digits, hyphens and underscores"
    )
    val idOrg: String?,

    @field:Schema(description = "ID организации партнёра для внутригруппового контракта", example = "1")
    @field:Pattern(
        regexp = "^[а-яА-Яa-zA-Z0-9_-]+$",
        message = "idOrgParty can only contain letters, digits, hyphens and underscores"
    )
    val idOrgParty: String?,

    @field:Schema(description = "Период действия контракта (начало)")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия контракта (конец)")
    val validityPeriodEnd: LocalDate?,

    @field:Schema(description = "Дата создания контракта")
    val contractDate: LocalDate?,

    @field:Schema(description = "ID типа контракта (сделка/договор)")
    val idContractType: Int?,

    @field:Schema(description = "Вид контракта (внешняя покупка/продажа, внутренняя покупка/продажа)", example = "eS", allowableValues = ["eP", "eS", "iP", "iS"])
    val inOut: String?,

    @field:Schema(description = "Описание")
    val description: String?,

    @field:Schema(description = "ID валюты контракта", example = "1")
    var idCurrency: Int?,

    @field:Schema(description = "ID валюты платежа", example = "1")
    var idCurrencyPayment: Int?,
)