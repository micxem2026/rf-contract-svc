package me.rightsflow.contracts.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.rightsflow.common.config.*
import me.rightsflow.common.permission.annotation.RequiresPermission
import me.rightsflow.contracts.dto.request.SoldRightsReportFilterRequest
import me.rightsflow.contracts.dto.response.ReportJobDto
import me.rightsflow.contracts.dto.response.SoldRightsReportPage
import me.rightsflow.contracts.entity.ReportJob
import me.rightsflow.contracts.entity.ReportJobStatus
import me.rightsflow.contracts.repository.ReportJobRepository
import me.rightsflow.contracts.service.ReportJobAdmissionService
import me.rightsflow.contracts.service.SoldRightsReportGenerationService
import me.rightsflow.contracts.service.SoldRightsReportQueryService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/reports/sold-rights")
@Tag(name = "Отчёт по проданным правам")
class SoldRightsReportController(
    private val jobRepository: ReportJobRepository,
    private val generationService: SoldRightsReportGenerationService,
    private val queryService: SoldRightsReportQueryService,
    private val subProvider: SecuritySubjectProvider,
    private val admissionService: ReportJobAdmissionService
) {

    @PostMapping
    @Operation(summary = "Запустить формирование отчёта по проданным правам")
    @RequiresPermission("SoldRightsReportController:CreateReport", description = "Формирование отчёта по проданным правам")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ValidationErrorResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun createReport(@Valid @RequestBody req: SoldRightsReportFilterRequest): ReportJobDto {
        val roles = subProvider.currentRoles()
        val bypass = roles.any { it in listOf("ADMIN", "SERVICE") }
        val username = subProvider.currentSub()

        // Бросит 429 Too Many Requests, если лимит одновременных генераций исчерпан
        val job = admissionService.tryCreateJob(req.idOrg!!, username)
        generationService.generate(job.id, req, if (bypass) null else username, bypass)
        return job.toDto()
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Принудительно отменить формирование отчёта")
    @RequiresPermission("SoldRightsReportController:CancelReport", description = "Отмена формирования отчёта")
    @NotFoundResponse
    @ConflictResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun cancel(@PathVariable jobId: UUID): ReportJobDto =
        generationService.requestCancel(jobId).toDto()

    @GetMapping("/{jobId}")
    @Operation(summary = "Получить статус задачи формирования отчёта")
    @RequiresPermission("SoldRightsReportController:GetReportStatus", description = "Статус задачи отчёта")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getStatus(@PathVariable jobId: UUID): ReportJobDto =
        jobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Задача отчёта [ID=$jobId] не найдена") }
            .toDto()

    @GetMapping("/{jobId}/page")
    @Operation(summary = "Получить очередную страницу отчёта (keyset-пагинация)")
    @RequiresPermission("SoldRightsReportController:GetReportPage", description = "Чтение страницы отчёта")
    @NotFoundResponse
    @CommonSecurityResponses
    @InternalServerErrorResponse
    fun getPage(
        @PathVariable jobId: UUID,
        @Parameter(description = "Курсор, полученный из предыдущего ответа (пусто — первая страница)")
        @RequestParam(required = false) cursor: String?,
        @Parameter(description = "Размер страницы (макс. 2000)")
        @RequestParam(defaultValue = "10") size: Int
    ): SoldRightsReportPage {
        val job = jobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Задача отчёта [ID=$jobId] не найдена") }

        check(job.status == ReportJobStatus.COMPLETED) {
            "Отчёт ещё не готов (статус: ${job.status})"
        }
        return queryService.getPage(jobId, cursor, size)
    }

    private fun ReportJob.toDto() = ReportJobDto(
        jobId = id, status = status, rowCount = rowCount, errorMessage = errorMessage,
        createdAt = createdAt, finishedAt = finishedAt
    )
}