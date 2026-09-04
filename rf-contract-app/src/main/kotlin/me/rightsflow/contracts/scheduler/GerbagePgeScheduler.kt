package me.rightsflow.contracts.scheduler

import me.rightsflow.contracts.service.PgeService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GarbagePgeScheduler(
     private val pgeService: PgeService
) {
    private val log = LoggerFactory.getLogger(GarbagePgeScheduler::class.java)

    /**
     * Очистка мусора
     */
    @Scheduled(fixedRateString = "\${rightsflow.app.scheduler.cleanup.interval:14400000}") // каждые 4 часа
    fun cleanupOldSearchResults() {
        val stats = pgeService.garbagePgeData()
        if (stats > 0) log.info("Очистка завершена: удалено {} записей", stats)
    }
}