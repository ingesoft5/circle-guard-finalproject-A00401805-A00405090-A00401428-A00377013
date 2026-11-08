def buildAndTest(String servicePath) {
    sh "./gradlew ${servicePath}:clean ${servicePath}:test ${servicePath}:jacocoTestReport ${servicePath}:jacocoTestCoverageVerification"
}

def sonarScan() {
    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
        sh "sonar-scanner -Dsonar.host.url=${env.SONAR_HOST_URL} -Dsonar.token=${SONAR_TOKEN}"
    }
}

def qualityGate() {
    withSonarQubeEnv('SonarQube') {
        timeout(time: 5, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
        }
    }
}

def publish(String servicePath) {
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh """
            NEXUS_URL=${env.NEXUS_URL} \
            NEXUS_USER=${NEXUS_USER} \
            NEXUS_PASSWORD=${NEXUS_PASSWORD} \
            ./gradlew ${servicePath}:publish
        """
    }
}

def dockerBuildPush(String serviceDir, String image, String tag) {
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${env.NEXUS_REGISTRY} -u \$NEXUS_USER --password-stdin"
        sh "docker build -t ${env.NEXUS_REGISTRY}/circleguard-docker/${image}:${tag} -f ${serviceDir}/Dockerfile ."
        sh "docker push ${env.NEXUS_REGISTRY}/circleguard-docker/${image}:${tag}"
    }
}

def deploy(String environment, String tag) {
    sshagent(['cg-envs-ssh']) {
        sh """
            ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ubuntu@${env.ENVS_IP} \
              'cd /opt/circleguard && COMPOSE_ROOT=/opt/circleguard/deploy/compose TAG=${tag} ./deploy/scripts/deploy.sh ${environment} ${tag}'
        """
    }
}

def e2e() {
    sh """
        cd tests/e2e
        npm install
        npx playwright install --with-deps chromium
        GATEWAY_URL=http://${env.ENVS_IP}:8087 npx playwright test
    """
}

def notify(String status) {
    if (!env.SLACK_WEBHOOK_URL?.trim()) {
        echo "SLACK_WEBHOOK_URL no configurado; notificación omitida."
        return
    }
    def color = status == 'success' ? 'good' : 'danger'
    sh """
        curl -fsS -X POST -H 'Content-Type: application/json' \
          -d '{"attachments":[{"color":"${color}","text":"${env.SERVICE} ${env.TAG}: ${status}"}]}' \
          ${env.SLACK_WEBHOOK_URL}
    """
}
