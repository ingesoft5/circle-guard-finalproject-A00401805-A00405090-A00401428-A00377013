# Pipeline compartido: Gateway y Form

El job multibranch existente, `circleguard-gateway`, obtiene el `Jenkinsfile` de la raíz.
Aunque conserve su nombre, ahora puede procesar Gateway y Form. No se necesita otro job
ni un Jenkinsfile dentro de cada microservicio.

El Jenkinsfile coordina las etapas, `jenkins/services.groovy` registra y selecciona los
servicios, y `vars/ci.groovy` aporta las funciones compartidas existentes. El selector
se carga desde la rama/PR obtenida por `checkout scm`; no hay que cambiar la versión
global de la biblioteca `circleguard` para probarlo.

## Selección y etapas

El parámetro `TARGET_SERVICE` tiene estas opciones:

| Opción | Comportamiento |
| --- | --- |
| `auto` | Seleccionar según todos los cambios de la rama o PR. Es el valor predeterminado. |
| `all` | Ejecutar todos los servicios **integrados actualmente**: Gateway y Form. |
| `gateway` | Seleccionar Gateway explícitamente. |
| `form` | Seleccionar Form explícitamente. |

| Cambio en modo automático | Resultado |
| --- | --- |
| Fuentes, recursos, pruebas o build de Form | Form. |
| Fuentes, recursos, pruebas o build de Gateway | Gateway. |
| Ambos servicios | Gateway y Form, secuencialmente. |
| Promotion | Gateway como respaldo y Form, cuya integración utiliza clases reales de Promotion. |
| Otro servicio pendiente o el cliente web | Gateway como respaldo y aviso de que el componente cambiado no se valida. |
| Jenkinsfile, selector, biblioteca o Gradle compartido | Gateway y Form. |
| Configuración actual de Sonar, despliegue o E2E de Gateway | Gateway. |
| Solo documentación informativa | Sin compilaciones. Los recursos y fixtures dentro de `src` nunca se ignoran por ser texto. |
| Ruta desconocida o referencia Git no disponible | Gateway y Form, con la razón visible en el log. |

Los PR y ramas de trabajo se comparan con el punto común con su rama destino
(`main` para una rama sin PR). Se obtiene la referencia destino con `git fetch`;
si no está disponible, se validan ambos servicios. Los renombrados incluyen las
rutas anterior y nueva, y se preservan los espacios de los nombres de archivo.

En `main` se comparan todos los cambios desde `GIT_PREVIOUS_SUCCESSFUL_COMMIT`.
Solo se acepta esa referencia cuando la ejecución exitosa anterior tiene el marcador
`[CI:auto]` o `[CI:all]` en su descripción. Una ejecución manual de un solo servicio,
un rollback o una ejecución antigua obliga a validar ambos en el siguiente automático.
Así una ejecución parcial no oculta cambios pendientes del otro servicio.

Ambos servicios ejecutan sus tareas `clean`, `test`, `jacocoTestReport`,
`jacocoTestCoverageVerification` y, si pasan las validaciones previas, `bootJar`.
Form incluye su integración Kafka en `test`; no tiene una tarea `integrationTest` separada.

Gateway conserva Sonar y su quality gate. En `main`, cuando está seleccionado,
conserva publicación Maven, imagen Docker, despliegues, E2E, aprobación de producción
y etiquetado. Esas operaciones no se ejecutan si solo está seleccionado Form.
El respaldo de Gateway para servicios pendientes conserva también ese flujo en `main`.

`ROLLBACK_TAG` mantiene su comportamiento anterior: tiene prioridad sobre la selección,
omite builds y, únicamente en `main`, redespliega Gateway en producción. No es un rollback de Form.
`SKIP_E2E` sigue afectando exclusivamente al E2E de Gateway.

## Cómo incorporar otro microservicio

Los bloques comentados del catálogo indican dónde integrar Auth, Identity, Promotion,
Notification, File y Dashboard. Cada responsable debe:

1. Completar el registro de su servicio siguiendo el de Form:

   ```groovy
   identity: [name: 'circleguard-identity-service',
              directory: 'services/circleguard-identity-service',
              gradlePath: ':services:circleguard-identity-service',
              active: true, dependsOn: []]
   ```

2. Comprobar que su módulo ofrece las tareas de pruebas, reporte JaCoCo,
   verificación de cobertura y `bootJar`, con dependencias de prueba reproducibles.
3. Declarar en `dependsOn` los servicios cuyas clases utiliza en sus pruebas de
   integración, y añadir escenarios de selección para esas relaciones.
4. Añadir su opción manual a `TARGET_SERVICE` y comprobar el pipeline en una rama o PR.
5. Configurar sus propias etapas de Sonar y publicación cuando corresponda. Activarlo
   para CI no habilita automáticamente las etapas de release de Gateway para ese servicio.

El cliente de `mobile` requiere un ejecutor Node/Expo; no se activa con rutas Gradle.
Los registros `active: false` y sus comentarios son una guía, no integraciones terminadas.
La opción `all` todavía no demuestra la ejecución de todos los componentes del enunciado.

## Comprobaciones locales

Desde la raíz, con Java 21:

```bash
./gradlew -p tests/jenkins test --console=plain
```

Este proyecto de pruebas está aislado del build de los microservicios. Prueba la
selección con repositorios Git temporales y simula los pasos del Jenkinsfile real.
No contacta Jenkins, Nexus, Sonar ni los servidores de despliegue. Su reporte queda en
`tests/jenkins/build/reports/tests/test/index.html`.

Con Docker disponible, comprobar además cada servicio:

```bash
./gradlew :services:circleguard-form-service:check :services:circleguard-form-service:bootJar --console=plain
./gradlew :services:circleguard-gateway-service:check :services:circleguard-gateway-service:bootJar --console=plain
```

Testcontainers inicia PostgreSQL y Kafka temporales para Form; no se necesita
levantar manualmente el Compose de desarrollo. El agente Jenkins debe poder acceder
al socket Docker y a los puertos de los contenedores. Los reportes JUnit y JaCoCo
se generan en el `build` de cada módulo; su publicación adicional para Form es DCG-23.

## Validación en Jenkins antes de fusionar

1. Una vez revisados los cambios y autorizada la publicación de la rama, entrar al
   Jenkins compartido. No se necesita levantar otro Jenkins local.
2. Abrir `circleguard-gateway` y ejecutar **Scan Multibranch Pipeline Now**. Abrir la
   rama `feat/HU-FORM-03-jenkins` o el PR que Jenkins descubra. Comprobar que Checkout
   obtiene el commit que contiene esta adaptación.
3. La primera ejecución puede usar `auto` porque los parámetros se registran al
   cargar el Jenkinsfile. Los cambios del pipeline seleccionarán ambos servicios.
4. Usar **Build with Parameters** en esa rama/PR, con `TARGET_SERVICE=form` y
   `ROLLBACK_TAG` vacío. Confirmar pruebas, cobertura y JAR de Form. Sonar, publicación,
   Docker, despliegues y aprobación de producción deben aparecer omitidos.
5. Probar `gateway` y `all` en la misma rama/PR. Gateway debe conservar Sonar y su
   quality gate; ningún modo debe publicar ni desplegar desde esa rama/PR.
6. Guardar enlace y número de ejecución, commit, servicios seleccionados y resultados.
   Las simulaciones locales no sustituyen esta ejecución real ni validan el entorno AWS.

Se conserva el temporizador existente. Debe comprobarse también que GitHub envía
los eventos al webhook `/github-webhook/`: el temporizador por sí solo no demuestra
la activación por webhook exigida en E1.4. Esta adaptación no modifica AWS ni webhooks.

## Coordinación con PR pendientes

- PR #19 modifica la publicación de reportes en `vars/ci.groovy`. Las rutas Gradle
  con `:` y las carpetas con `/` deben tratarse por separado; revisar esa interfaz
  antes de fusionar. Esta adaptación conserva las firmas y funciones existentes.
- PR #22 añade `integrationTest` de Redis a Gateway y también edita el Jenkinsfile.
  Al resolver la integración, conservar su etapa y condicionarla a Gateway seleccionado,
  sin rollback. No llamar esa tarea para Form, que prueba Kafka dentro de `test`.
- PR #18 propone un Jenkinsfile propio para Promotion. Coordinar su incorporación
  al catálogo común según el acuerdo del equipo de usar el Jenkinsfile raíz.

DCG-22 se confirma con la ejecución de Form en Jenkins. DCG-23 (Sonar/reportes)
y DCG-24 (publicación de imagen de Form) continúan pendientes.

## Resultado local de esta adaptación

Validación del 8 de noviembre de 2026, desde `feat/HU-FORM-03-jenkins`, con Java 21 y Docker:

| Comprobación | Resultado |
| --- | --- |
| Selector Git y simulaciones del Jenkinsfile | 27 pruebas, sin fallos ni omisiones. |
| Gateway | 19 pruebas, sin fallos ni omisiones; JAR generado. |
| JaCoCo Gateway | 95,92 % de líneas; su verificación existente pasa. |
| Form | 35 pruebas, sin fallos ni omisiones, incluida la integración Kafka; JAR generado. |
| JaCoCo Form | 86,43 % de líneas; supera el mínimo del 60 %. |
| Jenkins en AWS | Pendiente de publicar la rama con autorización y ejecutar su job. |

Las validaciones locales terminaron con `BUILD SUCCESSFUL`. La publicación,
los despliegues y el rollback se simularon; no se ejecutaron contra infraestructura real.
