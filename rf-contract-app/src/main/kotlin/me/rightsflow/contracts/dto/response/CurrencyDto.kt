package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Валюта")
data class CurrencyDto (
    @field:Schema(description = "ID", example = "1") val id: Int,
    @field:Schema(description = "Код валюты", example = "USD") val isoCharCode: String,
    @field:Schema(description = "Название валюты", example = "Доллар США") val name: String,
    @field:Schema(description = "По умолчанию", example = "false") val def: Boolean
)