package me.rightsflow.contracts.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import me.rightsflow.common.config.EmptyStringAsNullSerializer


/**
 * DTO для отправки обновлений в update_properties_batch
 */
data class PropertyUpdateBatchDto (

    @field:Schema(description = "Идентификатор сущности")
    @field:JsonProperty("id_entity")
    @field:NotNull
    var idEntity: Long,

    @field:Schema(description = "Код группы свойств")
    @field:JsonProperty("code_pg")
    @field:NotNull
    var codePg: String,

    @field:Schema(description = "Код или идентификатор свойства")
    @field:JsonProperty("property")
    @field:NotNull
    var property: String, // code или id

    @field:Schema(description = "Значение свойства")
    @field:JsonProperty("value")
    @field:com.fasterxml.jackson.databind.annotation.JsonSerialize(using = EmptyStringAsNullSerializer::class)
    val value: String?
)

data class ShortPropertyUpdateBatchDto (

    @field:Schema(description = "Код или идентификатор свойства")
    @field:JsonProperty("property")
    @field:NotNull
    var property: String, // code или id

    @field:Schema(description = "Значение свойства")
    @field:JsonProperty("value")
    @field:com.fasterxml.jackson.databind.annotation.JsonSerialize(using = EmptyStringAsNullSerializer::class)
    val value: String?
)

data class PropertyUpdateBatchRequest (

    @field:Schema(description = "Данные для обновления свойств" )
    @field:NotNull
    var updates: List<PropertyUpdateBatchDto>
)

data class ShortPropertyUpdateBatchRequest (

    @field:Schema(description = "Данные для обновления свойств" )
    @field:NotNull
    var updates: List<ShortPropertyUpdateBatchDto>
)

data class PropertyUpdateRequest (

    @field:Schema(description = "Код группы свойств")
    @field:NotNull
    var codePg: String,

    @field:Schema(description = "Код или идентификатор свойства")
    @field:NotNull
    var property: String,

    @field:Schema(description = "Идентификатор сущности")
    @field:NotNull
    var idEntity: Long,

    @field:Schema(description = "Значение свойства")
    var value: String?
)