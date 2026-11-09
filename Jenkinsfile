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
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Desplegar a dev/stage/prod (solo main).')
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
                    loadEnvironment('NEXUS_REGISTRY', 'TOOLS_IP', 'DEV_PRIVATE_IP',
                            'STAGE_PRIVATE_IP', 'PROD_PRIVATE_IP', 'GITHUB_OWNER', 'GITHUB_REPO')
                    if (params.ROLLBACK_TAG?.trim()) {
                        env.TAG = normalizeSemver(params.ROLLBACK_TAG.trim())
                        services = []
                        echo "Rollback solicitado: ${env.TAG}"
                    } else {
                        env.TAG = resolveSemver(params.TAG ?: '')
                        services = ciServices.resolve(params.SERVICE ?: 'auto')
                        env.SERVICES = services.collect { it.short }.join(',')
                        echo "Versión semántica: ${env.TAG}"
                        echo "Servicios a construir: ${env.SERVICES ?: 'ninguno'}"
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
                            timeout(time: 5, unit: 'MINUTES') {
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
                    services.each { service -> ci.publish(service) }
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
                    // Todo servicio con Dockerfile se empaqueta, no solo el gateway.
                    def images = services.findAll { it.docker }
                    if (!images) {
                        echo 'Ningún servicio seleccionado publica imagen.'
                    }
                    images.each { service ->
                        def digest = ci.dockerBuildPush(service, env.TAG)
                        echo "Digest publicado (${service.short}): ${digest}"
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
                    def digest = ci.dockerBuildPushClient(env.TAG)
                    echo "Digest publicado (web): ${digest}"
                }
            }
        }

        stage('Deploy dev') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY && services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.deploy('dev', env.TAG) } }
        }

        stage('E2E') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY && services && !params.SKIP_INTEGRATION && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.e2e() } }
        }

        stage('Deploy stage') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY && services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps { script { ci.deploy('stage', env.TAG) } }
        }

        stage('Approval prod') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY && services && !params.ROLLBACK_TAG?.trim() }
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
                    expression { params.DEPLOY && services && !params.ROLLBACK_TAG?.trim() }
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
