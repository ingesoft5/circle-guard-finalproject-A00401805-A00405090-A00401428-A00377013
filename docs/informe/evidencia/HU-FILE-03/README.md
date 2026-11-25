# Evidencia HU-FILE-03

Índice de artefactos de la historia `HU-FILE-03` (issue #135, PR #134).

| Archivo | Qué demuestra | Estado |
|---|---|---|
| `01-p1p4-evidence-table.md` | Validación de multibranch, webhook, biblioteca compartida, JaCoCo, gate y selectivo | Versionado en `docs/informe/HU-FILE-03.md` §1 |
| `11-pr134-idempotent-skip.png` | Consola de Jenkins con `Publicación omitida... ya existe en Nexus` | Captura en Jenkins on-prem |
| `12-selective-build-file.png` | Build con `SERVICE=circleguard-file-service` | Captura en Jenkins on-prem |
| `13-quality-gate-blocking.png` | Gate negativo con cobertura < umbral y build en FAILURE | Captura en Jenkins on-prem |
| `14-registry-semver-policy.png` | Política de inmutabilidad solo para `^\d+\.\d+\.\d+$` en Nexus `:5000` | Verificación en el host |
| `15-rebuild-onpremise-run1-run2.log` | Doble ejecución convergente de `rebuild-onpremise.sh` | Ejecución en `docker@205m07` |

## Comandos para completar la evidencia

```bash
# En el host on-premise, desde la raíz del repositorio:
bash infra/onpremise/rebuild-onpremise.sh 2>&1 | tee /tmp/rebuild-run-1.log
bash infra/onpremise/rebuild-onpremise.sh 2>&1 | tee /tmp/rebuild-run-2.log

# Verificación de la política de tags en Nexus Docker hosted (:5000):
set -a; . deploy/compose/tools/.env.onpremise; set +a
curl -fsS -u "$NEXUS_USER:$NEXUS_PASSWORD" \
  "$NEXUS_URL/service/rest/v1/repositories" \
  | jq -r '.[] | select(.format=="docker") | [.name,.type,.url] | @tsv'

# En Jenkins, con SERVICE=circleguard-file-service, TAG=1.0.0-<sha>-file,
# SKIP_INTEGRATION=true: capturar consola del primer build y del rerun.
```

Las imágenes se enlazan cuando se versionan en el repositorio.
