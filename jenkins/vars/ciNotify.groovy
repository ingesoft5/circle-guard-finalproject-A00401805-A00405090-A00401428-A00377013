// CG-DOC: Failure notifications through GitHub (PR/commit comment) plus the channels
// required by the assignment (E1.5): Slack or Discord. GitHub is extra, not the
// only channel. The real failing stage comes from FAILED_STAGE, captured at stage entry
// by the Jenkinsfile: in post.failure, Jenkins has already replaced STAGE_NAME with
// "Declarative: Post Actions".
def failure(List services) {
    def names = serviceNames(services)
    def stage = failedStage()
    def author = env.CHANGE_AUTHOR?.trim()
    def mention = author ? "@${author}" : ''
    def commitAuthor = ''
    try {
        commitAuthor = sh(script: "git show -s --format='%an' HEAD", returnStdout: true).trim()
    } catch (Exception ignored) {
        commitAuthor = 'unknown'
    }
    def body = """${mention} The CircleGuard pipeline failed.

- Services: ${names}
- Branch: ${env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'unknown'}
- Commit: ${env.GIT_COMMIT ?: 'unknown'}
- Commit author: ${commitAuthor}
- Build: ${env.BUILD_URL}
- Stage: ${stage}
"""
    try {
        githubComment(body)
    } catch (Exception ex) {
        echo "Could not comment on GitHub: ${ex.message}"
    }
    notifyAll('failure', "${names} failed in ${stage}. ${env.BUILD_URL}")
}

def success(List services) {
    notifyAll('success', "${serviceNames(services)} completed successfully. ${env.BUILD_URL}")
}

// The Jenkinsfile captures FAILED_STAGE at stage entry. STAGE_NAME is the fallback
// for builds that do not include that capture yet (for example, the library loaded from main).
def failedStage() {
    return env.FAILED_STAGE?.trim() ?: env.STAGE_NAME?.trim() ?: 'unknown'
}

// Send through every configured assignment channel and warn when none is configured,
// so a red pipeline never looks like "no notification".
def notifyAll(String status, String text) {
    def sent = false
    if (env.SLACK_WEBHOOK_URL?.trim()) {
        try {
            slack(status, text)
            sent = true
        } catch (Exception ex) {
            echo "Could not notify Slack: ${ex.message}"
        }
    }
    if (env.DISCORD_WEBHOOK_URL?.trim()) {
        try {
            discord(status, text)
            sent = true
        } catch (Exception ex) {
            echo "Could not notify Discord: ${ex.message}"
        }
    }
    if (!sent) {
        echo 'No assignment notification channel configured (SLACK_WEBHOOK_URL or DISCORD_WEBHOOK_URL); notification is incomplete.'
    }
}

// Human-readable list of selected services for notification bodies.
def serviceNames(List services) {
    if (services) {
        return services.collect { it.short }.join(', ')
    }
    return env.SERVICES ?: 'no services'
}

// Posts the body to the PR (or commit) with github-creds; never fails the build.
def githubComment(String body) {
    if (!env.GITHUB_OWNER || !env.GITHUB_REPO) {
        echo 'GITHUB_OWNER/GITHUB_REPO not set; comment skipped.'
        return
    }
    def endpoint
    if (env.CHANGE_ID) {
        endpoint = "https://api.github.com/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/issues/${env.CHANGE_ID}/comments"
    } else if (env.GIT_COMMIT) {
        endpoint = "https://api.github.com/repos/${env.GITHUB_OWNER}/${env.GITHUB_REPO}/commits/${env.GIT_COMMIT}/comments"
    } else {
        echo 'No CHANGE_ID or GIT_COMMIT; comment skipped.'
        return
    }
    writeFile file: '.ci-notification.md', text: body
    withEnv(["GITHUB_ENDPOINT=${endpoint}"]) {
        withCredentials([usernamePassword(credentialsId: 'github-creds',
                usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
            def status = sh(script: '''
                set +x
                body_json=$(jq -Rs . < .ci-notification.md) || exit 20
                payload=$(jq -n --argjson body "$body_json" '{body:$body}') || exit 21
                curl -fsS -X POST \
                  -H "Authorization: token ${GH_TOKEN}" \
                  -H "Accept: application/vnd.github+json" \
                  -H "Content-Type: application/json" \
                  -d "$payload" "$GITHUB_ENDPOINT" >/dev/null || exit 22
            ''', returnStatus: true)
            if (status != 0) {
                echo "Could not comment on GitHub (code ${status}); the real pipeline error is shown at the end."
            }
        }
    }
}

// Sends a Slack attachment through the configured webhook.
def slack(String status, String text) {
    if (!env.SLACK_WEBHOOK_URL?.trim()) {
        echo 'SLACK_WEBHOOK_URL not set; notification skipped.'
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

// Sends a Discord message through the configured webhook.
def discord(String status, String text) {
    if (!env.DISCORD_WEBHOOK_URL?.trim()) {
        echo 'DISCORD_WEBHOOK_URL not set; notification skipped.'
        return
    }
    // Discord does not understand Slack's payload: use content plus a colored embed.
    def color = status == 'success' ? 3066993 : 15158332
    withEnv(["DISCORD_TEXT=${text}", "DISCORD_COLOR=${color}"]) {
        sh '''
            set +x
            jq -n --arg text "$DISCORD_TEXT" --argjson color "$DISCORD_COLOR" \
              '{content:$text,embeds:[{description:$text,color:$color}]}' > .ci-discord.json
            curl -fsS -X POST -H 'Content-Type: application/json' \
              --data @.ci-discord.json "$DISCORD_WEBHOOK_URL"
        '''
    }
}
