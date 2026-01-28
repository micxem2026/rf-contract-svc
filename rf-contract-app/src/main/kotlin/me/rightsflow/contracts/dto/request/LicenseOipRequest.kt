package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на добавление ОИС в лицензию")
data class LicenseOipRequest (

    @field:Schema(description = "ID контракта", example = "1")
    @field:NotNull
    var idLicense: Long,

    @field:Schema(description = "Список ОИС для добавления в лицензию")
    @field:NotNull
    var listIdOip: List<OipStruct>
)

data class OipStruct (

    @field:Schema(description = "Список ID родителей через запятую", example = "1,2")
    var parents: String,

    @field:Schema(description = "ID ОИС для добавления в лицензию", example = "3")
    @field:NotNull
    var idOip: Int
)