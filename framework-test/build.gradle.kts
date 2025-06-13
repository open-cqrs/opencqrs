description = "OpenCQRS Framework Test Support"

dependencies {
    api(project(":framework"))
    api(project(":esdb-client"))
    implementation(project(":framework-spring-boot-autoconfigure"))
    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("org.testcontainers:testcontainers")

    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation(project(":esdb-client-spring-boot-starter"))
    testImplementation(project(":framework-spring-boot-starter"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.testcontainers:junit-jupiter")
}
