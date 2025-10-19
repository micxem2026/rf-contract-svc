package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "Запрос на обновление контракта")
data class ContractUpdateRequest(

    @field:Schema(description = "GUID")
    @field:Size(max = 255) val guid: String?,

    @field:Schema(description = "Номер контракта")
    @field:Size(max = 255) val num: String?,

    @field:Schema(description = "ID организации владельца контракта", example = "1")
    val idOrg: Int?,

    @field:Schema(description = "ID организации партнёра для внутригруппового контракта", example = "1")
    val idOrgParty: Int?,

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
    @field:Size(max = 511) val description: String?
)