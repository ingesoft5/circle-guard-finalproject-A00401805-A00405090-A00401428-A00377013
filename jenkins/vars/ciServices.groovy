// CG-DOC: Service catalog and selective execution (auto, all, or one service).
def catalog() {
    return [
            [
                    short         : 'gateway',
                    name          : 'circleguard-gateway-service',
                    path          : ':services:circleguard-gateway-service',
                    dir           : 'services/circleguard-gateway-service',
                    projectKey    : 'circleguard-gateway-service',
                    display       : 'CircleGuard Gateway',
                    sonarSettings : 'sonar-project.properties',
                    jacoco        : true,
                    integration   : true,
                    publish       : true,
                    docker        : true,
                    deploy        : true,
                    healthcheck   : true
            ],
            [
                    short         : 'auth',
                    name          : 'circleguard-auth-service',
                    path          : ':services:circleguard-auth-service',
                    dir           : 'services/circleguard-auth-service',
                    projectKey    : 'circleguard-auth-service',
                    display       : 'CircleGuard Auth',
                    sonarSettings : 'services/circleguard-auth-service/sonar-project.properties',
                    jacoco        : true,
                    integration   : true,
                    publish       : true,
                    docker        : true,
                    deploy        : true
            ],
            [
                    short         : 'form',
                    name          : 'circleguard-form-service',
                    path          : ':services:circleguard-form-service',
                    dir           : 'services/circleguard-form-service',
                    projectKey    : 'circleguard-form-service',
                    display       : 'CircleGuard Form',
                    sonarSettings : 'services/circleguard-form-service/sonar-project.properties',
                    jacoco        : true,
                    integration   : true,
                    publish       : true,
                    docker        : true,
                    deploy        : true
            ],
            [
                    short         : 'identity',
                    name          : 'circleguard-identity-service',
                    path          : ':services:circleguard-identity-service',
                    dir           : 'services/circleguard-identity-service',
                    projectKey    : 'circleguard-identity-service',
                    display       : 'CircleGuard Identity',
                    sonarSettings : 'services/circleguard-identity-service/sonar-project.properties',
                    jacoco        : true,
                    integration   : true,
                    publish       : true,
                    docker        : true,
                    deploy        : true,
                    healthcheck   : true
            ],
            [
                    short         : 'promotion',
                    name          : 'circleguard-promotion-service',
                    path          : ':services:circleguard-promotion-service',
                    dir           : 'services/circleguard-promotion-service',
                    projectKey    : 'circleguard-promotion-service',
                    display       : 'CircleGuard Promotion',
                    sonarSettings : 'services/circleguard-promotion-service/sonar-project.properties',
                    jacoco        : true,
                    integration   : true,
                    publish       : true,
                    docker        : true,
                    deploy        : true,
                    healthcheck   : true
            ],
            [
                    short         : 'notification',
                    name          : 'circleguard-notification-service',
                    path          : ':services:circleguard-notification-service',
                    dir           : 'services/circleguard-notification-service',
                    projectKey    : 'circleguard-notification-service',
                    display       : 'CircleGuard Notification',
                    sonarSettings : 'services/circleguard-notification-service/sonar-project.properties',
                    jacoco        : true,
                    integration   : true,
                    publish       : true,
                    docker        : true,
                    deploy        : true,
                    healthcheck   : true
            ],
            [
                    short         : 'file',
                    name          : 'circleguard-file-service',
                    path          : ':services:circleguard-file-service',
                    dir           : 'services/circleguard-file-service',
                    projectKey    : 'circleguard-file-service',
                    display       : 'CircleGuard File',
                    sonarSettings : 'services/circleguard-file-service/sonar-project.properties',
                    jacoco        : true,
                    integration   : false,
                    publish       : true,
                    docker        : true,
                    deploy        : false
            ],
            [
                    short         : 'dashboard',
                    name          : 'circleguard-dashboard-service',
                    path          : ':services:circleguard-dashboard-service',
                    dir           : 'services/circleguard-dashboard-service',
                    projectKey    : 'circleguard-dashboard-service',
                    display       : 'CircleGuard Dashboard',
                    sonarSettings : 'services/circleguard-dashboard-service/sonar-project.properties',
                    jacoco        : true,
                    integration   : false,
                    publish       : true,
                    docker        : true,
                    deploy        : false
            ]
    ]
}

def resolve(String mode) {
    // Assignment E1.4/P1.4 requires selective execution per service. The self-check
    // prevents a future catalog change from silently triggering full builds again.
    verifySelection()
    def requested = (mode ?: 'auto').trim()
    if (requested == 'all') {
        // "all" covers every service in the catalog.
        return catalog()
    }
    if (requested == 'auto') {
        return changed()
    }
    def service = catalog().find { it.short == requested || it.name == requested }
    if (!service) {
        error "Unknown service: ${requested}. Use auto, all or one of: ${catalog().collect { it.short }.join(', ')}"
    }
    return [service]
}

def changed() {
    // On main there is no CHANGE_TARGET: build every service so main is never left
    // unvalidated when the diff against itself is empty.
    if (!env.CHANGE_TARGET?.trim() && (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master')) {
        echo 'Main branch build: building all services.'
        return catalog()
    }
    def target = env.CHANGE_TARGET?.trim() ?: 'main'
    def base = "origin/${target}"
    def diff = ''
    try {
        // A branch workspace clones with a refspec restricted to that branch, so
        // "git fetch origin main" only writes FETCH_HEAD and does not create origin/main.
        // Request the explicit destination so ${base} exists before the diff.
        sh "git fetch --no-tags origin +refs/heads/${target}:refs/remotes/origin/${target}"
        diff = sh(script: "git diff --name-only ${base}...HEAD", returnStdout: true).trim()
    } catch (Exception ex) {
        echo "Could not compute the diff against ${base}: ${ex.message}. Building all services."
        return catalog()
    }
    if (!diff) {
        echo "No service changes relative to ${base}."
        return []
    }
    def files = diff.readLines().findAll { it.trim() }
    def selected = selectFromFiles(files)
    if (!selected) {
        echo "Changes with no matching Java service: ${files.join(', ')}"
    }
    return selected
}

// Only these paths change the compilation of EVERY Gradle module. mobile/, deploy/,
// docs/, tests/, infra/ and the pipeline definition are NOT global: keeping them here
// was what saturated the pipeline.
def globalBuildPaths() {
    return [
            'build.gradle.kts',
            'settings.gradle.kts',
            'gradle.properties',
            'gradlew',
            'gradlew.bat',
            'gradle/',
            'sonar-project.properties'
    ]
}

// A change to the Jenkinsfile or the shared library does not modify any Java microservice.
// Service validation is skipped, but verifySelection() still checks the library.
def ciOnlyPaths() {
    return ['Jenkinsfile', 'jenkins/']
}

def isGlobalBuildFile(String path) {
    return globalBuildPaths().any { candidate ->
        candidate.endsWith('/') ? path.startsWith(candidate) : path == candidate
    }
}

def isCiOnlyFile(String path) {
    return ciOnlyPaths().any { candidate ->
        candidate.endsWith('/') ? path.startsWith(candidate) : path == candidate
    }
}

// A deploy file belongs to a service if ANY path segment names it:
// deploy/k8s/base/promotion-alerts.yml, components/form-observability/,
// form06-dev/canary/... The character after the name must be -, _, . or a digit,
// so form06-dev maps to form but platform does not map to promotion.
def matchesDeployService(String path, String shortName) {
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

// Pure selection (no git), reused by changed() and by the self-check.
// - services/<service>/...              -> that service
// - deploy/** with the service name in the path -> that service
// - shared deploy/** (overlays, monitoring, ...) -> no Java service;
//   validated by the Trivy IaC gate instead of recompiling all eight modules.
// - Jenkinsfile, jenkins/...            -> no Java service (library self-check)
// - mobile/, docs/, tests/, infra/...   -> no Java service
def selectFromFiles(List files) {
    def catalog = catalog()
    if (files.any { path -> isGlobalBuildFile(path) }) {
        echo 'Global build change; building all services.'
        return catalog
    }
    def selected = []
    files.each { path ->
        if (path.startsWith('deploy/')) {
            def matched = catalog.findAll { service -> matchesDeployService(path, service.short) }
            matched.each { service ->
                if (!selected.contains(service)) selected << service
            }
            if (!matched) {
                echo "Shared deploy change; no Java service matched: ${path}"
            }
        } else if (isCiOnlyFile(path)) {
            echo "CI-only change; no Java service selected: ${path}"
        } else {
            catalog.findAll { service -> path.startsWith("${service.dir}/") }.each { service ->
                if (!selected.contains(service)) selected << service
            }
        }
    }
    // CPS-safe ordering: no comparator closure (Jenkins CPS .sort{} can return the
    // comparator Integer instead of the list, which broke the spread below in main #36).
    def ordered = []
    catalog.each { svc ->
        if (selected.contains(svc)) {
            ordered << svc
        }
    }
    return ordered
}

// Cases that must hold on every build. If one fails, the pipeline stops here instead
// of running eight unnecessary validations or, worse, leaving a service unvalidated.
def verifySelection() {
    def assertSelection = { List files, List expected ->
        def actual = selectFromFiles(files).collect { it.short }
        if (actual != expected) {
            error "Wrong selection for ${files}: expected ${expected}, got ${actual}"
        }
    }
    assertSelection(['services/circleguard-promotion-service/src/main/java/PromotionService.java'], ['promotion'])
    assertSelection([
            'services/circleguard-promotion-service/src/main/java/PromotionService.java',
            'services/circleguard-form-service/src/main/java/FormController.java'
    ], ['form', 'promotion'])
    assertSelection([
            'services/circleguard-promotion-service/src/main/java/PromotionService.java',
            'deploy/k8s/base/promotion-alerts.yml'
    ], ['promotion'])
    assertSelection(['deploy/k8s/base/form-storage.yml'], ['form'])
    assertSelection(['mobile/App.tsx', 'docs/informe/HU-FORM-06.md'], [])
    assertSelection(['docs/informe/HU-FORM-06.md', 'services/circleguard-form-service/src/test/FormTest.java'], ['form'])
    assertSelection(['build.gradle.kts'], catalog().collect { it.short })
    assertSelection(['deploy/k8s/base/apps.yml'], [])
    assertSelection(['deploy/k8s/overlays/dev/kustomization.yml'], [])
    assertSelection(['deploy/k8s/monitoring/prometheus.yml'], [])
    assertSelection(['deploy/k8s/components/form-observability/monitoring/form-alerts.yml'], ['form'])
    assertSelection(['deploy/k8s/form06-dev/canary/form-hu6.yml'], ['form'])
    assertSelection(['Jenkinsfile'], [])
    assertSelection(['jenkins/vars/ci.groovy'], [])
    assertSelection(['Jenkinsfile', 'deploy/k8s/overlays/prod/kustomization.yml'], [])
    assertSelection([
            'Jenkinsfile',
            'services/circleguard-promotion-service/src/main/java/PromotionService.java'
    ], ['promotion'])
    echo 'Per-service selection self-check: OK.'
}
