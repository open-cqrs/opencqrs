description = "OpenCQRS Java CQRS/ES Core Framework"

dependencies {
    api(project(":esdb-client"))
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("io.opentelemetry:opentelemetry-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    testImplementation(project(":framework-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
