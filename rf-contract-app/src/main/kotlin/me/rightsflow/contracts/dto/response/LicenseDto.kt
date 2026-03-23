package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Лицензия")
data class LicenseDto(
    @field:Schema(description = "ID", example = "1") val id: Long,
    @field:Schema(description = "ID контракта", example = "1") val idContract: Long,
    @field:Schema(description = "Номер контракта", example = "C-00001/2025") val contractNum: String?,
    @field:Schema(description = "ID формата лицензии", example = "1") val idLicFormat: Long?,
    @field:Schema(description = "Название формата лицензии", example = "Forever young") val licFormatName: String?,
    @field:Schema(description = "GUID", example = "014-12345678") val guid: String?,
    @field:Schema(description = "Номер лицензии", example = "LIC-001") val num: String,
    @field:Schema(description = "Название лицензии", example = "Лицензия 125") val name: String?,
    @field:Schema(description = "Стоимость", example = "100.00") val price: BigDecimal,
    @field:Schema(description = "Ставка НДС", example = "20.00") val vatRate: BigDecimal,
    @field:Schema(description = "Сумма НДС", example = "20.00") val vatAmount: BigDecimal,
    @field:Schema(description = "Всего", example = "120.00") val totalAmount: BigDecimal,
    @field:Schema(description = "Период действия лицензии (начало)", example = "2023-01-01") val validityPeriodStart: LocalDate?,
    @field:Schema(description = "Период действия лицензии (конец)", example = "2024-01-01") val validityPeriodEnd: LocalDate?,
    @field:Schema(description = "Описание", example = "Тестовое описание") val description: String?,

    @field:Schema(description = "ID главного ОИС", example = "1") val rootOipId: Int,
    @field:Schema(description = "Название главного ОИС", example = "ТАЙНЫ ЕДЫ. 10 выпуск. Сыр") val rootOipName: String,

    @field:Schema(description = "Пользователь, создавший запись", example = "admin") val createdBy: String,
    @field:Schema(description = "Дата и время создания записи", example = "2022-01-01T00:00:00Z") val createdAt: OffsetDateTime,
    @field:Schema(description = "Пользователь, обновивший запись", example = "admin") val updatedBy: String?,
    @field:Schema(description = "Дата и время обновления записи", example = "2022-01-01T00:00:00Z") val updatedAt: OffsetDateTime?
)