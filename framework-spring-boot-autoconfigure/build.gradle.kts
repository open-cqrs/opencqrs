description = "Spring Boot auto configurations for OpenCQRS framework"

dependencies {
    api(project(":framework"))
    compileOnly("jakarta.validation:jakarta.validation-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springframework.integration:spring-integration-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    testImplementation("org.springframework.integration:spring-integration-core")
    testRuntimeOnly("com.h2database:h2")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
