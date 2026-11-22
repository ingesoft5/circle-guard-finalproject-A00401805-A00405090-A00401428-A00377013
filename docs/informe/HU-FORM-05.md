<!-- CG-DOC: Form Kubernetes deployment audit, persistent uploads, scaling policy, and pending runtime evidence. -->
# HU-FORM-05 — Despliegue de form-service en Kubernetes

## Alcance y estado de las subtareas

Rama: `feat/HU-FORM-05-kubernetes-form`. Auditoría SSH de solo lectura realizada el 21 de noviembre de 2026. No se consultaron valores de Secrets, no se aplicaron manifiestos ni se ejecutaron promociones o rollbacks. Los cambios locales no prueban su funcionamiento en el servidor.

| Subtarea | Estado | Evidencia y pendiente |
| --- | --- | --- |
| 1. Manifiestos para dev, stage y prod | Done | Los tres overlays incluyen el Deployment y Service de Form y renderizan correctamente. Los Deployments existen en los tres namespaces. |
| 2. Configuración PostgreSQL y Kafka | Done | El Deployment referencia `cg-config`, `cg-secrets/POSTGRES_PASSWORD` y `kafka:9092`. Se comprobaron las referencias del Deployment; no se leyeron valores del Secret. |
| 3. Readiness y liveness | Done | Ambas sondas usan `/actuator/health:8086`. En la auditoría, Form tenía una réplica Ready en dev y prod; stage estaba en cero. |
| 4. Persistencia de adjuntos | Parcial | El cambio local añade el PVC y su montaje. El servidor todavía no tiene PVC ni volumen de Form. Falta demostrar escritura y conservación tras recrear el pod. |
| 5. Ingress y HPA | Parcial | Las cuatro rutas existen y se verificaron en los tres namespaces. El cambio local añade HPA de Form en dev/prod y lo excluye en stage; falta aplicarlo y verificar el escalado. |
| 6. Despliegue y promoción Jenkins | Parcial | `Jenkinsfile` contiene dev, E2E, stage, aprobación y prod. `ci.deploy` incluye Form. La API anónima de Jenkins respondió 403: no se confirmó una ejecución satisfactoria. El fallo Docker indicado queda como dependencia compartida hasta revisar su log. |
| 7. Rollback ejecutado | Pendiente | Existe `ROLLBACK_TAG`, pero las revisiones observadas de Form usan todas `1.4.0`; no demuestran regreso a una versión anterior. |

## Evidencia observada en el servidor

Host `205m07`, contexto `k3d-cg`, nodos Ready `k3d-cg-server-0` y `k3d-cg-agent-0`. Los nodos Kubernetes son contenedores del mismo computador físico.

| Entorno | Imagen de Form | Réplicas deseadas / Ready | HPA de Form | Volumen de Form |
| --- | --- | --- | --- | --- |
| `cg-dev` | `circleguard-docker/form:1.4.0` | 1 / 1 | No | No |
| `cg-stage` | `circleguard-docker/form:1.4.0` | 0 / 0 | No | No |
| `cg-prod` | `circleguard-docker/form:1.4.0` | 1 / 1 | No | No |

En stage todos los Deployments y StatefulSets estaban en cero y no había HPA. Dev y prod devolvieron HTTP 200 y una lista vacía en `GET /api/v1/questionnaires`; `/active` devolvió 404, comportamiento previsto cuando no hay cuestionario activo. Es necesario preparar uno mediante el flujo existente antes del E2E, con autorización para modificar datos.

La StorageClass `local-path` existe, usa `WaitForFirstConsumer`, política de recuperación `Delete` y no permite expansión. Los PV observados usan almacenamiento local y afinidad al nodo. Los demás servicios tienen sus propios PVC; ninguno almacena los adjuntos de Form.

## Persistencia de los adjuntos

El cliente envía el certificado a `POST /api/v1/attachments`. `StorageService` escribe el archivo en `/tmp/circleguard-uploads` y devuelve su nombre; la encuesta conserva ese nombre en `health_surveys.attachment_path`. Los bytes no se guardan en PostgreSQL ni se envían a `file-service`.

`deploy/k8s/base/form-storage.yml` declara `form-uploads`, de 1 GiB, `ReadWriteOnce` y clase `local-path`. Cada overlay crea un PVC independiente en su namespace. El Deployment monta ese PVC exactamente en la ruta que ya utiliza Java y declara `fsGroup: 10001` con `fsGroupChangePolicy: OnRootMismatch`, compatible con el usuario de la imagen. No se modifica la API ni el código Java.

La persistencia cubre la recreación del pod y los cambios de imagen que conservan el montaje. No es una copia de seguridad ni protege ante pérdida del nodo, del clúster o del PVC: la política `Delete` puede eliminar los datos al borrar el claim. `ReadWriteOnce` admite varios pods en el mismo nodo; las réplicas que utilicen este volumen local quedan ligadas al nodo del PV. No se promete disponibilidad entre nodos ni una capacidad de disco estrictamente limitada a 1 GiB por el provisionador local.

**Antes del primer despliegue:** un responsable autorizado debe inventariar y respaldar cualquier archivo existente dentro de los pods de dev/prod antes de reemplazarlos, y preparar su copia al PVC. El montaje nuevo oculta la ruta anterior y no migra su contenido. Esta auditoría no comprobó si existen adjuntos actuales: no se ejecutó `kubectl exec` ni se leyó su contenido. No sustituir el pod antes de resolver este paso.

## Autoscaling y operación de stage

El HPA `form` usa `autoscaling/v2`, CPU al 70 %, mínimo 1 y máximo 2 réplicas, con una ventana de estabilización de scale-down de 300 segundos. El Deployment ya solicita `100m` de CPU; no se añade un límite de CPU porque no es necesario para calcular la utilización. La base deja de declarar `spec.replicas` para que el HPA controle dev y prod. Las dos réplicas deben poder ejecutarse en el nodo del PV local; no se añade afinidad que las fuerce a nodos distintos.

El overlay de stage elimina el HPA **solo del render de Form** y fija `replicas: 1`. Esto permite que una promoción autorizada active Form para su validación. Una vez terminada, el responsable del ambiente puede devolverlo a cero: sin HPA de Form, no existe un autoscaler de esta HU que lo reactive. Una nueva aplicación del overlay vuelve a solicitar una réplica deliberadamente. No se pretende que aplicar stage conserve su apagado.

Actualmente stage no tiene HPA. Si se hubiera creado uno de Form por otro medio, excluirlo del render no lo borraría del clúster: antes de apagar stage, comprobar su ausencia y coordinar su retirada con autorización. Los HPA compartidos de auth, gateway y web se mantienen sin cambios en el repositorio; el procedimiento de suspensión de todo el ambiente sigue siendo responsabilidad de plataforma.

Una promoción necesita PostgreSQL y Kafka de stage disponibles y suficiente capacidad. En la auditoría ambos estaban en cero. Coordinar la ventana con plataforma: no encender un tercer ambiente completo cuando dev y prod ya consumen el host, ni apagar otro ambiente sin aprobación.

## Validaciones locales

Se analizaron los manifiestos YAML y se renderizaron los tres overlays con `kubectl kustomize` (Kustomize v5.7.1). Se utilizaron copias temporales fuera del repositorio, con valores ficticios para `secrets.env`; los Secrets renderizados no se imprimieron ni se guardaron como evidencia.

Resultado del cambio de persistencia: PASS para dev, stage y prod. Se verificaron el PVC, montaje, permisos declarados, CPU solicitada, sondas, Service, Ingress, referencias a ConfigMaps/Secrets y aislamiento por namespace. Los recursos renderizados ajenos a Form coinciden con los anteriores al cambio. Esto comprueba sintaxis y estructura local, no admisión del API, permisos efectivos del volumen ni ejecución del clúster.

Resultado del cambio de autoscaling: PASS para los tres overlays. Dev/prod renderizan el HPA de Form con objetivo, límites, estabilización y Deployment destino correctos, sin `spec.replicas` en Form. Stage renderiza una réplica y ningún HPA de Form. Se repitieron las comprobaciones de persistencia y referencias; los recursos ajenos a Form permanecen iguales. `git diff --check` pasó antes de cada commit. No se modificó Java ni se ejecutaron pruebas Gradle por estos cambios de manifiestos.

Para repetir únicamente el renderizado sin secretos reales desde PowerShell en la raíz del repositorio (requiere Python y kubectl):

```powershell
@'
import pathlib, shutil, subprocess, tempfile
with tempfile.TemporaryDirectory(prefix="cg-form05-render-") as folder:
    tree = pathlib.Path(folder) / "k8s"
    shutil.copytree("deploy/k8s", tree, ignore=shutil.ignore_patterns("secrets.env"))
    for env in ("dev", "stage", "prod"):
        overlay = tree / "overlays" / env
        keys = [line.split("=", 1)[0] for line in (overlay / "secrets.env.example").read_text().splitlines()
                if line.strip() and not line.lstrip().startswith("#") and "=" in line]
        (overlay / "secrets.env").write_text("".join(key + "=offline-placeholder\n" for key in keys))
        subprocess.run(["kubectl", "kustomize", str(overlay)], stdout=subprocess.DEVNULL, check=True)
        print(env + ": Kustomize PASS (offline)")
'@ | python -
git diff --check
```

## Comprobaciones posteriores de solo lectura

En el servidor, después de un despliegue autorizado:

```bash
kubectl get deployments,statefulsets,hpa -n cg-dev
kubectl get deployments,statefulsets,hpa -n cg-stage
kubectl get deployments,statefulsets,hpa -n cg-prod
kubectl get pvc,pv,storageclass -A
kubectl -n cg-dev describe hpa form
kubectl -n cg-prod describe hpa form
# No debe aparecer un HPA llamado form en stage.
kubectl -n cg-stage get hpa
kubectl -n cg-dev get pvc form-uploads
kubectl -n cg-prod get pvc form-uploads
kubectl -n cg-prod rollout history deployment/form
for ns in cg-dev cg-stage cg-prod; do
  kubectl -n "$ns" get deployment form -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}{.spec.template.spec.volumes}{"\n"}{.spec.template.spec.securityContext}{"\n"}'
  kubectl -n "$ns" get ingress circleguard
  kubectl -n "$ns" get pods -l app=form -o wide
done
curl -s -o /dev/null -w 'dev questionnaires HTTP %{http_code}\n' -H 'Host: dev.circleguard.local' http://127.0.0.1:30080/api/v1/questionnaires
curl -s -o /dev/null -w 'prod questionnaires HTTP %{http_code}\n' -H 'Host: prod.circleguard.local' http://127.0.0.1:30080/api/v1/questionnaires
```

Pendientes con autorización separada: migración de posibles adjuntos, primer despliegue, prueba de escritura y persistencia tras recrear el pod, promoción por Jenkins y rollback. Para la prueba de persistencia se deben comparar el nombre y el hash del mismo archivo antes y después de recrear el pod, conservando el PVC y sin publicar el contenido del certificado.

El rollback debe conservar el PVC y mostrar la imagen/digest anterior, el resultado de la recuperación y una comprobación funcional. El parámetro compartido `ROLLBACK_TAG` redespliega el conjunto de servicios, no solo Form: requiere coordinación de plataforma y aprobación explícita. El acceso SSH de esta HU es de solo lectura.

La prueba de escalado requiere `metrics-server`, métricas disponibles y una carga autorizada; crear el HPA no demuestra que haya escalado. Para comprobar la persistencia con dos réplicas, verificar también que ambas montan el mismo PVC y acceden al archivo de prueba desde el nodo del PV. El PVC no se debe borrar durante estas pruebas.
