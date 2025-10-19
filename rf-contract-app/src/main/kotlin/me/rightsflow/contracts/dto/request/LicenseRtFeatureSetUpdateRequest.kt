package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Запрос на обновление набора характеристик права для лицензии")
data class LicenseRtFeatureSetUpdateRequest(

    @field:Schema(description = "ID привязки права к лицензии")
    val idLicRights: Long?,

    @field:Schema(description = "Признак исключительности права")
    val isExclusive: Boolean?,

    @field:Schema(description = "Признак использования права при эксклюзивной продаже")
    val isUseRight: Boolean?,

    @field:Schema(description = "Период действия набора характеристик (начало)", example = "2022-01-01")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия набора характеристик (конец)", example = "2022-01-31")
    val validityPeriodEnd: LocalDate?

)