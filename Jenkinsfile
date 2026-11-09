// CG-DOC: Jenkins multibranch pipeline for the CircleGuard services.
// The multibranch job discovers branches and pull requests; this file orchestrates
// reusable stages from the circleguard Shared Library (jenkins/vars).
// Pin the shared library to main. The JCasC default version can be stale on the
// Jenkins host, and a PR cannot use library vars that only exist in its own branch.
@Library('circleguard@main') _

def services = []

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
                    services.findAll { it.docker }.each { service ->
                        def digest = ci.dockerBuildPush(service, env.TAG)
                        echo "Digest publicado para ${service.short}: ${digest}"
                    }
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
            script { ci.archive(services) }
        }
        success {
            echo "Pipeline exitoso: ${env.SERVICES ?: 'sin servicios'} ${env.TAG}"
        }
        failure {
            script { ciNotify.failure(services) }
        }
    }
}
