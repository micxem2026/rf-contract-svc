package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime

@Schema(description = "Контракт")
data class ContractDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "GUID", example = "013-123456789") val guid: String?,
    @field:Schema(description = "Номер контракта", example = "С-0123/2025") val num: String,
    @field:Schema(description = "ID организации", example = "1") val idOrg: Int,
    @field:Schema(description = "Наименование организации", example = "АО 'Рога и Копыта'") val nameOrg: String,
    @field:Schema(description = "ID организации второго участника ВГО-контракта", example = "1") val idOrgParty: Int?,
    @field:Schema(description = "Наименование организации второго участника ВГО-контракта", example = "АО 'Рога и Копыта'") val nameOrgParty: String?,
    @field:Schema(description = "Период действия (начало)", example = "2023-01-01") val validityPeriodStart: LocalDate?,
    @field:Schema(description = "Период действия (конец)", example = "2024-01-01") val validityPeriodEnd: LocalDate?,
    @field:Schema(description = "Дата создания контракта", example = "2023-01-01") val contractDate: LocalDate?,
    @field:Schema(description = "ID типа контракта", example = "1") val idContractType: Int,
    @field:Schema(description = "Наименование типа контракта", example = "Договор") val contractTypeName: String,
    @field:Schema(description = "ID статуса контракта", example = "1") val idContractStatus: Int,
    @field:Schema(description = "Наименование статуса контракта", example = "Подписан") val contractStatusName: String,
    @field:Schema(description = "Вид контракта (внешняя покупка/продажа, внутренняя покупка/продажа)", example = "eS") val inOut: String,
    @field:Schema(description = "Описание", example = "Договор на поставку товаров") val description: String?,
    @field:Schema(description = "Предупреждения целостности контракта", example = "Контракт не содержит лицензий!") val warning: String?,
    @field:Schema(description = "ID родственного контракта для сделки", example = "1") val idSibling: Long?,
    @field:Schema(description = "GUID родственного контракта для сделки", example = "013-123456789") val guidSibling: String?,
    @field:Schema(description = "Номер родственного контракта для сделки", example = "С-0123/2025") val numSibling: String?,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)