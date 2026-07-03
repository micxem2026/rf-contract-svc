package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Запрос на создание контракта")
data class ContractCreateRequest(
    @field:Schema(description = "GUID", example = "guid")
    @field:Size(max = 255)
    val guid: String?,

    @field:Schema(description = "Номер контракта", example = "123")
    @field:Size(max = 255)
    val num: String?,

    @field:Schema(description = "ID организации владельца контракта (можно передать 1C код организации)", example = "1")
    @field:NotNull
    @field:Pattern(
        regexp = "^[а-яА-Яa-zA-Z0-9_-]+$",
        message = "idOrg can only contain letters, digits, hyphens and underscores"
    )
    var idOrg: String,

    @field:Schema(description = "ID организации партнёра для внутригруппового контракта", example = "1")
    @field:Pattern(
        regexp = "^[а-яА-Яa-zA-Z0-9_-]+$",
        message = "idOrgParty can only contain letters, digits, hyphens and underscores"
    )
    val idOrgParty: String?,

    @field:Schema(description = "Период действия контракта (начало)", example = "2022-01-01")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия контракта (конец)", example = "2022-01-31")
    val validityPeriodEnd: LocalDate?,

    @field:Schema(description = "Дата создания контракта", example = "2022-01-01")
    val contractDate: LocalDate?,

    @field:Schema(description = "ID типа контракта (сделка/договор)", example = "1")
    @field:NotNull
    var idContractType: Int,

    @field:Schema(description = "Вид контракта (внешняя покупка/продажа, внутренняя покупка/продажа)", example = "eS", allowableValues = ["eP", "eS", "iP", "iS"])
    @field:NotNull
    var inOut: String,

    @field:Schema(description = "Статус контракта в 1С")
    val status1c: String?,

    @field:Schema(description = "Описание", example = "Описание")
    val description: String?,

    @field:Schema(description = "ID валюты контракта", example = "1")
    var idCurrency: Int?,

    @field:Schema(description = "ID валюты платежа", example = "1")
    var idCurrencyPayment: Int?,

    @field:Schema(description = "Стоимость для УНФ")
    var unfPrice: BigDecimal?,

    @field:Schema(description = "Ставка НДС для УНФ")
    var unfVatRate: BigDecimal?,

    @field:Schema(description = "Сумма НДС  для УНФ")
    var unfVatAmount: BigDecimal?,

    @field:Schema(description = "Всего для УНФ")
    var unfTotalAmount: BigDecimal?

)