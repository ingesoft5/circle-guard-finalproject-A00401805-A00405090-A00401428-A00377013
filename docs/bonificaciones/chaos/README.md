<!-- CG-DOC: Verificación de la bonificación de chaos engineering (enunciado L284). -->
# Verificación de la bonificación de chaos engineering (enunciado L284)

Bonificación de **chaos engineering** del enunciado L284.

Evidencia **real** obtenida en el clúster on-prem `k3d-cg` (host `10.147.20.67`).

## Alcance

- Chaos Mesh 2.8.8 desplegado en el namespace aislado `cg-chaos` (no modifica los
  manifiestos obligatorios de `cg-dev`).
- Experimento real de **PodChaos** (`pod-kill`) sobre un pod del deployment
  `gateway` en `cg-dev`.
- El dictamen de esta bonificación se apoya en el experimento real de
  **PodChaos**, verificado de extremo a extremo.
- El verificador `scripts/verify-chaos.sh` aborta si el recurso no se aplica y
  exige evidencia real: desaparición de un UID de pod, recuperación efectiva y
  limpieza tras el rollback.

## Hipótesis y estado estable

- **Hipótesis**: el sistema mantiene disponibilidad y tolerancia a fallos
  durante perturbaciones transitarias.
- **Estado estable observado**: `gateway` con 2 réplicas listas antes y después del
  experimento; sin recurso de chaos residual tras el rollback.

## Ejecución real

Comando ejecutado en el host (rama `fix/final-project-validation`, HEAD
`9144829`, carga del host `48.49 54.70 46.30`):

```bash
cd ~/circle-guard-mesh
git fetch -q origin fix/final-project-validation && \
  git reset -q --hard origin/fix/final-project-validation
timeout 600 bash scripts/verify-chaos.sh | tee /tmp/chaos-verification-final3.txt
```

Salida real archivada:

```text
=== 1. Chaos Mesh control plane ===
  [PASS] chaos-controller-manager ready=1
  [INFO] chaos-daemon ready=2/2 (pod-kill uses the controller API; full network injection would need all daemons)
=== 2. Baseline: gateway healthy before the experiment ===
  [PASS] gateway baseline ready=2
  [PASS] gateway pod UIDs captured (1038f4d2-57ac-45f8-a772-81a35e449078 a9ce862f-9483-4034-ab9d-306a4b2384be c2daaba8-6230-46e6-8fe7-e65d5bed6ec5 )
=== 3. Apply the real PodChaos experiment ===
podchaos.chaos-mesh.org/gateway-pod-kill created
  [PASS] PodChaos resource created (podchaos.chaos-mesh.org/gateway-pod-kill)
NAME               AGE
gateway-pod-kill   12s
=== 4. Injection evidence: one gateway pod UID must disappear ===
  gateway pod killed: uid a9ce862f-9483-4034-ab9d-306a4b2384be disappeared; current uids: 1038f4d2-57ac-45f8-a772-81a35e449078 c2daaba8-6230-46e6-8fe7-e65d5bed6ec5 
  [PASS] PodChaos really killed a gateway pod (UID disappeared)
=== 5. Recovery evidence: gateway returns to ready state (max 180s) ===
  [PASS] gateway recovered after pod-kill (ready=2)
=== 6. Rollback: delete experiment and confirm steady state ===
  [PASS] Chaos Mesh experiment deleted (rollback)
  [PASS] no chaos resource remains after rollback
  [PASS] gateway steady after rollback (ready=2)
=== RESULT ===
CHAOS_VERIFICATION_PASS
=== EXIT: 0 ===
```

## Dictamen

**CUMPLE** (condicionado a la finalización de los entregables obligatorios
E2.5, E2.7 y E2.10, tal como aplica a todas las bonificaciones L284).

La evidencia es real y reproducible: el PodChaos se creó, eliminó efectivamente
un pod (cambió el conjunto de UIDs), el servicio recuperó su estado estable y el
rollback dejó el clúster sin recursos de chaos. No se reporta NetworkChaos como
evidencia de esta bonificación.
