package me.rightsflow.contracts.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "Запрос на обновление статуса контракта")
data class ContractStatusUpdateRequest(

    @field:Schema(description = "Код статуса", example = "DRAFT", allowableValues = ["DRAFT", "ARCHIVE", "APPROVED"])
    @field:Size(max = 20) val statusCode: String?,

    @field:Schema(description = "Код статуса 1C")
    @field:NotNull
    @field:Size(max = 255) var status1c: String

)