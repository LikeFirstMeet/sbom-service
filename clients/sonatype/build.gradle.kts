val commonsIoVersion: String by project

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}