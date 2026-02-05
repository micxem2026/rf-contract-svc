package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.contracts.dto.request.LicenseCreateRequest
import me.rightsflow.contracts.dto.request.LicenseOipRequest
import me.rightsflow.contracts.dto.request.LicenseUpdateRequest
import me.rightsflow.contracts.dto.response.LicenseDto
import me.rightsflow.contracts.dto.response.LicenseOipDto
import me.rightsflow.contracts.service.LicenseOipService
import me.rightsflow.contracts.service.LicenseService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts/licenses")
@Tag(name = "Лицензии", description = "Операции с лицензиями")
class LicenseController(
    private val service: LicenseService,
    private val licenseOipService: LicenseOipService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить лицензию по ID записи")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Лицензия найдена")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findById(@PathVariable id: Long): LicenseDto = service.getById(id)

    @GetMapping("/oip-by-license/{id}")
    @Operation(summary = "Получить список ОИС лицензии по ID лицензии")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список ОИС лицензии получен")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findOipByLicenseId(@PathVariable id: Long): List<LicenseOipDto> = licenseOipService.findByLicense(id)

    @GetMapping("/by-contract/{id}")
    @Operation(summary = "Поиск лицензий контракта по фильтрам (с пагинацией)")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список лицензий получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByFilter(
        @PathVariable id: Long,
        @RequestParam(required = false) numFilter: String?,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseDto> {
        val page = service.findByFilter(id, numFilter, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать новую лицензию")
    @PreAuthorize("hasAnyAuthority('SCOPE_create','SCOPE_manager')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Лицензия создана")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: LicenseCreateRequest): LicenseDto = service.create(req)

    @PostMapping(value = ["/oip-by-license"])
    @Operation(summary = "Добавить ОИС(ы) в лицензию")
    @PreAuthorize("hasAnyAuthority('SCOPE_create','SCOPE_manager')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "ОИС(ы) добавлен(ы)")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun addLicenseOip(@Valid @RequestBody req: LicenseOipRequest): List<LicenseOipDto> = licenseOipService.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить лицензию по заданному ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_update','SCOPE_manager')")
    @ApiResponse(responseCode = "200", description = "Лицензия обновлена")
    @ValidationErrorResponse
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@PathVariable id: Long, @Valid @RequestBody req: LicenseUpdateRequest): LicenseDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить лицензию по заданному ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Лицензия удалена")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }

    @DeleteMapping("/oip-by-license/{id}")
    @Operation(summary = "Удалить ОИС из лицензии по заданному ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "ОИС удалён из лицензии")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteOip(@PathVariable id: Long) {
        licenseOipService.delete(id)
    }

    @DeleteMapping("/oip-by-license/{idLicense}/root/{idRoot}")
    @Operation(summary = "Удалить ОИС(ы) из лицензии по заданному ID корневого ОИС")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "ОИС(ы) удален(ы) из лицензии")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteOipByRoot(@PathVariable idLicense: Long, @PathVariable idRoot: Long) {
        licenseOipService.deleteByRoot(idLicense, idRoot)
    }

    @DeleteMapping("/oip-by-license/{idLicense}/license")
    @Operation(summary = "Удалить ОИС(ы) из лицензии по заданному ID лицензии")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "ОИС(ы) удален(ы) из лицензии")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteOipByLicense(@PathVariable idLicense: Long) {
        licenseOipService.deleteByLic(idLicense)
    }
}