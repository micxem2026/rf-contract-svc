package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Schema(description = "Фильтр отчёта по проданным правам")
data class SoldRightsReportFilterRequest(

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ID организации")
    @field:NotNull val idOrg: Int?,

    @field:Schema(description = "ID контрагента", example = "null") val idCParty: Int?,
    @field:Schema(description = "ID ОИС", example = "null") val idOip: Int?,
    @field:Schema(description = "ID типа контента (ОИС)", example = "null") val idOipType: Int?,
    @field:Schema(description = "ID способа использования", example = "null") val idRightType: Int?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Начало периода продаж")
    @field:NotNull val begDate: LocalDate?,

    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Конец периода продаж")
    @field:NotNull val endDate: LocalDate?,

    @field:Schema(description = "Территории", example = "[]") val idFeatureTerritory: List<Int>?,
    @field:Schema(description = "Каналы", example = "[]") val idFeatureChannel: List<Int>?,
    @field:Schema(description = "Сайты", example = "[]") val idFeatureSite: List<Int>?,

    @field:Schema(description = "Подготовил (username)", example = "null") val createdBy: String?,
    @field:Schema(description = "Ответственный (username)", example = "null") val managedBy: String?,

    @field:Schema(description = "Не использовать фильтр на данных отчёта", example = "false", allowableValues = ["true", "false", "null"])
    val noDataFilter: Boolean?
)