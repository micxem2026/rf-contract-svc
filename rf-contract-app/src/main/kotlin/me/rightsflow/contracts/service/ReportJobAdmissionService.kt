package me.rightsflow.contracts.service

import me.rightsflow.contracts.config.ReportProperties
import me.rightsflow.contracts.entity.ReportJob
import me.rightsflow.contracts.repository.ReportJobRepository
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

@Service
class ReportJobAdmissionService(
    private val jdbcTemplate: JdbcTemplate,
    private val jobRepository: ReportJobRepository,
    private val txTemplate: TransactionTemplate,
    private val reportProperties: ReportProperties
) {
    /**
     * Атомарно проверяет лимит одновременных генераций и создаёт задачу.
     * pg_advisory_xact_lock сериализует конкурентные вызовы этого метода
     * (в т.ч. с разных реплик сервиса), не давая гонке превысить лимит.
     */
    fun tryCreateJob(idOrg: Int, createdBy: String): ReportJob =
        txTemplate.execute {
            jdbcTemplate.execute("select pg_advisory_xact_lock(hashtext('report_job_concurrency'))")

            val activeCount = jdbcTemplate.queryForObject(
                "select count(*) from report_job where status in ('PENDING','RUNNING','CANCELLING')",
                Long::class.java
            ) ?: 0L

            if (activeCount >= reportProperties.maxConcurrentGenerations) {
                throw ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Достигнут лимит одновременно формируемых отчётов " +
                            "(${reportProperties.maxConcurrentGenerations}). Попробуйте позже."
                )
            }

            jobRepository.save(ReportJob(idOrg = idOrg, createdBy = createdBy))
        }!!
}