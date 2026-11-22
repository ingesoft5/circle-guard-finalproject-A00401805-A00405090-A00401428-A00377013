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
    def globalChange = files.any { path ->
        path == 'build.gradle.kts' ||
                path == 'settings.gradle.kts' ||
                path == 'gradle.properties' ||
                path == 'Jenkinsfile' ||
                path == 'sonar-project.properties' ||
                path == 'gradlew' ||
                path == 'gradlew.bat' ||
                path.startsWith('gradle/') ||
                path.startsWith('jenkins/') ||
                path.startsWith('mobile/') ||
                // Solo se excluye del build global el tooling de CI que no altera
                // ni la ejecución de los tests ni el despliegue: Dockerfile y
                // plugins.txt del controller, y el relay smee. El RESTO de
                // deploy/compose/tools (compose con TESTCONTAINERS_*, .env*,
                // jenkins/cac/, ingress/) y todo deploy/k8s SIGUEN siendo globales:
                // pueden afectar la validación (variables de Testcontainers,
                // rutas del E2E, executors/credenciales) o el despliegue de todos.
                (path.startsWith('deploy/') && !(
                        path == 'deploy/compose/tools/jenkins/Dockerfile' ||
                        path == 'deploy/compose/tools/jenkins/plugins.txt' ||
                        path.startsWith('deploy/compose/tools/smee-client/')
                ))
    }
    if (globalChange) {
        echo "Cambio global detectado; se construyen todos los servicios: ${files.join(', ')}"
        return catalog()
    }
    def selected = catalog().findAll { service ->
        files.any { path -> path.startsWith("${service.dir}/") }
    }
    if (!selected) {
        echo "Cambios sin servicio asociado: ${files.join(', ')}"
    }
    return selected
}
