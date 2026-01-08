description = "Spring Boot auto configurations for OpenCQRS framework"

dependencies {
    api(project(":framework"))
    compileOnly("jakarta.validation:jakarta.validation-api")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springframework.integration:spring-integration-core")
    testImplementation(project(":framework-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testImplementation("org.springframework.integration:spring-integration-core")
    testImplementation("org.springframework.integration:spring-integration-jdbc")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testRuntimeOnly("com.h2database:h2")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
