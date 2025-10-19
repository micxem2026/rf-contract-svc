package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.contracts.dto.request.LicenseRightsCreateRequest
import me.rightsflow.contracts.dto.request.LicenseRightsUpdateRequest
import me.rightsflow.contracts.dto.response.LicenseRightsDto
import me.rightsflow.contracts.service.LicenseRightsService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts/lic-rt")
@Tag(name = "Права лицензии", description = "Операции с привязками типов прав к лицензии")
class LicenseRightsController(
    private val service: LicenseRightsService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить право для лицензии по ID записи")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Право для лицензии получено")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getById(@PathVariable id: Long): LicenseRightsDto = service.getById(id)

    @GetMapping("/by-license/{id}")
    @Operation(summary = "Получить список прав лицензии по ID лицензии")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список прав лицензии получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByLicenseId(
        @PathVariable id: Long,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseRightsDto> {
        val page = service.findByLicense(id, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать привязку права к лицензии")
    @PreAuthorize("hasAnyAuthority('SCOPE_create','SCOPE_manager')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Привязка права к лицензии создана")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: LicenseRightsCreateRequest): LicenseRightsDto = service.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить привязку права к лицензии по ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_update','SCOPE_manager')")
    @ApiResponse(responseCode = "200", description = "Привязка права к лицензии обновлена")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@PathVariable id: Long, @Valid @RequestBody req: LicenseRightsUpdateRequest): LicenseRightsDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить привязку права к лицензии по ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ApiResponse(responseCode = "204", description = "Привязка права к лицензии удалена")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }
}