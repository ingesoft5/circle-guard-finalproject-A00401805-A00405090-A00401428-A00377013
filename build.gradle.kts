// CG-DOC: Root Gradle build configuration for all microservices.
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
}
