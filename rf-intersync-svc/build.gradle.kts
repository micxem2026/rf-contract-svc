import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

kotlin {
    jvmToolchain(17)
}

repositories {
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

dependencies {

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:${property("springBootVersion")}")

    // Spring Cloud
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
    implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")

    implementation("io.confluent:kafka-avro-serializer:8.0.0")
    implementation("org.apache.commons:commons-lang3:3.18.0") {
        because("CVE-2025-48924 - Security fix")
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