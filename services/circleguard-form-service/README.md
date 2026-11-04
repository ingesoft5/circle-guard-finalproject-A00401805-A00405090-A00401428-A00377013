# form-service: estructura del código

Este documento registra la estructura y las dependencias actuales de `circleguard-form-service` para las subtareas 1 y 2 de HU-FORM-01.

## Organización

El código principal está en `src/main/java/com/circleguard/form/`. `FormApplication.java` es el punto de entrada de Spring Boot. Las migraciones SQL están en `src/main/resources/db/migration/`.

| Paquete | Responsabilidad |
| --- | --- |
| `controller` | Expone las operaciones HTTP de cuestionarios, encuestas de salud, adjuntos y validación de certificados. |
| `service` | Implementa la lógica de cuestionarios, encuestas, detección de síntomas y almacenamiento de adjuntos. |
| `repository` | Accede a las entidades persistidas mediante Spring Data JPA. |
| `model` | Define las entidades y enumeraciones utilizadas por el servicio. |

## Controladores

| Clase | Ruta base | Responsabilidad |
| --- | --- | --- |
| `QuestionnaireController` | `/api/v1/questionnaires` | Lista, crea y activa cuestionarios; también consulta el cuestionario activo. |
| `HealthSurveyController` | `/api/v1/surveys` | Recibe y registra una encuesta de salud. |
| `AttachmentController` | `/api/v1/attachments` | Recibe un archivo adjunto y devuelve su nombre almacenado. |
| `CertificateValidationController` | `/api/v1/certificates` | Consulta encuestas pendientes y registra su validación. |

### Rutas HTTP existentes

| Método | Ruta | Operación |
| --- | --- | --- |
| `GET` | `/api/v1/questionnaires` | Lista los cuestionarios. |
| `GET` | `/api/v1/questionnaires/active` | Obtiene el cuestionario activo. |
| `POST` | `/api/v1/questionnaires` | Crea un cuestionario. |
| `POST` | `/api/v1/questionnaires/{id}/activate` | Activa un cuestionario. |
| `POST` | `/api/v1/surveys` | Envía una encuesta de salud. |
| `POST` | `/api/v1/attachments` | Sube un archivo mediante el campo `file`. |
| `GET` | `/api/v1/certificates/pending` | Lista las encuestas pendientes de validación. |
| `POST` | `/api/v1/certificates/{id}/validate` | Valida una encuesta con los parámetros `status` y `adminId`. |

## Servicios

| Clase | Responsabilidad |
| --- | --- |
| `QuestionnaireService` | Consulta, guarda y activa cuestionarios. |
| `HealthSurveyService` | Registra encuestas, consulta las pendientes, valida certificados y emite los eventos asociados. |
| `SymptomMapper` | Determina si las respuestas a un cuestionario activo indican síntomas. |
| `StorageService` | Guarda localmente los archivos adjuntos recibidos. |

## Repositorios y modelos

| Repositorio | Modelo | Uso principal |
| --- | --- | --- |
| `QuestionnaireRepository` | `Questionnaire` | Consulta cuestionarios y obtiene el activo por versión. |
| `QuestionRepository` | `Question` | Ofrece las operaciones JPA básicas para las preguntas. |
| `HealthSurveyRepository` | `HealthSurvey` | Consulta encuestas, incluidas las pendientes con adjunto. |

`Questionnaire` agrupa preguntas y su estado de activación. `Question` define el texto, tipo, opciones y orden de cada pregunta. `HealthSurvey` guarda el identificador anónimo, las respuestas y los datos de validación de una encuesta. Las enumeraciones `QuestionType` y `ValidationStatus` definen, respectivamente, los tipos de pregunta y los estados de validación.

## Migraciones de base de datos

| Migración | Cambio |
| --- | --- |
| `V1__initial_survey_schema.sql` | Crea `health_surveys` y un índice por identificador anónimo. |
| `V2__dynamic_questionnaire_schema.sql` | Crea `questionnaires` y `questions`, su relación y los índices de consulta. |
| `V3__add_responses_to_survey.sql` | Añade `responses` en formato JSONB a `health_surveys`. |
| `V4__add_attachment_to_survey.sql` | Añade la ruta del adjunto a `health_surveys`. |
| `V5__add_validation_status_to_survey.sql` | Añade el estado de validación y el identificador de quien validó la encuesta. |

## Dependencias y puntos de integración

**PostgreSQL** almacena los cuestionarios, sus preguntas y las encuestas de salud en la base `circleguard_form`. Los repositorios acceden a estos datos mediante Spring Data JPA y Flyway aplica las migraciones SQL descritas arriba. La conexión se define en `src/main/resources/application.yml`.

**Kafka** permite comunicar lo ocurrido con una encuesta a otros servicios. `HealthSurveyService` publica mensajes en estos temas:

| Tema | Cuándo se publica | Qué recibe `promotion-service` |
| --- | --- | --- |
| `survey.submitted` | Después de guardar una encuesta enviada a `POST /api/v1/surveys`. | El identificador anónimo, si se detectaron síntomas y la marca de tiempo. Si hay síntomas, solicita el cambio de estado a `SUSPECT`. |
| `certificate.validated` | Después de aprobar la validación de una encuesta en `POST /api/v1/certificates/{id}/validate`. | El identificador anónimo, el estado `APPROVED`, el identificador del administrador y la marca de tiempo. Solicita el cambio de estado a `ACTIVE`. |

La dirección de Kafka se configura en `application.yml`. **`promotion-service`** escucha ambos temas en `SurveyListener` y procesa los mensajes; `form-service` no le hace llamadas HTTP directas.
