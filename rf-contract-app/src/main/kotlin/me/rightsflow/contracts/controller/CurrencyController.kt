package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import me.rightsflow.common.config.NotFoundResponse
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.response.CurrencyDto
import me.rightsflow.contracts.service.CurrencyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contracts/currency")
@Tag(name = "Валюты", description = "Список валют")
class CurrencyController(
    private val service: CurrencyService
) {
    @GetMapping("/{id}")
    @Operation(summary = "Получить валюту по ID")
    @RequiresPermission("CurrencyController:GetCurrencyById", description = "Получение валюты по ID")
    @ApiResponse(responseCode = "200", description = "Запись найдена")
    @CommonSecurityResponses
    @NotFoundResponse
    @InternalServerErrorResponse
    fun findById(@Parameter(description = "ID валюты")
                 @PathVariable id: Int): CurrencyDto = service.getById(id)

    @GetMapping
    @Operation(summary = "Получить список всех валют")
    @RequiresPermission("CurrencyController:GetAllCurrencies", description = "Получение списка всех валют")
    @ApiResponse(responseCode = "200", description = "Список всех валют получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findAll(): List<CurrencyDto> = service.findAll()
}