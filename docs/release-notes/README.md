# Release Notes — CircleGuard

El pipeline genera una nota por versión en este directorio:

```text
v<major>.<minor>.<patch>.md
```

## Plantilla

```markdown
# CircleGuard vX.Y.Z — YYYY-MM-DD

**Tag:** vX.Y.Z
**Pipeline:** <URL del build>
**Imágenes:** <registry>/circleguard-docker/<servicio>:X.Y.Z

## Cambios

### Features
- ...

### Fixes
- ...

### Infraestructura y CI
- ...

### Documentación
- ...

## Migraciones y configuración
- ...

## Riesgos y rollback
- Tag estable anterior: vX.Y.Z
- Procedimiento: pipeline con `ROLLBACK_TAG=<tag>`

## Evidencia
- Jenkins:
- SonarQube:
- E2E:
```

## Reglas

- La versión se calcula desde el último tag y los Conventional Commits.
- Nunca se publica `latest`; la imagen siempre lleva semver.
- La nota se archiva en el build y se conserva en el repositorio si el cambio la incluye.
- El stage `GitHub Release` publica o actualiza la release del tag `vX.Y.Z` usando la misma nota (idempotente en reintentos).
- El tag solo se crea sobre `main` después de los checks.
