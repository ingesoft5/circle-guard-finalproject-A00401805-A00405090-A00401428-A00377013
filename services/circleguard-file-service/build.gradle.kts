// CG-DOC: Build configuration for the file service.
import java.time.Duration

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    jacoco
    `maven-publish`
}

apply(from = "$rootDir/gradle/jacoco-convention.gradle")

jacoco {
    toolVersion = "0.8.12"
}

dependencies {
    // Spring Boot BOM is applied centrally in the root build.

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Exposes /actuator/prometheus so Prometheus can scrape file-service metrics.
    implementation("io.micrometer:micrometer-registry-prometheus")
    // Bridges Spring observations to OpenTelemetry and exports traces through OTLP.
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4 split TestRestTemplate into optional modules; the integration
    // test injects the auto-configured template, so both must be on the test classpath.
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    // HU-FILE-04: the S3 client backs the real file-storage adapter; Testcontainers only supplies
    // the ephemeral MinIO server used by the integration tests.
    implementation("software.amazon.awssdk:s3:2.29.52")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
    finalizedBy(tasks.jacocoTestReport)
}

// Configuración JaCoCo
tasks.jacocoTestReport {
    // La tarea test se ejecuta antes y deja disponible la información de cobertura.
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude("**/*Application*") }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    // Verifica la cobertura generada por las pruebas del módulo.
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude("**/*Application*") }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

// ./gradlew check falle si la cobertura de líneas es menor al 60 %.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Tarea dedicada a las pruebas de integración (etiquetadas con @Tag("integration")).
// HU-FILE-04: same contract as the gateway integration task (always rerun).
// The container now tolerates a 3-minute cold start, so the task gets an 8-minute cap:
// enough for a slow MinIO boot plus the suite, still below Jenkins' 12-minute cap.
val integrationTest by tasks.registering(Test::class) {
    description = "Runs the ${project.name} integration tests against an ephemeral MinIO container."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
    timeout.set(Duration.ofMinutes(8))
    outputs.upToDateWhen { false }
    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }
}

// Publicación del artefacto en Nexus (snapshots o releases según la versión/TAG).
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