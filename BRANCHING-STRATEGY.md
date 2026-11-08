<!-- CG-DOC: Branching strategy, commit conventions, pull request rules, and validation hooks. -->
# Branching Strategy and Change Control - CircleGuard

This document defines the branching, integration, and change-control rules for the project, ensuring traceability, continuous integration, and DevOps best practices.

## 1. Chosen Strategy: GitHub Flow

The project applies **GitHub Flow** strictly, keeping `main` in a deployable state (green pipeline). The rules are:

- **Protected `main` branch:** direct pushes are not allowed. Every integration requires a Pull Request.
- **Ephemeral branches:** created from `dev` and kept short-lived.
- **Audit and cleanup:** branches are configured for automatic deletion after merge on GitHub. Traceability remains immutable in the **Merged Pull Requests** section, including code, approvals, and pipeline checks.
- **Peer review:** at least **1 approval** from another developer is required before merging.
- **Required checks:** the Jenkins CI pipeline must run and pass before merging.
- **Merge strategy:** **Squash and Merge** keeps an atomic, clean history in `dev` and `main`.

## 2. Branch Naming (Jira Traceability)

Branches must follow this structure to preserve traceability with Jira user stories:

**Format:** `[<type>/]HU-<MICROSERVICE>-<story number>-<short feature description>`

- **type (optional):** Conventional Commits prefix: `feat/`, `fix/`, `docs/`, `ci/`, `chore/`, `test/`, `refactor/`, `build/`, `perf/`, `style/`.
- **MICROSERVICE:** affected service name **in uppercase** (e.g. `AUTH`, `GATEWAY`, `FRONTEND`, `INFRA`).
- **story number:** numeric Jira task ID.
- **short feature description:** lowercase words separated by hyphens.

**Valid examples:**

- `feat/HU-GATEWAY-01-configurar-servidores`
- `fix/HU-AUTH-03-corregir-expiracion-qr`
- `HU-GATEWAY-01-configurar-servidores`

## 3. Commit Convention (Conventional Commits)

The project uses **Conventional Commits** to enable semantic versioning and release notes. Local commit messages **do not include the Jira ID**, because traceability is provided by the branch name and the pull request title.

**Structure:** `<type> ( <scope> ) : <description>`

**Breaking change:** `<type> ( <scope> ) !: <description>`

**Scope:** mandatory in every commit.

**Language:** all commit messages are written **in English**, even when repository documentation is written in Spanish.

**Validation regex:**

```text
^(feat|fix|docs|ci|chore|test|refactor|build|perf|style)[[:space:]]+\([[:space:]]*[a-z-]+[[:space:]]*\)[[:space:]]+!?:[[:space:]]+.+$
```

**Examples:**

```text
feat ( gateway ) : add secure gate validation
fix ( auth ) : correct QR expiration
feat ( auth ) !: replace role schema
```


**Allowed types and SemVer impact:**

- `feat`: new functionality (MINOR).
- `fix`: bug fix (PATCH).
- `feat!` or `BREAKING CHANGE`: incompatible change (MAJOR).
- `chore`, `ci`, `docs`, `test`, `refactor`, `build`, `perf`, `style`: no version increment.

**Valid examples:**

- `feat(auth): add JWT token validation`
- `fix(gateway): correct QR timestamp`
- `ci(jenkins): add SonarQube analysis stage`
- `chore: initialize folder structure`

## 4. Quality Control and Change Management in Pull Requests

In GitHub Flow, the Pull Request is the core quality gate. To prevent incomplete code from being merged:

1. **Required template:** every PR must use `.github/pull_request_template.md` (fields: *Branch Name, Context, What changed, How to test it*).
2. **PR title (traceability):** the PR title is written **in English** and must include the branch name at the end. With *Squash & Merge*, this title becomes the official commit message in `main`.
   - *Example:* `feat(auth): add login endpoint (HU-AUTH-12-implement-login)`
3. **Conversation resolution:** `main` requires *"Require conversation resolution before merging"*. If a reviewer requests context, merging stays blocked until it is resolved.
4. **Pipeline gate:** no PR can be merged while the Jenkins pipeline is failing.

## 5. Local Validation Tools (Git Hooks)

To prevent human errors before reaching the remote repository, the project includes shared local hooks in `.githooks/`:

- `commit-msg`: validates Conventional Commits and English commit messages.
- `pre-push`: blocks direct pushes to `main` and `dev`, and validates the branch name against `[<type>/]HU-<MICROSERVICE>-<number>-<description>`.

*(Team note: enable the hooks once per machine with `git config core.hooksPath .githooks`.)*
