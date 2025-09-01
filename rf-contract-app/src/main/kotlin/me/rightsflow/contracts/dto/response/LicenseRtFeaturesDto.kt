package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Характеристика набора характеристик")
data class LicenseRtFeaturesDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID привязки типа прав к лицензии", example = "1") val idLicRt: Long,
    @field:Schema(description = "Название типа прав", example = "SVOD") val rightTypeName: String?,
    @field:Schema(description = "ID набора характеристик", example = "1") val idFeatureSet: Long,
    @field:Schema(description = "ID категории характеристики", example = "1") val idFeatureCategory: Int,
    @field:Schema(description = "Название категории характеристики", example = "Территория") val featureCategoryName: String?,
    @field:Schema(description = "ID характеристики", example = "1") val idFeature: Int,
    @field:Schema(description = "Название характеристики", example = "Россия") val featureName: String?,
    @field:Schema(description = "Признак включения", example = "true") val isIncluded: Boolean,
    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)