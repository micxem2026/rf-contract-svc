package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
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
    val hbDays: Int?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Стоимость", example = "100.00")
    @field:NotNull var price: BigDecimal = BigDecimal.ZERO,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Сумма НДС", example = "20.00")
    @field:NotNull var vatAmount: BigDecimal = BigDecimal.ZERO,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Всего", example = "120.00")
    @field:NotNull var totalAmount: BigDecimal = BigDecimal.ZERO,

    @field:Schema(description = "Описание", example = "Право на бесплатное использование видео контента")
    val description: String?

)