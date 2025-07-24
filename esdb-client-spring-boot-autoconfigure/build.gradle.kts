
import org.springframework.boot.gradle.plugin.SpringBootPlugin

description = "Spring Boot auto configurations for the ESDB client SDK"

plugins {
    id("org.springframework.boot") version "3.5.3"
}

dependencies {
    api(project(":esdb-client"))
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")

    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.17.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
