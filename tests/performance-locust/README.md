# CG-DOC: Pruebas de rendimiento con Locust (E2.7)

Escenarios: validación de acceso con QR y envío de cuestionario de salud.

```bash
pip install locust
mkdir -p tests/performance-locust/results
locust -f tests/performance-locust/locustfile.py --headless \
  -u 100 -r 10 -t 5m --host "$TARGET_URL" \
  --csv tests/performance-locust/results/run
```

Objetivos declarados: p95 ≤ 800 ms (validación QR), p95 ≤ 1200 ms
(cuestionario), tasa de error < 1 % y throughput ≥ 50 req/s.

Los CSV generados (`*_stats.csv`, `*_stats_history.csv`) son la evidencia
de la ejecución; se entregan con el informe final. Si el entorno de demo no
permite cargar el gateway, se documenta el bloqueo y se ejecuta contra el
servicio expuesto por el Ingress del ambiente `dev`.
