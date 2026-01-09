description = "OpenCQRS Library Example Application"

plugins {
    id("java")
    id("org.springframework.boot") version "4.0.1"
    id("com.google.cloud.tools.jib") version "3.5.2"
}

dependencies {
    implementation(project(":framework-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.integration:spring-integration-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("com.h2database:h2")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":framework-test"))
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.slf4j:jul-to-slf4j")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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