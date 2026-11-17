# Entornos dev, stage y prod

> **Parte 1 (histórica).** Este documento describe la topología de cuatro hosts con Compose que
> se usó en el primer entregable. Como la cuenta de AWS quedó inaccesible, la Parte 2 corre
> sobre un solo host on-premise con un clúster k3d y tres namespaces. La topología vigente está
> en `docs/infra/migracion-onpremise.md` y `deploy/k8s/README.md`.
>
> | Parte 1 | Parte 2 |
> |---|---|
> | Instancia EC2 `cg-tools` | Proyecto Compose `circle-guard-dasf` en el mismo host |
> | Tres hosts EC2 | Tres namespaces (`cg-dev`, `cg-stage`, `cg-prod`) en k3d `cg` |
> | `deploy/scripts/*.sh` por SSH | `kubectl apply -k deploy/k8s/overlays/<entorno>` |
> | IP pública + ingress | Traefik `:30080` ruteado por el header `Host` |

## Topología

```text
cg-tools (m7i-flex.large, Elastic IP)
├── Jenkins        :8080
├── SonarQube      :9000
├── Nexus          :8081
├── Registro       :5000 (privado)
└── Ingress        :80

cg-dev   (m7i-flex.large, privada 10.x.x.x)
cg-stage (m7i-flex.large, privada 10.x.x.x)
cg-prod  (m7i-flex.large, privada 10.x.x.x)
├── postgres   :5432
├── neo4j      :7687
├── kafka      :9092 / externo :29092
├── redis      :6379
├── openldap   :389
├── auth       :8180
├── identity   :8083
├── promotion  :8088
├── form       :8086
├── gateway    :8087
└── web        :8080 (publicado como 8081 en el host)
```

Cada entorno ejecuta el mismo stack con el mismo tag de imagen. Los cinco servicios centrales
y el cliente web se descargan del registro; las imágenes del middleware están fijadas a
versiones concretas en el archivo compose. El aislamiento es por host y por red de Docker, no
por diferencias de configuración.

## Reglas de red

- Jenkins despliega usando la IP privada de cada entorno.
- Solo `cg-tools` tiene Elastic IP.
- Las IP públicas de los entornos son efímeras y se usan para capturas.
- El puerto 8080 es público para el webhook de GitHub y la interfaz de Jenkins.
- Los puertos 9000 y 8081 están restringidos al profesor y al administrador.
- El puerto 80 es público para el ingress de la aplicación.
- Los puertos 5000, 5432, 7687, 9092 y 6379 quedan privados dentro de la red de Docker.
- Los puertos 8087 (gateway), 8083 (identity), 8086 (form), 8088 (promotion), 8180 (auth) y
  8081 (web) aceptan tráfico desde `cg-tools`, donde corre la sonda de salud del despliegue y
  la suite de extremo a extremo de stage.
- El puerto 29092 es el listener externo de Kafka: anuncia `ENV_HOST_IP`, de modo que la suite
  de extremo a extremo puede publicar un evento de cuestionario desde Jenkins.

## Variables obligatorias

Cada entorno lee `deploy/compose/<entorno>/.env` (modo 600, nunca versionado).
`deploy/compose/<entorno>/.env.example` lista las claves:

| Clave | Propósito |
|---|---|
| `TAG` | Tag de imagen que despliega el pipeline |
| `NEXUS_REGISTRY` | Registro que sirve las imágenes de servicios y del cliente |
| `ENV_HOST_IP` | Dirección que Kafka anuncia en su listener externo |
| `POSTGRES_PASSWORD` | Contraseña de las bases de datos PostgreSQL |
| `NEO4J_PASSWORD` | Contraseña del grafo de promoción |
| `OPENLDAP_ADMIN_PASSWORD` | Contraseña del administrador LDAP que usa auth |
| `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT` | Cifrado y *hashing* de la bóveda de identidad |
| `QR_SECRET`, `JWT_SECRET` | Secretos de firma compartidos con el gateway |

`JWT_SECRET` debe tener al menos 32 bytes: el servicio se niega a arrancar con una clave más
corta (`WeakKeyException` de la librería JWT). `bootstrap-envs.sh` genera 48 caracteres
hexadecimales, suficiente. La misma regla aplica a `QR_SECRET` y `VAULT_SALT` en los manifiestos
de Kubernetes; ver `deploy/k8s/README.md`.

## Comandos

```bash
# Preparar un entorno en su host
REGISTRY_HOST=<IP_PRIVADA_TOOLS>:5000 ./infra/bootstrap/bootstrap-envs.sh dev

# Desplegar todo el stack
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/deploy.sh dev <TAG>

# Revertir
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <TAG>

# Sembrar los visitantes que va a promover una ejecución de extremo a extremo
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/seed-visitor.sh dev <anonymousId>...

# IP para capturas
./infra/bootstrap/bootstrap-ips.sh
```

`deploy.sh` descarga las cinco imágenes centrales más el cliente web, levanta el stack y espera
las comprobaciones de salud de gateway, auth, identity, promotion, form y web. Un stack que
arranca pero nunca se vuelve saludable falla el despliegue en vez de dejar un entorno a medias.

## Aislamiento

Cada entorno tiene su propia instancia EC2, red de Docker, volúmenes para Redis, PostgreSQL,
Neo4j, Kafka y OpenLDAP, `.env`, IP privada y contenedores de aplicación. `SG-envs` es
compartido, mientras que el aislamiento de la aplicación se garantiza por host y por red de
Docker.

## Orden de arranque

1. `bootstrap-aws.sh` aprovisiona los cuatro hosts y los grupos de seguridad.
2. `bootstrap-tools.sh` levanta Jenkins, SonarQube, Nexus, el registro y el ingress.
3. `bootstrap-envs.sh <entorno>` crea el `.env`, descarga el stack y espera la salud.
4. El pipeline despliega el tag de la aplicación y ejecuta la suite de extremo a extremo.
