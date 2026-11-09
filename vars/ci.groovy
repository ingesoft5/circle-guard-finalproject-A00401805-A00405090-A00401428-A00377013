// CG-DOC: Shared Jenkins pipeline helpers for build, analysis, publish, deploy, and notify.
def buildAndTest(String servicePath) {
    sh "./gradlew ${servicePath}:clean ${servicePath}:test ${servicePath}:jacocoTestReport ${servicePath}:jacocoTestCoverageVerification"
}

def sonarScan() {
    withSonarQubeEnv('SonarQube') {
        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
            sh "sonar-scanner -Dsonar.host.url=${env.SONAR_HOST_URL} -Dsonar.token=${SONAR_TOKEN}"
        }
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
    if (tag == 'latest' || !(tag ==~ /^v\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/)) {
        error "Invalid image tag '${tag}'. Use vX.Y.Z or vX.Y.Z-<sha>; latest is forbidden."
    }
    def fullImage = "${env.NEXUS_REGISTRY}/circleguard-docker/${image}:${tag}"
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${env.NEXUS_REGISTRY} -u \$NEXUS_USER --password-stdin"
        sh "docker build --build-arg VERSION=${tag} -t ${fullImage} -f ${serviceDir}/Dockerfile ."
        sh "docker push ${fullImage}"
        sh """
            mkdir -p build
            docker inspect --format='{{index .RepoDigests 0}}' ${fullImage} | tee build/image-digest.txt
        """
        archiveArtifacts artifacts: 'build/image-digest.txt', fingerprint: true
    }
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
    sh """
        cd tests/e2e
        npm install
        npx playwright install --with-deps chromium
        GATEWAY_URL=http://${env.DEV_PRIVATE_IP}:8087 npx playwright test
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
