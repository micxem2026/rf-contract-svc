package me.rightsflow.contracts.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableConfigurationProperties(ReportProperties::class)
class ReportAsyncConfig(private val reportProperties: ReportProperties) {

    // Отдельный пул — чтобы генерация тяжёлых отчётов не конкурировала
    // с обычными HTTP-запросами за общий @Async executor приложения
    @Bean("reportGenerationExecutor")
    fun reportGenerationExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = reportProperties.maxConcurrentGenerations
            maxPoolSize = reportProperties.maxConcurrentGenerations
            queueCapacity = reportProperties.maxConcurrentGenerations
            setThreadNamePrefix("report-gen-")
            initialize()
        }
}