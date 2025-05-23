description = "OpenCQRS Framework Test Support"

dependencies {
    api(project(":framework"))
    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("org.testcontainers:testcontainers")

    implementation(project(":framework-spring-boot-autoconfigure"))
    testImplementation(project(":framework-spring-boot-starter"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.testcontainers:junit-jupiter")
}
