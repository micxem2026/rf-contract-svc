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

    @field:Schema(description = "ID организации")
    val idOrg: Int?,

    @field:Schema(description = "Период действия контракта (начало)")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия контракта (конец)")
    val validityPeriodEnd: LocalDate?,

    @field:Schema(description = "Дата подписания контракта")
    val signDate: LocalDate?,

    @field:Schema(description = "ID типа контракта (сделка/договор)")
    val idContractType: Int?,

    @field:Schema(description = "ID статуса контракта (черновик/подписан)")
    val idContractStatus: Int?,

    @field:Schema(description = "Вид контракта (покупка/продажа)")
    val inOut: String?,

    @field:Schema(description = "Описание")
    @field:Size(max = 511) val description: String?
)