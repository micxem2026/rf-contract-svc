package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на создание характеристики набора характеристик для лицензии")
data class LicenseRtFeaturesCreateRequest(

    @field:Schema(description = "ID привязки типа прав", example = "1")
    @field:NotNull val idLicRt: Long,

    @field:Schema(description = "ID набора характеристик", example = "1")
    @field:NotNull val idFeatureSet: Long,

    @field:Schema(description = "ID характеристики", example = "1")
    @field:NotNull val idFeature: Int,

    @field:Schema(description = "Признак включения", example = "true")
    @field:NotNull val isIncluded: Boolean

)