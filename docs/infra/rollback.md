<!-- CG-DOC: Rollback operativo para la plataforma on-premise. -->
# Rollback de producción (on-premise)

La reversión se apoya en dos propiedades del diseño: las imágenes son
inmutables y los PVC/bases de datos no se eliminan. Cambiar el tag de una
aplicación no altera los datos.

## 1. Cuándo usarlo

| Situación | Acción |
|---|---|
| Fallo funcional detectado después de `Deploy prod` | Revertir con `ROLLBACK_TAG` al último tag bueno |
| Fallo aislado de un deployment | `kubectl rollout undo deploy/<servicio>` en `cg-prod` |
| Fallo de middleware o migración incompatible | Detener la promoción y escalar el deployment afectado a 0; no borrar PVC |
| Error de Terraform/IaC | Revertir el commit de IaC y volver a aplicar el plan del tag anterior; nunca `terraform destroy` |

## 2. Rollback controlado por Jenkins (recomendado)

1. Identificar el último tag bueno en `docs/release-notes/` o en el registro
   (`circleguard-docker/<servicio>:<tag>`).
2. En el job multibranch de `main`, ejecutar con parámetros:

   ```text
   ROLLBACK_TAG = 1.3.9
   SERVICE = auto
   ```

3. `Jenkinsfile` normaliza el SemVer, omite build/pruebas/escaneos y ejecuta
   únicamente `ci.deploy('prod', params.ROLLBACK_TAG)` en la etapa
   `Rollback prod`. No se reconstruyen imágenes ni se aplica IaC.
4. Verificar con `kubectl -n cg-prod rollout status deploy/<servicio>` y con el
   healthcheck del Ingress.

## 3. Rollback manual por servicio

```bash
kubectl -n cg-prod rollout undo deploy/auth
kubectl -n cg-prod rollout status deploy/auth
kubectl -n cg-prod get pods -l app=auth
```

Para fijar un tag concreto sin depender del historial de ReplicaSet:

```bash
kubectl -n cg-prod set image deploy/auth auth=circleguard-docker/auth:1.3.9
kubectl -n cg-prod rollout status deploy/auth
```

Los deployments y nombres de contenedor declarados en `deploy/k8s/base/apps.yml`
son: `auth`, `identity`, `promotion`, `form`, `gateway`, `dashboard`, `file`,
`notification` y `web`.

## 4. Verificación posterior

```bash
kubectl -n cg-prod get pods
curl -o /dev/null -s -w 'web=%{http_code}\n' \
  -H 'Host: prod.circleguard.local' http://10.147.20.67:30080/
curl -s -H 'Host: prod.circleguard.local' \
  http://10.147.20.67:30080/api/v1/auth/actuator/health
kubectl -n cg-prod get pvc
```

Se espera: todos los pods `Ready`, `web=200`, healthcheck `UP` y los PVC
`Bound`. Si el fallo persiste, repetir con el tag anterior y registrar el
`traceID` o el mensaje de error en el informe de incidencia.

## 5. Simulación local del cambio de tag

Se renderizó una vista temporal del overlay `prod` con `auth` en `1.3.9`, sin
modificar el repositorio:

```text
render_rc=0
643:        image: circleguard-docker/auth:1.3.9
```

Esto confirma que Kustomize sustituye solo la imagen seleccionada. La ejecución
real del rollback en `cg-prod` queda registrada en la retrospectiva del Sprint 2
como acción con responsable y fecha; no se reporta como ejecutada.
