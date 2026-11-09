// CG-DOC: Jenkins multibranch pipeline for the CircleGuard services.
// The multibranch job discovers branches and pull requests; this file orchestrates
// reusable stages from the circleguard Shared Library (jenkins/vars).
@Library('circleguard') _

def services = []

// The Jenkins container carries the environment addresses and the registry in its
// process environment (deploy/compose/tools/.env), but a pipeline does not see those
// in `env`. They are read here, in the file that ships with the branch, instead of in
// the shared library: the library is loaded from main, so a new library method would
// only exist after the change had already merged.
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
        string(name: 'TAG', defaultValue: '', description: 'Tag semver. Vacío = calcular con el commit.')
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
                        env.TAG = params.ROLLBACK_TAG.trim()
                        services = []
                        echo "Rollback solicitado: ${env.TAG}"
                    } else {
                        env.TAG = params.TAG?.trim() ?: "1.0.0-${(env.GIT_COMMIT ?: 'local').take(7)}"
                        services = ciServices.resolve(params.SERVICE ?: 'auto')
                        env.SERVICES = services.collect { it.short }.join(',')
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
                    // Todo servicio con Dockerfile se empaqueta, no solo el gateway: el
                    // despliegue a dev levanta la pila completa y cada imagen tiene que
                    // existir en el registro con el tag que el pipeline va a desplegar.
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

        stage('Tag') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY && services && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-creds',
                        usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh 'git tag -f v${TAG}'
                    sh 'git push -f https://${GH_USER}:${GH_TOKEN}@github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git v${TAG}'
                }
            }
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
