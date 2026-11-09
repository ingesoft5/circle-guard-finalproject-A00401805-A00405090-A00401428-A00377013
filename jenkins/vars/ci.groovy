// CG-DOC: Reusable CI steps for CircleGuard services (build, test, analysis, package, publish).
def checkoutRepo() {
    checkout scm
    sh 'rm -f sonar-links.txt image-digest.txt .ci-notification.md .ci-slack.json'
}

def build(Map service) {
    sh "./gradlew ${service.path}:classes --console=plain"
}

def unitTest(Map service) {
    if (service.jacoco) {
        sh "./gradlew ${service.path}:test ${service.path}:jacocoTestReport ${service.path}:jacocoTestCoverageVerification --console=plain"
    } else {
        echo "JaCoCo: ${service.short} no tiene cobertura configurada; se ejecutan solo las pruebas."
        sh "./gradlew ${service.path}:test --console=plain"
    }
}

def sonarScan(Map service) {
    if (!service.jacoco) {
        echo "SonarQube: ${service.short} no tiene JaCoCo; se omite el análisis con gate de cobertura."
        return
    }
    withSonarQubeEnv('SonarQube') {
        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
            sh """
                sonar-scanner \
                  -Dproject.settings=${service.sonarSettings} \
                  -Dsonar.host.url=${env.SONAR_HOST_URL} \
                  -Dsonar.token=\$SONAR_TOKEN \
                  -Dsonar.projectVersion=${env.TAG}
            """
        }
    }
    def toolsIp = env.TOOLS_IP ?: 'localhost'
    sh "echo '${service.display}: http://${toolsIp}:9000/dashboard?id=${service.projectKey}' >> sonar-links.txt"
}

def qualityGate(Map service) {
    if (!service.jacoco) {
        return
    }
    withSonarQubeEnv('SonarQube') {
        timeout(time: 5, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
        }
    }
    // Refuerzo: el webhook puede tardar; se valida el estado real del proyecto por API.
    withSonarQubeEnv('SonarQube') {
        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
            sh """
                curl -fsS -u "\$SONAR_TOKEN:" \
                  "${env.SONAR_HOST_URL}/api/qualitygates/project_status?projectKey=${service.projectKey}" \
                  | jq -e '.projectStatus.status == "OK"' >/dev/null
            """
        }
    }
}

def integrationTest(Map service) {
    if (!service.integration) {
        echo "Integración: ${service.short} no declara pruebas de integración."
        return
    }
    sh "./gradlew ${service.path}:integrationTest --console=plain"
}

def packageService(Map service) {
    sh "./gradlew ${service.path}:bootJar --console=plain"
}

def publish(Map service) {
    if (!service.publish) {
        echo "Publicación: ${service.short} no publica en Nexus."
        return
    }
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh """
            NEXUS_URL=${env.NEXUS_URL} \
            NEXUS_USER=\$NEXUS_USER \
            NEXUS_PASSWORD=\$NEXUS_PASSWORD \
            ./gradlew ${service.path}:publish --console=plain
        """
    }
}

def dockerBuildPush(Map service, String tag) {
    if (!service.docker) {
        return ''
    }
    def image = "${env.NEXUS_REGISTRY}/circleguard-docker/${service.short}:${tag}"
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${env.NEXUS_REGISTRY} -u \$NEXUS_USER --password-stdin"
        sh "docker build -t ${image} -f ${service.dir}/Dockerfile ."
        sh "docker push ${image}"
        digest = sh(script: "docker inspect --format='{{index .RepoDigests 0}}' ${image}", returnStdout: true).trim()
        if (!digest || digest == '<no value>') {
            digest = sh(script: "docker images --digests --format '{{.Digest}}' ${image} | head -1", returnStdout: true).trim()
        }
        writeFile file: 'image-digest.txt', text: "${image}\n${digest}\n"
    }
    return digest
}

def archive(List services) {
    services.each { service ->
        if (fileExists("${service.dir}/build/test-results/test")) {
            junit testResults: "${service.dir}/build/test-results/test/*.xml",
                    allowEmptyResults: true, keepLongStdio: true
        }
        if (service.integration && fileExists("${service.dir}/build/test-results/integrationTest")) {
            junit testResults: "${service.dir}/build/test-results/integrationTest/*.xml",
                    allowEmptyResults: true, keepLongStdio: true
        }
        if (service.jacoco && fileExists("${service.dir}/build/reports/jacoco/test/jacocoTestReport.xml")) {
            jacoco execPattern: "${service.dir}/build/jacoco/test.exec",
                    classPattern: "${service.dir}/build/classes/**",
                    sourcePattern: "${service.dir}/src/main/java,${service.dir}/src/main/kotlin",
                    exclusionPattern: "**/*Application*,**/config/**"
        }
    }
    archiveArtifacts artifacts: '**/build/reports/jacoco/test/html/**,**/build/reports/jacoco/test/jacocoTestReport.xml,**/build/test-results/**/*.xml,sonar-links.txt,image-digest.txt',
            allowEmptyArchive: true, fingerprint: true
}

def deploy(String environment, String tag) {
    def ip = env."${environment.toUpperCase()}_PRIVATE_IP"
    sshagent(['cg-envs-ssh']) {
        sh """
            ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ubuntu@${ip} \
              'cd /opt/circleguard && COMPOSE_ROOT=/opt/circleguard/deploy/compose TAG=${tag} ./deploy/scripts/deploy.sh ${environment} ${tag}'
        """
    }
}

def e2e() {
    sh """
        cd tests/e2e
        npm install
        npx playwright install --with-deps chromium
        GATEWAY_URL=http://${env.DEV_PRIVATE_IP}:8087 npx playwright test
    """
}
