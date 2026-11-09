# CircleGuard Shared Library

The `circleguard` Jenkins Shared Library lives in this directory. JCasC loads it
with `libraryPath: jenkins`, so the entry point is `jenkins/vars/`.

## Global variables

| File | Purpose |
|---|---|
| `vars/ci.groovy` | Build, unit tests, JaCoCo, SonarQube, quality gate, integration, package, publish, Docker, archive, deploy, E2E. |
| `vars/ciServices.groovy` | Service catalog and selective execution (`auto`, `all`, or one service). |
| `vars/ciVersion.groovy` | Semantic version from Git tags and Conventional Commits; creates `vX.Y.Z` tags on `main`. |
| `vars/ciNotify.groovy` | GitHub PR/commit comment and Slack notifications. |

## Pipeline usage

```groovy
@Library('circleguard') _

def services = ciServices.resolve(params.SERVICE ?: 'auto')
services.each { ci.build(it) }
```

The multibranch job discovers branches and pull requests; the Jenkinsfile at the
repository root orchestrates the reusable steps.

## Semantic versioning

`ciVersion.resolve()` uses the latest `vX.Y.Z` Git tag and the Conventional
Commits since that tag. Breaking changes bump major, `feat` bumps minor, and any
other commit bumps patch. On `main`, `ciVersion.createTag()` pushes the
`vX.Y.Z` tag before Docker publication. The pipeline never publishes `latest`.
