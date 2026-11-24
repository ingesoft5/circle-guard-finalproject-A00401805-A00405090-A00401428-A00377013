<!-- CG-DOC: Módulo `storage` (E2.1). -->
# Módulo `storage` (E2.1)

Declara los buckets MinIO de archivos y backups del ambiente (con versioning)
y el PVC de datos de las aplicaciones. `force_destroy` se habilita solo en dev.

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `env` | Identificador del ambiente | — |
| `namespace` | Namespace del PVC | — |
| `file_bucket_name` | Bucket de archivos | — |
| `backup_bucket_name` | Bucket de backups | — |
| `pvc_size` | Tamaño del PVC | `10Gi` |
| `force_destroy` | Permitir borrar buckets con objetos | `false` |
| `minio_endpoint` | Endpoint S3 informado en las salidas | `http://127.0.0.1:9002` |

Salidas: `file_bucket`, `backup_bucket`, `pvc_name`, `minio_endpoint`.
