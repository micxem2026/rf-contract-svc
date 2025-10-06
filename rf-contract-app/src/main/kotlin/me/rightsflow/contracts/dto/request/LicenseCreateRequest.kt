package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Запрос на создание лицензии")
data class LicenseCreateRequest(

    @field:Schema(description = "ID контракта", example = "1")
    @field:NotNull var idContract: Long,

    @field:Schema(description = "ID формата для лицензии", example = "1")
    val idLicFormat: Long?,

    @field:Schema(description = "GUID", example = "014-12345678")
    @field:Size(max = 255)
    val guid: String?,

    @field:Schema(description = "Номер лицензии", example = "LIC-001")
    @field:Size(max = 255)
    val num: String?,

    @field:Schema(description = "Стоимость", example = "100.00")
    @field:NotNull var price: BigDecimal,

    @field:Schema(description = "ID валюты", example = "1")
    @field:NotNull var idCurrency: Int,

    @field:Schema(description = "Период действия лицензии (начало)", example = "2022-01-01")
    val validityPeriodStart: LocalDate?,

    @field:Schema(description = "Период действия лицензии (конец)", example = "2022-01-31")
    val validityPeriodEnd: LocalDate?,

    @field:Schema(description = "Описание", example = "Лицензия на использование видео контента")
    @field:Size(max = 511)
    val description: String?
)