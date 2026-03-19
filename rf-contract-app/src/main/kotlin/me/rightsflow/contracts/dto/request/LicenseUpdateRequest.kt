package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Запрос на обновление лицензии")
data class LicenseUpdateRequest(

    @field:Schema(description = "ID формата для лицензии")
    val idLicFormat: Long?,

    @field:Schema(description = "GUID")
    @field:Size(max = 255) val guid: String?,

    @field:Schema(description = "Номер лицензии")
    @field:Size(max = 255) val num: String?,

    @field:Schema(description = "Название лицензии")
    @field:Size(max = 255) val name: String?,

    @field:Schema(description = "Стоимость")
    val price: BigDecimal?,

    @field:Schema(description = "Ставка НДС", example = "20.00")
    @field:NotNull var vatRate: BigDecimal?,

    @field:Schema(description = "Сумма НДС", example = "20.00")
    @field:NotNull var vatAmount: BigDecimal?,

    @field:Schema(description = "Всего", example = "120.00")
    @field:NotNull var totalAmount: BigDecimal?,

    @field:Schema(description = "Период действия лицензии (начало)", example = "2022-01-01")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия лицензии (конец)", example = "2022-01-31")
    val validityPeriodEnd: LocalDate?,

    @field:Schema(description = "Описание")
    val description: String?
)