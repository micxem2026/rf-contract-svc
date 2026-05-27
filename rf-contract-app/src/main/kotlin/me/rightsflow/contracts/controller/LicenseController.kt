package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.common.permission.annotation.RequiresPermission
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
    @RequiresPermission("LicenseController:GetLicenseById", description = "Получение лицензии по ID")
    @ApiResponse(responseCode = "200", description = "Лицензия найдена")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findById(@Parameter(description = "ID лицензии")
                 @PathVariable id: Long): LicenseDto = service.getById(id)

    @GetMapping("/oip-by-license/{id}")
    @Operation(summary = "Получить список ОИС лицензии по ID лицензии")
    @RequiresPermission("LicenseController:GetLicenseOipByLicenseId", description = "Получение списка ОИС лицензии")
    @ApiResponse(responseCode = "200", description = "Список ОИС лицензии получен")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findOipByLicenseId(
        @Parameter(description = "ID лицензии")
        @PathVariable id: Long,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseOipDto> {
        val page = licenseOipService.findByLicense(id, pageable)
        return PagedModel(page)
    }

    @GetMapping("/by-contract/{id}")
    @Operation(summary = "Поиск лицензий контракта по фильтрам (с пагинацией)")
    @RequiresPermission("LicenseController:FindAllLicensesByContractId", description = "Поиск лицензий контракта по фильтрам (с пагинацией)")
    @ApiResponse(responseCode = "200", description = "Список лицензий получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByFilter(
        @Parameter(description = "ID контракта")
        @PathVariable id: Long,
        @RequestParam(required = false) numFilter: String?,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseDto> {
        val page = service.findByFilter(id, numFilter, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать новую лицензию")
    @RequiresPermission("LicenseController:CreateLicense", description = "Создание новой лицензии")
    @ApiResponse(responseCode = "201", description = "Лицензия создана")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: LicenseCreateRequest): LicenseDto = service.create(req)

    @PostMapping(value = ["/oip-by-license"])
    @Operation(summary = "Добавить ОИС(ы) в лицензию")
    @RequiresPermission("LicenseController:AddLicenseOip", description = "Добавление ОИС в лицензию")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "ОИС(ы) добавлен(ы)")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun addLicenseOip(@Valid @RequestBody req: LicenseOipRequest): List<LicenseOipDto> = licenseOipService.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить лицензию по заданному ID записи")
    @RequiresPermission("LicenseController:UpdateLicense", description = "Изменение лицензии")
    @ApiResponse(responseCode = "200", description = "Лицензия обновлена")
    @ValidationErrorResponse
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@Parameter(description = "ID лицензии")
               @PathVariable id: Long,
               @Valid @RequestBody req: LicenseUpdateRequest): LicenseDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить лицензию по заданному ID записи")
    @RequiresPermission("LicenseController:DeleteLicense", description = "Удаление лицензии")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Лицензия удалена")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@Parameter(description = "ID лицензии")
               @PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }

    @DeleteMapping("/oip-by-license/{id}")
    @Operation(summary = "Удалить ОИС из лицензии по заданному ID записи")
    @RequiresPermission("LicenseController:DeleteOipFromLicenseById", description = "Удаление ОИС из лицензии")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "ОИС удалён из лицензии")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteOip(@Parameter(description = "ID записи \"лицензия -> оис\"")
                  @PathVariable id: Long) {
        licenseOipService.delete(id)
    }

    @DeleteMapping("/oip-by-license/{idLicense}/root/{idRoot}")
    @Operation(summary = "Удалить ОИС(ы) из лицензии по заданному ID корневого ОИС")
    @RequiresPermission("LicenseController:DeleteOipFromLicenseByIdRootOip", description = "Удаление ОИС из лицензии по ID корневого ОИС")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "ОИС(ы) удален(ы) из лицензии")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteOipByRoot(@Parameter(description = "ID лицензии")
                        @PathVariable idLicense: Long,
                        @Parameter(description = "ID корневого ОИС")
                        @PathVariable idRoot: Long) {
        licenseOipService.deleteByRoot(idLicense, idRoot)
    }

    @DeleteMapping("/oip-by-license/{idLicense}/license")
    @Operation(summary = "Удалить ОИС(ы) из лицензии по заданному ID лицензии")
    @RequiresPermission("LicenseController:DeleteAllOipFromLicense", description = "Удаление всех ОИС из лицензии")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "ОИС(ы) удален(ы) из лицензии")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteOipByLicense(@Parameter(description = "ID лицензии")
                           @PathVariable idLicense: Long) {
        licenseOipService.deleteByLic(idLicense)
    }
}