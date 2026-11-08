// CG-DOC: Build configuration for the gateway service.
import java.time.Duration

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    jacoco
    `maven-publish`
}

// En Jenkins el TAG llega como variable de entorno; en local se mantiene SNAPSHOT.
version = System.getenv("TAG") ?: "1.0.0-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    useJUnitPlatform()
    // GW-CI-02: las pruebas de integracion viven en su propia tarea para no exigir Docker en cada build.
    exclude("**/integration/**")
    finalizedBy(tasks.jacocoTestReport)
}

// GW-CI-02: tarea dedicada para las pruebas contra un Redis real con Testcontainers.
val integrationTest by tasks.registering(Test::class) {
    description = "Ejecuta las pruebas de integracion del gateway contra un Redis real (Testcontainers)."
    group = "verification"
    useJUnitPlatform()
    include("**/integration/**")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    timeout.set(Duration.ofMinutes(3))
    outputs.upToDateWhen { false }
    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude("**/*Application*", "**/config/**") }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude("**/*Application*", "**/config/**") }
        })
    )
    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks.bootJar)
        }
    }
    repositories {
        maven {
            name = "nexus"
            val nexusBase = (System.getenv("NEXUS_URL") ?: "http://localhost:8081").trimEnd('/')
            val repo = if (version.toString().endsWith("SNAPSHOT")) "maven-snapshots" else "maven-releases"
            url = uri("${nexusBase}/repository/${repo}/")
            isAllowInsecureProtocol = true
            credentials {
                username = System.getenv("NEXUS_USER") ?: "jenkins"
                password = System.getenv("NEXUS_PASSWORD") ?: "change-me"
            }
        }
    }
}
