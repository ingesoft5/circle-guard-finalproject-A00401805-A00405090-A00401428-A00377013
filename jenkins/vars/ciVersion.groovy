// CG-DOC: Semantic version resolution from Git tags and Conventional Commits.
// The pipeline only publishes immutable semver image tags; "latest" is never used.

def resolve(String override = '') {
    def provided = (override ?: '').trim()
    if (provided) {
        echo "Versión semántica recibida por parámetro: ${provided}."
        return normalize(provided)
    }

    // Jenkins may not fetch tags on a detached checkout; force them before reading.
    sh 'git fetch --tags --force --quiet'

    def exactTag = sh(
            script: "git describe --tags --exact-match --match 'v[0-9]*' HEAD 2>/dev/null || true",
            returnStdout: true
    ).trim()
    if (exactTag) {
        def exactVersion = normalize(exactTag)
        echo "HEAD ya tiene el tag exacto ${exactTag}; se publica la versión ${exactVersion}."
        return exactVersion
    }

    def lastTag = sh(
            script: "git tag --list 'v[0-9]*' --sort=-v:refname | head -n1",
            returnStdout: true
    ).trim()
    if (!lastTag) {
        echo 'No hay tags semver previos; la primera versión publicada será 1.0.0.'
        return '1.0.0'
    }
    def base = normalize(lastTag)
    def range = "${lastTag}..HEAD"

    def subjects = sh(script: "git log --format='%s' ${range}", returnStdout: true).trim()
    if (!subjects) {
        echo "Sin commits nuevos desde ${lastTag ?: 'el inicio'}; se mantiene ${base}."
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
    echo "Versión semántica calculada: ${next} (base ${base}, breaking=${breaking}, feat=${feature})."
    return next
}

def normalize(String value) {
    def candidate = (value ?: '').trim()
    if (candidate.startsWith('v')) {
        candidate = candidate.substring(1)
    }
    if (!(candidate ==~ /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/)) {
        error "Versión inválida '${value}'. Use el formato semver X.Y.Z o vX.Y.Z."
    }
    return candidate
}

def createTag(String version) {
    def tag = normalize(version)
    withCredentials([usernamePassword(credentialsId: 'github-creds',
            usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
        sh """
            set -eu
            existing=\$(git rev-parse -q --verify "refs/tags/v${tag}" || true)
            if [ -n "\$existing" ] && [ "\$existing" != "\${GIT_COMMIT}" ]; then
                echo "El tag v${tag} ya existe en \$existing y no apunta al commit actual." >&2
                exit 1
            fi
            if [ -z "\$existing" ]; then
                git tag "v${tag}" "\${GIT_COMMIT}"
                git push "https://\${GH_USER}:\${GH_TOKEN}@github.com/\${GITHUB_OWNER}/\${GITHUB_REPO}.git" "refs/tags/v${tag}"
                echo "Tag v${tag} publicado sobre \${GIT_COMMIT}."
            else
                echo "El tag v${tag} ya apunta al commit actual; no se recrea."
            fi
        """
    }
}
