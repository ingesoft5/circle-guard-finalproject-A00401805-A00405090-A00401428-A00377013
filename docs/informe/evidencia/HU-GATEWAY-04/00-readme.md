# Metadatos de la evidencia — HU-GATEWAY-04

Completar al momento de capturar las evidencias reales.

| Campo | Valor |
|---|---|
| Fecha | Pendiente |
| Autor | Pendiente |
| Commit SHA | Pendiente |
| URL del build | Pendiente |
| URL de SonarQube | Pendiente |
| Entorno | Pendiente (`<TOOLS_EIP>`, IPs privadas) |
| Navegador | Pendiente |

## Verificación

- [ ] Las capturas corresponden al commit indicado.
- [ ] La URL del build abre el mismo build de las capturas.
- [ ] El reporte JaCoCo muestra cobertura de líneas ≥ 60 %.
- [ ] El quality gate de Sonar está en verde.
- [ ] El webhook tiene una entrega reciente con respuesta 200.
- [ ] `main` exige PR, aprobación y el check de Jenkins.
- [ ] No hay secretos visibles en las imágenes.
