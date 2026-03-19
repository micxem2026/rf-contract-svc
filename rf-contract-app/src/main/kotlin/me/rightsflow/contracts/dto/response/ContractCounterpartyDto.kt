package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Контрагент контракта")
data class ContractCounterpartyDto (
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID контракта", example = "1") val idContract: Long,
    @field:Schema(description = "Номер контракта", example = "С-0123/2025") val contractNum: String,
    @field:Schema(description = "ID контрагента", example = "1") val idCpart: Int,
    @field:Schema(description = "Код 1С контрагента", example = "1") val code1c: String?,
    @field:Schema(description = "Наименование контрагента", example = "АО 'Рога и Копыта'") val cpartName: String,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)

@Schema(description = "Контрагент контракта (сокращённый вариант)")
data class ContractCounterpartyShortDto (
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID контрагента", example = "1") val idCpart: Int,
    @field:Schema(description = "Код 1С контрагента", example = "1") val code1c: String?,
    @field:Schema(description = "Наименование контрагента", example = "АО 'Рога и Копыта'") val cpartName: String
)