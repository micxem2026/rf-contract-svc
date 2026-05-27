package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.request.LicenseFormatCreateRequest
import me.rightsflow.contracts.dto.request.LicenseFormatUpdateRequest
import me.rightsflow.contracts.dto.response.LicenseFormatDto
import me.rightsflow.contracts.service.LicenseFormatService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/formats")
@Tag(name = "Форматы лицензий", description = "Операции с форматами лицензий")
class LicenseFormatController(
    private val service: LicenseFormatService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить формат лицензии по ID записи")
    @RequiresPermission("LicenseFormatController:GetLicenseFormatById", description = "Получить формат лицензии по ID")
    @ApiResponse(responseCode = "200", description = "Формат лицензии найден")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findById(@Parameter(description = "ID формата лицензии")
                 @PathVariable id: Long): LicenseFormatDto = service.getById(id)

    @GetMapping
    @Operation(summary = "Поиск форматов лицензий по фильтру (с пагинацией)")
    @RequiresPermission("LicenseFormatController:FindAllLicenseFormatsByFilter", description = "Поиск форматов лицензий по фильтру (с пагинацией)")
    @ApiResponse(responseCode = "200", description = "Список форматов лицензий получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByFilter(
        @RequestParam(required = false) nameFilter: String?,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseFormatDto> {
        val page = service.findByFilter(nameFilter, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать новый формат лицензии")
    @RequiresPermission("LicenseFormatController:CreateLicenseFormat", description = "Создать формат лицензии")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Формат лицензии создан")
    @ConflictResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: LicenseFormatCreateRequest): LicenseFormatDto = service.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить формат лицензии по заданному ID записи")
    @RequiresPermission("LicenseFormatController:UpdateLicenseFormat", description = "Изменить формат лицензии")
    @ApiResponse(responseCode = "200", description = "Формат лицензии обновлён")
    @NotFoundResponse
    @ConflictResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@Parameter(description = "ID формата лицензии")
               @PathVariable id: Long, @Valid @RequestBody req: LicenseFormatUpdateRequest): LicenseFormatDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить формат лицензии по заданному ID записи")
    @RequiresPermission("LicenseFormatController:DeleteLicenseFormat", description = "Удалить формат лицензии")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Формат лицензии удалён")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@Parameter(description = "ID формата лицензии")
               @PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }

}