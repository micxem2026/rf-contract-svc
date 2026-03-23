package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "ОИС в составе лицензии")
data class LicenseOipDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID лицензии", example = "1") val idLicense: Long,
    @field:Schema(description = "Номер лицензии", example = "Л-00001/2025") val licenseNum: String?,
    @field:Schema(description = "ID ОИС", example = "1") val idOip: Int,
    @field:Schema(description = "Название ОИС") val oipName: String?,
    @field:Schema(description = "Список ID родительских ОИС") val parents: List<ParentInfo>,
    @field:Schema(description = "ID корневого ОИС") val rootOipId: Int,
    @field:Schema(description = "Название корневого ОИС") val rootOipName: String,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)

@Schema(description = "Информация о родительском ОИС")
data class ParentInfo(
    @field:Schema(description = "ID родительского ОИС", example = "5")
    val id: Int,
    @field:Schema(description = "Название родительского ОИС", example = "Пакет 1")
    val name: String,
    @field:Schema(description = "Уровень родителя", example = "1")
    var level: Int?
)

