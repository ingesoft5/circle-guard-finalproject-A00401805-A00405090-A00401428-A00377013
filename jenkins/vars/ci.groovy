// CG-DOC: Reusable CI steps for CircleGuard services (build, test, analysis, package, publish).
def checkoutRepo() {
    checkout scm
    sh 'rm -f sonar-links.txt image-digest.txt .ci-notification.md .ci-slack.json'
}

def build(Map service) {
    sh "./gradlew ${service.path}:classes --no-daemon --console=plain"
}

// `check` is the contract: each module decides what belongs in it (tests, coverage
// threshold, no-skip guard), so the pipeline does not need to know which tasks a service
// defines. Naming the tasks here skipped verifyUnitTestsRan, which meant Jenkins enforced
// the coverage number but never proved the unit suite actually ran.
def unitTest(Map service) {
    if (service.jacoco) {
        echo "JaCoCo: ${service.short} corre check (pruebas + umbral de cobertura)."
    } else {
        echo "JaCoCo: ${service.short} no tiene cobertura configurada; check ejecuta solo las pruebas."
    }
    sh "./gradlew ${service.path}:clean ${service.path}:check --no-daemon --console=plain"
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
    sh "./gradlew ${service.path}:integrationTest --no-daemon --no-build-cache --rerun-tasks --console=plain"
    // GW-CI-02: una corrida sin resultados debe fallar el build, no pasar en silencio.
    junit testResults: "${service.dir}/build/test-results/integrationTest/*.xml",
            allowEmptyResults: false, keepLongStdio: true
    archiveArtifacts artifacts: "${service.dir}/build/integration-test/*.log",
            allowEmptyArchive: true
}

def packageService(Map service) {
    sh "./gradlew ${service.path}:bootJar --no-daemon --console=plain"
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
            ./gradlew ${service.path}:publish --no-daemon --console=plain
        """
    }
}

def dockerBuildPush(Map service, String tag) {
    if (!service.docker) {
        return ''
    }
    def version = (tag ?: '').trim()
    if (!version) {
        error "No se puede publicar ${service.short}: la versión semántica está vacía."
    }
    def commit = env.GIT_COMMIT ?: 'unknown'
    def source = env.GITHUB_REPO_URL ?: 'unknown'
    def image = "${env.NEXUS_REGISTRY}/circleguard-docker/${service.short}:${version}"
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${env.NEXUS_REGISTRY} -u \$NEXUS_USER --password-stdin"
        sh """
            docker build \
              --build-arg VERSION=${version} \
              --build-arg GIT_COMMIT=${commit} \
              --build-arg SOURCE_URL=${source} \
              -t ${image} -f ${service.dir}/Dockerfile .
        """
        def uid = sh(script: "docker run --rm --entrypoint id ${image} -u", returnStdout: true).trim()
        if (uid == '0') {
            error "La imagen ${image} se ejecuta como root; se requiere un usuario no root."
        }
        if (service.healthcheck) {
            def healthcheck = sh(script: "docker inspect --format='{{json .Config.Healthcheck}}' ${image}", returnStdout: true).trim()
            if (!healthcheck || healthcheck == 'null') {
                error "La imagen ${image} no define HEALTHCHECK."
            }
        }
        sh "docker push ${image}"
        digest = sh(script: "docker inspect --format='{{index .RepoDigests 0}}' ${image}", returnStdout: true).trim()
        if (!digest || digest == '<no value>') {
            digest = sh(script: "docker images --digests --format '{{.Digest}}' ${image} | head -1", returnStdout: true).trim()
        }
        sh "printf '%s\n%s\ncommit=%s\n' '${image}' '${digest}' '${commit}' >> image-digest.txt"
    }
    return digest
}

// The Expo web client is not a Gradle module, so it uses its own Dockerfile and
// image name while sharing the same registry/tag as the Java services.
def dockerBuildPushClient(String tag) {
    def image = "${env.NEXUS_REGISTRY}/circleguard-docker/web:${tag}"
    def commit = env.GIT_COMMIT ?: 'unknown'
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${env.NEXUS_REGISTRY} -u \$NEXUS_USER --password-stdin"
        def source = env.GITHUB_REPO_URL ?: 'unknown'
        sh "docker build --build-arg VERSION=${tag} --build-arg GIT_COMMIT=${commit} --build-arg SOURCE_URL=${source} -t ${image} -f mobile/Dockerfile mobile"
        sh "docker push ${image}"
        digest = sh(script: "docker inspect --format='{{index .RepoDigests 0}}' ${image}", returnStdout: true).trim()
        if (!digest || digest == '<no value>') {
            digest = sh(script: "docker images --digests --format '{{.Digest}}' ${image} | head -1", returnStdout: true).trim()
        }
        sh "printf '%s\n%s\ncommit=%s\n' '${image}' '${digest}' '${commit}' >> image-digest.txt"
    }
    return digest
}

def archive(List services) {
    services.each { service ->
        if (fileExists("${service.dir}/build/test-results/test")) {
            junit testResults: "${service.dir}/build/test-results/test/*.xml",
                    allowEmptyResults: true, keepLongStdio: true
        }
        if (service.jacoco && fileExists("${service.dir}/build/reports/jacoco/test/jacocoTestReport.xml")) {
            jacoco execPattern: "${service.dir}/build/jacoco/test.exec",
                    classPattern: "${service.dir}/build/classes/**",
                    sourcePattern: "${service.dir}/src/main/java,${service.dir}/src/main/kotlin",
                    exclusionPattern: "**/*Application*,**/config/**"
        }
    }
    archiveArtifacts artifacts: '**/build/reports/jacoco/test/html/**,**/build/reports/jacoco/test/jacocoTestReport.xml,**/build/test-results/**/*.xml,**/build/integration-test/*.log,sonar-links.txt,image-digest.txt',
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
    // Cada corrida siembra sus propios visitantes; un id fijo reutilizaría el
    // estado que dejó la corrida anterior.
    def runId = "e2e-${env.BUILD_NUMBER}-${(env.GIT_COMMIT ?: 'local').take(7)}"
    sshagent(['cg-envs-ssh']) {
        sh """
            ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ubuntu@${env.DEV_PRIVATE_IP} \
              'cd /opt/circleguard && COMPOSE_ROOT=/opt/circleguard/deploy/compose \
                 ./deploy/scripts/seed-visitor.sh dev ${runId}-symptoms ${runId}-control'
        """
    }
    sh """
        cd tests/e2e
        npm install
        npx playwright install --with-deps chromium
        WEB_URL=http://${env.DEV_PRIVATE_IP}:8081 \
        GATEWAY_URL=http://${env.DEV_PRIVATE_IP}:8087 \
        PROMOTION_URL=http://${env.DEV_PRIVATE_IP}:8088 \
        KAFKA_BROKERS=${env.DEV_PRIVATE_IP}:29092 \
        E2E_ANONYMOUS_ID=${runId} \
        npx playwright test
    """
}
