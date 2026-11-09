plugins {
    groovy
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(localGroovy())
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    systemProperty("repository.root", rootDir.resolve("../..").canonicalPath)
}
