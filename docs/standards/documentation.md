# Documentation Standard

## Purpose

This repository uses English for source comments, file headers, API documentation, and configuration notes. Documentation explains intent, responsibilities, inputs, outputs, side effects, and operational constraints. It does not repeat code that is already self-explanatory.

## Language

- All new comments, docstrings, Javadoc, TSDoc, and file headers are written in English.
- Existing product documentation may remain in Spanish when it is part of a course deliverable, but every source/config file must include an English purpose header.
- Commit messages and pull request titles are written in English.

## Required file header

Every source, script, configuration, migration, template, and documentation file starts with a short English block:

```text
CG-DOC: <one sentence describing the file purpose>
```

Use the comment syntax of the file type:

- Java, TypeScript, TSX, JavaScript: `/** CG-DOC: ... */`
- Shell, YAML, properties, env examples: `# CG-DOC: ...`
- SQL: `-- CG-DOC: ...`
- Groovy, Kotlin DSL, Cypher: `// CG-DOC: ...`
- Markdown, HTML: `<!-- CG-DOC: ... -->`
- FreeMarker: `<#-- CG-DOC: ... -->`
- LaTeX: `% CG-DOC: ...`
- CSS: `/* CG-DOC: ... */`

## Java

- Add Javadoc to public classes, interfaces, records, enums, and public methods.
- Document parameters, return values, thrown exceptions, and security assumptions.
- Add `package-info.java` when a package has a non-obvious responsibility.
- Do not comment trivial getters, setters, or generated members.

## TypeScript and React Native

- Use TSDoc for exported hooks, components, contexts, and utilities.
- Document props, return values, side effects, and external dependencies.
- Add a header to screens, layouts, and configuration files.

## SQL

- Document the migration purpose, affected tables, dependencies, and ordering constraints.
- Do not alter executable SQL while documenting it.
- Keep migration files immutable after they have been applied.

## YAML and Docker Compose

- Document each service, exposed port, volume, healthcheck, and required environment variable.
- Mark optional values and safe defaults.
- Never include real secrets.

## Shell scripts

- Start with purpose, usage, prerequisites, environment variables, idempotency behavior, side effects, and cleanup instructions.
- Document functions that perform AWS, Docker, SSH, or file mutations.
- Prefer `set -euo pipefail`.

## Jenkins and Groovy

- Document pipeline stages, parameters, credentials, artifacts, deployment targets, and rollback behavior.
- Keep secrets out of source and logs.

## Documentation files

- Use English titles and summaries.
- Link related modules and operational runbooks.
- Keep the file index updated when adding or removing tracked files.

## Exclusions

The following are documented in `docs/FILE_INDEX.md` but do not receive inline headers:

- Binary assets: PDF, PNG, JPG, TTF, JAR.
- Generated dependency locks: `package-lock.json`.
- Generated IDE output: `bin/`.
- Generated Gradle wrappers: `gradlew`, `gradlew.bat`, wrapper JAR.
- JSON files, because the format does not support comments.
