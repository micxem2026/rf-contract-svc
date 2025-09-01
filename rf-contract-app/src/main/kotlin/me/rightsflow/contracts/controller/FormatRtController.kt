package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.contracts.dto.request.FormatRtCreateRequest
import me.rightsflow.contracts.dto.request.FormatRtUpdateRequest
import me.rightsflow.contracts.dto.response.FormatRtDto
import me.rightsflow.contracts.service.FormatRtService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts/fmt-rt")
@Tag(name = "Тип права формата", description = "Операции с привязками типов прав к форматам")
class FormatRtController(
    private val service: FormatRtService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить тип права формата по ID записи")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Тип права формата найден")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getById(@PathVariable id: Long): FormatRtDto = service.getById(id)

    @GetMapping("/by-format/{id}")
    @Operation(summary = "Получить список типов прав формата по ID формата")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список типов прав формата получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByLicFormat(
        @PathVariable id: Long,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<FormatRtDto> {
        val page = service.findByLicFormat(id, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать привязку типа права к формату")
    @PreAuthorize("hasAnyAuthority('SCOPE_create','SCOPE_manager')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Привязка типа права к формату создана")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: FormatRtCreateRequest): FormatRtDto = service.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить привязку типа права к формату по ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_update','SCOPE_manager')")
    @ApiResponse(responseCode = "200", description = "Привязка типа права к формату обновлена")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@PathVariable id: Long, @Valid @RequestBody req: FormatRtUpdateRequest): FormatRtDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить привязку типа права к формату по ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ApiResponse(responseCode = "204", description = "Привязка типа права к формату удалена")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }
}