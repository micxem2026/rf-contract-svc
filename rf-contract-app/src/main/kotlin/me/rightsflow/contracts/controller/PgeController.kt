package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import me.rightsflow.common.config.ValidationErrorResponse
import me.rightsflow.contracts.dto.request.PropertyUpdateBatchRequest
import me.rightsflow.contracts.dto.request.PropertyUpdateRequest
import me.rightsflow.contracts.dto.response.PropertyDataDto
import me.rightsflow.contracts.dto.response.PropertyGroupDto
import me.rightsflow.contracts.service.PgeService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pge")
@Tag(name = "Группы свойств", description = "Операции с группами свойств")
class PgeController(
    private val service: PgeService
) {
    @GetMapping("/{objId}")
    @Operation(summary = "Получить список групп свойств привязанных к объекту по его ID")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Список групп свойств получен")
    //@NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getPropertyGroups(@PathVariable objId: Int): List<PropertyGroupDto> = service.getPropertyGroups(objId)

    @GetMapping("/data")
    @Operation(summary = "Получить данные для заданной группы свойств и списка сущностей")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Данные для группы свойств получены")
    //@NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getPgData(
        @Parameter(description = "Код группы свойств")
        @RequestParam codePg: String,
        @Parameter(description = "Список идентификаторов сущностей")
        @RequestParam entityIds: List<Long>
    ): List<PropertyDataDto> = service.getPgData(codePg, entityIds)

    @GetMapping("/property")
    @Operation(summary = "Получить данные конкретного свойства")
    @PreAuthorize("hasAuthority('SCOPE_user')")
    @ApiResponse(responseCode = "200", description = "Данные конкретного свойства получены")
    //@NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getProperty(
        @Parameter(description = "Код группы свойств")
        @RequestParam codePg: String,
        @Parameter(description = "Идентификатор или код свойства")
        @RequestParam property: String,
        @Parameter(description = "Список идентификаторов сущностей")
        @RequestParam entityIds: List<Long>
    ): List<PropertyDataDto> = service.getProperty(codePg, property, entityIds)

    @PutMapping("/property")
    @Operation(summary = "Обновить данные конкретного свойства")
    @PreAuthorize("hasAnyAuthority('SCOPE_update','SCOPE_manager')")
    @ApiResponse(responseCode = "200", description = "Данные конкретного свойства обновлены")
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun updateProperty(@Valid @RequestBody req: PropertyUpdateRequest): PropertyDataDto = service.updateProperty(req)

    @PutMapping("/property/batch")
    @Operation(summary = "Обновить данные нескольких свойств")
    @PreAuthorize("hasAnyAuthority('SCOPE_update','SCOPE_manager')")
    @ApiResponse(responseCode = "200", description = "Данные свойств обновлены")
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun updatePropertiesBatch(@Valid @RequestBody req: PropertyUpdateBatchRequest): Int =
        service.updatePropertiesBatch(req)

}