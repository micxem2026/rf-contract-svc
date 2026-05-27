package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.request.LicenseRightsCreateRequest
import me.rightsflow.contracts.dto.request.LicenseRightsUpdateRequest
import me.rightsflow.contracts.dto.request.ShortPropertyUpdateBatchRequest
import me.rightsflow.contracts.dto.response.LicenseRightsDto
import me.rightsflow.contracts.service.LicenseRightsService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts/lic-rt")
@Tag(name = "Права лицензии", description = "Операции с привязками права (способа использования) к лицензии")
class LicenseRightsController(
    private val service: LicenseRightsService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить право для лицензии по ID записи")
    @RequiresPermission("LicenseRightsController:GetLicenseRightsById", description = "Получение права лицензии")
    @ApiResponse(responseCode = "200", description = "Право для лицензии получено")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getById(@Parameter(description = "ID права лицензии")
                @PathVariable id: Long): LicenseRightsDto = service.getById(id)

    @GetMapping("/by-license/{id}")
    @Operation(summary = "Получить список прав лицензии по ID лицензии")
    @RequiresPermission("LicenseRightsController:GetAllLicenseRights", description = "Получение списка прав лицензии (с пагинацией)")
    @ApiResponse(responseCode = "200", description = "Список прав лицензии получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByLicenseId(
        @Parameter(description = "ID лицензии")
        @PathVariable id: Long,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseRightsDto> {
        val page = service.findByLicense(id, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать привязку права к лицензии")
    @RequiresPermission("LicenseRightsController:CreateLicenseRights", description = "Создание права лицензии")
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
    @RequiresPermission("LicenseRightsController:UpdateLicenseRights", description = "Изменение права лицензии")
    @ApiResponse(responseCode = "200", description = "Привязка права к лицензии обновлена")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@Parameter(description = "ID права лицензии")
               @PathVariable id: Long, @Valid @RequestBody req: LicenseRightsUpdateRequest): LicenseRightsDto =
        service.update(id, req)

    @PutMapping("/finCond/{id}")
    @Operation(summary = "Обновить свойства финансовых условий для ID сущности licenseRightsRt")
    @RequiresPermission("LicenseRightsController:UpdateLicenseRightsFinCond", description = "Обновление финансовых условий права лицензии")
    @ApiResponse(responseCode = "200", description = "Свойства финансовых условий обновлены")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun updateFinCond(@Parameter(description = "ID записи \"право лицензии -> тип права\"")
                      @PathVariable id: Long, @Valid @RequestBody req: ShortPropertyUpdateBatchRequest): Int =
        service.updateFinCond(id, req)

    @PutMapping("/addCond/{id}")
    @Operation(summary = "Обновить свойства дополнительных условий для ID сущности licenseRightsRt")
    @RequiresPermission("LicenseRightsController:UpdateLicenseRightsAddCond", description = "Обновление дополнительных условий права лицензии")
    @ApiResponse(responseCode = "200", description = "Свойства дополнительных условий обновлены")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun updateAddCond(@Parameter(description = "ID записи \"право лицензии -> тип права\"")
                      @PathVariable id: Long, @Valid @RequestBody req: ShortPropertyUpdateBatchRequest): Int =
        service.updateAddCond(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить привязку права к лицензии по ID записи")
    @RequiresPermission("LicenseRightsController:DeleteLicenseRights", description = "Удаление права лицензии")
    @ApiResponse(responseCode = "204", description = "Привязка права к лицензии удалена")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@Parameter(description = "ID права лицензии")
               @PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }
}