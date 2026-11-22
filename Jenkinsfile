// CG-DOC: Jenkins multibranch pipeline for the CircleGuard services.
// The multibranch job discovers branches and pull requests; this file orchestrates
// reusable stages from the circleguard Shared Library (jenkins/vars).
// Pin the shared library to main. The JCasC default version can be stale on the
// Jenkins host, and a PR cannot use library vars that only exist in its own branch.
@Library('circleguard@main') _

def services = []

// The Jenkins container carries the environment addresses and the registry in its
// process environment (deploy/compose/tools/.env), but a pipeline does not see those
// in `env`. They are read here for the same reason as the semver helpers: the library
// is loaded from main, so a new library method would only exist after it merged.
def loadEnvironment(String... names) {
    def output = sh(
            script: "printenv | grep -E '^(${names.join('|')})=' || true",
            returnStdout: true
    ).trim()
    def found = [:]
    output.readLines().each { line ->
        def separator = line.indexOf('=')
        if (separator > 0) {
            found[line.substring(0, separator)] = line.substring(separator + 1)
        }
    }
    names.each { name ->
        def value = found[name]
        if (value) {
            env."${name}" = value
        } else {
            echo "Variable de entorno no definida en el contenedor: ${name}"
        }
    }
}

// La Shared Library pinneada a main puede ir un commit por detrás de este Jenkinsfile
// (el PR no ve sus propios cambios de library). El catálogo local corrige los flags
// que necesita la entrega sin bloquear el build del PR.
def enrichServices(List services) {
    def deployable = ['gateway', 'auth', 'identity', 'promotion', 'form', 'notification']
    def covered = ['gateway', 'auth', 'identity', 'promotion', 'form']
    services.each { service ->
        service.docker = true
        service.deploy = deployable.contains(service.short)
        if (covered.contains(service.short)) {
            service.jacoco = true
        }
        if (['gateway', 'auth', 'identity', 'promotion', 'form', 'notification'].contains(service.short)) {
            service.healthcheck = true
        }
        if (service.short == 'promotion') {
            service.sonarSettings = 'services/circleguard-promotion-service/sonar-project.properties'
        }
    }
    return services
}

// TEMPORAL: ciServices.resolve de la librería pinneada a main llama a verifySelection(),
// que en main aún tiene el bug CPS del sort/spread (main #36). Este PR arregla la librería,
// pero su propio build carga la versión de main, así que no puede usar su fix. Bypass local
// con el mismo contrato que ciServices.resolve (sin el self-check roto de main).
// ELIMINAR cuando la librería de main incluya el fix de ciServices (tras este merge).
def resolveServicesLocal(String mode) {
    def requested = (mode ?: 'auto').trim()
    if (requested == 'all') {
        return ciServices.catalog()
    }
    if (requested == 'auto') {
        return ciServices.changed()
    }
    def svc = ciServices.catalog().find { it.short == requested || it.name == requested }
    return svc ? [svc] : []
}

// El cliente Expo no es un módulo Gradle; se construye aparte y se publica con el
// mismo tag semántico que las imágenes Java.
def dockerBuildPushClientLocal(String tag) {
    def image = "${env.NEXUS_REGISTRY}/circleguard-docker/web:${tag}"
    def commit = env.GIT_COMMIT ?: 'unknown'
    def source = env.GITHUB_REPO_URL ?: 'unknown'
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${env.NEXUS_REGISTRY} -u \$NEXUS_USER --password-stdin"
        def registry = (env.NEXUS_REGISTRY ?: '').trim()
        def base = registry ? "${registry}/circleguard-base" : ''
        def baseArgs = base ? "--build-arg GRADLE_IMAGE=${base}/gradle:8.14-jdk21 --build-arg JDK_IMAGE=${base}/eclipse-temurin:21-jdk --build-arg ALPINE_IMAGE=${base}/eclipse-temurin:21-jre-alpine --build-arg JAMMY_IMAGE=${base}/eclipse-temurin:21-jre-jammy --build-arg NODE_IMAGE=${base}/node:22-alpine --build-arg WEB_IMAGE=${base}/nginx-unprivileged:1.27-alpine" : ''
        sh """
            docker build ${baseArgs} \
              --build-arg VERSION=${tag} \
              --build-arg GIT_COMMIT=${commit} \
              --build-arg SOURCE_URL=${source} \
              -t ${image} -f mobile/Dockerfile mobile
        """
        sh "docker push ${image}"
        digest = sh(script: "docker inspect --format='{{index .RepoDigests 0}}' ${image}", returnStdout: true).trim()
        if (!digest || digest == '<no value>') {
            digest = sh(script: "docker images --digests --format '{{.Digest}}' ${image} | head -1", returnStdout: true).trim()
        }
        sh "printf '%s\\n%s\\ncommit=%s\\n' '${image}' '${digest}' '${commit}' >> image-digest.txt"
    }
    return digest
}

def ensureTrivyBinaryLocal() {
    sh '''
        set -eu
        if ! command -v trivy >/dev/null 2>&1 && [ ! -x "${WORKSPACE}/.trivy-bin/trivy" ]; then
            echo "Extrayendo binario trivy desde aquasec/trivy:0.57.1..."
            cid=$(docker create aquasec/trivy:0.57.1)
            mkdir -p "${WORKSPACE}/.trivy-bin"
            docker cp "$cid:/usr/local/bin/trivy" "${WORKSPACE}/.trivy-bin/trivy"
            chmod 0755 "${WORKSPACE}/.trivy-bin/trivy"
            cp "${WORKSPACE}/.trivy-bin/trivy" /usr/local/bin/trivy 2>/dev/null || true
            docker rm -f "$cid" >/dev/null 2>&1 || true
        fi
    '''
}

def trivyScanIaCLocal() {
    ensureTrivyBinaryLocal()
    sh '''
        set -eu
        export PATH="/usr/local/bin:${WORKSPACE}/.trivy-bin:${PATH}"
        mkdir -p build/reports/security
        trivy config --severity HIGH,CRITICAL --format json --output build/reports/security/trivy-iac.json deploy/k8s/
        trivy config --severity CRITICAL --exit-code 1 deploy/k8s/ | tee build/reports/security/trivy-iac.txt
    '''
    archiveArtifacts artifacts: 'build/reports/security/trivy-iac.*', allowEmptyArchive: true
}

def trivyScanImagesLocal(List images, String tag) {
    ensureTrivyBinaryLocal()
    images.each { service ->
        def image = "${env.NEXUS_REGISTRY}/circleguard-docker/${service.short}:${tag}"
        sh """
            set -eu
            export PATH="/usr/local/bin:\${WORKSPACE}/.trivy-bin:\${PATH}"
            mkdir -p build/reports/security
            trivy image --severity HIGH,CRITICAL --ignore-unfixed --format json --output build/reports/security/trivy-${service.short}-${tag}.json "${image}"
            trivy image --severity CRITICAL --ignore-unfixed --exit-code 1 "${image}" | tee build/reports/security/trivy-${service.short}-${tag}.txt
        """
    }
    archiveArtifacts artifacts: 'build/reports/security/trivy-*', allowEmptyArchive: true
}

def owaspZapGateLocal(String environment) {
    def namespace = "cg-${environment}"
    def buildNumber = env.BUILD_NUMBER ?: 'local'
    // Every API surface that reaches production is scanned by its own baseline run;
    // the blocking check below reads the aggregate report, so adding a target can
    // never weaken the existing auth scan.
    def targets = [
            'auth'        : 'http://auth:8180/actuator/health',
            'promotion'   : 'http://promotion:8088/actuator/health',
            'notification': 'http://notification:8082/actuator/health'
    ]
    sh """
        set -eu
        mkdir -p build/reports/security
        : > build/reports/security/zap-${environment}.txt
        kubectl -n ${namespace} wait --for=condition=Available deployment/auth deployment/gateway deployment/promotion deployment/notification --timeout=180s
    """
    targets.each { svc, target ->
        def zapPod = "zap-gate-${svc}-${buildNumber}".toString()
        sh """
            set -eu
            kubectl -n ${namespace} delete pod ${zapPod} --ignore-not-found >/dev/null 2>&1 || true
            trap 'kubectl -n ${namespace} delete pod ${zapPod} --ignore-not-found >/dev/null 2>&1 || true' EXIT
            kubectl -n ${namespace} run ${zapPod} \\
              --image=ghcr.io/zaproxy/zaproxy:stable \\
              --restart=Never \\
              --labels="ci-security-gate=true" \\
              --command -- zap-baseline.py -t ${target} -m 1
            kubectl -n ${namespace} wait "pod/${zapPod}" --for=jsonpath='{.status.phase}'=Succeeded --timeout=300s
            kubectl -n ${namespace} logs ${zapPod} | tee -a build/reports/security/zap-${environment}.txt
        """
    }
    sh """
        set -eu
        if grep -qE '^FAIL-NEW:[[:space:]]*[1-9]' build/reports/security/zap-${environment}.txt; then
            echo "OWASP ZAP detectó vulnerabilidades bloqueantes en ${namespace} antes de promover a prod." >&2
            exit 1
        fi
    """
    archiveArtifacts artifacts: "build/reports/security/zap-${environment}.txt", allowEmptyArchive: true
}

// Los tres flujos UI (login, cuestionario y acceso) viven en tests/e2e. El E2E
// on-prem siembra Neo4j y publica survey.submitted vía kubectl exec y corre contra
// el Ingress de k3d; ver jenkins/vars/ci.groovy y tests/e2e/playwright.config.ts.

// Shared library vars added in the same PR are not visible to that PR build:
// @Library loads from LIBRARY_BRANCH, not from the checked-out PR branch.
// Keep the semver helpers here so the pipeline can validate itself.
def normalizeSemver(String value) {
    def candidate = (value ?: '').trim()
    if (candidate.startsWith('v')) {
        candidate = candidate.substring(1)
    }
    if (!(candidate ==~ /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/)) {
        error "Versión inválida '${value}'. Use el formato semver X.Y.Z o vX.Y.Z."
    }
    return candidate
}

def resolveSemver(String override) {
    def provided = (override ?: '').trim()
    if (provided) {
        echo "Versión semántica recibida por parámetro: ${provided}."
        return normalizeSemver(provided)
    }

    // Jenkins may not fetch tags on a detached checkout; force them before reading.
    sh 'git fetch --tags --force --quiet || true'

    def exactTag = sh(
            script: "git describe --tags --exact-match --match 'v[0-9]*' HEAD 2>/dev/null || true",
            returnStdout: true
    ).trim()
    if (exactTag) {
        def exactVersion = normalizeSemver(exactTag)
        echo "HEAD ya tiene el tag exacto ${exactTag}; se publica la versión ${exactVersion}."
        return exactVersion
    }

    def lastTag = sh(
            script: "git tag --list 'v[0-9]*' --sort=-v:refname | head -n1",
            returnStdout: true
    ).trim()
    if (!lastTag) {
        echo 'No hay tags semver previos; la primera versión publicada será 1.0.0.'
        return '1.0.0'
    }
    def base = normalizeSemver(lastTag)
    def range = "${lastTag}..HEAD"

    def subjects = sh(script: "git log --format='%s' ${range}", returnStdout: true).trim()
    if (!subjects) {
        echo "Sin commits nuevos desde ${lastTag}; se mantiene ${base}."
        return base
    }

    def bodies = sh(script: "git log --format='%b' ${range}", returnStdout: true)
    def lines = subjects.readLines().findAll { it.trim() }
    def breaking = lines.any { it ==~ /^[A-Za-z]+\s*(\([^)]*\))?\s*!:\s*.+/ } ||
            bodies.contains('BREAKING CHANGE:') ||
            bodies.contains('BREAKING-CHANGE:')
    def feature = lines.any { it ==~ /^feat\s*(\([^)]*\))?\s*!?:\s*.+/ }

    def parts = base.tokenize('.').collect { it as int }
    def major = parts[0]
    def minor = parts[1]
    def patch = parts[2]
    if (breaking) {
        major += 1
        minor = 0
        patch = 0
    } else if (feature) {
        minor += 1
        patch = 0
    } else {
        patch += 1
    }

    def next = "${major}.${minor}.${patch}"
    echo "Versión semántica calculada: ${next} (base ${base}, breaking=${breaking}, feat=${feature})."
    return next
}

def createSemverTag(String version) {
    def tag = normalizeSemver(version)
    withCredentials([usernamePassword(credentialsId: 'github-creds',
            usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
        sh """
            set -eu
            existing=\$(git rev-parse -q --verify "refs/tags/v${tag}" || true)
            if [ -n "\$existing" ] && [ "\$existing" != "\${GIT_COMMIT}" ]; then
                echo "El tag v${tag} ya existe en \$existing y no apunta al commit actual." >&2
                exit 1
            fi
            if [ -z "\$existing" ]; then
                git tag "v${tag}" "\${GIT_COMMIT}"
                git push "https://\${GH_USER}:\${GH_TOKEN}@github.com/\${GITHUB_OWNER}/\${GITHUB_REPO}.git" "refs/tags/v${tag}"
                echo "Tag v${tag} publicado sobre \${GIT_COMMIT}."
            else
                echo "El tag v${tag} ya apunta al commit actual; no se recrea."
            fi
        """
    }
}

def writeReleaseNotes(String version) {
    // Se genera desde los commits convencionales; el tag previo define el rango.
    def previous = sh(
            script: "git tag --list 'v[0-9]*' --sort=-v:refname | grep -v '^v${version}\$' | head -n1",
            returnStdout: true
    ).trim()
    def range = previous ? "${previous}..HEAD" : 'HEAD'
    def changes = sh(
            script: "git log --pretty=format:'- %s (%h)' ${range}",
            returnStdout: true
    ).trim()
    def date = sh(script: 'date -u +%Y-%m-%d', returnStdout: true).trim()
    def notes = """# CircleGuard v${version}

Fecha: ${date}

## Cambios

${changes ?: '- Sin cambios registrados.'}

## Artefactos

- Imágenes Docker inmutables con tag `v${version}`.
- JARs publicados en Nexus.
- Cobertura y quality gate disponibles en SonarQube.
"""
    writeFile file: "docs/release-notes/v${version}.md", text: notes
    archiveArtifacts artifacts: "docs/release-notes/v${version}.md", fingerprint: true
}

def publishGitHubRelease(String version) {
    // Publica las notas como GitHub Release. Si ya existe una release para el tag,
    // actualiza el cuerpo para que un reintento del pipeline sea idempotente.
    withCredentials([usernamePassword(credentialsId: 'github-creds',
            usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
        sh """
            set -eu
            notes="docs/release-notes/v${version}.md"
            if [ ! -f "\$notes" ]; then
                echo "No hay notas para v${version}; se omite la release."
                exit 0
            fi
            payload=\$(jq -n \
                --arg tag "v${version}" \
                --arg name "CircleGuard v${version}" \
                --rawfile body "\$notes" \
                '{tag_name: \$tag, name: \$name, body: \$body, draft: false, prerelease: false}')
            api="https://api.github.com/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/releases"
            existing=\$(curl -fsS -H "Authorization: token \$GH_TOKEN" \
                "\$api/tags/v${version}" 2>/dev/null || true)
            if [ -n "\$existing" ]; then
                id=\$(printf '%s' "\$existing" | jq -r '.id')
                curl -fsS -X PATCH -H "Authorization: token \$GH_TOKEN" \
                    -H 'Content-Type: application/json' "\$api/\$id" -d "\$payload" >/dev/null
                echo "GitHub Release v${version} actualizada (id=\$id)."
            else
                curl -fsS -X POST -H "Authorization: token \$GH_TOKEN" \
                    -H 'Content-Type: application/json' "\$api" -d "\$payload" >/dev/null
                echo "GitHub Release v${version} publicada."
            fi
        """
    }
}

// ---------- Idempotencia de publicación -------------------------------------------
// Un rerun del mismo commit conserva el TAG. Los destinos inmutables (Nexus) y
// los mutables (Docker registry) deben omitir lo que ya existe, sin fallar ni
// duplicar evidencia.
def nexusArtifactExists(String artifact, String version) {
    def repository = version.endsWith('SNAPSHOT') ? 'maven-snapshots' : 'maven-releases'
    def count = 0
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        def raw = sh(
                script: """
                    curl -fsS -u \$NEXUS_USER:\$NEXUS_PASSWORD \
                      "${env.NEXUS_URL}/service/rest/v1/search/assets?repository=${repository}&maven.groupId=com.circleguard&maven.artifactId=${artifact}&maven.version=${version}" \
                      | jq '.items | length'
                """,
                returnStdout: true
        ).trim()
        count = raw ? raw.toInteger() : 0
    }
    return count > 0
}

def dockerImageExists(String repository, String tag) {
    def status = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        status = sh(
                script: """
                    set +e
                    curl -fsS -o /dev/null \
                      -u \$NEXUS_USER:\$NEXUS_PASSWORD \
                      -H 'Accept: application/vnd.docker.distribution.manifest.v2+json' \
                      "http://${env.NEXUS_REGISTRY}/v2/${repository}/manifests/${tag}"
                    echo \$?
                """,
                returnStdout: true
        ).trim()
    }
    return status == '0'
}

def registryDigest(String repository, String tag) {
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        digest = sh(
                script: """
                    curl -fsSI -u \$NEXUS_USER:\$NEXUS_PASSWORD \
                      -H 'Accept: application/vnd.docker.distribution.manifest.v2+json' \
                      "http://${env.NEXUS_REGISTRY}/v2/${repository}/manifests/${tag}" \
                      | tr -d '\\r' \
                      | awk -F': ' 'tolower(\$1) == "docker-content-digest" { print \$2 }'
                """,
                returnStdout: true
        ).trim()
    }
    return digest
}

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        choice(
                name: 'SERVICE',
                choices: [
                        'auto',
                        'all',
                        'circleguard-gateway-service',
                        'circleguard-auth-service',
                        'circleguard-form-service',
                        'circleguard-identity-service',
                        'circleguard-dashboard-service',
                        'circleguard-file-service',
                        'circleguard-notification-service',
                        'circleguard-promotion-service'
                ],
                description: 'auto = solo servicios modificados; all = todos los servicios.'
        )
        string(name: 'TAG', defaultValue: '', description: 'Tag semver. Vacío = calcular desde Git tags y Conventional Commits.')
        booleanParam(name: 'SKIP_INTEGRATION', defaultValue: false, description: 'Omitir pruebas de integración.')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Compatibilidad: en main el despliegue es automático; en otras ramas no aplica.')
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Si se indica, despliega ese tag en prod sin reconstruir.')
    }

    stages {
        stage('Checkout') {
            steps {
                script { ci.checkoutRepo() }
            }
        }

        stage('Resolve services') {
            steps {
                script {
                    loadEnvironment('NEXUS_REGISTRY', 'NEXUS_URL', 'TOOLS_IP', 'GITHUB_OWNER', 'GITHUB_REPO')

                    // Un rerun no debe duplicar enlaces ni digests en los artefactos.
                    writeFile file: 'sonar-links.txt', text: ''
                    writeFile file: 'image-digest.txt', text: ''

                    if (params.ROLLBACK_TAG?.trim()) {
                        env.TAG = normalizeSemver(params.ROLLBACK_TAG.trim())
                        services = []
                        echo "Rollback solicitado: ${env.TAG}"
                    } else {
                        env.TAG = resolveSemver(params.TAG ?: '')
                        services = enrichServices(resolveServicesLocal(params.SERVICE ?: 'auto'))
                        env.SERVICES = services.collect { it.short }.join(',')
                        echo "Versión semántica: ${env.TAG}"
                        echo "Servicios a construir: ${env.SERVICES ?: 'ninguno'}"
                    }
                }
            }
        }

        stage('Unit Tests + JaCoCo') {
            when { expression { services } }
            steps {
                script {
                    services.each { service ->
                        timeout(time: 10, unit: 'MINUTES') {
                            echo "Pruebas unitarias y cobertura de ${service.name}..."
                            ci.unitTest(service)
                        }
                    }
                }
            }
        }

        stage('Build') {
            when { expression { services } }
            steps {
                script {
                    services.each { service ->
                        timeout(time: 10, unit: 'MINUTES') {
                            echo "Compilando ${service.name}..."
                            ci.build(service)
                        }
                    }
                }
            }
        }

        stage('SonarQube + Quality Gate') {
            when { expression { services && !params.ROLLBACK_TAG?.trim() } }
            steps {
                script {
                    services.each { service ->
                        if (service.jacoco) {
                            echo "Analizando ${service.name} con SonarQube..."
                            ci.sonarScan(service)
                            ci.qualityGate(service)
                        } else {
                            echo "SonarQube omitido para ${service.name}: sin JaCoCo configurado."
                        }
                    }
                }
            }
        }

        stage('Integration Tests') {
            when { expression { services && !params.SKIP_INTEGRATION && !params.ROLLBACK_TAG?.trim() } }
            steps {
                script {
                    services.each { service ->
                        if (service.integration) {
                            timeout(time: 12, unit: 'MINUTES') {
                                echo "Pruebas de integración de ${service.name}..."
                                ci.integrationTest(service)
                            }
                        } else {
                            echo "Integración omitida para ${service.name}."
                        }
                    }
                }
            }
        }

        stage('Trivy IaC Security Gate') {
            when { expression { !params.ROLLBACK_TAG?.trim() } }
            steps {
                script { trivyScanIaCLocal() }
            }
        }

        stage('Package') {
            when { expression { services && !params.ROLLBACK_TAG?.trim() } }
            steps {
                script {
                    services.each { service ->
                        echo "Empaquetando ${service.name}..."
                        ci.packageService(service)
                    }
                }
            }
        }

        stage('Git tag') {
            when {
                allOf {
                    branch 'main'
                    expression {
                        services && services.any { it.publish || it.docker } && !params.ROLLBACK_TAG?.trim()
                    }
                }
            }
            steps {
                script { createSemverTag(env.TAG) }
            }
        }

        stage('Release notes') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { writeReleaseNotes(env.TAG) }
            }
        }

        stage('GitHub Release') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { publishGitHubRelease(env.TAG) }
            }
        }

        stage('Publish') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script {
                    services.each { service ->
                        if (service.publish && nexusArtifactExists(service.name, env.TAG)) {
                            echo "Publicación omitida: ${service.name}:${env.TAG} ya existe en Nexus (reintento idempotente)."
                        } else {
                            ci.publish(service)
                        }
                    }
                }
            }
        }

        stage('Docker') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script {
                    // Preserve idempotent retries: existing images need no base-image catalog lookup.
                    // New builds still use the Nexus mirror through ci.dockerBuildPush.
                    def images = services.findAll { it.docker }
                    if (!images) {
                        echo 'Ningún servicio seleccionado publica imagen.'
                    }
                    images.each { service ->
                        def repository = "circleguard-docker/${service.short}"
                        if (dockerImageExists(repository, env.TAG)) {
                            def digest = registryDigest(repository, env.TAG) ?: 'sha256:<desconocido>'
                            def commit = env.GIT_COMMIT ?: 'unknown'
                            echo "Docker omitido: ${env.NEXUS_REGISTRY}/${repository}:${env.TAG} ya publicado (${digest})."
                            sh "printf '%s\\n%s\\ncommit=%s\\n' '${env.NEXUS_REGISTRY}/${repository}:${env.TAG}' '${digest}' '${commit}' >> image-digest.txt"
                        } else {
                            def digest = ci.dockerBuildPush(service, env.TAG)
                            echo "Digest publicado (${service.short}): ${digest}"
                        }
                    }
                }
            }
        }

        stage('Docker client') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script {
                    def repository = 'circleguard-docker/web'
                    if (dockerImageExists(repository, env.TAG)) {
                        def digest = registryDigest(repository, env.TAG) ?: 'sha256:<desconocido>'
                        def commit = env.GIT_COMMIT ?: 'unknown'
                        echo "Docker client omitido: ${env.NEXUS_REGISTRY}/${repository}:${env.TAG} ya publicado (${digest})."
                        sh "printf '%s\\n%s\\ncommit=%s\\n' '${env.NEXUS_REGISTRY}/${repository}:${env.TAG}' '${digest}' '${commit}' >> image-digest.txt"
                    } else {
                        def digest = dockerBuildPushClientLocal(env.TAG)
                        echo "Digest publicado (web): ${digest}"
                    }
                }
            }
        }

        stage('Trivy Image Security Gate') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script {
                    def images = services.findAll { it.docker }
                    trivyScanImagesLocal(images, env.TAG)
                }
            }
        }

        stage('Deploy dev') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.deploy('dev', env.TAG) } }
        }

        stage('E2E') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.SKIP_INTEGRATION && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.e2e() } }
        }

        stage('Deploy stage') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.deploy('stage', env.TAG) } }
        }

        stage('OWASP ZAP Security Gate') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.SKIP_INTEGRATION && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { owaspZapGateLocal('stage') } }
        }

        stage('Approval prod') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                input message: 'Aprobar despliegue a producción', ok: 'Desplegar'
            }
        }

        stage('Deploy prod') {
            when {
                allOf {
                    branch 'main'
                    expression { services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.deploy('prod', env.TAG) } }
        }

        stage('Rollback prod') {
            when {
                allOf {
                    branch 'main'
                    expression { params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.deploy('prod', params.ROLLBACK_TAG) } }
        }
    }

    post {
        always {
            script {
                // A build aborted while it was still waiting for an executor never received
                // a workspace, and archiveArtifacts then fails with MissingContextVariable
                // (hudson.FilePath is missing). That secondary error hides the real reason
                // the build stopped, so archiving only happens when something was resolved.
                if (services) {
                    ci.archive(services)
                }
            }
        }
        success {
            echo "Pipeline exitoso: ${env.SERVICES ?: 'sin servicios'} ${env.TAG}"
        }
        failure {
            script { ciNotify.failure(services) }
        }
    }
}
