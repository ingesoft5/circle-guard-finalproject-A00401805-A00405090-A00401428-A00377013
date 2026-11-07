# Registro de uso de IAG

## HU-GATEWAY-CICD-01 — Plataforma CI/CD del gateway

- **Nivel de uso:** 3 (colaboración con IAG).
- **Herramienta:** OpenCode / DeepSeek.
- **Contenido propio entregado:** estructura del repositorio, hallazgos del análisis del gateway, requisitos del PDF, decisiones de topología (2 instancias, `dev`/`stage`/`prod`).
- **Prompts principales:**
  - Analizar la estructura del microservicio Gateway.
  - Validar el plan de la HU contra el PDF del proyecto.
  - Ajustar la HU a la Parte 1 / Sprint 1 y al microservicio gateway.
  - Generar scripts Bash idempotentes, Docker Compose y pipeline de Jenkins.
- **Resultado:** scripts de infraestructura, compose de herramientas y ambientes, Jenkinsfile, Shared Library, correcciones de seguridad del gateway, pruebas y documentación.
- **Verificación humana:** el equipo debe revisar y ejecutar cada script, validar las credenciales y estar en capacidad de explicar la configuración en la sustentación.
