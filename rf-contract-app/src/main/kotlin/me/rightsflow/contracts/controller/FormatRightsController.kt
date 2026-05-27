package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.request.FormatRightsCreateRequest
import me.rightsflow.contracts.dto.request.FormatRightsUpdateRequest
import me.rightsflow.contracts.dto.response.FormatRightsDto
import me.rightsflow.contracts.service.FormatRtService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/formats/fmt-rt")
@Tag(name = "Права формата", description = "Операции с привязками типов прав к форматам")
class FormatRightsController(
    private val service: FormatRtService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить право для формата по ID записи")
    @RequiresPermission("FormatRightsController:GetFormatRightsById", description = "Получение права формата по ID")
    @ApiResponse(responseCode = "200", description = "Право для формата получено")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getById(@Parameter(description = "ID права формата") @PathVariable id: Long): FormatRightsDto = service.getById(id)

    @GetMapping("/by-format/{id}")
    @Operation(summary = "Получить список прав формата по ID формата")
    @RequiresPermission("FormatRightsController:GetAllFormatRights", description = "Получение списка прав формата (с пагинацией)")
    @ApiResponse(responseCode = "200", description = "Список прав формата получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByLicFormat(
        @Parameter(description = "ID формата")
        @PathVariable id: Long,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<FormatRightsDto> {
        val page = service.findByLicFormat(id, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать привязку права к формату")
    @RequiresPermission("FormatRightsController:CreateFormatRights", description = "Создание права формата")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Привязка права к формату создана")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: FormatRightsCreateRequest): FormatRightsDto = service.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить привязку права к формату по ID записи")
    @RequiresPermission("FormatRightsController:UpdateFormatRights", description = "Изменение права формата")
    @ApiResponse(responseCode = "200", description = "Привязка права к формату обновлена")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@Parameter(description = "ID права формата")
               @PathVariable id: Long, @Valid @RequestBody req: FormatRightsUpdateRequest): FormatRightsDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить привязку права к формату по ID записи")
    @RequiresPermission("FormatRightsController:DeleteFormatRights", description = "Удаление права формата")
    @ApiResponse(responseCode = "204", description = "Привязка права к формату удалена")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@Parameter(description = "ID права формата")
               @PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }
}