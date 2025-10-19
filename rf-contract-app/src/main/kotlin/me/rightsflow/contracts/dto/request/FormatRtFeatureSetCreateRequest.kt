package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Schema(description = "Запрос на создание набора характеристик права для формата")
data class FormatRtFeatureSetCreateRequest(

    @field:Schema(description = "ID привязки права к формату", example = "1")
    @field:NotNull var idFmtRights: Long,

    @field:Schema(description = "Признак исключительности права", example = "true")
    @field:NotNull val isExclusive: Boolean,

    @field:Schema(description = "Признак использования права при эксклюзивной продаже", example = "true")
    @field:NotNull val isUseRight: Boolean,

    @field:Schema(description = "Период действия набора характеристик (начало)", example = "2022-01-01")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия набора характеристик (конец)", example = "2022-01-31")
    val validityPeriodEnd: LocalDate?

)