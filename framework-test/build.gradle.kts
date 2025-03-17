dependencies {
    api(project(":framework"))
    implementation("org.springframework.boot:spring-boot-starter-test")

    implementation(project(":framework-spring-boot-autoconfigure"))
    testImplementation(project(":framework-spring-boot-starter"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}
