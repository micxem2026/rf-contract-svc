package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на добавление ОИС в лицензию")
data class LicenseOipRequest (

    @field:Schema(description = "ID контракта", example = "1")
    @field:NotNull
    val idLicense: Long,

    @field:Schema(description = "ID ОИС", example = "1")
    @field:NotNull
    val idOip: Int
)