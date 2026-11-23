# CG-DOC: Estimación de costos de infraestructura (E2.9)

La solución final es on-premise sobre k3d; el_cost real de nube corresponde a
la Parte 1 (AWS EC2 para Jenkins, SonarQube y Nexus).

| Concepto | Estimación mensual | Estado |
| --- | --- | --- |
| 3 instancias EC2 (Jenkins, SonarQube, Nexus) | USD 90–150 | Parte 1, apagadas tras la evaluación |
| Elastic IP y volúmenes EBS | USD 15–25 | Parte 1 |
| Clúster k3d on-prem | USD 0 (hardware local) | Parte 2 |
| Registro Nexus y Docker Hub free tier | USD 0 | Parte 2 |

Costo real: se reporta en la factura de la cuenta de laboratorio; el informe
final distingue estimación de gasto efectivo y recomienda `terraform destroy`
para los recursos de nube residuales.
