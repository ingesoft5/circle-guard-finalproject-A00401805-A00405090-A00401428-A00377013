// CG-DOC: Failure notifications through GitHub (PR/commit comment) and Slack fallback.
def failure(List services) {
    def names = serviceNames(services)
    def author = env.CHANGE_AUTHOR?.trim()
    def mention = author ? "@${author}" : ''
    def commitAuthor = ''
    try {
        commitAuthor = sh(script: "git show -s --format='%an' HEAD", returnStdout: true).trim()
    } catch (Exception ignored) {
        commitAuthor = 'desconocido'
    }
    def body = """${mention} El pipeline de CircleGuard falló.

- Servicios: ${names}
- Rama: ${env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'desconocida'}
- Commit: ${env.GIT_COMMIT ?: 'desconocido'}
- Autor del commit: ${commitAuthor}
- Build: ${env.BUILD_URL}
- Etapa: ${env.STAGE_NAME ?: 'desconocida'}
"""
    try {
        githubComment(body)
    } catch (Exception ex) {
        echo "No se pudo comentar en GitHub: ${ex.message}"
    }
    try {
        slack('failure', "${names} falló. ${env.BUILD_URL}")
    } catch (Exception ex) {
        echo "No se pudo notificar a Slack: ${ex.message}"
    }
}

def success(List services) {
    try {
        slack('success', "${serviceNames(services)} completó correctamente. ${env.BUILD_URL}")
    } catch (Exception ex) {
        echo "No se pudo notificar a Slack: ${ex.message}"
    }
}

def serviceNames(List services) {
    if (services) {
        return services.collect { it.short }.join(', ')
    }
    return env.SERVICES ?: 'sin servicios'
}

def githubComment(String body) {
    if (!env.GITHUB_OWNER || !env.GITHUB_REPO) {
        echo 'GITHUB_OWNER/GITHUB_REPO no configurados; comentario omitido.'
        return
    }
    def endpoint
    if (env.CHANGE_ID) {
        endpoint = "https://api.github.com/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/issues/${env.CHANGE_ID}/comments"
    } else if (env.GIT_COMMIT) {
        endpoint = "https://api.github.com/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/commits/${env.GIT_COMMIT}/comments"
    } else {
        echo 'Sin CHANGE_ID ni GIT_COMMIT; comentario omitido.'
        return
    }
    writeFile file: '.ci-notification.md', text: body
    withEnv(["GITHUB_ENDPOINT=${endpoint}"]) {
        withCredentials([usernamePassword(credentialsId: 'github-creds',
                usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
            sh '''
                set +x
                body_json=$(jq -Rs . < .ci-notification.md)
                payload=$(jq -n --argjson body "$body_json" '{body:$body}')
                curl -fsS -X POST \
                  -H "Authorization: token ${GH_TOKEN}" \
                  -H "Accept: application/vnd.github+json" \
                  -H "Content-Type: application/json" \
                  -d "$payload" "$GITHUB_ENDPOINT" >/dev/null
            '''
        }
    }
}

def slack(String status, String text) {
    if (!env.SLACK_WEBHOOK_URL?.trim()) {
        echo 'SLACK_WEBHOOK_URL no configurado; notificación omitida.'
        return
    }
    def color = status == 'success' ? 'good' : 'danger'
    withEnv(["SLACK_TEXT=${text}", "SLACK_COLOR=${color}"]) {
        sh '''
            set +x
            jq -n --arg text "$SLACK_TEXT" --arg color "$SLACK_COLOR" \
              '{attachments:[{color:$color,text:$text}]}' > .ci-slack.json
            curl -fsS -X POST -H 'Content-Type: application/json' \
              --data @.ci-slack.json "$SLACK_WEBHOOK_URL"
        '''
    }
}
