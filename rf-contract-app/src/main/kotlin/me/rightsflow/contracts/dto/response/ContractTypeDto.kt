package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Тип контракта")
data class ContractTypeDto(
    @field:Schema(description = "ID", example = "1") val id: Int,
    @field:Schema(description = "Наименование", example = "Сделка") val name: String,
    @field:Schema(description = "По умолчанию", example = "true") val def: Boolean
)