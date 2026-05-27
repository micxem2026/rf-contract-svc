package me.rightsflow.acl.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.acl.dto.ContractRequest
import me.rightsflow.acl.dto.LicenseOipRequest
import me.rightsflow.acl.dto.LicenseRequest
import me.rightsflow.acl.dto.LicenseRightsRequest
import me.rightsflow.acl.dto.LicenseRightsRtRequest
import me.rightsflow.acl.dto.LicenseRtFeatureSetRequest
import me.rightsflow.acl.dto.LicenseRtFeaturesRequest
import me.rightsflow.acl.service.AclSyncService
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/acl")
@Tag(name = "ACL", description = "Операции для работы acl-слоя")
class AclController(
    private val service: AclSyncService
) {

    @PostMapping("/syncContract")
    @Operation(summary = "Синхронизация таблицы контрактов")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncContract(@Valid @RequestBody req: ContractRequest): Long? = service.syncContract(req)

    @PostMapping("/compContract/{id}")
    @Operation(summary = "Компенсация (удаление) контракта")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Компенсация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun compContract(@PathVariable id: Long): Long? = service.compensateContract(id)

    @PostMapping("/syncLicense")
    @Operation(summary = "Синхронизация таблицы лицензий")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncLicense(@Valid @RequestBody req: LicenseRequest): Long? = service.syncLicense(req)

    @PostMapping("/syncLicenseOip")
    @Operation(summary = "Синхронизация таблицы ОИС лицензий")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncLicenseOip(@Valid @RequestBody req: LicenseOipRequest): Long? = service.syncLicenseOip(req)

    @PostMapping("/syncLicenseRights")
    @Operation(summary = "Синхронизация таблицы прав лицензий")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncLicenseRights(@Valid @RequestBody req: LicenseRightsRequest): Long? = service.syncLicenseRights(req)

    @PostMapping("/syncLicenseRightsRt")
    @Operation(summary = "Синхронизация таблицы способов использования прав лицензий")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncLicenseRightsRt(@Valid @RequestBody req: LicenseRightsRtRequest): Long? = service.syncLicenseRightsRt(req)

    @PostMapping("/syncLicenseRtFeatureSet")
    @Operation(summary = "Синхронизация таблицы наборов характеристик")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncLicenseRtFeatureSet(@Valid @RequestBody req: LicenseRtFeatureSetRequest): Long? = service.syncLicenseRtFeatureSet(req)

    @PostMapping("/syncLicenseRtFeatures")
    @Operation(summary = "Синхронизация таблицы характеристик")
    @PreAuthorize("hasRole('SERVICE')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncLicenseRtFeatures(@Valid @RequestBody req: LicenseRtFeaturesRequest): Long? = service.syncLicenseRtFeatures(req)
}