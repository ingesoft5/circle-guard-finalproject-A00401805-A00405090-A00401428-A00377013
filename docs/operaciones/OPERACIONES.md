<!-- CG-DOC: Manual de operaciones (E2.9). -->
# Manual de operaciones (E2.9)

## Arranque local

```bash
./gradlew test --no-daemon
cd mobile && npm ci --no-audit --no-fund && npm run test:coverage
docker compose -f deploy/compose/dev/docker-compose.yml up -d
```

## Despliegue y promoción

1. Jenkins ejecuta `terraform plan/apply` y construye las imágenes.
2. Trivy bloquea la promoción si encuentra vulnerabilidades sobre el umbral.
3. `kubectl apply -k deploy/k8s/overlays/dev` y validación de sondas.
4. Stage se promociona tras E2E; prod requiere aprobación manual.

## Rollback

El camino controlado es el pipeline: en Jenkins, sobre `main`, usar **Build with Parameters** con `ROLLBACK_TAG=<tag-anterior>`. Eso omite las etapas de compilación y ejecuta `Rollback prod` → `ci.deploy('prod', <tag-anterior>)`. Ver `docs/infra/rollback.md`.

Verificación:

```bash
kubectl -n cg-prod rollout status deployment/<servicio>
```

Camino directo de emergencia:

```bash
kubectl -n cg-prod set image deployment/<servicio> <contenedor>=circleguard-docker/<servicio>:<tag-anterior>
kubectl -n cg-prod rollout status deployment/<servicio>
```

`deploy/scripts/rollback.sh` pertenece al camino legacy de Docker Compose y exige el ambiente como primer argumento:

```bash
./deploy/scripts/rollback.sh prod <tag-anterior>
```

No es el mecanismo del despliegue K8s actual, que ejecuta `ci.deploy()` desde `jenkins/vars/ci.groovy`.

## Troubleshooting

- Verificar `/actuator/health` antes de escalar.
- Consultar `docs/infra/runbook.md` y `docs/infra/environments.md`.
- Alertas y dashboards: `deploy/k8s/monitoring/`.
