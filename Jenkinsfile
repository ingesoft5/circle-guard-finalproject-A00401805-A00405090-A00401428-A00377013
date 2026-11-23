// CG-DOC: Jenkins multibranch pipeline for the CircleGuard services.
// The multibranch job discovers branches and pull requests; this file orchestrates
// reusable stages from the circleguard Shared Library (jenkins/vars).
// Pin the shared library to main. The JCasC default version can be stale on the
// Jenkins host, and a PR cannot use library vars that only exist in its own branch.
@Library('circleguard@main') _

def services = []
def javaServices = []
def resolvedFiles = []
def autoDiffAvailable = false

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
            echo "Environment variable not set in the container: ${name}"
        }
    }
}

// The Docker daemon (not this container) resolves registry names. Nexus is published
// on the host port 5000 over plain HTTP, and Docker only trusts loopback names by
// default; using the host IP makes `docker login` negotiate HTTPS and fail with
// "server gave HTTP response to HTTPS client". Map the local host alias to loopback
// while keeping env.NEXUS_REGISTRY for Kubernetes manifests and evidence.
def dockerRegistryForDaemon() {
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
        def loopback = "127.0.0.1:${port}"
        echo "Registry ${registry} is the local plain-HTTP Nexus: Docker daemon will use ${loopback}."
        return loopback
    }
    return registry
}

// The shared library pinned to main can be one commit behind this Jenkinsfile
// (a PR cannot see its own library changes). The local catalog fixes the flags
// needed for the deliverable without blocking the PR build. It also supplies the
// mobile entry until the shared-library catalog reaches main.
def localMobileService() {
    return [
            short         : 'mobile',
            name          : 'circleguard-mobile',
            path          : '',
            dir           : 'mobile',
            projectKey    : 'circleguard-mobile',
            display       : 'CircleGuard Mobile',
            sonarSettings : 'mobile/sonar-project.properties',
            type          : 'node',
            jacoco        : true,
            integration   : false,
            publish       : false,
            docker        : false,
            deploy        : false,
            healthcheck   : false
    ]
}

def enrichServices(List resolved, boolean includeMobile) {
    def selected = resolved.findAll { it.short != 'mobile' || includeMobile }
    if (includeMobile && !selected.any { it.short == 'mobile' }) {
        selected << localMobileService()
    }
    def deployable = ['gateway', 'auth', 'identity', 'promotion', 'form', 'notification']
    def covered = ['gateway', 'auth', 'identity', 'promotion', 'form', 'notification',
                   'file', 'dashboard', 'mobile']
    selected.each { service ->
        service.docker = service.type == 'node' ? false : true
        service.deploy = service.type == 'node' ? false : deployable.contains(service.short)
        if (covered.contains(service.short)) {
            service.jacoco = true
        }
        if (['gateway', 'auth', 'identity', 'promotion', 'form', 'notification', 'dashboard'].contains(service.short)) {
            service.healthcheck = true
        }
        // HU-FILE-04: the shared library is pinned to main, so this PR cannot see the
        // catalog flag change; enable the existing Integration Tests stage for file here.
        if (service.short == 'file') {
            service.integration = true
        }
        if (service.short == 'promotion') {
            service.sonarSettings = 'services/circleguard-promotion-service/sonar-project.properties'
        }
        // Same override pattern: the pinned main library still has an empty
        // sonarSettings for notification, which made sonar-scanner fall back to the
        // root gateway settings and fail (PR-154 build #7). The catalog fix in this
        // branch makes it permanent once merged; this line keeps THIS build green.
        if (service.short == 'notification') {
            service.sonarSettings = 'services/circleguard-notification-service/sonar-project.properties'
        }
    }
    javaServices = selected.findAll { it.type != 'node' && it.short != 'mobile' }
    return selected
}

// Mobile is resolved from the same diff as the Java services. The library pinned
// to main predates the mobile catalog entry, so this bridge keeps PR builds honest.
def isMobileSelected(String mode) {
    def requested = (mode ?: 'auto').trim()
    if (requested == 'all' || requested == 'mobile') {
        return true
    }
    if (requested != 'auto') {
        return false
    }
    if ((env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master') && !env.CHANGE_TARGET?.trim()) {
        return true
    }
    if (!autoDiffAvailable) {
        // The diff could not be computed; resolveServicesLocal() already fell back
        // to every Java service, so mobile coverage must be included as well.
        return true
    }
    return resolvedFiles.any { it == 'mobile' || it.startsWith('mobile/') }
}

// TEMPORARY: the library is pinned to main (@Library('circleguard@main')), so a PR
// build cannot see the changes to jenkins/vars/ciServices.groovy that the PR itself
// carries. Until the selective-execution fix reaches main, this local copy applies
// the same rule (E1.4/P1.4) to the PR that fixes it. REMOVE after merging to main.
// A deploy path matches a service when a segment equals the short name or starts
// with it followed by -, _, . or a digit.
def matchesDeployServiceLocal(String path, String shortName) {
    return path.tokenize('/').any { segment ->
        if (segment == shortName) {
            return true
        }
        if (!segment.startsWith(shortName)) {
            return false
        }
        def rest = segment.substring(shortName.length())
        if (!rest) {
            return false
        }
        def first = rest.substring(0, 1)
        return first == '-' || first == '_' || first == '.' || first ==~ /[0-9]/
    }
}

// True only for paths that change the Gradle build of every module.
def isGlobalBuildFileLocal(String path) {
    def candidates = ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
                      'gradlew', 'gradlew.bat', 'gradle/', 'sonar-project.properties']
    return candidates.any { candidate ->
        candidate.endsWith('/') ? path.startsWith(candidate) : path == candidate
    }
}

// Pure file-to-service selection, mirroring ciServices.selectFromFiles() from main.
def selectServicesFromFilesLocal(List files) {
    def catalog = ciServices.catalog()
    if (files.any { path -> isGlobalBuildFileLocal(path) }) {
        echo 'Global build change; building all services.'
        return catalog
    }
    def selected = []
    files.each { path ->
        if (path.startsWith('deploy/')) {
            def matched = catalog.findAll { service ->
                service.type != 'node' && matchesDeployServiceLocal(path, service.short)
            }
            matched.each { service ->
                if (!selected.contains(service)) selected << service
            }
            if (!matched) {
                echo "Shared deploy change; no Java service matched: ${path}"
            }
        } else if (path == 'Jenkinsfile' || path.startsWith('jenkins/')) {
            echo "CI-only change; no Java service selected: ${path}"
        } else {
            catalog.findAll { service -> path.startsWith("${service.dir}/") }.each { service ->
                if (!selected.contains(service)) selected << service
            }
        }
    }
    // CPS-safe ordering: no comparator .sort{} and no spread (*.short); main build #36 died there.
    def ordered = []
    catalog.each { svc ->
        if (selected.contains(svc)) ordered << svc
    }
    return ordered
}

// Resolves auto/all/<service> using the same contract as ciServices.resolve() on main.
def resolveServicesLocal(String mode) {
    def requested = (mode ?: 'auto').trim()
    if (requested == 'mobile') {
        return [localMobileService()]
    }
    if (requested == 'all') {
        return ciServices.catalog()
    }
    if (requested == 'auto') {
        if ((env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master') && !env.CHANGE_TARGET?.trim()) {
            echo 'Main branch build: building all services.'
            autoDiffAvailable = true
            return ciServices.catalog()
        }
        def target = env.CHANGE_TARGET?.trim() ?: 'main'
        def diff = ''
        try {
            // Multibranch workspaces use a restricted refspec, so create origin/<target> explicitly.
            sh "git fetch --no-tags origin +refs/heads/${target}:refs/remotes/origin/${target}"
            diff = sh(script: "git diff --name-only origin/${target}...HEAD", returnStdout: true).trim()
        } catch (Exception ex) {
            echo "Could not compute the diff against origin/${target}: ${ex.message}. Building all services."
            autoDiffAvailable = false
            resolvedFiles = []
            return ciServices.catalog()
        }
        if (!diff) {
            echo "No service changes relative to origin/${target}."
            autoDiffAvailable = true
            resolvedFiles = []
            return []
        }
        def files = diff.readLines().findAll { it.trim() }
        autoDiffAvailable = true
        resolvedFiles = files
        def selected = selectServicesFromFilesLocal(files)
        if (!selected) {
            echo "Changes with no matching Gradle service: ${files.join(', ')}"
        }
        return selected
    }
    def svc = ciServices.catalog().find { it.short == requested || it.name == requested }
    if (!svc) {
        error "Unknown service: ${requested}."
    }
    return [svc]
}

// The Expo client is not a Gradle module; it is built separately and published with
// the same semantic tag as the Java images.
def dockerBuildPushClientLocal(String tag) {
    def registry = dockerRegistryForDaemon()
    def image = "${registry}/circleguard-docker/web:${tag}"
    def publicImage = "${env.NEXUS_REGISTRY}/circleguard-docker/web:${tag}"
    def commit = env.GIT_COMMIT ?: 'unknown'
    def source = env.GITHUB_REPO_URL ?: 'unknown'
    def digest = ''
    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
            usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
        sh "echo \$NEXUS_PASSWORD | docker login ${registry} -u \$NEXUS_USER --password-stdin"
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
        sh "printf '%s\\n%s\\ncommit=%s\\n' '${publicImage}' '${digest}' '${commit}' >> image-digest.txt"
    }
    return digest
}

def ensureTrivyBinaryLocal() {
    sh '''
        set -eu
        if ! command -v trivy >/dev/null 2>&1 && [ ! -x "${WORKSPACE}/.trivy-bin/trivy" ]; then
            echo "Extracting the Trivy binary from aquasec/trivy:0.57.1..."
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
    def registry = dockerRegistryForDaemon()
    images.each { service ->
        def image = "${registry}/circleguard-docker/${service.short}:${tag}"
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
            echo "OWASP ZAP found blocking vulnerabilities in ${namespace} before promotion to production." >&2
            exit 1
        fi
    """
    archiveArtifacts artifacts: "build/reports/security/zap-${environment}.txt", allowEmptyArchive: true
}

// The three UI flows (login, questionnaire, access) live in tests/e2e. The on-premise
// E2E seeds Neo4j and publishes survey.submitted via kubectl exec, then runs against
// the k3d Ingress; see jenkins/vars/ci.groovy and tests/e2e/playwright.config.ts.

// Shared library vars added in the same PR are not visible to that PR build:
// @Library loads from LIBRARY_BRANCH, not from the checked-out PR branch.
// Keep the semver helpers here so the pipeline can validate itself.
def normalizeSemver(String value) {
    def candidate = (value ?: '').trim()
    if (candidate.startsWith('v')) {
        candidate = candidate.substring(1)
    }
    if (!(candidate ==~ /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/)) {
        error "Invalid version '${value}'. Use semver format X.Y.Z or vX.Y.Z."
    }
    return candidate
}

def resolveSemver(String override) {
    def provided = (override ?: '').trim()
    if (provided) {
        echo "Semantic version supplied as a parameter: ${provided}."
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
        echo "HEAD already has the exact tag ${exactTag}; publishing version ${exactVersion}."
        return exactVersion
    }

    def lastTag = sh(
            script: "git tag --list 'v[0-9]*' --sort=-v:refname | head -n1",
            returnStdout: true
    ).trim()
    if (!lastTag) {
        echo 'No previous semver tags; the first published version will be 1.0.0.'
        return '1.0.0'
    }
    def base = normalizeSemver(lastTag)
    def range = "${lastTag}..HEAD"

    def subjects = sh(script: "git log --format='%s' ${range}", returnStdout: true).trim()
    if (!subjects) {
        echo "No new commits since ${lastTag}; keeping ${base}."
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
    echo "Calculated semantic version: ${next} (base ${base}, breaking=${breaking}, feat=${feature})."
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
                echo "Tag v${tag} already exists at \$existing and does not point to the current commit." >&2
                exit 1
            fi
            if [ -z "\$existing" ]; then
                git tag "v${tag}" "\${GIT_COMMIT}"
                git push "https://\${GH_USER}:\${GH_TOKEN}@github.com/\${GITHUB_OWNER}/\${GITHUB_REPO}.git" "refs/tags/v${tag}"
                echo "Tag v${tag} published on \${GIT_COMMIT}."
            else
                echo "Tag v${tag} already points to the current commit; not recreating it."
            fi
        """
    }
}

def writeReleaseNotes(String version) {
    // Generated from conventional commits; the previous tag defines the range.
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

Date: ${date}

## Changes

${changes ?: '- No recorded changes.'}

## Artifacts

- Immutable Docker images tagged `v${version}`.
- JARs published to Nexus.
- Coverage and quality gate available in SonarQube.
"""
    writeFile file: "docs/release-notes/v${version}.md", text: notes
    archiveArtifacts artifacts: "docs/release-notes/v${version}.md", fingerprint: true
}

def publishGitHubRelease(String version) {
    // Publishes the notes as a GitHub Release. If a release already exists for the tag,
    // update its body so a pipeline retry stays idempotent.
    withCredentials([usernamePassword(credentialsId: 'github-creds',
            usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
        sh """
            set -eu
            notes="docs/release-notes/v${version}.md"
            if [ ! -f "\$notes" ]; then
                echo "No release notes for v${version}; skipping the release."
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
                echo "GitHub Release v${version} updated (id=\$id)."
            else
                curl -fsS -X POST -H "Authorization: token \$GH_TOKEN" \
                    -H 'Content-Type: application/json' "\$api" -d "\$payload" >/dev/null
                echo "GitHub Release v${version} published."
            fi
        """
    }
}

// ---------- Publish idempotency ---------------------------------------------------
// A rerun of the same commit keeps the same TAG. Immutable targets (Nexus) and
// mutable ones (Docker registry) must skip what already exists, without failing
// or duplicating evidence.
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
                description: 'auto = only modified services; all = all services.'
        )
        string(name: 'TAG', defaultValue: '', description: 'Semver tag. Empty = derive from Git tags and Conventional Commits.')
        booleanParam(name: 'SKIP_INTEGRATION', defaultValue: false, description: 'Skip integration tests.')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Compatibility: on main, deployment is automatic; it does not apply to other branches.')
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'If set, deploys that tag to production without rebuilding.')
    }

    stages {
        stage('Preflight resources') {
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    // All numeric comparisons stay in awk: the Jenkins sandbox rejects
                    // Groovy static calls such as Double.parseDouble and Integer.parseInt.
                    sh script: '''
                        load=$(awk '{print $1}' /proc/loadavg)
                        avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
                        echo "Host: load=${load}, available memory=${avail}MB."
                        if awk -v l="$load" -v a="$avail" 'BEGIN { exit !(l > 14 || a < 2048) }'; then
                            echo "WARNING: host under pressure; integration tests may take longer than usual."
                        fi
                        if awk -v l="$load" -v a="$avail" 'BEGIN { exit !(l > 24 || a < 512) }'; then
                            echo "Host lacks capacity to run the pipeline (load=${load}, available=${avail}MB)."
                            exit 1
                        fi
                    '''
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Checkout') {
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { ci.checkoutRepo() }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Resolve services') {
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    loadEnvironment('NEXUS_REGISTRY', 'NEXUS_URL', 'TOOLS_IP', 'TOOLS_PRIVATE_IP',
                            'GITHUB_OWNER', 'GITHUB_REPO')
                    env.DOCKER_REGISTRY = dockerRegistryForDaemon()
                    echo "Docker login target: ${env.DOCKER_REGISTRY ?: 'none'} (advertised registry: ${env.NEXUS_REGISTRY ?: 'none'})"

                    // A rerun must not duplicate links or digests in the artifacts.
                    writeFile file: 'sonar-links.txt', text: ''
                    writeFile file: 'image-digest.txt', text: ''

                    if (params.ROLLBACK_TAG?.trim()) {
                        env.TAG = normalizeSemver(params.ROLLBACK_TAG.trim())
                        services = []
                        javaServices = []
                        env.MOBILE_CHANGED = 'false'
                        echo "Rollback requested: ${env.TAG}"
                    } else {
                        env.TAG = resolveSemver(params.TAG ?: '')
                        def requestedService = params.SERVICE ?: 'auto'
                        def resolved = resolveServicesLocal(requestedService)
                        def mobileSelected = isMobileSelected(requestedService)
                        env.MOBILE_CHANGED = mobileSelected.toString()
                        services = enrichServices(resolved, mobileSelected)
                        env.SERVICES = services.collect { it.short }.join(',')
                        echo "Semantic version: ${env.TAG}"
                        echo "Components to build: ${env.SERVICES ?: 'none'}"
                        echo "Mobile coverage: ${env.MOBILE_CHANGED}"
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Mobile tests + coverage') {
            when { expression { env.MOBILE_CHANGED == 'true' && !params.ROLLBACK_TAG?.trim() } }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    timeout(time: 15, unit: 'MINUTES') {
                        echo 'Jest coverage for circleguard-mobile (global threshold >= 60%)...'
                        dir('mobile') {
                            sh 'npm ci --no-audit --no-fund'
                            sh 'npm run test:coverage'
                        }
                    }
                }
            }
            post {
                failure {
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Unit Tests + JaCoCo') {
            when { expression { javaServices } }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    javaServices.each { service ->
                        timeout(time: 10, unit: 'MINUTES') {
                            echo "Unit tests and coverage for ${service.name}..."
                            ci.unitTest(service)
                        }
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Build') {
            when { expression { javaServices } }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    javaServices.each { service ->
                        timeout(time: 10, unit: 'MINUTES') {
                            echo "Building ${service.name}..."
                            ci.build(service)
                        }
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('SonarQube + Quality Gate') {
            when { expression { (javaServices || env.MOBILE_CHANGED == 'true') && !params.ROLLBACK_TAG?.trim() } }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    def analyzed = []
                    analyzed.addAll(javaServices)
                    analyzed.addAll(services.findAll { it.type == 'node' })
                    analyzed.each { service ->
                        if (service.jacoco) {
                            echo "Analyzing ${service.name} with SonarQube..."
                            ci.sonarScan(service)
                            ci.qualityGate(service)
                        } else {
                            echo "SonarQube skipped for ${service.name}: no coverage configured."
                        }
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Integration Tests') {
            when { expression { javaServices && !params.SKIP_INTEGRATION && !params.ROLLBACK_TAG?.trim() } }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    javaServices.each { service ->
                        if (service.integration) {
                            timeout(time: 12, unit: 'MINUTES') {
                                echo "Integration tests for ${service.name}..."
                                ci.integrationTest(service)
                            }
                        } else {
                            echo "Integration skipped for ${service.name}."
                        }
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Trivy IaC Security Gate') {
            when { expression { !params.ROLLBACK_TAG?.trim() } }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { trivyScanIaCLocal() }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Package') {
            when { expression { javaServices && !params.ROLLBACK_TAG?.trim() } }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    javaServices.each { service ->
                        echo "Packaging ${service.name}..."
                        ci.packageService(service)
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Git tag') {
            when {
                allOf {
                    branch 'main'
                    expression {
                        javaServices && javaServices.any { it.publish || it.docker } && !params.ROLLBACK_TAG?.trim()
                    }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { createSemverTag(env.TAG) }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Release notes') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { writeReleaseNotes(env.TAG) }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('GitHub Release') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { publishGitHubRelease(env.TAG) }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Publish') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    javaServices.each { service ->
                        if (service.publish && nexusArtifactExists(service.name, env.TAG)) {
                            echo "Publish skipped: ${service.name}:${env.TAG} already exists in Nexus (idempotent retry)."
                        } else {
                            ci.publish(service)
                        }
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Docker') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    // Preserve idempotent retries: existing images need no base-image catalog lookup.
                    // New builds still use the Nexus mirror through ci.dockerBuildPush.
                    def images = javaServices.findAll { it.docker }
                    if (!images) {
                        echo 'No selected service publishes an image.'
                    }
                    images.each { service ->
                        def repository = "circleguard-docker/${service.short}"
                        if (dockerImageExists(repository, env.TAG)) {
                            def digest = registryDigest(repository, env.TAG) ?: 'sha256:<unknown>'
                            def commit = env.GIT_COMMIT ?: 'unknown'
                            echo "Docker skipped: ${env.NEXUS_REGISTRY}/${repository}:${env.TAG} already published (${digest})."
                            sh "printf '%s\\n%s\\ncommit=%s\\n' '${env.NEXUS_REGISTRY}/${repository}:${env.TAG}' '${digest}' '${commit}' >> image-digest.txt"
                        } else {
                            def digest = ci.dockerBuildPush(service, env.TAG)
                            echo "Published digest (${service.short}): ${digest}"
                        }
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Docker client') {
            when {
                allOf {
                    branch 'main'
                    expression { (javaServices || env.MOBILE_CHANGED == 'true') && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    def repository = 'circleguard-docker/web'
                    if (dockerImageExists(repository, env.TAG)) {
                        def digest = registryDigest(repository, env.TAG) ?: 'sha256:<unknown>'
                        def commit = env.GIT_COMMIT ?: 'unknown'
                        echo "Docker client skipped: ${env.NEXUS_REGISTRY}/${repository}:${env.TAG} already published (${digest})."
                        sh "printf '%s\\n%s\\ncommit=%s\\n' '${env.NEXUS_REGISTRY}/${repository}:${env.TAG}' '${digest}' '${commit}' >> image-digest.txt"
                    } else {
                        def digest = dockerBuildPushClientLocal(env.TAG)
                        echo "Published digest (web): ${digest}"
                    }
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Trivy Image Security Gate') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script {
                    def images = javaServices.findAll { it.docker }
                    trivyScanImagesLocal(images, env.TAG)
                }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Deploy dev') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { ci.deploy('dev', env.TAG) }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('E2E') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.SKIP_INTEGRATION && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { ci.e2e() }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Deploy stage') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { ci.deploy('stage', env.TAG) }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('OWASP ZAP Security Gate') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.SKIP_INTEGRATION && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { owaspZapGateLocal('stage') }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Approval prod') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                input message: 'Approve production deployment', ok: 'Deploy'
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
        }

        stage('Deploy prod') {
            when {
                allOf {
                    branch 'main'
                    expression { javaServices && !params.ROLLBACK_TAG?.trim() }
                }
            }
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { ci.deploy('prod', env.TAG) }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
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
            steps {
                script { env.FAILED_STAGE = env.STAGE_NAME }
                script { ci.deploy('prod', params.ROLLBACK_TAG) }
            }
            post {
                failure {
                    // Preserve the real stage if the stage body aborted before the
                    // entry capture could be observed by the global post block.
                    script { if (!env.FAILED_STAGE?.trim()) env.FAILED_STAGE = env.STAGE_NAME }
                }
            }
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
                // The library pinned to main does not know about LCOV yet; archive
                // the mobile evidence locally until the ci.groovy change is merged.
                if (env.MOBILE_CHANGED == 'true' && fileExists('mobile/coverage/lcov.info')) {
                    archiveArtifacts artifacts: 'mobile/coverage/lcov.info,mobile/coverage/**',
                            allowEmptyArchive: true, fingerprint: true
                }
            }
        }
        success {
            echo "Pipeline succeeded: ${env.SERVICES ?: 'no services'} ${env.TAG}"
        }
        failure {
            script {
                // Declarative has already overwritten STAGE_NAME with "Declarative: Post Actions".
                // env.FAILED_STAGE was captured at stage entry, so restore it before notifying.
                // The library is still pinned to @main and reads STAGE_NAME; this bridge works
                // without waiting for the ciNotify change to reach main.
                env.STAGE_NAME = env.FAILED_STAGE ?: env.STAGE_NAME
                echo "Actual failure stage: ${env.STAGE_NAME}"
                ciNotify.failure(services)
            }
        }
    }
}
