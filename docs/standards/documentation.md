<!-- CG-DOC: Convenciones de documentación para el código fuente, la configuración y la documentación del proyecto en mantenimiento activo. -->
# Estándar de documentación

## Propósito

Este repositorio usa el inglés para los comentarios del código fuente, los encabezados de archivos, la documentación de API y las notas de configuración. La documentación explica la intención, las responsabilidades, las entradas, las salidas, los efectos secundarios y las restricciones operativas. No repite código que ya se entiende por sí mismo.

## Idioma

- Todos los comentarios nuevos, docstrings, Javadoc, TSDoc y encabezados de archivos se escriben en inglés.
- La documentación de producto existente puede permanecer en español cuando forma parte de una entrega del curso, pero cada archivo de código fuente/configuración debe incluir un encabezado de propósito en inglés.
- Los mensajes de commit y los títulos de los pull requests se escriben en inglés.

## Encabezado obligatorio de archivo

Todo archivo nuevo o en mantenimiento activo de código fuente, scripts, configuración, migraciones, plantillas y documentación comienza con un bloque breve en inglés:

```text
CG-DOC: <one sentence describing the file purpose>
```

**Archivos exentos.** Los siguientes se conservan como registros puntuales y no necesitan un encabezado `CG-DOC`: notas de planificación/revisión/retrospectiva de sprint, notas de versión, `CHANGELOG.md`, `IAG_REGISTRO.md`, índices de evidencia y archivos de evidencia, informes finales (PDF/DOCX y sus fuentes Markdown), informes de arquitectura y patrones, y otras entregas del curso. No los reescribas solo para añadir un encabezado; añádelo al crear un documento activo nuevo o al actualizar sustancialmente un archivo exento.

Usa la sintaxis de comentarios propia del tipo de archivo:

- Java, TypeScript, TSX, JavaScript: `/** CG-DOC: ... */`
- Shell, YAML, properties, ejemplos de env: `# CG-DOC: ...`
- SQL: `-- CG-DOC: ...`
- Groovy, Kotlin DSL, Cypher: `// CG-DOC: ...`
- Markdown, HTML: `<!-- CG-DOC: ... -->`
- FreeMarker: `<#-- CG-DOC: ... -->`
- LaTeX: `% CG-DOC: ...`
- CSS: `/* CG-DOC: ... */`

## Java

- Añade Javadoc a las clases, interfaces, records, enums y métodos públicos.
- Documenta parámetros, valores de retorno, excepciones lanzadas y supuestos de seguridad.
- Añade `package-info.java` cuando un paquete tenga una responsabilidad no evidente.
- No comentes getters, setters ni miembros generados triviales.

## TypeScript y React Native

- Usa TSDoc para hooks, componentes, contextos y utilidades exportados.
- Documenta props, valores de retorno, efectos secundarios y dependencias externas.
- Añade un encabezado a las pantallas, los diseños y los archivos de configuración.

## SQL

- Documenta el propósito de la migración, las tablas afectadas, las dependencias y las restricciones de orden.
- No modifiques el SQL ejecutable mientras lo documentas.
- Mantén los archivos de migración inmutables después de haberse aplicado.

## YAML y Docker Compose

- Documenta cada servicio, puerto expuesto, volumen, healthcheck y variable de entorno requerida.
- Marca los valores opcionales y los valores predeterminados seguros.
- Nunca incluyas secretos reales.

## Scripts de shell

- Comienza con el propósito, el uso, los prerrequisitos, las variables de entorno, el comportamiento de idempotencia, los efectos secundarios y las instrucciones de limpieza.
- Documenta las funciones que realizan operaciones con AWS, Docker, SSH o mutaciones de archivos.
- Prefiere `set -euo pipefail`.

## Jenkins y Groovy

- Documenta las etapas del pipeline, los parámetros, las credenciales, los artefactos, los destinos de despliegue y el comportamiento de reversión.
- Mantén los secretos fuera del código fuente y de los registros.

## Archivos de documentación

- Usa títulos y resúmenes en inglés.
- Enlaza los módulos relacionados y los runbooks operativos.
- Mantén actualizado el índice de archivos al añadir o eliminar archivos rastreados.

## Exclusiones

Los siguientes no reciben encabezados en línea:

- Recursos binarios: PDF, PNG, JPG, TTF, JAR.
- Bloqueos de dependencias generados: `package-lock.json`.
- Salidas generadas por IDE: `bin/`.
- Wrappers de Gradle generados: `gradlew`, `gradlew.bat`, wrapper JAR.
- Archivos JSON, porque el formato no admite comentarios.
