import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

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

dependencies {

    implementation(platform("org.springdoc:springdoc-openapi-bom:${property("springDocVersion")}"))

    implementation("me.rightsflow:rf-common-lib:${property("rfCommonLibVersion")}")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:${property("springBootVersion")}")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:${property("springBootVersion")}")

    // Database
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.10.3")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Swagger/OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui")

    implementation("ch.qos.logback:logback-classic:1.5.19") {
        because("CVE-2025-11226 - Security fix")
    }

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
