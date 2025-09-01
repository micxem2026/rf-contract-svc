package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime

@Schema(description = "Набор характеристик для формата")
data class FormatRtFeatureSetDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID привязки формата к типу прав", example = "1") val idFmtRt: Long,
    @field:Schema(description = "Наименование типа права", example = "SVOD") val rightTypeName: String,
    @field:Schema(description = "Признак исключительности", example = "true") val isExclusive: Boolean,
    @field:Schema(description = "Признак использования права", example = "false") val isUseRight: Boolean,
    @field:Schema(description = "Период действия набора характеристик (начало)", example = "2022-01-01") val validityPeriodStart: LocalDate?,
    @field:Schema(description = "Период действия набора характеристик (конец)", example = "2022-01-31") val validityPeriodEnd: LocalDate?,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)