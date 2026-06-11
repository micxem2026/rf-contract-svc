package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.request.ContractCounterpartyRequest
import me.rightsflow.contracts.dto.request.ContractCreateRequest
import me.rightsflow.contracts.dto.request.ContractStatusUpdateRequest
import me.rightsflow.contracts.dto.request.ContractUpdateRequest
import me.rightsflow.contracts.dto.response.ContractChangeStatusDto
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
    @RequiresPermission("ContractController:GetContractById", description = "Получение контракта по ID")
    @ApiResponse(responseCode = "200", description = "Контракт найден")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findById(@Parameter(description = "ID контракта")
                 @PathVariable id: Long): ContractDto = service.getById(id)

    @GetMapping("/cparty-by-contract/{id}")
    @Operation(summary = "Получить список контрагентов контракта по ID контракта")
    @RequiresPermission("ContractController:GetContractCounterparties", description = "Получение контрагентов контракта по ID контракта")
    @ApiResponse(responseCode = "200", description = "Список контрагентов контракта получен")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findCounterpartyByContractId(@Parameter(description = "ID контракта")
                                     @PathVariable id: Long): List<ContractCounterpartyDto> =
        counterpartyService.findByContract(id)

    @GetMapping
    @Operation(summary = "Поиск контрактов по фильтрам (с пагинацией)")
    @RequiresPermission("ContractController:FindAllContractsByFilter", description = "Поиск контрактов по фильтрам (с пагинацией)")
    @ApiResponse(responseCode = "200", description = "Список контрактов получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByFilter(
        @Parameter(description = "Фильтр по ID типа контракта")
        @RequestParam(required = false) idContractType: Int?,
        @Parameter(description = "Фильтр по ID статуса контракта")
        @RequestParam(required = false) idContractStatus: List<Int>?,
        @Parameter(description = "Фильтр по статусу 1С контракта")
        @RequestParam(required = false) status1c: List<String>?,
        @Parameter(description = "Фильтр по ID организации или коду 1С организации")
        @RequestParam(required = false) idOrg: String?,
        @Parameter(description = "Фильтр по номеру договора, коду 1C контрагента или названию контрагента")
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false)
        @Parameter(name = "inOut", schema = Schema(type = "string", allowableValues = ["eP", "eS", "iP", "iS"]),
            description = "Фильтр по виду контракта:\n\n" +
                    " - *eP* — внешняя покупка\n\n" +
                    " - *eS* — внешняя продажа\n\n" +
                    " - *iP* — внутренняя покупка\n\n"+
                    " - *iS* — внутренняя продажа") inOut: String?,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<ContractDto> {
        val page = service.findByFilter(idContractType, idContractStatus, status1c, idOrg, filter, inOut,pageable)
        return PagedModel(page)
    }

    @PostMapping
    @Operation(summary = "Создать новый контракт")
    @RequiresPermission("ContractController:CreateContract", description = "Создание нового контракта")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Контракт создан")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: ContractCreateRequest): ContractDto = service.create(req)

    @PostMapping(value = ["/cparty-by-contract"])
    @Operation(summary = "Добавить контрагента в контракт")
    @RequiresPermission("ContractController:AddCounterpartyToContract", description = "Добавление контрагента в контракт")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Контрагент добавлен")
    @ValidationErrorResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun addCounterparty(@Valid @RequestBody req: ContractCounterpartyRequest): ContractCounterpartyDto =
        counterpartyService.create(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить контракт по заданному ID контракта")
    @RequiresPermission("ContractController:UpdateContract", description = "Изменение контракта")
    @ApiResponse(responseCode = "200", description = "Контракт обновлён")
    @ValidationErrorResponse
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(@Parameter(description = "ID контракта")
               @PathVariable id: Long, @Valid @RequestBody req: ContractUpdateRequest): ContractDto =
        service.update(id, req)

    @PutMapping("/set-status/{id}")
    @Operation(summary = "Изменить статус контракта по заданному ID контракта")
    @RequiresPermission("ContractController:UpdateContractStatusById", description = "Изменение статуса контракта по ID")
    @ApiResponse(responseCode = "200", description = "Обновление статуса выполнено")
    @ValidationErrorResponse
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun updateStatus(@Parameter(description = "ID контракта")
                     @PathVariable id: Long, @Valid @RequestBody req: ContractStatusUpdateRequest): ContractDto =
        service.updateStatus(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить контракт по заданному ID записи")
    @RequiresPermission("ContractController:DeleteContractById", description = "Удаление контракта")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Контракт удалён")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@Parameter(description = "ID контракта")
               @PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }

    @DeleteMapping("/cparty-by-contract/{id}")
    @Operation(summary = "Удалить контрагента из контракта по заданному ID записи")
    @RequiresPermission("ContractController:DeleteCounterpartyFromContract", description = "Удаление контрагента из контракта")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Контрагент удалён из контракта")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteCounterparty(@Parameter(description = "ID записи \"контракт -> контрагент\"")
                           @PathVariable id: Long) {
        counterpartyService.delete(id)
    }
}