package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на создание привязки типов прав к формату")
data class FormatRightsCreateRequest(

    @field:Schema(description = "ID формата", example = "1")
    @field:NotNull var idLicFormat: Long,

    @field:Schema(description = "Список ID типов прав для добавления в формат", example = "[1,2]")
    @field:NotNull var listIdRightTypes: List<Int>

)