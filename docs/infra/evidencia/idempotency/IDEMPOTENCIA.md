<!-- CG-DOC: Evidencia de que el bootstrap de CircleGuard converge en ejecuciones repetidas. -->
# Evidencia de idempotencia

## Resultado

`diff_lines=0`

Una segunda ejecución completa de `./infra/bootstrap/bootstrap-all.sh` no produjo cambios en:

- IDs, estados, IPs privadas, IPs públicas y tipos de las instancias EC2.
- Elastic IPs.
- Grupos de seguridad y reglas.
- Pares de claves.
- Hashes locales y remotos de `.env`.
- IDs de contenedores e imágenes.
- Repositorios y usuarios de Nexus.
- Proyectos y usuarios de SonarQube.

## Ejecución

- Rama: `test/HU-INFRA-01-idempotencia`
- Región: `us-east-2`
- Código de salida del bootstrap: `0`
- Fecha de la evidencia: 8 de noviembre de 2026

## Archivos

- `files.before` / `files.after`: hashes de archivos locales de estado sin datos sensibles.
- `remote-env.before` / `remote-env.after`: hashes de los archivos `.env` remotos.
- `instances.*.json`: estado EC2 normalizado.
- `eips.*.json`: estado de Elastic IP normalizado.
- `sgs.*.json`: estado de los grupos de seguridad normalizado.
- `keys.*.json`: nombres de los pares de claves.
- `*.containers.*.txt`: IDs de contenedores e imágenes por host.
- `nexus-*.json`: repositorios y usuarios.
- `sonar-*.json`: proyectos y usuarios.
- `diffs.txt`: vacío cuando la comprobación de idempotencia es satisfactoria.
- `bootstrap-final-run.log`: registro de la segunda ejecución.

## Notas

No se incluyen contraseñas, tokens ni claves privadas. `files.*` y `remote-env.*` contienen
solo hashes SHA-256.
