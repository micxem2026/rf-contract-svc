package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на создание привязки типов прав к формату")
data class FormatRtCreateRequest(

    @field:Schema(description = "ID формата", example = "1")
    @field:NotNull val idLicFormat: Long,

    @field:Schema(description = "ID типа прав", example = "1")
    @field:NotNull val idRightType: Int,

)