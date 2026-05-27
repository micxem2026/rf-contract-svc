package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import me.rightsflow.common.config.NotFoundResponse
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.response.ContractTypeDto
import me.rightsflow.contracts.service.ContractTypeService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contracts/contract-types")
@Tag(name = "Типы контрактов", description = "Список типов контрактов")
class ContractTypeController(
    private val service: ContractTypeService
) {
    @GetMapping("/{id}")
    @Operation(summary = "Получить тип контракта по ID")
    @RequiresPermission("ContractTypeController:GetContractTypeById", description = "Получение типа контракта по ID")
    @ApiResponse(responseCode = "200", description = "Запись найдена")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findById(@Parameter(description = "ID типа контракта")
                 @PathVariable id: Int): ContractTypeDto = service.getById(id)

    @GetMapping
    @Operation(summary = "Получить список всех типов контрактов")
    @RequiresPermission("ContractTypeController:GetAllContractTypes", description = "Получение списка всех типов контрактов")
    @ApiResponse(responseCode = "200", description = "Список всех типов контрактов получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findAll(): List<ContractTypeDto> = service.findAll()
}