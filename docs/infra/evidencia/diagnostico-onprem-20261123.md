# Diagnóstico del clúster on-prem — 23 nov 2026

Fecha de corte: 2026-11-23. Fuente: ejecución real vía SSH en `docker@10.147.20.67`,
contexto `k3d-cg`. Se registra el estado encontrado antes de cualquier cambio.

## Hallazgo principal (bloqueante)

El nodo `k3d-cg-agent-0` está **NotReady**. El contenedor `k3d-cg-agent-0` está
`Up`, pero `containerd` dentro del nodo no expone la API de runtime:

```text
time="2026-11-23T04:07:30Z" level=info msg="Waiting for containerd startup:
rpc error: code = Unimplemented desc = unknown service runtime.v1.RuntimeService"
```

Consecuencias verificadas:

- Los PVC con afinidad a ese nodo no pueden programarse: `0/2 nodes are
  available: 1 node(s) didn't match PersistentVolume's node affinity, 1 node(s)
  were unschedulable`.
- En `cg-stage`, `postgres-0`, `form-...`, `redis-0`, `openldap-0` y
  `cg-stage-registry-...` quedan en `Pending`.
- Los servicios que dependen de PostgreSQL entran en `CrashLoopBackOff` con
  `java.net.ConnectException: Connection refused` durante el arranque de
  Flyway (ej. `auth` en `cg-dev`).
- En `cg-obs-{dev,stage,prod}`, los pods de Prometheus y Alertmanager quedan en
  `Terminating` desde hace 24–26 h en el nodo caído; Grafana, Loki y el
  operador siguen `Running` en `k3d-cg-server-0`.

## Estado de nodos

```text
NAME              STATUS     ROLES           AGE    VERSION
k3d-cg-agent-0    NotReady   <none>          6d9h   v1.35.5+k3s1
k3d-cg-server-0   Ready      control-plane   6d9h   v1.35.5+k3s1
```

## Ingress

```text
NAMESPACE   NAME          CLASS     HOSTS                     ADDRESS      PORTS
cg-dev      circleguard   traefik   dev.circleguard.local     172.20.0.2   80
cg-prod     circleguard   traefik   prod.circleguard.local    172.20.0.2   80
cg-stage    circleguard   traefik   stage.circleguard.local   172.20.0.2   80
```

El frontend responde `200`, pero `/api/v1/auth/actuator/health` devuelve
`no available server`: la capa web está viva, los backends no.

## Impacto en el plan de la Parte 2

Bloqueadas hasta reparar el nodo: DCG-440 (trazas Jaeger y alertas en clúster),
DCG-441 (Locust y ZAP sobre `cg-stage`), rollback real en `cg-prod`, activación
de TLS/ServiceAccount y la demostración en vivo.

## Intentos de reparación (23 nov 2026)

1. Recrear el contenedor `k3d-cg-agent-0` con la imagen original
   `rancher/k3s:v1.35.5-k3s1` y volúmenes nuevos → persiste
   `unknown service runtime.v1.RuntimeService`. Descartado: no es corrupción
   del volumen.
2. Recrear con `rancher/k3s:v1.31.1-k3s1` (disponible en el host) →
   `containerd` arranca, pero aparece un segundo bloqueo:
   `inotify_init: too many open files` y `Failed to start cAdvisor`.
3. Ajustar `--ulimit nofile=65536:262144` en el contenedor → sin efecto, porque
   el límite es del host: `/proc/sys/fs/inotify/max_user_instances = 128`.
4. Subir el límite con `sysctl` → no permitido: `docker is not in the sudoers file`.

El agente fue **restaurado a su configuración original** (imagen v1.35.5 y
volúmenes iniciales) al finalizar esa ronda de pruebas.

## Intento final de recuperación (23 nov 2026, 04:30–04:40 UTC)

Siguiendo el plan de cierre de 20 minutos se intentó la recreación controlada
del agente:

1. Limpieza de pods en `Terminating` afinados al agente.
2. `k3d node delete k3d-cg-agent-0` → se colgó: el contenedor con ese nombre
   **no tenía etiquetas `k3d.*`**, por lo que k3d no lo reconoce como nodo
   gestionado. El nombre real del nodo k3d es `k3d-k3d-cg-agent-0-0`.
3. `k3d node create ... --image rancher/k3s:v1.31.1-k3s1` con el nombre
   incorrecto generó un contenedor con prefijo duplicado; al corregir el nombre
   a `cg-agent-0`, el contenedor quedó en estado `Dead` y `k3d node list` lo
   mantuvo en `removing`.
4. Tras 8 minutos sin que el nodo alcanzara `Ready` se aplicó la regla de corte:
   no se realizaron más intentos. El clúster quedó con `k3d-cg-server-0 Ready`
   y sin agente operativo (`k3d-cg-agent-0` en `Dead`/`removing`).

**Resultado: PARCIAL.** No se logró recuperar el nodo agente en esta sesión.
En consecuencia siguen sin poder ejecutarse las evidencias que dependen del
clúster: trazas Jaeger (DCG-440), Locust/ZAP (DCG-441), rollback real en
`cg-prod`, TLS/ServiceAccount y la demostración en vivo. Se reporta esta
limitación de forma explícita en el informe final en lugar de marcar esos
criterios como completados.

## Acción correctiva requerida en el host

Con acceso root en el host: (a) elevar `fs.inotify.max_user_instances` (p. ej.
`sysctl -w fs.inotify.max_user_instances=1024`) y (b) usar una imagen de k3s
cuyo `containerd` exponga `runtime.v1` — o reconstruir el clúster k3d con una
versión coherente de servidor y agente. Después verificar `kubectl get nodes`
con ambos nodos `Ready`, liberar los PVC afinados al agente y reanudar DCG-440,
DCG-441, el rollback real, TLS/ServiceAccount y la demostración en vivo.
