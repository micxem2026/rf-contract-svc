package me.rightsflow.contracts.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rightsflow.report")
class ReportProperties {
    /** Максимум одновременно выполняемых генераций отчётов (глобально, across все реплики сервиса) */
    var maxConcurrentGenerations: Int = 3
}