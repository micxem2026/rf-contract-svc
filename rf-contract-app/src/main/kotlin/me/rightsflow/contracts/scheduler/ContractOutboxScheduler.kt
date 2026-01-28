package me.rightsflow.contracts.scheduler

import me.rightsflow.contracts.service.ContractService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ContractOutboxScheduler(
    private val contractService: ContractService
) {
    private val log = LoggerFactory.getLogger(ContractOutboxScheduler::class.java)

    /**
     * Отправка события на пересчёт прав
     */
    @Scheduled(fixedRateString = "\${rightsflow.app.scheduler.outbox.interval:3600000}") // раз в час
    fun makeOutboxEventResults() {
        val stats = contractService.makeOutboxEvent()
        if (stats > 0) log.info("Отправлено на пересчёт: {} записей", stats)
    }
}