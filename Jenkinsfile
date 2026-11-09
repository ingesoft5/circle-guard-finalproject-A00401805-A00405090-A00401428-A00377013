// CG-DOC: One multibranch pipeline selects services and reuses the shared CI library.
@Library('circleguard') _

def serviceRegistry
def selectedServices = []

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout()
    }

    triggers {
        cron('H/5 * * * *')
    }

    parameters {
        choice(name: 'TARGET_SERVICE', choices: ['auto', 'all', 'gateway', 'form'],
                description: 'auto: detectar cambios; all: todos los integrados (Gateway y Form).')
        string(name: 'TAG', defaultValue: '', description: 'Tag semver. Vacío = calcular con el commit.')
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Si se indica, despliega ese tag en prod sin reconstruir.')
        booleanParam(name: 'SKIP_E2E', defaultValue: false, description: 'Omitir E2E (solo para diagnóstico).')
    }

    environment {
        SERVICE = 'circleguard-gateway-service'
        SERVICE_DIR = 'services/circleguard-gateway-service'
        SERVICE_PATH = ':services:circleguard-gateway-service'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Prepare') {
            steps {
                script {
                    if (params.ROLLBACK_TAG?.trim()) {
                        env.TAG = params.ROLLBACK_TAG.trim()
                    } else {
                        env.TAG = params.TAG?.trim() ?: "1.0.0-${(env.GIT_COMMIT ?: 'local').take(7)}"
                    }
                    echo "Tag de build: ${env.TAG}"
                }
            }
        }

        stage('Select services') {
            steps {
                script {
                    // Load selection from this checkout; do not change the global library version.
                    serviceRegistry = load 'jenkins/services.groovy'
                    def changes = [files: [], reliable: true]
                    def target = params.TARGET_SERVICE ?: 'auto'
                    if (target == 'auto' && !params.ROLLBACK_TAG?.trim()) {
                        changes = serviceRegistry.detectChanges([
                                branch: env.BRANCH_NAME,
                                changeTarget: env.CHANGE_TARGET,
                                previousSuccess: env.GIT_PREVIOUS_SUCCESSFUL_COMMIT,
                                previousDescription: currentBuild.previousSuccessfulBuild?.description
                        ])
                    }
                    def selection = serviceRegistry.selectServices(
                            params.ROLLBACK_TAG?.trim() ? 'gateway' : target,
                            changes.files, changes.reliable)
                    selectedServices = selection.services
                    env.SERVICE = selectedServices.collect {
                        serviceRegistry.catalog()[it].name
                    }.join(', ') ?: 'sin servicios: documentación'
                    currentBuild.description = params.ROLLBACK_TAG?.trim()
                            ? "[CI:rollback] Gateway: ${env.TAG}"
                            : "[CI:${target}] Servicios: ${selectedServices.join(', ') ?: 'ninguno'}"
                    echo "Servicios seleccionados: ${selectedServices.join(', ') ?: 'ninguno'}"
                    if (changes.reason) { echo changes.reason }
                    selection.reasons.each { echo it }
                    selection.pending.each {
                        echo "PENDIENTE: ${it} todavía no está integrado; ejecutar Gateway no valida ese servicio."
                    }
                }
            }
        }

        stage('Build & Test') {
            when { expression { !params.ROLLBACK_TAG?.trim() && !selectedServices.isEmpty() } }
            steps {
                script {
                    // Sequential execution avoids concurrent Gradle clean/test in one workspace.
                    selectedServices.each { id ->
                        def service = serviceRegistry.catalog()[id]
                        withEnv(["SERVICE=${service.name}", "SERVICE_DIR=${service.directory}",
                                 "SERVICE_PATH=${service.gradlePath}"]) {
                            echo "Compilar, probar y verificar cobertura: ${id}"
                            ci.buildAndTest(service.gradlePath)
                        }
                    }
                }
            }
        }

        // Gateway keeps its existing Sonar project and release lifecycle.
        // Form Sonar/report publication and image publication belong to DCG-23/DCG-24.
        stage('SonarQube') {
            when { expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } }
            steps { script { ci.sonarScan() } }
        }

        stage('Quality Gate') {
            when { expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } }
            steps { script { ci.qualityGate() } }
        }

        stage('Package') {
            when { expression { !params.ROLLBACK_TAG?.trim() && !selectedServices.isEmpty() } }
            steps {
                script {
                    selectedServices.each { id ->
                        def service = serviceRegistry.catalog()[id]
                        sh "./gradlew ${service.gradlePath}:bootJar"
                    }
                }
            }
        }

        stage('Publish') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps { script { ci.publish(env.SERVICE_PATH) } }
        }

        stage('Docker') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps { script { ci.dockerBuildPush(env.SERVICE_DIR, 'gateway', env.TAG) } }
        }

        stage('Deploy dev') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps { script { ci.deploy('dev', env.TAG) } }
        }

        stage('E2E') {
            when { allOf { branch 'main'; expression { !params.SKIP_E2E && !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps { script { ci.e2e() } }
        }

        stage('Deploy stage') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps { script { ci.deploy('stage', env.TAG) } }
        }

        stage('Approval prod') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps {
                input message: 'Aprobar despliegue a producción', ok: 'Desplegar'
            }
        }

        stage('Deploy prod') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps { script { ci.deploy('prod', env.TAG) } }
        }

        stage('Tag') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() && selectedServices.contains('gateway') } } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-creds',
                        usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh 'git tag -f v${TAG}'
                    sh 'git push -f https://${GH_USER}:${GH_TOKEN}@github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git v${TAG}'
                }
            }
        }

        stage('Rollback prod') {
            when { allOf { branch 'main'; expression { params.ROLLBACK_TAG?.trim() } } }
            steps { script { ci.deploy('prod', params.ROLLBACK_TAG) } }
        }

        stage('Notify') {
            steps { script { ci.notify('success') } }
        }
    }

    post {
        success { echo "Pipeline exitoso: ${env.SERVICE} ${env.TAG}" }
        failure {
            script { ci.notify('failure') }
            echo "Pipeline falló: ${env.SERVICE} ${env.TAG}"
        }
    }
}
