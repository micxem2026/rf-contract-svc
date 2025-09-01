package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Запрос на обновление привязки типов прав к лицензии")
data class LicenseRtUpdateRequest(

    @field:Schema(description = "ID лицензии")
    val idLicense: Long?,

    @field:Schema(description = "ID типа прав")
    val idRightType: Int?,

    @field:Schema(description = "Дата начала действия holdback-а")
    val hbStartDate: LocalDate?,

    @field:Schema(description = "Количество дней действия holdback-а")
    val hbDays: Int?

)