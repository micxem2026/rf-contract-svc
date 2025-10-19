package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Schema(description = "Запрос на создание привязки типов прав к лицензии")
data class LicenseRightsCreateRequest(

    @field:Schema(description = "ID лицензии", example = "1")
    @field:NotNull var idLicense: Long,

    @field:Schema(description = "Список ID типов прав для добавления в лицензию", example = "[1,2]")
    @field:NotNull var listIdRightTypes: List<Int>,

    @field:Schema(description = "Дата начала действия holdback-а", example = "2023-01-01")
    val hbStartDate: LocalDate?,

    @field:Schema(description = "Количество дней действия holdback-а", example = "30")
    val hbDays: Int?

)