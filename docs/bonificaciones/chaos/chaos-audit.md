<!-- CG-DOC: Bonificación de ingeniería del caos - Registro de verificación. -->
# Bonificación de ingeniería del caos - Registro de verificación
# Enunciado L284: ingeniería del caos con Chaos Mesh/Litmus

## Hipótesis
El sistema CircleGuard mantiene una disponibilidad y unos tiempos de respuesta aceptables
durante fallas transitorias de la infraestructura (eliminación de pods, latencia de red).

## Definición del estado estable
- Todos los servicios núcleo (gateway, form, auth, identity) responden a las comprobaciones de salud
  con una latencia P99 dentro de 500ms
- Cero errores 5xx durante la inyección de caos
- El tiempo de recuperación del servicio es < 30 segundos después de que se detenga la inyección de fallas

## Plan de experimentos

### Experimento 1: eliminación de pods del gateway
- **Objetivo**: despliegue de gateway (app=gateway) en cg-dev
- **Acción**: eliminar el 100 % de los pods durante 30 segundos
- **Impacto esperado**: errores 503 breves durante el reinicio de los pods
- **Criterios de recuperación**: todos los pods vuelven a Running en 30 s y pasan las comprobaciones de salud
- **Reversión**: `kubectl rollout restart deployment/gateway -n cg-dev`

### Experimento 2: latencia de red de Redis
- **Objetivo**: despliegue de redis en cg-dev
- **Acción**: inyectar una latencia de red de 2000ms ± 500ms durante 60 segundos
- **Impacto esperado**: tiempos de respuesta degradados en las operaciones en caché
- **Criterios de recuperación**: la latencia vuelve a <100ms después de que finalice el experimento
- **Reversión**: `kubectl rollout restart deployment/redis -n cg-dev`

## Criterios de éxito
1. El sistema permanece disponible (sin interrupción total)
2. Las comprobaciones de salud vuelven a UP en un plazo de 60 s tras eliminar la falla
3. No se observa corrupción de datos
4. Las alertas de monitoreo se activan correctamente

## Procedimiento de reversión
1. Detener el experimento de caos: `kubectl delete chaosengine <name> -n cg-chaos`
2. Reiniciar forzosamente el despliegue afectado si es necesario
3. Verificar la salud del servicio: `kubectl get pods -n cg-dev`