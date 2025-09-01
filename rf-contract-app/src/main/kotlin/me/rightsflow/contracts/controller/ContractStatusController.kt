package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import me.rightsflow.common.config.NotFoundResponse
import me.rightsflow.contracts.dto.response.ContractStatusDto
import me.rightsflow.contracts.service.ContractStatusService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contracts/contract-statuses")
@Tag(name = "Статусы контрактов", description = "Список статусов контрактов")
class ContractStatusController(
    private val service: ContractStatusService
) {
    @GetMapping("/{id}")
    @Operation(summary = "Получить статус контракта по ID")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Запись найдена")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findById(@PathVariable id: Int): ContractStatusDto = service.getById(id)

    @GetMapping
    @Operation(summary = "Получить список всех статусов контрактов")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список всех статусов контрактов получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findAll(): List<ContractStatusDto> = service.findAll()
}