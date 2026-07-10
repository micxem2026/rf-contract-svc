package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Запрос на обновление набора характеристик права для лицензии")
data class LicenseRtFeatureSetUpdateRequest(

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ID привязки права к лицензии")
    val idLicRights: Long?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Признак исключительности права", example = "true")
    val isExclusive: Boolean?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Признак использования права при эксклюзивной продаже", example = "false")
    val isUseRight: Boolean?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Признак сублицензирования", example = "false")
    val isSubLicense: Boolean?,

    @field:Schema(description = "Период действия набора характеристик (начало)", example = "2022-01-01")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия набора характеристик (конец)", example = "2022-01-31")
    val validityPeriodEnd: LocalDate?

)