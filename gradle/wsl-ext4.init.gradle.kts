// CG-DOC: Local-only Gradle init script for WSL.
// Keeps compiled classes and test reports on ext4 instead of the slow /mnt/c (9p)
// mount, so the gateway integration tests stay under their 3-minute task timeout.
// Jenkins checks out on ext4 and does not need this script.
//
// Usage:
//   ./gradlew -I gradle/wsl-ext4.init.gradle.kts \
//     :services:circleguard-gateway-service:integrationTest
//
// Override the destination directory with CIRCGUARD_EXT4_BUILD.
val ext4Root = File(System.getenv("CIRCGUARD_EXT4_BUILD") ?: "/tmp/circleguard-build")

allprojects {
    layout.buildDirectory.set(File(ext4Root, project.path.replace(':', '/').trimStart('/')))
}
