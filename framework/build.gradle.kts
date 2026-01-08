description = "OpenCQRS Java CQRS/ES Core Framework"

dependencies {
    api(project(":esdb-client"))
    compileOnly("jakarta.validation:jakarta.validation-api")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    testImplementation(project(":framework-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.awaitility:awaitility:4.3.0")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
