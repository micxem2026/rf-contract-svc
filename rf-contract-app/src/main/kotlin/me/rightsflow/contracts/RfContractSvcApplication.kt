package me.rightsflow.contracts

import me.rightsflow.intersync.service.BindingControlService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.web.config.EnableSpringDataWebSupport
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@SpringBootApplication(
    scanBasePackages = [
        "me.rightsflow.contracts", // нужен чтобы находился SecurityConfig
        "me.rightsflow.common",
        "me.rightsflow.acl",
        "me.rightsflow.intersync"
    ]
)
@EnableJpaRepositories(
    basePackages = [
        "me.rightsflow.contracts.repository",
        "me.rightsflow.intersync.repository"
    ]
)
@EntityScan(
    basePackages = [
        "me.rightsflow.contracts.entity",
        "me.rightsflow.intersync.entity"
    ]
)
@EnableDiscoveryClient
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
class RfContractSvcApplication : CommandLineRunner {

    @Autowired
    private lateinit var bindingControlService: BindingControlService

    override fun run(vararg args: String?) {
        bindingControlService.updateBindings()
    }

}

fun main(args: Array<String>) {
    runApplication<RfContractSvcApplication>(*args)
}

internal fun Instant.toOffsetDateTime(zone : ZoneId): OffsetDateTime =
    this.atZone(zone).toOffsetDateTime()


