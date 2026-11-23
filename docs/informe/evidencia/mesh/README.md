# CG-DOC: Service Mesh bonus verification (enunciado L284)

Bonificación de **service mesh (Istio con mTLS y canary)** del enunciado L284.
Evidencia **real** obtenida en el clúster on-prem `k3d-cg` (k3s v1.31.1). No se
inventan certificados ni distribuciones de tráfico: todos los valores provienen
de `kubectl`, del endpoint `:15000/certs` y `:15000/stats` de Envoy, y de
peticiones HTTP reales al ingress.

## Alcance y aislamiento

- El control plane de Istio 1.27.1 se instaló con Helm: `istio-base`, `istiod`,
  `istio-ingress` (todos `STATUS=deployed`).
- La malla se aplicó **solo** en el namespace aislado `cg-mesh-canary`
  (`istio-injection=enabled`). `cg-dev` **no** fue inyectado, para no reiniciar
  ni arriesgar los ocho servicios obligatorios de la Parte 2.
- Las imágenes `istio/pilot:1.27.1`, `istio/proxyv2:1.27.1` y
  `circleguard-docker/form:1.8.0` se importaron a ambos nodos con
  `scripts/import-mesh-images.sh` (no había salida a registros externos para
  los pods).

## Configuración aplicada

- `PeerAuthentication` `STRICT` en el puerto de aplicación `8086` para
  `form-stable` y `form-hu6`.
- `DestinationRule` con `tls.mode: ISTIO_MUTUAL` hacia ambos servicios del
  canary; regla `DISABLE` explícita para `postgres.cg-dev` (dependencia legacy
  sin sidecar, para no romper el arranque de Form).
- `Gateway` `circleguard-canary-gateway` y `VirtualService` `form-canary`:
  reparto **90/10** entre `form:1.4.0` (estable) y `form:1.8.0` (canary), más
  una regla `x-canary: false` que fija el 100% al estable.

## Archivos

- `deploy/mesh/istio/istio-install.sh` — instalación idempotente de Istio + aplicación de políticas.
- `deploy/mesh/istio/mtls.yml` — `PeerAuthentication` STRICT y `DestinationRules`.
- `deploy/mesh/istio/canary.yml` — `Gateway`, `VirtualService` y los dos deployments (1.4.0 / 1.8.0).
- `scripts/import-mesh-images.sh` — importación idempotente de imágenes a los nodos.
- `scripts/verify-mesh.sh` — verificador de los 6 checks.

## Verificación ejecutada

Reproducible con:

```bash
bash deploy/mesh/istio/istio-install.sh   # instalar / aplicar (idempotente)
bash scripts/verify-mesh.sh               # verificar (solo lectura + requests)
```

Salida real del verificador (`MESH_VERIFICATION_PASS`, exit 0):

```
=== 1. Mesh control plane and canary pods ===
istiod-58dbbf8cf6-42vvf       1/1 Running   k3d-cg-server-0
istio-ingress-78dcb9b689-kglt5 1/1 Running  k3d-cg-agent-0
form-hu6-58d84c7d48-pwr5c     2/2 Running   k3d-cg-agent-0
form-stable-678557755d-sb92b  2/2 Running   k3d-cg-agent-0
  [PASS] form-stable ready=1
  [PASS] form-hu6 ready=1
  [PASS] 2 istio-proxy sidecars injected

=== 2. mTLS policy ===
  [PASS] STRICT mTLS on both canary ports
  [PASS] ISTIO_MUTUAL originations present

=== 3. SPIFFE certificate from the Envoy sidecar ===
       "uri": "spiffe://cluster.local/ns/cg-mesh-canary/sa/default"
       "uri": "spiffe://cluster.local/ns/cg-mesh-canary/sa/default"
  [PASS] workload identity SPIFFE present in sidecar (73 matches)

=== 4. Traffic through Envoy ===
HTTP/1.1 200 OK
server: istio-envoy
  [PASS] response served by istio-envoy

=== 5. Canary 90/10 split (40 requests) ===
  before: stable=94 hu6=10
  after:  stable=130 hu6=14
  delta:  stable=36 hu6=4 total=40
  canary share: 10%
  [PASS] weighted routing sends traffic to BOTH versions (stable=36, canary=4)

=== 6. Header-based stable pin (x-canary: false -> 100% stable) ===
  pinned delta: stable=10 hu6=0
  [PASS] x-canary:false pinned 10/10 to stable

=== RESULT ===
MESH_VERIFICATION_PASS
```

## Dictamen

**CUMPLE.** La bonificación de service mesh está implementada y verificada en
el clúster real: mTLS `STRICT` con identidad SPIFFE en los sidecars y
despliegue canary con reparto 90/10 medido sobre 40 peticiones HTTP. Aporta
hasta **+0.10** a la Parte 2, dentro del tope acumulado de **+0.25** de L284, y
siempre condicionado a que los entregables obligatorios estén completos
(E2.5, E2.7 y E2.10 siguen abiertos en DCG-440/441/442).
