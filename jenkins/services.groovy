// Service registration and change selection for the root Jenkinsfile.
// This script is loaded from checkout scm so a PR tests its own selection rules.

Map catalog() {
    [
        gateway: [name: 'circleguard-gateway-service', directory: 'services/circleguard-gateway-service',
                  gradlePath: ':services:circleguard-gateway-service', active: true, dependsOn: []],
        form: [name: 'circleguard-form-service', directory: 'services/circleguard-form-service',
               gradlePath: ':services:circleguard-form-service', active: true, dependsOn: ['promotion']],

        // AUTH: aquí se activa Auth cuando su responsable verifique check/bootJar en Jenkins.
        auth: [directory: 'services/circleguard-auth-service', active: false],
        // IDENTITY: aquí se añaden sus rutas Gradle y dependencias antes de activar Identity.
        identity: [directory: 'services/circleguard-identity-service', active: false],
        // PROMOTION: aquí se integra Promotion; sus cambios también deben validar Form.
        promotion: [directory: 'services/circleguard-promotion-service', active: false],
        // NOTIFICATION: aquí se integra Notification con sus pruebas y empaquetado.
        notification: [directory: 'services/circleguard-notification-service', active: false],
        // FILE: aquí se integra File cuando sus pruebas y dependencias estén configuradas.
        file: [directory: 'services/circleguard-file-service', active: false],
        // DASHBOARD: aquí se integra Dashboard con sus rutas y requisitos de ejecución.
        dashboard: [directory: 'services/circleguard-dashboard-service', active: false],
        // WEB: el cliente usa Node/Expo, no tareas Gradle. Necesita su ejecutor propio.
        web: [directory: 'mobile', active: false]
    ]
}

boolean documentation(String path) {
    // Never ignore files in src: text and images can be application resources or fixtures.
    if (path.contains('/src/')) { return false }
    path.startsWith('docs/') || path == 'IAG_REGISTRO.md' ||
            path == 'BRANCHING-STRATEGY.md' || path == 'LICENSE' ||
            path ==~ /(?i)(.*\/)?(readme|changelog|contributing)(\.[^\/]*)?/
}

boolean sharedChange(String path) {
    path in ['Jenkinsfile', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
             'gradlew', 'gradlew.bat', '.dockerignore'] ||
            ['jenkins/', 'vars/', 'gradle/', 'buildSrc/', 'libs/',
             'deploy/compose/tools/jenkins/'].any { path.startsWith(it) }
}

Map selectServices(String target, List changedFiles, boolean reliableBase) {
    def services = catalog()
    def active = services.findAll { id, config -> config.active }.keySet().toList()
    def pending = services.findAll { id, config -> !config.active }.keySet().toList()
    if (!(target in ['auto', 'all'] + active)) {
        throw new IllegalArgumentException("Servicio no integrado o selección inválida: ${target}")
    }
    if (target == 'all') {
        return [services: active, pending: pending, reasons: ['Solicitud manual: todos los servicios integrados.']]
    }
    if (target != 'auto') {
        return [services: [target], pending: [], reasons: ["Solicitud manual: ${target}."]]
    }
    if (!reliableBase) {
        return [services: active, pending: [], reasons: ['Sin referencia Git fiable: validar todos los integrados.']]
    }

    def selected = [] as Set
    def pendingChanges = [] as Set
    def reasons = []
    changedFiles.each { String path ->
        if (documentation(path)) { return }
        if (sharedChange(path)) {
            selected.addAll(active)
            reasons << "Cambio compartido: ${path}"
            return
        }
        def entry = services.find { id, config -> path.startsWith(config.directory + '/') }
        if (entry) {
            if (entry.value.active) {
                selected.add(entry.key)
            } else {
                pendingChanges.add(entry.key)
                selected.add('gateway')
            }
            // Reverse dependencies: Form's integration uses real Promotion classes.
            services.each { id, config ->
                if (config.active && config.dependsOn.contains(entry.key)) { selected.add(id) }
            }
        } else if (path == 'sonar-project.properties' ||
                ['deploy/', 'infra/', 'tests/e2e/'].any { path.startsWith(it) }) {
            selected.add('gateway')
        } else {
            // Unknown executable/configuration changes must not silently skip validation.
            selected.addAll(active)
            reasons << "Ruta sin regla específica: validar todos los integrados (${path})."
        }
    }
    if (!pendingChanges.isEmpty()) {
        reasons << 'Transición: se conserva Gateway como respaldo para servicios pendientes.'
    }
    if (selected.isEmpty()) { reasons << 'Sin cambios ejecutables: no se requieren compilaciones.' }
    [services: active.findAll { selected.contains(it) }, pending: pendingChanges.toList(), reasons: reasons]
}

Map detectChanges(Map context) {
    try {
        String base
        if (context.branch == 'main' && !context.changeTarget) {
            // A manual Form/Gateway run did not validate the other service's changes.
            // Legacy builds and rollback builds cannot establish a complete CI baseline either.
            String previousDescription = context.previousDescription ?: ''
            if (!['[CI:auto]', '[CI:all]'].any { previousDescription.startsWith(it) }) {
                return [files: [], reliable: false, reason: 'La última ejecución exitosa no fue una validación automática o completa.']
            }
            base = context.previousSuccess
            if (!(base ==~ /[0-9a-fA-F]{7,64}/)) {
                return [files: [], reliable: false, reason: 'main aún no tiene una revisión exitosa de referencia.']
            }
            def valid = withEnv(["CG_CI_BASE=${base}"]) {
                sh(returnStatus: true, script: 'git merge-base --is-ancestor "$CG_CI_BASE" HEAD')
            }
            if (valid != 0) {
                return [files: [], reliable: false, reason: 'La última revisión exitosa no es antecesora de HEAD.']
            }
        } else {
            // Compare the entire PR/branch with its target, not only the last commit.
            String target = context.changeTarget ?: 'main'
            def fetched = withEnv(["CG_CI_TARGET=${target}"]) {
                int valid = sh(returnStatus: true,
                        script: 'git check-ref-format "refs/heads/$CG_CI_TARGET"')
                valid == 0 ? sh(returnStatus: true,
                        script: 'git fetch --no-tags origin "refs/heads/$CG_CI_TARGET"') : valid
            }
            if (fetched != 0) {
                return [files: [], reliable: false, reason: 'No se pudo obtener la rama destino; validar ambos servicios.']
            }
            base = sh(returnStdout: true, script: 'git merge-base FETCH_HEAD HEAD').trim()
        }
        def output = withEnv(["CG_CI_BASE=${base}"]) {
            // Include old and new paths for renames, and preserve spaces in filenames.
            sh(returnStdout: true, script: 'git diff --name-only --no-renames -z "$CG_CI_BASE" HEAD')
        }
        [files: output.tokenize('\u0000'), reliable: true, reason: "Referencia Git: ${base}"]
    } catch (InterruptedException interrupted) {
        throw interrupted
    } catch (Exception ignored) {
        [files: [], reliable: false, reason: 'No se pudo determinar el cambio completo; validar ambos servicios.']
    }
}

return this
