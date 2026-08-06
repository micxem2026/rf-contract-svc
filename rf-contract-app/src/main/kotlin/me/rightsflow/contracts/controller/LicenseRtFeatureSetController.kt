package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.request.LicenseRtFeatureSetCreateRequest
import me.rightsflow.contracts.dto.request.LicenseRtFeatureSetUpdateRequest
import me.rightsflow.contracts.dto.request.LicenseRtFeaturesCreateBulkRequest
import me.rightsflow.contracts.dto.request.LicenseRtFeaturesCreateRequest
import me.rightsflow.contracts.dto.response.LicenseRtFeatureSetDto
import me.rightsflow.contracts.dto.response.LicenseRtFeaturesDto
import me.rightsflow.contracts.service.LicenseRtFeatureSetService
import me.rightsflow.contracts.service.LicenseRtFeaturesService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/contracts/lic-rt-fs")
@Tag(name = "Набор характеристик права для лицензии", description = "Операции с набором характеристик права для лицензии")
class LicenseRtFeatureSetController(
    private val service: LicenseRtFeatureSetService,
    private val licenseRtFeaturesService: LicenseRtFeaturesService
) {
    @GetMapping("/{id}")
    @Operation(summary = "Получить набор характеристик права по ID записи")
    @RequiresPermission("LicenseRtFeatureSetController:GetLicenseRtFeatureSetById", description = "Получение набора характеристик по ID")
    @ApiResponse(responseCode = "200", description = "Набор характеристик права найден")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getById(@Parameter(description = "ID набора характеристик")
                @PathVariable id: Long): LicenseRtFeatureSetDto = service.getById(id)

    @GetMapping("/by-license-rt/{id}")
    @Operation(summary = "Получить список наборов характеристик прав по ID привязки права к лицензии")
    @RequiresPermission("LicenseRtFeatureSetController:GetAllLicenseRtFeatureSets", description = "Получить все наборы характеристик (с пагинацией)")
    @ApiResponse(responseCode = "200", description = "Список наборов характеристик прав получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findByLicenseRt(
        @Parameter(description = "ID права лицензии (idLicRights)")
        @PathVariable id: Long,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.ASC) @ParameterObject pageable: Pageable
    ): PagedModel<LicenseRtFeatureSetDto> {
        val page = service.findByLicenseRt(id, pageable)
        return PagedModel(page)
    }

    @GetMapping("/features-by-fs/{id}")
    @Operation(summary = "Получить список характеристик по ID набора характеристик")
    @RequiresPermission("LicenseRtFeatureSetController:GetAllLicenseRtFeatures", description = "Получить все характеристики набора характеристик")
    @ApiResponse(responseCode = "200", description = "Список характеристик получен")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun findFeaturesByFeatureSet(@Parameter(description = "ID набора характеристик (idFeatureSet)")
                                 @PathVariable id: Long): List<LicenseRtFeaturesDto> =
        licenseRtFeaturesService.findByFeatureSet(id)

    @PostMapping
    @Operation(summary = "Создать набор характеристик права для лицензии")
    @RequiresPermission("LicenseRtFeatureSetController:CreateLicenseRtFeatureSet", description = "Создать набор характеристик")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Набор характеристик права создан")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun create(@Valid @RequestBody req: LicenseRtFeatureSetCreateRequest): LicenseRtFeatureSetDto = service.create(req)

    @PostMapping(value = ["/features-by-fs"])
    @Operation(summary = "Добавить характеристику в набор характеристик")
    @RequiresPermission("LicenseRtFeatureSetController:CreateLicenseRtFeature", description = "Добавить характеристику в набор характеристик")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Характеристика добавлена")
    @ConflictResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun addFeatureToFeatureSet(@Valid @RequestBody req: LicenseRtFeaturesCreateRequest): LicenseRtFeaturesDto =
        licenseRtFeaturesService.create(req)

    @PostMapping(value = ["/features-by-fs-bulk"])
    @Operation(summary = "Добавить несколько характеристик в набор характеристик")
    @RequiresPermission("LicenseRtFeatureSetController:BulkCreateLicenseRtFeature", description = "Добавить несколько характеристик в набор характеристик")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Характеристики добавлены")
    @ConflictResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun addFeatureToFeatureSetBulk(@Valid @RequestBody req: LicenseRtFeaturesCreateBulkRequest): List<LicenseRtFeaturesDto> =
        licenseRtFeaturesService.createBulk(req)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить набор характеристик права по ID записи")
    @RequiresPermission("LicenseRtFeatureSetController:UpdateLicenseRtFeatureSet", description ="Изменить набор характеристик")
    @ApiResponse(responseCode = "200", description = "Набор характеристик права обновлён")
    @ConflictResponse
    @NotFoundResponse
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun update(
        @Parameter(description = "ID набора характеристик")
        @PathVariable id: Long,
        @Valid @RequestBody req: LicenseRtFeatureSetUpdateRequest
    ): LicenseRtFeatureSetDto =
        service.update(id, req)

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить набор характеристик права по ID записи")
    @RequiresPermission("LicenseRtFeatureSetController:DeleteLicenseRtFeatureSet", description ="Удалить набор характеристик")
    @ApiResponse(responseCode = "204", description = "Набор характеристик права удалён")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun delete(@Parameter(description = "ID набора характеристик")
               @PathVariable id: Long,
               @RequestParam(required = false, defaultValue = "false") useCascade: Boolean) {
        service.delete(id, useCascade)
    }

    @DeleteMapping("/features-by-fs/{id}")
    @Operation(summary = "Удалить характеристику из набора характеристик по заданному ID записи")
    @RequiresPermission("LicenseRtFeatureSetController:DeleteLicenseRtFeature", description ="Удалить характеристику из набора характеристик")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Характеристика удалена из набора характеристик")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteFeatureFromFeatureSet(@Parameter(description = "ID записи \"набор характеристик -> характеристика\"")
                                    @PathVariable id: Long) {
        licenseRtFeaturesService.delete(id)
    }

    @DeleteMapping("/features-by-fs-bulk")
    @Operation(summary = "Удалить несколько характеристик из набора характеристик по заданным в массиве ID записей")
    @RequiresPermission("LicenseRtFeatureSetController:BulkDeleteLicenseRtFeature", description ="Удалить несколько характеристик из набора характеристик")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Характеристики удалены из набора характеристик")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun deleteFeatureFromFeatureSetBulk(@RequestBody ids: List<Long>) {
        licenseRtFeaturesService.deleteBulk(ids)
    }

}