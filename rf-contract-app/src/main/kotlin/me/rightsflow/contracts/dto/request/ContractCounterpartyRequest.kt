package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на добавление контрагента в контракт")
data class ContractCounterpartyRequest (

    @field:Schema(description = "ID контракта", example = "1")
    @field:NotNull
    val idContract: Long,

    @field:Schema(description = "ID контрагента", example = "1")
    @field:NotNull
    val idCpart: Int
)