// CG-DOC: Reusable CI steps for CircleGuard services (build, test, analysis, package, publish).
def checkoutRepo() {
    checkout scm
    sh 'rm -f sonar-links.txt image-digest.txt .ci-notification.md .ci-slack.json'
}

// The Docker daemon resolves registry names, not this Jenkins container. The local
// Nexus is plain HTTP on port 5000 and Docker only trusts loopback names by default;
// logging in with the host IP makes it negotiate HTTPS and fail with
// "server gave HTTP response to HTTPS client". Keep NEXUS_REGISTRY for Kubernetes
// manifests and evidence, and use this loopback alias only for daemon operations.
def dockerRegistryForDaemon() {
    def configured = (env.DOCKER_REGISTRY ?: '').trim()
    if (configured) return configured
    def registry = (env.NEXUS_REGISTRY ?: '').trim()
    if (!registry) return registry
    def separator = registry.lastIndexOf(':')
    if (separator <= 0 || registry.indexOf(']') > separator) return registry
    def host = registry.substring(0, separator)
    def port = registry.substring(separator + 1)
    if (host == 'localhost' || host == '127.0.0.1' || host == '[::1]') return registry
    def privateIp = (env.TOOLS_PRIVATE_IP ?: '').trim()
    def publicIp = (env.TOOLS_IP ?: '').trim()
    if (host == privateIp || host == publicIp) {
        return "127.0.0.1:${port}"
    }
    return registry
}

def build(Map service) {
    if (service.type == 'node') {
        echo "Build: ${service.short} is a Node component; Gradle compilation does not apply."
        return
    }
    sh "./gradlew ${service.path}:classes --no-daemon --console=plain"
}

// `check` is the contract: each module decides what belongs in it (tests, coverage
// threshold, no-skip guard), so the pipeline does not need to know which tasks a service
// defines. Naming the tasks here skipped verifyUnitTestsRan, which meant Jenkins enforced
// the coverage number but never proved the unit suite actually ran.
def unitTest(Map service) {
    if (service.type == 'node') {
        echo "Unit tests: ${service.short} runs in the Mobile tests + coverage stage."
        return
    }
    if (service.jacoco) {
        echo "JaCoCo: ${service.short} corre check (pruebas + umbral de cobertura)."
    } else {
        echo "JaCoCo: ${service.short} no tiene cobertura configurada; check ejecuta solo las pruebas."
    }
    sh "./gradlew ${service.path}:clean ${service.path}:check --no-daemon --console=plain"
}

// The mobile client is not a Gradle module. Jest produces the LCOV report that
// SonarQube consumes, and the global coverageThreshold enforces >= 60 %.
def mobileTest(Map service) {
    if (service.type != 'node') {
        echo "Mobile tests: ${service.short} is not a Node component."
        return
    }
    dir('mobile') {
        sh 'npm ci --no-audit --no-fund'
        sh 'npm run test:coverage'
    }
}

def sonarScan(Map service) {
    if (!service.jacoco) {
        echo "SonarQube: ${service.short} no tiene cobertura configurada; se omite el análisis con gate de cobertura."
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
    if (service.type == 'node') {
        echo "Integración: ${service.short} is a Node component; Gradle integrationTest does not apply."
        return
    }
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
    if (service.type == 'node') {
        echo "Package: ${service.short} is packaged by the Docker client stage."
        return
    }
    sh "./gradlew ${service.path}:bootJar --no-daemon --console=plain"
}

def publish(Map service) {
    if (service.type == 'node') {
        echo "Publicación: ${service.short} does not publish Gradle artifacts."
        return
    }
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
    if (service.type == 'node') {
        echo "Docker: ${service.short} uses the dedicated Docker client stage."
        return ''
    }
    if (!service.docker) {
        return ''
    }
    def version = (tag ?: '').trim()
    if (!version) {
        error "No se puede publicar ${service.short}: la versión semántica está vacía."
    }
    def commit = env.GIT_COMMIT ?: 'unknown'
    def source = env.GITHUB_REPO_URL ?: 'unknown'
    def registry = dockerRegistryForDaemon()
    def image = "${registry}/circleguard-docker/${service.short}:${version}"
    def publicImage = "${env.NEXUS_REGISTRY}/circleguard-docker/${service.short}:${version}"
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${registry} -u \$NEXUS_USER --password-stdin"
        // El host no tiene ruta IPv6 al CDN de Docker Hub: las imágenes base se
        // toman del mirror circleguard-base en Nexus (ver seed-base-images.sh).
        def base = registry ? "${registry}/circleguard-base" : ''
        def baseArgs = base ? "--build-arg GRADLE_IMAGE=${base}/gradle:8.14-jdk21 --build-arg JDK_IMAGE=${base}/eclipse-temurin:21-jdk --build-arg ALPINE_IMAGE=${base}/eclipse-temurin:21-jre-alpine --build-arg JAMMY_IMAGE=${base}/eclipse-temurin:21-jre-jammy --build-arg NODE_IMAGE=${base}/node:22-alpine --build-arg WEB_IMAGE=${base}/nginx-unprivileged:1.27-alpine" : ''
        sh """
            docker build ${baseArgs} \
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
        sh "printf '%s\n%s\ncommit=%s\n' '${publicImage}' '${digest}' '${commit}' >> image-digest.txt"
    }
    return digest
}

// The Expo web client is not a Gradle module, so it uses its own Dockerfile and
// image name while sharing the same registry/tag as the Java services.
def dockerBuildPushClient(String tag) {
    def registry = dockerRegistryForDaemon()
    def image = "${registry}/circleguard-docker/web:${tag}"
    def publicImage = "${env.NEXUS_REGISTRY}/circleguard-docker/web:${tag}"
    def commit = env.GIT_COMMIT ?: 'unknown'
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${registry} -u \$NEXUS_USER --password-stdin"
        def source = env.GITHUB_REPO_URL ?: 'unknown'
        sh "docker build --build-arg VERSION=${tag} --build-arg GIT_COMMIT=${commit} --build-arg SOURCE_URL=${source} -t ${image} -f mobile/Dockerfile mobile"
        sh "docker push ${image}"
        digest = sh(script: "docker inspect --format='{{index .RepoDigests 0}}' ${image}", returnStdout: true).trim()
        if (!digest || digest == '<no value>') {
            digest = sh(script: "docker images --digests --format '{{.Digest}}' ${image} | head -1", returnStdout: true).trim()
        }
        sh "printf '%s\n%s\ncommit=%s\n' '${publicImage}' '${digest}' '${commit}' >> image-digest.txt"
    }
    return digest
}

def archive(List services) {
    services.each { service ->
        if (service.type == 'node') {
            // Jest/Istanbul reports live outside build/ and use LCOV, not JaCoCo.
            return
        }
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
    archiveArtifacts artifacts: '**/build/reports/jacoco/test/html/**,**/build/reports/jacoco/test/jacocoTestReport.xml,**/build/test-results/**/*.xml,**/build/integration-test/*.log,mobile/coverage/lcov.info,mobile/coverage/**,build/reports/security/**,sonar-links.txt,image-digest.txt',
            allowEmptyArchive: true, fingerprint: true
}

def ensureTrivyBinary() {
    sh '''
        set -eu
        if ! command -v trivy >/dev/null 2>&1 && [ ! -x "${WORKSPACE}/.trivy-bin/trivy" ]; then
            cid=$(docker create aquasec/trivy:0.57.1)
            mkdir -p "${WORKSPACE}/.trivy-bin"
            docker cp "$cid:/usr/local/bin/trivy" "${WORKSPACE}/.trivy-bin/trivy"
            chmod 0755 "${WORKSPACE}/.trivy-bin/trivy"
            cp "${WORKSPACE}/.trivy-bin/trivy" /usr/local/bin/trivy 2>/dev/null || true
            docker rm -f "$cid" >/dev/null 2>&1 || true
        fi
    '''
}

def trivyScanIaC() {
    ensureTrivyBinary()
    sh '''
        set -eu
        export PATH="/usr/local/bin:${WORKSPACE}/.trivy-bin:${PATH}"
        mkdir -p build/reports/security
        trivy config --severity HIGH,CRITICAL --format json --output build/reports/security/trivy-iac.json deploy/k8s/
        trivy config --severity CRITICAL --exit-code 1 deploy/k8s/ | tee build/reports/security/trivy-iac.txt
    '''
}

def trivyScanImage(Map service, String tag) {
    if (!service.docker) {
        return
    }
    ensureTrivyBinary()
    def image = "${dockerRegistryForDaemon()}/circleguard-docker/${service.short}:${tag}"
    sh """
        set -eu
        export PATH="/usr/local/bin:\${WORKSPACE}/.trivy-bin:\${PATH}"
        mkdir -p build/reports/security
        trivy image --severity HIGH,CRITICAL --ignore-unfixed --format json --output build/reports/security/trivy-${service.short}-${tag}.json "${image}"
        trivy image --severity CRITICAL --ignore-unfixed --exit-code 1 "${image}" | tee build/reports/security/trivy-${service.short}-${tag}.txt
    """
}

def owaspZapScan(String environment) {
    def namespace = "cg-${environment}"
    def zapPod = "zap-gate-${env.BUILD_NUMBER ?: 'local'}"
    sh """
        set -eu
        mkdir -p build/reports/security
        kubectl -n ${namespace} delete pod ${zapPod} --ignore-not-found >/dev/null 2>&1 || true
        trap 'kubectl -n ${namespace} delete pod ${zapPod} --ignore-not-found >/dev/null 2>&1 || true' EXIT
        kubectl -n ${namespace} wait --for=condition=Available deployment/auth deployment/gateway --timeout=180s
        kubectl -n ${namespace} run ${zapPod} \\
          --image=ghcr.io/zaproxy/zaproxy:stable \\
          --restart=Never \\
          --labels="ci-security-gate=true" \\
          --command -- zap-baseline.py -t http://auth:8180/actuator/health -m 1
        kubectl -n ${namespace} wait "pod/${zapPod}" --for=jsonpath='{.status.phase}'=Succeeded --timeout=300s
        kubectl -n ${namespace} logs ${zapPod} | tee build/reports/security/zap-${environment}.txt
        if grep -qE '^FAIL-NEW:[[:space:]]*[1-9]' build/reports/security/zap-${environment}.txt; then
            echo "OWASP ZAP detectó vulnerabilidades bloqueantes en ${namespace}." >&2
            exit 1
        fi
    """
}

// Despliega sobre k3d creando un tag del overlay en el workspace y aplicando con
// Kustomize; no se commitea nada. Las imágenes se leen del mismo registro HTTP del
// host donde el pipeline hace push, usando el alias loopback que acepta el daemon,
// y k3d las importa a los nodos porque `IfNotPresent` no llega a un registro
// con TLS propio del clúster.
def deploy(String environment, String tag) {
    def namespace = "cg-${environment}"
    def overlay = "deploy/k8s/overlays/${environment}"
    def registry = dockerRegistryForDaemon()
    def services = ['auth', 'identity', 'promotion', 'form', 'gateway', 'dashboard', 'file', 'notification', 'web']
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${registry} -u \$NEXUS_USER --password-stdin"
    }
    sh """
        set -e
        for svc in ${services.join(' ')}; do
            docker pull ${registry}/circleguard-docker/\${svc}:${tag}
            docker tag ${registry}/circleguard-docker/\${svc}:${tag} circleguard-docker/\${svc}:${tag}
            k3d image import -c cg circleguard-docker/\${svc}:${tag}
        done
        cd "${overlay}"
        for svc in ${services.join(' ')}; do
            kustomize edit set image "circleguard-docker/\${svc}=circleguard-docker/\${svc}:${tag}"
        done
        kubectl --namespace ${namespace} apply -k .
        cd "${WORKSPACE}"
        for svc in ${services.join(' ')}; do
            kubectl --namespace ${namespace} rollout status "deployment/\${svc}" --timeout=180s
        done
    """
}

// Cada corrida siembra sus propios visitantes vía kubectl exec (sin SSH ni IPs del
// mundo AWS); un id fijo reutilizaría el estado que dejó la corrida anterior.
def e2e() {
    // Seed Neo4j y ejecuta Playwright contra el Ingress de k3d (Traefik :30080).
    def runId = "e2e-${env.BUILD_NUMBER}-${(env.GIT_COMMIT ?: 'local').take(7)}"
    sh """
        set -e
        NEO4J_PASSWORD=\$(kubectl get secret cg-secrets -n cg-dev -o jsonpath='{.data.NEO4J_PASSWORD}' | base64 -d)
        for id in "${runId}-symptoms" "${runId}-control"; do
            kubectl exec -n cg-dev deploy/neo4j -- cypher-shell -a bolt://localhost:7687 -u neo4j -p "\$NEO4J_PASSWORD" \\
                "MERGE (u:User {anonymousId: '\${id}'}) SET u.status = 'ACTIVE', u.statusUpdatedAt = timestamp()" >/dev/null
            echo "Visitante sembrado: \${id} (ACTIVE)"
        done
    """
    sh """
        set -e
        cd tests/e2e
        npm install
        npx playwright install --with-deps chromium
        KUBE_NS=cg-dev \
        WEB_URL=http://dev.circleguard.local:30080 \
        GATEWAY_URL=http://dev.circleguard.local:30080 \
        AUTH_URL=http://dev.circleguard.local:30080 \
        IDENTITY_URL=http://dev.circleguard.local:30080 \
        PROMOTION_URL=http://dev.circleguard.local:30080 \
        E2E_HOST_RESOLVER='MAP dev.circleguard.local 127.0.0.1' \
        E2E_ANONYMOUS_ID=${runId} \
        npx playwright test
    """
}
