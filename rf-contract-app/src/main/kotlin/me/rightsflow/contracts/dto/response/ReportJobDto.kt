package me.rightsflow.contracts.dto.response

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import me.rightsflow.contracts.entity.ReportJobStatus
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Статус задачи формирования отчёта")
data class ReportJobDto(
    val jobId: UUID,
    val status: ReportJobStatus,
    val rowCount: Long?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime,
    val finishedAt: OffsetDateTime?
)

@Schema(description = "Страница отчёта по проданным правам")
data class SoldRightsReportPage(
    @field:Schema(description = "Строки отчёта (по одной на контракт)") val content: List<JsonNode>,
    @field:Schema(description = "Есть ли ещё страницы") val hasMore: Boolean,
    @field:Schema(description = "Курсор для следующего запроса (null, если данные закончились)") val nextCursor: String?,
    @field:Schema(description = "Суммарное количество прочитанных записей, включая эту страницу") val readCount: Long
)