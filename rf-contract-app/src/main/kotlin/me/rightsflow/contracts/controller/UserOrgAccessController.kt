package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.ConflictResponse
import me.rightsflow.common.config.InternalServerErrorResponse
import me.rightsflow.common.config.NotFoundResponse
import me.rightsflow.common.config.ValidationErrorResponse
import me.rightsflow.contracts.dto.request.UserOrgAccessRequest
import me.rightsflow.contracts.dto.request.UserOrgBulkRequest
import me.rightsflow.contracts.dto.response.UserOrgAccessDto
import me.rightsflow.contracts.service.UserOrgAccessService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST API для управления привязками пользователей к организациям.
 *
 * Доступ к эндпоинтам разрешён только пользователям с ролями ADMIN или PERMISSION_MANAGER.
 *
 * Назначение:
 *   - Пользователь без привязок не имеет доступа ни к одному контракту.
 *   - Пользователи с ролями ADMIN и SERVICE имеют доступ ко всем контрактам
 *     (bypass реализован в pkg_contract.get_user_org_ids).
 *   - Контроль доступа применяется автоматически в PL/pgSQL-функциях
 *     при всех операциях с контрактами, лицензиями и связанными сущностями.
 */
@RestController
@RequestMapping("/user-org-access")
@Tag(
    name = "Доступ пользователей к организациям",
    description = "Управление привязкой пользователей к организациям для разграничения доступа к контрактам"
)
class UserOrgAccessController(
    private val service: UserOrgAccessService
) {

    // ================================================================
    // GET
    // ================================================================

    @GetMapping("/by-user/{username}")
    @Operation(
        summary = "Получить список организаций пользователя",
        description = "Возвращает все организации, к которым привязан указанный пользователь"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'PERMISSION_MANAGER')")
    @ApiResponse(responseCode = "200", description = "Список организаций получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getByUsername(
        @Parameter(description = "Username пользователя (JWT sub)")
        @PathVariable username: String
    ): List<UserOrgAccessDto> =
        service.getByUsername(username)

    @GetMapping("/by-org/{idOrg}")
    @Operation(
        summary = "Получить список пользователей организации",
        description = "Возвращает всех пользователей, привязанных к указанной организации"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'PERMISSION_MANAGER')")
    @ApiResponse(responseCode = "200", description = "Список пользователей получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getByOrg(
        @Parameter(description = "ID организации или её код 1С")
        @PathVariable idOrg: String,
        @PageableDefault(size = 20, sort = ["username"], direction = Sort.Direction.ASC)
        @ParameterObject pageable: Pageable
    ): PagedModel<UserOrgAccessDto> =
        PagedModel(service.getByOrg(idOrg, pageable))

    // ================================================================
    // POST
    // ================================================================

    @PostMapping
    @Operation(
        summary = "Привязать пользователя к организации",
        description = "Создаёт одну привязку пользователь → организация. " +
                "Если привязка уже существует — возвращает 400."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'PERMISSION_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Привязка создана")
    @NotFoundResponse
    @ConflictResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun assign(
        @Valid @RequestBody req: UserOrgAccessRequest
    ): UserOrgAccessDto =
        service.assign(req)

    // ================================================================
    // PUT
    // ================================================================

    @PutMapping("/by-user/{username}/bulk")
    @Operation(
        summary = "Задать полный список организаций пользователя",
        description = "Replace-операция: полностью заменяет список организаций пользователя. " +
                "Привязки, не входящие в новый список — удаляются. " +
                "Новые привязки — добавляются. Возвращает итоговый список."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'PERMISSION_MANAGER')")
    @ApiResponse(responseCode = "200", description = "Список организаций пользователя обновлён")
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun assignBulk(
        @Parameter(description = "Username пользователя (JWT sub)")
        @PathVariable username: String,
        @Valid @RequestBody req: UserOrgBulkRequest
    ): List<UserOrgAccessDto> {
        // Гарантируем соответствие username в пути и теле запроса
        require(username == req.username) {
            "Username в пути '$username' не совпадает с username в теле запроса '${req.username}'"
        }
        return service.assignBulk(req)
    }

    // ================================================================
    // DELETE
    // ================================================================

    @DeleteMapping("/by-user/{username}/org/{idOrg}")
    @Operation(
        summary = "Отвязать пользователя от организации",
        description = "Удаляет конкретную привязку пользователь → организация"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'PERMISSION_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Привязка удалена")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun revoke(
        @Parameter(description = "Username пользователя (JWT sub)")
        @PathVariable username: String,
        @Parameter(description = "ID организации или её код 1С")
        @PathVariable idOrg: String
    ) = service.revoke(username, idOrg)

    @DeleteMapping("/by-user/{username}")
    @Operation(
        summary = "Удалить все привязки пользователя",
        description = "Удаляет все привязки к организациям для указанного пользователя. " +
                "После этой операции пользователь теряет доступ ко всем контрактам."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'PERMISSION_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Все привязки удалены")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun revokeAll(
        @Parameter(description = "Username пользователя (JWT sub)")
        @PathVariable username: String
    ) = service.revokeAll(username)
}
