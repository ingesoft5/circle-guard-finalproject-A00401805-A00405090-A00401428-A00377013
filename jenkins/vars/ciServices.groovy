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
                    sonarSettings : '',
                    jacoco        : false,
                    integration   : true,
                    publish       : true,
                    docker        : true,
                    deploy        : false
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
    // E1.4/P1.4 del enunciado: ejecución selectiva por servicio. La autoverificación
    // evita que una ampliación futura del catálogo vuelva a disparar builds completos.
    verifySelection()
    def requested = (mode ?: 'auto').trim()
    if (requested == 'all') {
        // "all" cubre todos los servicios del catálogo.
        return catalog()
    }
    if (requested == 'auto') {
        return changed()
    }
    def service = catalog().find { it.short == requested || it.name == requested }
    if (!service) {
        error "Servicio no reconocido: ${requested}. Use auto, all o uno de: ${catalog()*.short.join(', ')}"
    }
    return [service]
}

def changed() {
    // En main no hay CHANGE_TARGET: se construyen todos los servicios para no
    // dejar main sin validar cuando el diff contra sí mismo está vacío.
    if (!env.CHANGE_TARGET?.trim() && (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master')) {
        echo 'Build de main: se construyen todos los servicios.'
        return catalog()
    }
    def target = env.CHANGE_TARGET?.trim() ?: 'main'
    def base = "origin/${target}"
    def diff = ''
    try {
        // El workspace de rama clona con un refspec restringido a esa rama, así que
        // "git fetch origin main" solo escribe FETCH_HEAD y no crea origin/main.
        // Se pide el destino explícito para que ${base} exista antes del diff.
        sh "git fetch --no-tags origin +refs/heads/${target}:refs/remotes/origin/${target}"
        diff = sh(script: "git diff --name-only ${base}...HEAD", returnStdout: true).trim()
    } catch (Exception ex) {
        echo "No se pudo calcular el diff contra ${base}: ${ex.message}. Se construyen todos los servicios."
        return catalog()
    }
    if (!diff) {
        echo "Sin cambios de servicio respecto a ${base}."
        return []
    }
    def files = diff.readLines().findAll { it.trim() }
    def selected = selectFromFiles(files)
    if (!selected) {
        echo "Cambios sin servicio Java asociado: ${files.join(', ')}"
    }
    return selected
}

// Solo estas rutas cambian la compilación de TODOS los módulos Gradle. mobile/, deploy/,
// docs/, tests/, infra/ y la definición del pipeline NO son globales: mantenerlos aquí era
// lo que saturaba el pipeline.
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

// Un cambio en Jenkinsfile o en la Shared Library no modifica ningún microservicio Java.
// Se omite la validación de servicios, pero verifySelection() sigue comprobando la librería.
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

// Selección pura (sin git), reutilizable por changed() y por la autoverificación.
// - services/<servicio>/...            -> ese servicio
// - deploy/**/<servicio>-*.yml          -> ese servicio (p. ej. promotion-alerts.yml)
// - cualquier otro archivo de deploy/   -> todos, porque es configuración compartida
// - Jenkinsfile, jenkins/...            -> ningún servicio Java (self-check de la librería)
// - mobile/, docs/, tests/, infra/...   -> ningún servicio Java
def selectFromFiles(List files) {
    def catalog = catalog()
    if (files.any { path -> isGlobalBuildFile(path) }) {
        echo 'Cambio global en la build; se construyen todos los servicios.'
        return catalog
    }
    def selected = []
    def sharedDeployChange = false
    files.each { path ->
        if (path.startsWith('deploy/')) {
            def fileName = path.tokenize('/').last()
            def matched = catalog.findAll { service -> fileName.startsWith("${service.short}-") }
            matched.each { service ->
                if (!selected.contains(service)) selected << service
            }
            if (!matched) sharedDeployChange = true
        } else if (isCiOnlyFile(path)) {
            echo "Cambio solo de CI; no selecciona servicios Java: ${path}"
        } else {
            catalog.findAll { service -> path.startsWith("${service.dir}/") }.each { service ->
                if (!selected.contains(service)) selected << service
            }
        }
    }
    if (sharedDeployChange) {
        echo 'Cambio compartido en deploy/; se construyen todos los servicios.'
        return catalog
    }
    return selected.sort { a, b -> catalog*.short.indexOf(a.short) <=> catalog*.short.indexOf(b.short) }
}

// Casos que deben cumplirse en cada build. Si fallan, el pipeline para aquí en vez
// de ejecutar ocho validaciones innecesarias o, peor, dejar un servicio sin validar.
def verifySelection() {
    def assertSelection = { List files, List expected ->
        def actual = selectFromFiles(files)*.short
        if (actual != expected) {
            error "Selección incorrecta para ${files}: esperado ${expected}, obtenido ${actual}"
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
    assertSelection(['build.gradle.kts'], catalog()*.short)
    assertSelection(['deploy/k8s/base/apps.yml'], catalog()*.short)
    assertSelection(['Jenkinsfile'], [])
    assertSelection(['jenkins/vars/ci.groovy'], [])
    assertSelection([
            'Jenkinsfile',
            'services/circleguard-promotion-service/src/main/java/PromotionService.java'
    ], ['promotion'])
    echo 'Verificación de selección por servicio: OK.'
}
