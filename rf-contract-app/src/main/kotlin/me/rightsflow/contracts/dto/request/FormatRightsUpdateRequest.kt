package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на изменение привязки типов прав к формату")
data class FormatRightsUpdateRequest(

    @field:Schema(description = "ID формата", example = "1")
    val idLicFormat: Long,

    @field:Schema(description = "Список ID типов прав для изменения в формате", example = "[1,2]")
    @field:NotNull var listIdRightTypes: List<Int>

)