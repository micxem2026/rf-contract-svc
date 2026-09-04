package me.rightsflow.contracts.service

import me.rightsflow.contracts.dto.request.SoldRightsReportFilterRequest
import me.rightsflow.contracts.entity.ReportJob
import me.rightsflow.contracts.entity.ReportJobStatus
import me.rightsflow.contracts.repository.ReportJobRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.web.server.ResponseStatusException

@Service
class SoldRightsReportGenerationService(
    private val jdbcTemplate: JdbcTemplate,
    private val jobRepository: ReportJobRepository,
    private val txTemplate: TransactionTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cancelledSqlState = "57014" // query_canceled

    @Async("reportGenerationExecutor")
    fun generate(jobId: UUID, filter: SoldRightsReportFilterRequest, username: String?, bypass: Boolean) {
        // Атомарный переход PENDING -> RUNNING. Если задачу уже отменили,
        // пока она стояла в очереди executor'а, статус будет CANCELLED, и мы просто выходим.
        val started = txTemplate.execute {
            jdbcTemplate.update(
                "update report_job set status='RUNNING', started_at=now() where id=? and status='PENDING'",
                jobId
            ) > 0
        } ?: false

        if (!started) {
            log.info("Задача отчёта {} отменена до начала генерации, пропускаем", jobId)
            return
        }

        try {
            // ВАЖНО: сам вызов процедуры генерации намеренно НЕ оборачивается в txTemplate —
            // INSERT внутри неё атомарен сам по себе (один statement), и нам не нужна
            // Spring-транзакция поверх него: при отмене PG откатит именно этот statement.
            val rowCount = jdbcTemplate.execute(ConnectionCallback { con ->
                val pid = con.createStatement().use { st ->
                    st.executeQuery("select pg_backend_pid()").use { rs -> rs.next(); rs.getInt(1) }
                }
                jdbcTemplate.update("update report_job set backend_pid = ? where id = ?", pid, jobId)

                con.prepareCall(
                    "{? = call pkg_contract.generate_sold_rights_report(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}"
                ).use { stmt ->
                    stmt.registerOutParameter(1, Types.BIGINT)
                    stmt.setObject(2, jobId)
                    stmt.setInt(3, filter.idOrg!!)
                    stmt.setObject(4, filter.idCParty, Types.INTEGER)
                    stmt.setObject(5, filter.idOip, Types.INTEGER)
                    stmt.setObject(6, filter.idOipType, Types.INTEGER)
                    stmt.setObject(7, filter.idRightType, Types.INTEGER)
                    stmt.setObject(8, filter.begDate)
                    stmt.setObject(9, filter.endDate)
                    stmt.setArray(10, con.toSqlIntArrayOrNull(filter.idFeatureTerritory))
                    stmt.setArray(11, con.toSqlIntArrayOrNull(filter.idFeatureChannel))
                    stmt.setArray(12, con.toSqlIntArrayOrNull(filter.idFeatureSite))
                    stmt.setString(13, filter.createdBy)
                    stmt.setString(14, filter.managedBy)
                    stmt.setBoolean(15, filter.noDataFilter?:false)
                    stmt.setString(16, username)
                    stmt.setBoolean(17, bypass)
                    stmt.execute()
                    stmt.getLong(1)
                }
            })

            txTemplate.execute {
                jobRepository.findById(jobId).ifPresent { job ->
                    job.status = ReportJobStatus.COMPLETED
                    job.rowCount = rowCount
                    job.finishedAt = OffsetDateTime.now()
                    jobRepository.save(job)
                }
            }
        } catch (ex: Exception) {
            val cancelled = ex.sqlState() == cancelledSqlState
            if (cancelled) {
                log.info("Генерация отчёта {} отменена пользователем", jobId)
            } else {
                log.error("Ошибка формирования отчёта {}", jobId, ex)
            }
            txTemplate.execute {
                jobRepository.findById(jobId).ifPresent { job ->
                    job.status = if (cancelled) ReportJobStatus.CANCELLED else ReportJobStatus.FAILED
                    if (!cancelled) job.errorMessage = ex.message?.take(2000)
                    job.finishedAt = OffsetDateTime.now()
                    jobRepository.save(job)
                }
            }
        }
    }

    /** Запрашивает отмену задачи. Возвращает актуальное состояние задачи после запроса. */
    fun requestCancel(jobId: UUID): ReportJob {
        val job = jobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Задача отчёта [ID=$jobId] не найдена") }

        return when (job.status) {
            ReportJobStatus.COMPLETED, ReportJobStatus.FAILED, ReportJobStatus.CANCELLED ->
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Отчёт уже завершён (статус: ${job.status}), отмена невозможна"
                )

            ReportJobStatus.PENDING -> {
                // Ещё не запущен на этой (или любой другой) реплике —
                // просто помечаем отменённым, generate() увидит это при попытке стартовать.
                jdbcTemplate.update(
                    "update report_job set status='CANCELLED', finished_at=now() where id=? and status='PENDING'",
                    jobId
                )
                jobRepository.findById(jobId).orElseThrow()
            }

            ReportJobStatus.RUNNING, ReportJobStatus.CANCELLING -> {
                job.backendPid?.let { pid ->
                    // Шлём отмену напрямую в Postgres — работает независимо от того,
                    // какая реплика rf-contract-svc фактически выполняет генерацию.
                    jdbcTemplate.queryForObject("select pg_cancel_backend(?)", Boolean::class.java, pid)
                }
                job.status = ReportJobStatus.CANCELLING
                jobRepository.save(job)
            }
        }
    }

    private fun Throwable.sqlState(): String? {
        var t: Throwable? = this
        while (t != null) {
            if (t is SQLException) return t.sqlState
            t = t.cause
        }
        return null
    }

    private fun java.sql.Connection.toSqlIntArrayOrNull(values: List<Int>?): java.sql.Array? =
        values?.takeIf { it.isNotEmpty() }?.let { createArrayOf("integer", it.toTypedArray()) }
}