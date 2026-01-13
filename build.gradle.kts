plugins {
    id("org.springframework.boot") version "3.5.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.spring") version "2.2.20" apply false
    kotlin("plugin.jpa") version "2.2.20" apply false
}

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {

    apply(plugin = "kotlin")
    apply(plugin = "kotlin-jpa")
    apply(plugin = "kotlin-spring")
    apply(plugin = "io.spring.dependency-management")

    group = "me.rightsflow"
    version = "0.0.1-SNAPSHOT"

    extra["springCloudVersion"] = "2025.0.0"
    extra["springBootVersion"] = "3.5.5"
    extra["springDocVersion"] = "2.8.9"
    extra["rfCommonLibVersion"] = "1.0.5"
    extra["micrometerVersion"] = "1.5.5"

}