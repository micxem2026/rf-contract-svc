package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Запрос на изменение привязки типов прав к формату")
data class FormatRtUpdateRequest(

    @field:Schema(description = "ID формата", example = "1")
    val idLicFormat: Long,

    @field:Schema(description = "ID типа прав", example = "1")
    val idRightType: Int,

)