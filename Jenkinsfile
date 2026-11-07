@Library('circleguard') _

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'TAG', defaultValue: '', description: 'Tag semver. Vacío = calcular con el commit.')
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Si se indica, despliega ese tag en prod.')
        booleanParam(name: 'SKIP_E2E', defaultValue: false, description: 'Omitir E2E (solo para diagnóstico).')
    }

    environment {
        AUTH_SERVICE = 'circleguard-auth-service'
        AUTH_SERVICE_DIR = 'services/circleguard-auth-service'
        SERVICE = 'circleguard-gateway-service'
        SERVICE_DIR = 'services/circleguard-gateway-service'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Prepare') {
            steps {
                script {
                    env.TAG = params.TAG?.trim() ?: "1.0.0-${(env.GIT_COMMIT ?: 'local').take(7)}"
                    echo "Tag de build: ${env.TAG}"
                }
            }
        }

        stage('Build & Test') {
            steps {
                ci.buildAndTest(env.AUTH_SERVICE_DIR)
                ci.buildAndTest(env.SERVICE_DIR)
            }
        }

        stage('SonarQube') {
            steps { ci.sonarScan() }
        }

        stage('Quality Gate') {
            steps { ci.qualityGate() }
        }

        stage('Package') {
            steps {
                sh "./gradlew :services:circleguard-auth-service:bootJar :services:circleguard-gateway-service:bootJar"
            }
        }

        stage('Publish') {
            steps {
                ci.publish(env.AUTH_SERVICE_DIR)
                ci.publish(env.SERVICE_DIR)
            }
        }

        stage('Docker') {
            steps {
                ci.dockerBuildPush(env.AUTH_SERVICE_DIR, 'auth', env.TAG)
                ci.dockerBuildPush(env.SERVICE_DIR, 'gateway', env.TAG)
            }
        }

        stage('Deploy dev') {
            steps { ci.deploy('dev', env.TAG) }
        }

        stage('E2E') {
            when { expression { !params.SKIP_E2E } }
            steps { ci.e2e() }
        }

        stage('Deploy stage') {
            steps { ci.deploy('stage', env.TAG) }
        }

        stage('Approval prod') {
            steps {
                input message: 'Aprobar despliegue a producción', ok: 'Desplegar'
            }
        }

        stage('Deploy prod') {
            steps { ci.deploy('prod', env.TAG) }
        }

        stage('Tag') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-creds',
                        usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh 'git tag -f v${TAG}'
                    sh 'git push -f https://${GH_USER}:${GH_TOKEN}@github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git v${TAG}'
                }
            }
        }

        stage('Rollback') {
            when { expression { params.ROLLBACK_TAG?.trim() } }
            steps { ci.deploy('prod', params.ROLLBACK_TAG) }
        }

        stage('Notify') {
            steps { ci.notify('success') }
        }
    }

    post {
        success { echo "Pipeline exitoso: ${env.AUTH_SERVICE} + ${env.SERVICE} ${env.TAG}" }
        failure {
            script { ci.notify('failure') }
            echo "Pipeline falló: ${env.AUTH_SERVICE} + ${env.SERVICE} ${env.TAG}"
        }
    }
}
