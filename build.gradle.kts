// CG-DOC: Root Gradle build configuration for all microservices.
import org.gradle.api.GradleException

plugins {
    id("org.springframework.boot") version "4.0.10" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false
}

allprojects {
    group = "com.circleguard"
    version = System.getenv("TAG") ?: "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:4.0.10"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:4.0.10"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        // Boot 4 defaults to Jackson 3; the codebase still uses Jackson 2 ObjectMapper.
        "implementation"("org.springframework.boot:spring-boot-jackson2")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.springframework.boot:spring-boot-starter-webmvc-test")
        "testImplementation"("org.springframework.boot:spring-boot-starter-actuator-test")
        "testRuntimeOnly"("com.h2database:h2")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.set(listOf("-Xjsr305=strict"))
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // lombok.config enables @lombok.Generated so JaCoCo stops counting the generated
    // equals/hashCode branches. Gradle does not treat it as a compiler input, so the
    // build cache would replay classes compiled before the file existed; declaring it
    // here invalidates the cache whenever the configuration changes.
    tasks.withType<JavaCompile> {
        inputs.file(rootProject.file("lombok.config"))
            .withPropertyName("lombokConfig")
            .optional()
    }

    // E1: a JaCoCo report built without a `test.exec` file would silently measure
    // nothing. Both the report and the coverage gate must fail when the unit suite
    // never produced execution data (e.g. after a clean with skipped tests).
    tasks.matching { it.name == "jacocoTestReport" || it.name == "jacocoTestCoverageVerification" }
        .configureEach {
            doFirst {
                val execFile = layout.buildDirectory.file("jacoco/test.exec").get().asFile
                if (!execFile.isFile) {
                    throw GradleException(
                        "Missing ${execFile}. The unit suite did not produce JaCoCo data; " +
                            "coverage cannot be verified. Run :${project.name}:test first."
                    )
                }
            }
        }

    // E2.1: every module with an integrationTest task must prove that the suite ran.
    // `disabledWithoutDocker = true` turns a missing Docker daemon into a skipped
    // test; the guard reads the JUnit XML so a skip (or an empty result set) fails
    // the task instead of being reported as a pass. Modules that already register
    // their own guard (promotion) keep it.
    afterEvaluate {
        val integration = tasks.findByName("integrationTest") as? Test ?: return@afterEvaluate
        val guardName = "verifyIntegrationTestsRan"
        val guard = tasks.findByName(guardName) ?: tasks.register(guardName) {
            group = "verification"
            description = "Fails when the integration suite produced no XML or skipped any test."
            doLast {
                val dir = layout.buildDirectory.dir("test-results/integrationTest").get().asFile
                val reports = dir.listFiles { file ->
                    file.isFile && file.name.startsWith("TEST-") && file.name.endsWith(".xml")
                } ?: emptyArray()
                if (reports.isEmpty()) {
                    throw GradleException("No integration-test XML under ${dir}; the suite did not run.")
                }
                val skipped = reports.mapNotNull { report ->
                    val suiteTag = Regex("""<testsuite[^>]*>""").find(report.readText())?.value ?: ""
                    val count = Regex("""\bskipped="(\d+)"""").find(suiteTag)?.groupValues?.get(1)?.toInt() ?: 0
                    if (count > 0) "${report.name.removePrefix("TEST-").removeSuffix(".xml")} ($count skipped)" else null
                }
                if (skipped.isNotEmpty()) {
                    throw GradleException(
                        "Integration tests were skipped: ${skipped.joinToString(", ")}. " +
                            "Docker is required; a skip is not a pass."
                    )
                }
            }
        }
        integration.finalizedBy(guard)
    }
}
