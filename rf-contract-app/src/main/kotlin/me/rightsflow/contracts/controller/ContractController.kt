package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.contracts.dto.request.ContractCounterpartyRequest
import me.rightsflow.contracts.dto.request.ContractCreateRequest
import me.rightsflow.contracts.dto.request.ContractUpdateRequest
import me.rightsflow.contracts.dto.response.ContractCounterpartyDto
import me.rightsflow.contracts.dto.response.ContractDto
import me.rightsflow.contracts.service.ContractCounterpartyService
import me.rightsflow.contracts.service.ContractService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts")
@Tag(name = "Контракты", description = "Операции с контрактами")
class ContractController(
    private val service: ContractService,
    private val counterpartyService: ContractCounterpartyService
) {
    @GetMapping("/{id}")
    @Operation(summary = "Получить контракт по ID записи")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Контракт найден")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findById(@PathVariable id: Long): ContractDto = service.getById(id)

    @GetMapping("/cparty-by-contract/{id}")
    @Operation(summary = "Получить список контрагентов контракта по ID контракта")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список контрагентов контракта получен")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findCounterpartyByContractId(@PathVariable id: Long): List<ContractCounterpartyDto> =
        counterpartyService.findByContract(id)

    @GetMapping
    @Operation(summary = "Поиск контрактов по фильтрам (с пагинацией)")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список контрактов получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByFilter(
        @RequestParam(required = false) idContractType: Int?,
        @RequestParam(required = false) idContractStatus: Int?,
        @RequestParam(required = false) idOrg: Int?,
        @RequestParam(required = false) numFilter: String?,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<ContractDto> {
        val page = service.findByFilter(idContractType, idContractStatus, idOrg, numFilter, pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать новый контракт")
    @PreAuthorize("hasAnyAuthority('SCOPE_create','SCOPE_manager')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Контракт создан")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: ContractCreateRequest): ContractDto = service.create(req)

    @PostMapping(value = ["/cparty-by-contract"])
    @Operation(summary = "Добавить контрагента в контракт")
    @PreAuthorize("hasAnyAuthority('SCOPE_create','SCOPE_manager')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Контрагент добавлен")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun addCounterparty(@Valid @RequestBody req: ContractCounterpartyRequest): ContractCounterpartyDto =
        counterpartyService.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить контракт по заданному ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_update','SCOPE_manager')")
    @ApiResponse(responseCode = "200", description = "Контракт обновлён")
    @ValidationErrorResponse
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@PathVariable id: Long, @Valid @RequestBody req: ContractUpdateRequest): ContractDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить контракт по заданному ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Контракт удалён")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }

    @DeleteMapping("/cparty-by-contract/{id}")
    @Operation(summary = "Удалить контрагента из контракта по заданному ID записи")
    @PreAuthorize("hasAnyAuthority('SCOPE_delete','SCOPE_manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Контрагент удалён из контракта")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteCounterparty(@PathVariable id: Long) {
        counterpartyService.delete(id)
    }
}