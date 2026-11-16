# Kubernetes on-premise (k3d)

Manifiestos Kustomize para los ocho servicios de CircleGuard, el cliente web y su
middleware, sobre el clúster `cg` que corre en el host on-premise.

## Estructura

```
deploy/k8s/
├── base/
│   ├── kustomization.yaml
│   ├── middleware.yaml   # postgres, redis, neo4j, kafka, openldap + ConfigMaps
│   ├── apps.yaml         # 8 servicios + web
│   └── ingress.yaml      # rutas verificadas del API y del cliente web
└── overlays/
    ├── dev/              # namespace cg-dev, host dev.circleguard.local
    ├── stage/            # namespace cg-stage, host stage.circleguard.local
    └── prod/             # namespace cg-prod, host prod.circleguard.local
```

Cada overlay trae `secrets.env.example`. El archivo real (`secrets.env`) no se versiona.

## Por qué Kustomize y no Helm

La plataforma ya tiene un `docker-compose` por ambiente que sirve de referencia. Kustomize
permite reutilizar una sola base y cambiar solo el namespace, el host y el tag de imagen por
ambiente, sin mantener un chart ni una plantilla con valores por defecto. Para tres ambientes
y nueve aplicaciones, el costo de Helm no se justifica.

## Registro e imágenes

El registro Nexus vive en `10.147.20.67:5000` y se publica por HTTP, no por HTTPS. En el
clúster eso se declara en el `registries.yaml` de k3d y con `imagePullSecrets`; el puerto no
se expone a Internet.

Para el primer despliegue, antes de que el pipeline publique imágenes, se construyen en el
host y se importan al clúster:

```bash
TAG=1.4.0
for svc in auth identity promotion form gateway dashboard file notification; do
  docker build -t "circleguard-docker/$svc:$TAG" "services/circleguard-$svc-service"
done
docker build -t "circleguard-docker/web:$TAG" mobile

k3d image import -c cg \
  circleguard-docker/auth:$TAG \
  circleguard-docker/identity:$TAG \
  circleguard-docker/promotion:$TAG \
  circleguard-docker/form:$TAG \
  circleguard-docker/gateway:$TAG \
  circleguard-docker/dashboard:$TAG \
  circleguard-docker/file:$TAG \
  circleguard-docker/notification:$TAG \
  circleguard-docker/web:$TAG
```

Los manifiestos usan `imagePullPolicy: IfNotPresent`, así que el clúster toma la imagen local
y no intenta descargarla. Cuando el pipeline publique en Nexus (P2-08), el overlay cambiará
el `newName` a `10.147.20.67:5000/circleguard-docker/<servicio>`.

## Secretos

```bash
cd deploy/k8s/overlays/dev
cp secrets.env.example secrets.env
# Reemplazar cada valor. JWT_SECRET debe tener al menos 32 bytes.
```

`secrets.env` se carga con `secretGenerator` y se convierte en el `Secret cg-secrets`. Las
aplicaciones lo consumen por `envFrom`, igual que en Compose.

## Despliegue

```bash
# Validar sin aplicar
kubectl kustomize deploy/k8s/overlays/dev

# Aplicar
kubectl apply -k deploy/k8s/overlays/dev

# Verificar
kubectl -n cg-dev get pods,svc,ingress
```

Orden recomendado: primero el middleware (`kubectl -n cg-dev get pods` hasta que Postgres,
Redis, Neo4j, Kafka y OpenLDAP estén `Ready`) y después las aplicaciones. El mismo
`kubectl apply -k` aplica todo, pero las probes de las apps toleran el arranque del
middleware.

## Acceso

El Ingress usa Traefik, que ya viene con k3d. Como no hay DNS para `*.circleguard.local`,
se prueba con `port-forward`:

```bash
kubectl -n cg-dev port-forward svc/web 8081:80
kubectl -n cg-dev port-forward svc/auth 8180:8180
kubectl -n cg-dev port-forward svc/promotion 8088:8088
```

Para probar el Ingress sin DNS:

```bash
curl -H "Host: dev.circleguard.local" http://10.147.20.67:30080/
```

El puerto `30080` es el que k3d publica para el balanceador de Traefik.

## Recursos y capacidad

El host tiene 31 GiB de RAM y ya ejecuta Jenkins, SonarQube y Nexus. La suma de límites de
los nueve servicios y el middleware supera la RAM disponible si se levantan los tres
namespaces a la vez, por eso:

- `dev` corre completo.
- `stage` y `prod` se levantan para la demostración y se bajan después.
- Las réplicas son 1 y los límites están ajustados; el objetivo de P2-02 es demostrar el
  despliegue, no soportar carga real.

## Rollback

```bash
kubectl -n cg-dev rollout undo deploy/auth
kubectl -n cg-dev rollout status deploy/auth
```

Como las imágenes son inmutables y los PVC no se tocan, el rollback solo cambia la imagen de
la aplicación. Es el mismo criterio del `rollback.sh` de Compose.
