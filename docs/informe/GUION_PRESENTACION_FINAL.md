# Guion — Parte final de la presentación CircleGuard

**Alcance:** diapositivas **16 a 19 + cierre** (`CircleGuard_PRESENTACION_FINAL.pdf`).
**Duración objetivo:** ~5 minutos a ritmo natural (≈135–140 palabras/min).
**Convenciones:**
- El texto en normal se lee en voz alta; las notas entre corchetes `[...]` son indicaciones de escenario y no se leen.
- `[__]` = completar antes de presentar con los números reales de la corrida final de Locust (o, si se muestra en vivo en la demo, señalar la pantalla y decir "estos son los números de la corrida final").
- Nombres verificados contra el repo: `identity` (bóveda de anonimización), `auth` (circuit breaker + fallback), flujo `form → Kafka → promotion → notification`, Istio con mTLS STRICT + canary 90/10, Chaos Mesh (PodChaos sobre `gateway`), KEDA + OpenCost.

---

## Diapositiva 16 — Aplicación, señales y falla provocada (~1:10)

> P2.11 · Aplicación y dashboards

[Transición desde el compañero anterior; opcional]
"Todo lo que vimos hasta acá — pipeline, clúster, observabilidad — ahora lo vemos funcionando junto, en la aplicación real desplegada en **cg-dev**."

[Señalar la captura del login]
"Este es el login real de la aplicación móvil: credenciales contra el servicio de autenticación. El plan es sencillo: vamos a **provocar una falla a propósito** para demostrar que el sistema la detecta, la hace visible y se recupera solo. Son cuatro momentos.

**Uno:** el usuario inicia sesión y diligencia el formulario de salud; el flujo avanza normal.

**Dos — y acá está lo interesante:** detenemos el servicio `identity`, la bóveda que anonimiza identidades. `auth` deja de recibir respuesta, los reintentos fallan y el **circuit breaker se abre**: auth responde con su **fallback** — la app no se cuelga ni arrastra al usuario.

**Tres:** eso no queda invisible. En **Grafana** sube el error rate y cambia el estado del circuito, y la regla crítica dispara la **alerta en Alertmanager**.

**Cuatro:** en **Jaeger** seguimos un solo **traceID** de punta a punta: form → Kafka → promotion → notification. La misma petición, contada por todos los servicios que la tocaron.

[Señalar la franja inferior: OPEN → FIRING → RECUPERACIÓN]
"Al final restauramos identity: el circuito se cierra, la alerta se resuelve sola y el sistema vuelve a la normalidad. **Falla real, señal visible, recuperación automática** — eso, para nosotros, es observabilidad de verdad."

---

## Diapositiva 17 — Rendimiento, lectura y mejoras (~1:15)

> P2.11 · Resultados y lecciones

"Con la prueba de carga pasamos de *'funciona'* a *'funciona bajo presión, y lo puedo probar con cifras'*.

Lo importante: los objetivos se definieron **antes** de correr nada, y **por endpoint**. [Señalar la tarjeta izquierda]

- `POST /gate/validate` — validar el ingreso al campus — en **p95 de 500 milisegundos o menos**.
- `POST /auth/login` en **p95 de 800 milisegundos o menos**.
- **Throughput sostenido de al menos 50 peticiones por segundo**.
- Y **menos del 1 % de errores**.

La corrida: **Locust, 50 usuarios concurrentes durante 5 minutos**, con reporte en HTML y CSV.

¿Por qué **percentiles** y no promedio? Porque el promedio esconde las peores experiencias. El p95 es la promesa real que le hacemos al 95 % de los usuarios.

En la corrida final obtuvimos `[p95 de __ ms en gate/validate · __ req/s · __ % de error]`. [Si se muestra en vivo:] "Estos son los números de la corrida que se presentan en la demo; el HTML y el CSV quedan archivados como evidencia." Y si algo no cumple, la lectura es igual de honesta: identificar el cuello de botella — base de datos, broker o servicio — y aplicar la acción de mejora.

De esta parte nos quedamos con **tres lecciones**: [señalar tarjetas 01–03]
**La evidencia manda** — cada cifra tiene un XML, un log o una captura.
**Automatizar el camino feliz** — el pipeline repite lo que ya funciona.
**Y observar antes de optimizar** — sin métricas, cualquier mejora es intuición."

---

## Diapositiva 18 — Bonificaciones implementadas (~1:00)

> Parte 2 · Bonificaciones

"Antes de cerrar, las **tres bonificaciones**. Y un punto de honestidad metodológica: solo las evaluamos **después de completar los 19 criterios obligatorios**, y cada una tiene un script que cualquiera puede volver a ejecutar. [Señalar los badges PASS al recorrerlas]

**Primera, service mesh con Istio:** tráfico cifrado con **mTLS STRICT**, identidad **SPIFFE** para cada workload, y un **canary real 90/10** — el 90 % de las peticiones a la versión estable y el 10 % a la nueva, con una regla que permite fijar el 100 % a la estable si algo falla.

**Segunda, chaos engineering con Chaos Mesh:** un **PodChaos real** que elimina un pod de `gateway`; Kubernetes lo recupera y el verificador comprueba que no quede **ningún residuo** del experimento.

**Tercera, FinOps:** **KEDA** para escalar a cero los servicios ociosos, **OpenCost** para ver el costo por servicio en Grafana, y la **política de nodos spot** documentada con sus compromisos.

Las tres pasaron: **PASS**, con evidencia versionada en el repositorio."

---

## Diapositiva 19 — Galería de evidencias (~0:45)

> Evidencias

"Esta galería es un vistazo rápido a los **artefactos reales** que respaldan todo lo que dijimos hoy. [Recorrer con la mano, sin detenerse mucho]

Arriba, **GitHub**: ramas protegidas, revisión aprobada — nada llega a main sin pull request — y el **webhook** que dispara Jenkins con **ejecución selectiva**: solo corre lo que cambió.

En el centro, la calidad: el **reporte JUnit en HTML** — 9 tests, cero fallas, **100 % de éxito** — y el **cierre del Sprint 0 en Jira**, con las historias DCS-1 a la 7 completadas.

Abajo, la plataforma: **Jenkins on-premise** — reconstruido en el clúster local —, **Nexus** como repositorio de artefactos e imágenes, y las **releases con semver**: vamos por la **v1.14.0, marcada como latest**, y cada versión anterior queda publicada y trazable.

Todo verde, todo real, todo versionado."

---

## Cierre — Lo que queda demostrado (~0:30)

> Cierre / GRACIAS

[Marcar una pausa; bajar un poco el ritmo]

"Un proyecto no termina cuando compila: **termina cuando otra persona puede reproducirlo**. [Pausa breve]

Eso resume lo que hicimos:
**Evidencia antes que afirmación** — cada cifra tiene su captura, su XML, su log o su commit.
**Automatizar lo repetible** — pipeline, imágenes, ambientes y releases.
**Y observar para decidir** — métricas, trazas y logs conectados al flujo de negocio.

**CircleGuard**: una plataforma DevOps de extremo a extremo, construida y comprobada por Daniela Castaño, Santiago Estrada, Faiber Piedrahita y Alejandro Quiñones.

**Muchas gracias.**"

---

## Si el jurado pregunta (respuestas de un minuto)

- **¿Por qué percentiles y no promedio?** → El promedio esconde colas: un p95 alto con promedio bajo significa que hay usuarios esperando demasiado. Gestionamos la experiencia del percentil 95, no la ilusión del promedio.
- **¿Por qué el canary/chaos/FinOps en namespaces aislados?** → Para no arriesgar los ocho servicios obligatorios de la Parte 2: la bonificación demuestra la capacidad sin poner en riesgo la entrega.
- **¿Qué pasó con AWS?** → La cuenta se bloqueó media hora antes de la entrega de la Parte 1; se reconstruyó la plataforma completa on-premise (k3d + ZeroTier) y toda la evidencia actual sale de ese clúster.
- **¿La alerta se recupera sola?** → Sí: la regla se resuelve cuando identity vuelve y las métricas vuelven a su rango; no requiere intervención manual.
