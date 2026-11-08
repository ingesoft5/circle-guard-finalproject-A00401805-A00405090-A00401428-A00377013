// CG-DOC: Build configuration for the promotion service.
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("jacoco")
}

// Spring Boot 3.2.4 manages Testcontainers 1.19.7, whose Docker client negotiates
// API 1.32 while Docker Engine 29 rejects anything below 1.40. The Boot BOM exposes
// this version as a property, so overriding it here keeps the whole Testcontainers
// stack (core, jdbc, database-commons, junit-jupiter, kafka) on a supported release.
extra["testcontainers.version"] = "1.21.4"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-neo4j")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:neo4j")
    testImplementation("org.testcontainers:kafka")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    useJUnitPlatform {
        // PromotionPerformanceTest asserts a 1 s wall-clock budget against a Neo4j
        // container with 10k nodes. That is a benchmark, not a unit test: it varies with
        // host load and fails intermittently at ~2.2 s. Run it with `performanceTest`.
        excludeTags("performance")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register<Test>("performanceTest") {
    group = "verification"
    description = "Runs the NFR-1 wall-clock benchmark against a real Neo4j container."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("performance")
    }
    shouldRunAfter(tasks.test)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                // 0.70, not 0.60: the container classes use
                // @Testcontainers(disabledWithoutDocker = true), so a run without Docker
                // skips them and still builds green at 65.8 % line coverage. This threshold
                // is above that skipped-run figure on purpose, so the gate fails unless the
                // container tests actually executed.
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// The coverage threshold is a proxy: if the unit suite ever covers more than 70 % on its
// own, a Docker-less run would pass while the container classes were skipped. This task
// checks the surefire results directly, so a skipped container class always fails `check`.
val containerBackedTestClasses = listOf(
    "com.circleguard.promotion.listener.KafkaPromotionIntegrationTest",
    "com.circleguard.promotion.service.AdministrativeCorrectionTest",
    "com.circleguard.promotion.service.HealthStatusReevaluationTest",
)

val verifyContainerTestsRan by tasks.registering {
    group = "verification"
    description = "Fails when a container-backed test class was skipped instead of executed."
    dependsOn(tasks.test)
    val resultsDir = layout.buildDirectory.dir("test-results/test")
    doLast {
        val problems = mutableListOf<String>()
        containerBackedTestClasses.forEach { className ->
            val report = resultsDir.get().file("TEST-$className.xml").asFile
            if (!report.isFile) {
                problems += "$className (no result file)"
                return@forEach
            }
            val suiteTag = Regex("""<testsuite[^>]*>""").find(report.readText())?.value ?: ""
            val skipped = Regex("""\bskipped="(\d+)"""")
                .find(suiteTag)?.groupValues?.get(1)?.toInt() ?: 0
            if (skipped > 0) problems += "$className ($skipped skipped)"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Container-backed tests did not execute: ${problems.joinToString(", ")}. " +
                    "Start Docker and run the suite again; these tests are not optional."
            )
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, verifyContainerTestsRan)
}
