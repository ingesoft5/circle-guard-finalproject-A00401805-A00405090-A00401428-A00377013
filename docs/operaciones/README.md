# CG-DOC: Manual de operaciones (E2.9)

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

```bash
./deploy/scripts/rollback.sh <tag-anterior>
kubectl -n circleguard-prod rollout status deployment/<servicio>
```

## Troubleshooting

- Verificar `/actuator/health` antes de escalar.
- Consultar `docs/infra/runbook.md` y `docs/infra/environments.md`.
- Alertas y dashboards: `deploy/k8s/monitoring/`.
