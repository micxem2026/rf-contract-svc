package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime

@Schema(description = "Привязка типов прав к лицензии")
data class LicenseRtDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID лицензии", example = "1") val idLicense: Long,
    @field:Schema(description = "Номер лицензии", example = "Л-1234/2025") val licenseNum: String?,
    @field:Schema(description = "ID типа прав", example = "1") val idRightType: Int,
    @field:Schema(description = "Название типа прав", example = "SVOD") val rightTypeName: String?,
    @field:Schema(description = "Дата начала действия holdback-а", example = "2023-01-01") val hbStartDate: LocalDate?,
    @field:Schema(description = "Количество дней действия holdback-а", example = "30") val hbDays: Int?,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)