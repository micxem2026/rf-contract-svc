package me.rightsflow.contracts.dto.response

import io.swagger.v3.oas.annotations.media.Schema

/**
 * DTO для результата pkg_pge.get_property_groups
 */
data class PropertyGroupDto(
    @field:Schema(description = "ID группы свойств", example = "1")  val id: Int,
    @field:Schema(description = "Код группы свойств", example = "PG_RT_COMMON")  val code: String,
    @field:Schema(description = "Название группы свойств", example = "Общие свойства способов использования")  val name: String
)

/**
 * DTO для результатов get_pg_data, get_property, update_property
 */
data class PropertyDataDto(
    @field:Schema(description = "ID сущности, которой соответствует свойство", example = "1") val idEntity: Long,
    //@field:Schema(description = "ID свойства в слое группы свойств", example = "1")  val id: Int,
    //@field:Schema(description = "ID слоя группы свойств", example = "1")  val idPgl: Int,
    @field:Schema(description = "ID свойства", example = "1")  val idProperty: Int,
    @field:Schema(description = "Порядок появления свойства в составе группы свойств", example = "1")  val pgOrder: Int,
    @field:Schema(description = "Название свойства", example = "Язык субтитров")  val nameProp: String,
    @field:Schema(description = "Код свойства", example = "subtitleLang")  val codeProp: String,
    @field:Schema(description = "ID типа свойства", example = "1")  val idPropType: Int,
    @field:Schema(description = "Название типа свойства", example = "Справочник языков")  val namePropType: String,
    @field:Schema(description = "Хранимое значение свойства", example = "800")  val propertyValue: String?,
    @field:Schema(description = "Использовать ли множественный выбор", example = "False")  val useMultiSelect: Boolean,
    @field:Schema(description = "Отображаемое значение свойства", example = "Русский")  val displayValue: String?
)