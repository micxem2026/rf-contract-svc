package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Запрос на создание формата лицензирования")
data class LicenseFormatCreateRequest(

    @field:Schema(description = "Название формата", example = "TV")
    @field:NotBlank @field:Size(max = 255)
    val name: String,

    @field:Schema(description = "Описание формата", example = "Описание формата TV")
    @field:Size(max = 511)
    val description: String?
)