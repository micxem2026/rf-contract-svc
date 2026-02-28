import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

apply(plugin = "org.springframework.boot")

repositories {
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }

    // Безопасное получение свойства
    val gitlabRegistryUrl = findProperty("gitlab.registry.url")?.toString()
    // Добавляем репозиторий только если URL задан и не пуст
    if (!gitlabRegistryUrl.isNullOrEmpty()) {
        maven {
            name = "GitLab"
            url = uri(gitlabRegistryUrl)

            credentials(HttpHeaderCredentials::class) {
                name = "Private-Token"
                value = findProperty("gitlab.registry.token")?.toString() ?: ""
            }

            authentication {
                create<HttpHeaderAuthentication>("header")
            }
            isAllowInsecureProtocol = project.findProperty("gitlab.allow.insecure") == "true"
        }
    }
}

description = "RightsFlow Contract Service"

/*kotlin {
    jvmToolchain(17)
}*/
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {

    implementation(platform("io.micrometer:micrometer-tracing-bom:${property("micrometerVersion")}"))

    // RightsFlow libs
    implementation("me.rightsflow:rf-common-lib:${property("rfCommonLibVersion")}")
    implementation(project(":rf-intersync-svc"))
    implementation(project(":rf-acl-svc"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    //implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // "Мост" между Micrometer (API метрик) и Prometheus (система мониторинга)
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Spring Cloud
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
    implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")
    //implementation("org.springframework.kafka:spring-kafka")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.10.3")

    // Structured Logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    // Micrometer Tracing
    implementation("io.micrometer:micrometer-tracing")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Swagger/OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springDocVersion")}")
    implementation("org.apache.commons:commons-lang3:3.18.0") {
        because("CVE-2025-48924 - Security fix")
    }

    // Avro
    //implementation("org.apache.avro:avro:1.11.4")
    //implementation("io.confluent:kafka-avro-serializer:8.0.0")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<BootRun>("bootRun") {
    systemProperties(System.getProperties().mapKeys { it.key.toString() })
    environment(System.getenv())
}

tasks.named<BootJar>("bootJar") {
    archiveBaseName.set("rf-contract-svc") // Задаем основное имя файла
    archiveVersion.set("")               // Убираем версию из имени файла
}
