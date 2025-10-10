
description = "Spring Boot auto configurations for the ESDB client SDK"

dependencies {
    api(project(":esdb-client"))
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("io.opentelemetry:opentelemetry-api")
    // https://github.com/gradle/gradle/issues/33950
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
