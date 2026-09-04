package me.rightsflow.contracts.scheduler

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReportCleanupTask(private val jdbcTemplate: JdbcTemplate) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Каждый час удаляем задачи старше 48 часов; report_sold_rights_row
    // удалится каскадом по FK job_id -> report_job.id
    @Scheduled(cron = "0 0 * * * *")
    fun cleanupOldReports() {
        val deleted = jdbcTemplate.update(
            "delete from report_job where created_at < now() - interval '48 hours'"
        )
        if (deleted > 0) log.info("Удалено устаревших задач отчёта: {}", deleted)
    }
}