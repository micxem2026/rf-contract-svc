package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Привязка типа прав к лицензии")
data class LicenseRightsRtDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID привязки права к лицензии", example = "1") val idLicRights: Long,
    @field:Schema(description = "ID типа права") val idRightType: Int,
    @field:Schema(description = "Наименование типа права") val nameRightType: String,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime
)