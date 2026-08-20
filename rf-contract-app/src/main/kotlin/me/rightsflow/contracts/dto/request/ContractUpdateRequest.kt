package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

@Schema(title = "ContractUpdateRequest",
        description = "Запрос на обновление контракта\n\n" +
                      "**NULL** поля: guid, idOrgParty, contractDate, description, idCurrency, idCurrencyPayment, unfPrice, unfVatRate, unfVatAmount, unfTotalAmount")
data class ContractUpdateRequest(

    @field:Schema(description = "GUID")
    @field:Size(max = 255) val guid: String?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Номер контракта")
    @field:Size(max = 255) val num: String?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                  description = "ID организации владельца контракта (можно передать 1C код организации)", example = "1")
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

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Период действия контракта (начало)")
    val validityPeriodStart: LocalDate?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Период действия контракта (конец)")
    val validityPeriodEnd: LocalDate?,

    @field:Schema(description = "Дата создания контракта")
    val contractDate: LocalDate?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ID типа контракта (сделка/договор)")
    val idContractType: Int?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                  description = "Вид контракта (внешняя покупка/продажа, внутренняя покупка/продажа)", example = "eS",
                  allowableValues = ["eP", "eS", "iP", "iS"])
    val inOut: String?,

    @field:Schema(description = "Описание")
    val description: String?,

    @field:Schema(description = "ID валюты контракта", example = "643")
    var idCurrency: Int?,

    @field:Schema(description = "ID валюты платежа", example = "643")
    var idCurrencyPayment: Int?,

    @field:Schema(description = "ID родительского контракта (связь [Договор -> Доп. соглашение к договору])", example = "null")
    var idParent: Long?,

    @field:Schema(description = "ID родственного контракта (связь [Сделка -> Договор])", example = "null")
    var idSibling: Long?,

    @field:Schema(description = "Стоимость для УНФ")
    var unfContractPrice: BigDecimal?,

    @field:Schema(description = "Ставка НДС для УНФ")
    var unfContractVatRate: BigDecimal?,

    @field:Schema(description = "Сумма НДС  для УНФ")
    var unfContractVatAmount: BigDecimal?,

    @field:Schema(description = "Всего для УНФ")
    var unfContractTotalAmount: BigDecimal?,

    @field:Schema(description = "ID договора в VP", example = "122")
    var idContractVp: Int?,
)