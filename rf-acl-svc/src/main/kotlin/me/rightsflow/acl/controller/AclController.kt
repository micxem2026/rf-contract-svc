package me.rightsflow.acl.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.acl.dto.ContractRequest
import me.rightsflow.acl.service.AclSyncService
import me.rightsflow.common.config.CommonSecurityResponses
import me.rightsflow.common.config.InternalServerErrorResponse
import org.springframework.security.access.prepost.PreAuthorize
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
    @PreAuthorize("hasAnyAuthority('SCOPE_admin')")
    @ApiResponse(responseCode = "200", description = "Синхронизация выполнена")
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun syncContract(@Valid @RequestBody req: ContractRequest): Long? = service.syncContract(req)
}