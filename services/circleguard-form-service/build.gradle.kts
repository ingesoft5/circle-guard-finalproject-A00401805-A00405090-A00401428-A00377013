// CG-DOC: Build configuration for the form service.
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    jacoco
    `maven-publish`
}

// Alinea los módulos y el núcleo de Testcontainers en el classpath de pruebas.
extra["testcontainers.version"] = "1.21.4"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":services:circleguard-promotion-service")) {  //SOLO para la prueba de integración FORM -> PROMOTION
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-security")
        // Usa las clases reales sin incluir migraciones ni application.yml de Promotion.
        attributes {
            attribute(
                org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(org.gradle.api.attributes.LibraryElements.CLASSES)
            )
        }
    }
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:kafka:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// Configuración JaCoCo
tasks.jacocoTestReport {
    // La tarea test se ejecuta antes y deja disponible la información de cobertura.
    dependsOn(tasks.test)
    reports {
        xml.required = true
        csv.required = false
        html.required = true
        html.outputLocation = layout.buildDirectory.dir("reports/jacoco/test/html")
    }
}

tasks.jacocoTestCoverageVerification {
    // Verifica la cobertura generada por las pruebas del módulo y deja el reporte disponible.
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {

            element = "BUNDLE"
            
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
val integrationTest by tasks.registering(Test::class) {
    description = "Runs the ${project.name} integration tests."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
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
