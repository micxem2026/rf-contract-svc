package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import me.rightsflow.contracts.service.ConstraintService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/constraints")
@Tag(name = "Проверка ограничений", description = "Операции для отложенной проверки ограничений")
class ConstraintController(
    private val constraintService: ConstraintService
) {

    @GetMapping("/oip/{id}")
    @Operation(summary = "Проверить использование ОИС в контрактах")
    @ApiResponse(responseCode = "200", description = "Статус использования ОИС получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun checkOipConstraint(@PathVariable id: Int) =
        constraintService.checkOipUse(id)


    @GetMapping("/right-type/{id}")
    @Operation(summary = "Проверить использование типа права в контрактах")
    @ApiResponse(responseCode = "200", description = "Статус использования типа права получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun checkRightTypeConstraint(@PathVariable id: Int) =
        constraintService.checkRightTypeUse(id)


    @GetMapping("/feature-category/{id}")
    @Operation(summary = "Проверить использование категории характеристик в контрактах")
    @ApiResponse(responseCode = "200", description = "Статус использования категории характеристик получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun checkFeatureCategoryConstraint(@PathVariable id: Int) =
        constraintService.checkFeatureCategoryUse(id)

    @GetMapping("/feature/{id}")
    @Operation(summary = "Проверить использование характеристики в контрактах")
    @ApiResponse(responseCode = "200", description = "Статус использования характеристики получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun checkFeatureConstraint(@PathVariable id: Int) =
        constraintService.checkFeatureUse(id)

    @GetMapping("/counterparty/{id}")
    @Operation(summary = "Проверить использование контрагента в контрактах")
    @ApiResponse(responseCode = "200", description = "Статус использования контрагента получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun checkCounterpartyConstraint(@PathVariable id: Int) =
        constraintService.checkCounterpartyUse(id)

    @GetMapping("/organization/{id}")
    @Operation(summary = "Проверить использование организации в контрактах")
    @ApiResponse(responseCode = "200", description = "Статус использования организации получен")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun checkOrganizationConstraint(@PathVariable id: Int) =
        constraintService.checkOrganizationUse(id)

}