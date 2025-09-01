package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Статус контракта")
data class ContractStatusDto(
    @field:Schema(description = "ID", example = "1") val id: Int,
    @field:Schema(description = "ID типа контракта", example = "1") val idContractType: Int,
    @field:Schema(description = "Наименование", example = "Черновик") val name: String,
    @field:Schema(description = "По умолчанию", example = "true") val def: Boolean,
    @field:Schema(description = "Наименование типа контракта", example = "Сделка") val contractTypeName: String
)