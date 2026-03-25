description = "OpenCQRS Java CQRS/ES Core Framework"

dependencies {
    api(project(":esdb-client"))
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("io.opentelemetry:opentelemetry-api")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    testImplementation(project(":framework-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:testcontainers-mockserver:2.0.4")
    testImplementation("org.mock-server:mockserver-client-java:5.15.0")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.springframework.boot:spring-boot-starter-opentelemetry")
}
