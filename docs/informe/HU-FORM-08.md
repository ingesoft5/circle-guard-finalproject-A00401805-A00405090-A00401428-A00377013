# HU-FORM-08 — Controles de seguridad de form-service (DCG-368)

## 1–2. Gates existentes de Trivy

El catálogo `jenkins/vars/ciServices.groovy` incluye `form` como servicio Java con imagen Docker. En `Jenkinsfile`, la etapa `Trivy Image Security Gate` recorre todas las imágenes de `javaServices` con `docker=true` en `main` y archiva `trivy-form-<tag>.json`; bloquea hallazgos CRITICAL. La etapa `Trivy IaC Security Gate` corre en ramas sin rollback y ejecuta `trivy config` sobre todo `deploy/k8s`, incluidos `base/apps.yml`, `base/form-storage.yml` y componentes de Form; bloquea CRITICAL y archiva `trivy-iac-k8s.json`. No se necesitó un gate duplicado.

La participación está comprobada por lectura de la configuración versionada. Faltan los reportes de una ejecución real de Jenkins para acreditar resultados de escaneo de la imagen publicada y de IaC. Los umbrales son los compartidos del proyecto; hallazgos HIGH se registran pero no bloquean.
