package me.rightsflow.contracts.dto.response

import java.time.OffsetDateTime

/**
 * Ответ с данными одной привязки пользователь → организация.
 */
data class UserOrgAccessDto(
    val id: Long,
    val username: String,
    val idOrg: Int,

    /**
     * Название организации из sync__klf_organization.
     * Подтягивается Join-ом для удобства отображения в UI.
     */
    val orgName: String?,

    val createdBy: String,
    val createdAt: OffsetDateTime
)
