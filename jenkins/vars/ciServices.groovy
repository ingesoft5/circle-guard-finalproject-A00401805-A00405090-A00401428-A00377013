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
                    docker        : true
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
                    integration   : false,
                    publish       : true,
                    docker        : true
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
                    integration   : false,
                    publish       : false,
                    docker        : false
            ],
            [
                    short         : 'identity',
                    name          : 'circleguard-identity-service',
                    path          : ':services:circleguard-identity-service',
                    dir           : 'services/circleguard-identity-service',
                    projectKey    : 'circleguard-identity-service',
                    display       : 'CircleGuard Identity',
                    sonarSettings : '',
                    jacoco        : false,
                    integration   : false,
                    publish       : false,
                    docker        : false
            ],
            [
                    short         : 'promotion',
                    name          : 'circleguard-promotion-service',
                    path          : ':services:circleguard-promotion-service',
                    dir           : 'services/circleguard-promotion-service',
                    projectKey    : 'circleguard-promotion-service',
                    display       : 'CircleGuard Promotion',
                    sonarSettings : '',
                    jacoco        : false,
                    integration   : false,
                    publish       : false,
                    docker        : false
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
                    integration   : false,
                    publish       : false,
                    docker        : false
            ],
            [
                    short         : 'file',
                    name          : 'circleguard-file-service',
                    path          : ':services:circleguard-file-service',
                    dir           : 'services/circleguard-file-service',
                    projectKey    : 'circleguard-file-service',
                    display       : 'CircleGuard File',
                    sonarSettings : '',
                    jacoco        : false,
                    integration   : false,
                    publish       : false,
                    docker        : false
            ],
            [
                    short         : 'dashboard',
                    name          : 'circleguard-dashboard-service',
                    path          : ':services:circleguard-dashboard-service',
                    dir           : 'services/circleguard-dashboard-service',
                    projectKey    : 'circleguard-dashboard-service',
                    display       : 'CircleGuard Dashboard',
                    sonarSettings : '',
                    jacoco        : false,
                    integration   : false,
                    publish       : false,
                    docker        : false
            ]
    ]
}

def resolve(String mode) {
    def requested = (mode ?: 'auto').trim()
    if (requested == 'all') {
        // "all" cubre los servicios con JaCoCo y quality gate de cobertura real.
        return catalog().findAll { it.jacoco }
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
    // En main no hay CHANGE_TARGET: se construyen los servicios CI para no
    // dejar main sin validar cuando el diff contra sí mismo está vacío.
    if (!env.CHANGE_TARGET?.trim() && (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master')) {
        echo 'Build de main: se construyen todos los servicios con JaCoCo.'
        return catalog().findAll { it.jacoco }
    }
    def target = env.CHANGE_TARGET?.trim() ?: 'main'
    def base = "origin/${target}"
    def diff = ''
    try {
        sh "git fetch --no-tags origin ${target}"
        diff = sh(script: "git diff --name-only ${base}...HEAD", returnStdout: true).trim()
    } catch (Exception ex) {
        echo "No se pudo calcular el diff contra ${base}: ${ex.message}. Se construyen los servicios CI."
        return catalog().findAll { it.jacoco }
    }
    if (!diff) {
        echo "Sin cambios de servicio respecto a ${base}."
        return []
    }
    def files = diff.readLines().findAll { it.trim() }
    def globalChange = files.any { path ->
        path == 'build.gradle.kts' ||
                path == 'settings.gradle.kts' ||
                path == 'gradle.properties' ||
                path == 'Jenkinsfile' ||
                path == 'sonar-project.properties' ||
                path == 'gradlew' ||
                path == 'gradlew.bat' ||
                path.startsWith('gradle/') ||
                path.startsWith('jenkins/')
    }
    if (globalChange) {
        echo "Cambio global detectado; se construyen los servicios CI: ${files.join(', ')}"
        return catalog().findAll { it.jacoco }
    }
    def selected = catalog().findAll { service ->
        files.any { path -> path.startsWith("${service.dir}/") }
    }
    if (!selected) {
        echo "Cambios sin servicio asociado: ${files.join(', ')}"
    }
    return selected
}
