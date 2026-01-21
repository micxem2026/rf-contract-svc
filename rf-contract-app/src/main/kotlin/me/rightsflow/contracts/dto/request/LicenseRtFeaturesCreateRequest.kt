package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Запрос на создание характеристики набора характеристик для лицензии")
data class LicenseRtFeaturesCreateRequest(

    @field:Schema(description = "ID привязки права к лицензии", example = "1")
    @field:NotNull var idLicRights: Long,

    @field:Schema(description = "ID набора характеристик", example = "1")
    @field:NotNull var idFeatureSet: Long,

    @field:Schema(description = "ID характеристики", example = "1")
    @field:NotNull var idFeature: Int,

    @field:Schema(description = "Признак включения/исключения характеристики", example = "true")
    @field:NotNull val isIncluded: Boolean

)

@Schema(description = "Запрос на создание нескольких характеристик набора характеристик для лицензии")
data class LicenseRtFeaturesCreateBulkRequest(

    @field:Schema(description = "ID привязки права к лицензии", example = "1")
    @field:NotNull var idLicRights: Long,

    @field:Schema(description = "ID набора характеристик", example = "1")
    @field:NotNull var idFeatureSet: Long,

    @field:Schema(description = "Список ID характеристик", example = "[1,2,3,4]")
    @field:NotNull var idFeatures: List<Int>,

    @field:Schema(description = "Признак включения/исключения характеристики", example = "true")
    @field:NotNull val isIncluded: Boolean

)