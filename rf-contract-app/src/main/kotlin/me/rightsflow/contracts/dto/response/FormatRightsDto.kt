package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Привязка прав к формату")
data class FormatRightsDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID формата", example = "1") val idLicFormat: Long,
    @field:Schema(description = "Наименование формата", example = "TV") val licFormatName: String?,
    @field:Schema(description = "Типы прав") val formatRightsRt: List<FormatRightsRtDto>,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)