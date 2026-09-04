package me.rightsflow.contracts.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * Запрос на добавление одной привязки пользователь → организация.
 */
data class UserOrgAccessRequest(

    @field:NotBlank(message = "Username не может быть пустым")
    @field:Size(max = 50, message = "Username не может быть длиннее 50 символов")
    val username: String,

    @field:NotNull(message = "ID организации обязателен")
    var idOrg: String
)

/**
 * Запрос на замену всего списка организаций для пользователя (replace-семантика).
 * Существующие привязки, не входящие в новый список, будут удалены.
 */
data class UserOrgBulkRequest(

    @field:NotBlank(message = "Username не может быть пустым")
    @field:Size(max = 50, message = "Username не может быть длиннее 50 символов")
    val username: String,

    val orgIds: List<String>
)
