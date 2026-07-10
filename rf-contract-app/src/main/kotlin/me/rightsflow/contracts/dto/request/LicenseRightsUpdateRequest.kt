package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Запрос на обновление привязки типов прав к лицензии")
data class LicenseRightsUpdateRequest(

    @field:Schema(description = "ID лицензии")
    val idLicense: Long?,

    @field:Schema(description = "Список ID типов прав для добавления в лицензию", example = "[1,2]")
    var listIdRightTypes: List<Int>?,

    @field:Schema(description = "Дата начала действия holdback-а")
    val hbStartDate: LocalDate?,

    @field:Schema(description = "Количество дней действия holdback-а")
    val hbDays: Int?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Стоимость", example = "100.00")
    var price: BigDecimal?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Сумма НДС", example = "20.00")
    var vatAmount: BigDecimal?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Всего", example = "120.00")
    var totalAmount: BigDecimal?,

    @field:Schema(description = "Описание", example = "Право на бесплатное использование видео контента")
    val description: String?

)