package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import me.rightsflow.common.config.NotFoundResponse
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.response.ContractStatusDto
import me.rightsflow.contracts.service.ContractStatusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contracts/contract-statuses")
@Tag(name = "Статусы контрактов", description = "Список статусов контрактов")
class ContractStatusController(
    private val service: ContractStatusService
) {
    @GetMapping("/{id}")
    @Operation(summary = "Получить статус контракта по ID")
    @RequiresPermission("ContractStatusController:GetContractStatusById", description = "Получение статуса контракта по ID")
    @ApiResponse(responseCode = "200", description = "Запись найдена")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findById(@Parameter(description = "ID статуса контракта")
                 @PathVariable id: Int): ContractStatusDto = service.getById(id)

    @GetMapping
    @Operation(summary = "Получить список всех статусов контрактов")
    @RequiresPermission("ContractStatusController:GetAllContractStatuses", description = "Получение списка всех статусов контрактов")
    @ApiResponse(responseCode = "200", description = "Список всех статусов контрактов получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findAll(@Parameter(description = "Фильтр по ID типа контракта")
                @RequestParam(required = false) idContractType: Int?): List<ContractStatusDto> = service.findAll(idContractType)
}