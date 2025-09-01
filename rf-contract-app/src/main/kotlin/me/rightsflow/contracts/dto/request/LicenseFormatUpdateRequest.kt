package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "Запрос на обновление формата лицензирования")
data class LicenseFormatUpdateRequest(

    @field:Schema(description = "Название формата", example = "TV")
    @field:Size(max = 255)
    val name: String?,

    @field:Schema(description = "Описание формата", example = "Описание формата TV")
    @field:Size(max = 511)
    val description: String?
)