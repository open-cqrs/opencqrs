dependencies {
    compileOnly("jakarta.validation:jakarta.validation-api")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation(project(":esdb-client-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.2.2")
}
