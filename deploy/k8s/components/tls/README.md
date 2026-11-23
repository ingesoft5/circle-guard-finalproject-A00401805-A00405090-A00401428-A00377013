<!-- CG-DOC: Optional TLS component for the shared ingress and its verification steps. -->
# TLS opcional para el Ingress de CircleGuard

El despliegue base sirve HTTP en `:30080` porque el dominio definitivo todavía no
está delegado. Este componente deja la terminación TLS declarada y lista, sin
guardar certificados ni llaves en el repositorio.

## Activación por ambiente

1. Obtener un certificado válido para `dev.circleguard.local`,
   `stage.circleguard.local` y `prod.circleguard.local` (o uno por ambiente con el
   mismo `secretName`).
2. Crear el Secret en cada namespace, fuera de Kustomize:

   ```bash
   kubectl -n cg-dev create secret tls circleguard-tls \
     --cert=/ruta/circleguard.crt --key=/ruta/circleguard.key \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

   Repetir para `cg-stage` y `cg-prod` con sus certificados.
3. Habilitar el entrypoint `websecure` (443) en Traefik y, si se desea redirección,
   publicar un Middleware `redirectScheme` en el mismo namespace.
4. Usar la vista TLS lista para el ambiente (`dev-tls`, `stage-tls` o `prod-tls`),
   que hereda el overlay normal y habilita este componente:

   ```bash
   kubectl kustomize deploy/k8s/overlays/dev-tls | grep -A6 'tls:'
   kubectl apply -k deploy/k8s/overlays/dev-tls
   ```

## Verificación

```bash
kubectl -n cg-dev get ingress circleguard -o jsonpath='{.spec.tls[0].secretName}'
curl -vk --resolve dev.circleguard.local:443:10.147.20.67 \
  https://dev.circleguard.local/api/v1/auth/actuator/health
```

`spec.tls[0].secretName` debe ser `circleguard-tls` y `curl` debe mostrar la cadena
válida. Si el Secret falta, Traefik no podrá terminar TLS: no aplicar el componente
hasta crearlo. La verificación en el clúster queda registrada en
`docs/infra/evidencia/E2.4-E2.5-seguridad.md` cuando se ejecute en el host on-prem.
