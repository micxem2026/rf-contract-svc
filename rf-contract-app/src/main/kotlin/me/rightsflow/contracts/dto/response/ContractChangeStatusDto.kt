package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Результат изменения статуса договора")
data class ContractChangeStatusDto (
    @field:Schema(description = "Результат изменения статуса договора", example = "true") val success: Boolean,
    @field:Schema(description = "Информация о результате изменения статуса договора", example = "Статус договора успешно изменен") val info: String,
    @field:Schema(description = "Идентификатор договора", example = "1") val contractId: Long,
    @field:Schema(description = "Контракт") val contract: ContractDto
)