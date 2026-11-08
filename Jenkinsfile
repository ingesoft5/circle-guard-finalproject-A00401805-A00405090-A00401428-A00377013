// CG-DOC: Jenkins multibranch pipeline for the gateway service lifecycle.
@Library('circleguard') _

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    triggers {
        cron('H/5 * * * *')
    }

    parameters {
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

        stage('Build & Test') {
            when { expression { !params.ROLLBACK_TAG?.trim() } }
            steps { ci.buildAndTest(env.SERVICE_PATH) }
        }

        stage('SonarQube') {
            when { expression { !params.ROLLBACK_TAG?.trim() } }
            steps { ci.sonarScan() }
        }

        stage('Quality Gate') {
            when { expression { !params.ROLLBACK_TAG?.trim() } }
            steps { ci.qualityGate() }
        }

        stage('Package') {
            when { expression { !params.ROLLBACK_TAG?.trim() } }
            steps { sh "./gradlew ${env.SERVICE_PATH}:bootJar" }
        }

        stage('Publish') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() } } }
            steps { ci.publish(env.SERVICE_PATH) }
        }

        stage('Docker') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() } } }
            steps { ci.dockerBuildPush(env.SERVICE_DIR, 'gateway', env.TAG) }
        }

        stage('Deploy dev') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() } } }
            steps { ci.deploy('dev', env.TAG) }
        }

        stage('E2E') {
            when { allOf { branch 'main'; expression { !params.SKIP_E2E && !params.ROLLBACK_TAG?.trim() } } }
            steps { ci.e2e() }
        }

        stage('Deploy stage') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() } } }
            steps { ci.deploy('stage', env.TAG) }
        }

        stage('Approval prod') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() } } }
            steps {
                input message: 'Aprobar despliegue a producción', ok: 'Desplegar'
            }
        }

        stage('Deploy prod') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() } } }
            steps { ci.deploy('prod', env.TAG) }
        }

        stage('Tag') {
            when { allOf { branch 'main'; expression { !params.ROLLBACK_TAG?.trim() } } }
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
            steps { ci.deploy('prod', params.ROLLBACK_TAG) }
        }

        stage('Notify') {
            steps { ci.notify('success') }
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
