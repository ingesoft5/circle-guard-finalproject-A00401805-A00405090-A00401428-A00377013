<!-- CG-DOC: Biblioteca compartida de CircleGuard. -->
# Biblioteca compartida de CircleGuard

La biblioteca compartida de Jenkins `circleguard` se encuentra en este directorio. JCasC la carga
con `libraryPath: jenkins`, de modo que el punto de entrada es `jenkins/vars/`.

## Variables globales

| Archivo | Propósito |
|---|---|
| `vars/ci.groovy` | Compilación, pruebas unitarias, JaCoCo, SonarQube, quality gate, integración, empaquetado, publicación, Docker, archivado, despliegue, E2E. |
| `vars/ciServices.groovy` | Catálogo de servicios y ejecución selectiva (`auto`, `all` o un servicio). |
| `vars/ciVersion.groovy` | Versión semántica a partir de etiquetas de Git y Conventional Commits; crea etiquetas `vX.Y.Z` en `main`. |
| `vars/ciNotify.groovy` | Comentarios en PR/commit de GitHub y notificaciones de Slack. |

## Uso de la canalización

```groovy
@Library('circleguard') _

def services = ciServices.resolve(params.SERVICE ?: 'auto')
services.each { ci.build(it) }
```

El job multibranch descubre ramas y pull requests; el Jenkinsfile en la raíz del
repositorio orquesta los pasos reutilizables.

## Versionado semántico

`ciVersion.resolve()` usa la última etiqueta Git `vX.Y.Z` y los Conventional
Commits posteriores a esa etiqueta. Los cambios incompatibles incrementan major, `feat` incrementa minor y
cualquier otro commit incrementa patch. En `main`, `ciVersion.createTag()` envía la
etiqueta `vX.Y.Z` antes de la publicación en Docker. La canalización nunca publica `latest`.
