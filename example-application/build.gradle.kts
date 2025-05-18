description = "OpenCQRS Library Example Application"

plugins {
    id("java")
    id("org.springframework.boot") version "3.4.5"
    id("com.google.cloud.tools.jib") version "3.4.5"
}

dependencies {
    implementation(project(":framework-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.integration:spring-integration-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("com.h2database:h2")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(project(":framework-test"))
    testImplementation("org.testcontainers:junit-jupiter")
}

jib {
    container {
        mainClass = "com.opencqrs.example.LibraryApplication"
    }
    from {
        image = when (System.getProperty("os.arch")) {
            "aarch64" -> "gcr.io/distroless/java21-debian12:latest-arm64"
            else -> "gcr.io/distroless/java21-debian12:latest-amd64"
        }
    }
    to {
        image = "opencqrs/example-application:latest"
    }
}