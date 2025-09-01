import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

apply(plugin = "org.springframework.boot")

repositories {
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
    maven {
        name = "GitLab"
        url = uri(findProperty("gitlab.registry.url") ?: "")

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

description = "RightsFlow Contract Service"

kotlin {
    jvmToolchain(17)
}

dependencies {

    // RightsFlow libs
    implementation("me.rightsflow:rf-common-lib:1.0.2")
    implementation(project(":rf-intersync-svc"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    //implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

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

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Swagger/OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
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
    archiveBaseName.set("rf-catalog-svc") // Задаем основное имя файла
    archiveVersion.set("")               // Убираем версию из имени файла
}
