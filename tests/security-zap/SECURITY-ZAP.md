<!-- CG-DOC: Línea base de OWASP ZAP (E2.7). -->
# Línea base de OWASP ZAP (E2.7)

Script reproducible para el escaneo de seguridad pasivo del cliente web y del
gateway expuesto:

```bash
chmod +x tests/security-zap/zap-baseline.sh
tests/security-zap/zap-baseline.sh https://<ambiente>/ 
```

Los reportes HTML/JSON quedan en `tests/security-zap/reports/`. El pipeline
de Jenkins puede invocarlo como etapa `zap-baseline` posterior al despliegue en
`dev`; los hallazgos de nivel *Medium* o superior se revisan antes de promover
a `stage`.
