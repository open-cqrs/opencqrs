description = "Client SDK for the EventSourcingDB"

dependencies {
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("io.opentelemetry:opentelemetry-api")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    testImplementation(project(":esdb-client-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient:4.0.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
