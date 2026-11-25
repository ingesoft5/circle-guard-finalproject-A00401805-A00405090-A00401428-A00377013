<!-- CG-DOC: Estimación de costos de infraestructura (E2.9) -->
# Estimación de costos de infraestructura (E2.9)

La solución final es on-premise sobre k3d; el costo de nube corresponde a la
Parte 1. La cuenta de AWS fue bloqueada, de modo que las cuatro instancias EC2
se conservan como evidencia histórica y no están operativas.

| Concepto | Estimación mensual | Estado |
| --- | --- | --- |
| 4 instancias EC2 (`cg-tools`, `cg-dev`, `cg-stage`, `cg-prod`) | USD 90–150 | Parte 1; cuenta bloqueada, fuera de operación |
| Elastic IP y volúmenes EBS | USD 15–25 | Parte 1 |
| Clúster k3d on-prem | USD 0 (hardware local) | Parte 2 |
| Registro Nexus y Docker Hub free tier | USD 0 | Parte 2 |

Costo real: se reporta en la factura de la cuenta de laboratorio; el informe
final distingue la estimación del gasto efectivo.
