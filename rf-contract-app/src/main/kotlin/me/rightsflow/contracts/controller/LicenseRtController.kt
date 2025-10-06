package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.contracts.dto.request.LicenseRtCreateRequest
import me.rightsflow.contracts.dto.request.LicenseRtUpdateRequest
import me.rightsflow.contracts.dto.response.LicenseRtDto
import me.rightsflow.contracts.service.LicenseRtService
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
@Tag(name = "Тип права лицензии", description = "Операции с привязками типов прав к лицензии")
class LicenseRtController(
    private val service: LicenseRtService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить тип права лицензии по ID записи")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Тип права лицензии найден")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getById(@PathVariable id: Long): LicenseRtDto = service.getById(id)

    @GetMapping("/by-license/{id}")
    @Operation(summary = "Получить список типов прав лицензии по ID лицензии")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список типов прав лицензии получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByLicenseId(
        @PathVariable id: Long,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseRtDto> {
        val page = service.findByLicense(id, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать привязку типа права к лицензии")
    @PreAuthorize("hasAnyAuthority('SCOPE_create','SCOPE_manager')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Привязка типа права к лицензии создана")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: LicenseRtCreateRequest): LicenseRtDto = service.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить привязку типа права к лицензии по ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_update','SCOPE_manager')")
    @ApiResponse(responseCode = "200", description = "Привязка типа права к лицензии обновлена")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@PathVariable id: Long, @Valid @RequestBody req: LicenseRtUpdateRequest): LicenseRtDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить привязку типа права к лицензии по ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ApiResponse(responseCode = "204", description = "Привязка типа права к лицензии удалена")
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