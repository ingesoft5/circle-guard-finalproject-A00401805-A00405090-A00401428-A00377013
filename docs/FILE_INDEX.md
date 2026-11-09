<!-- CG-DOC: English purpose of every tracked repository file. -->
# File Index

English purpose of every tracked repository file.

## .agent

- `.agent/workflows/run-project.md` — Markdown documentation: run-project.

## (root)

- `.dockerignore` — Docker build context exclusions.

## .githooks

- `.githooks/commit-msg` — Repository file.

- `.githooks/pre-push` — Repository file.

## .github

- `.github/pull_request_template.md` — Pull request template with context, changes, and test instructions.

- `.github/workflows/enforce-pr.yml` — GitHub Actions workflow: enforce-pr.

- `.github/workflows/require-two-approvals.yml` — GitHub Actions workflow: require-two-approvals.

## (root)

- `.gitignore` — Git ignore rules for generated files, secrets, and local state.

- `BRANCHING-STRATEGY.md` — Branching strategy, commit conventions, pull request rules, and validation hooks.

- `CHANGELOG.md` — Markdown documentation: CHANGELOG.

- `IAG_REGISTRO.md` — Registry of generative AI usage and human verification.

- `Jenkinsfile` — Shared Jenkins multibranch pipeline for CircleGuard services, quality gates, semver tags, and Docker publication.

- `Proyecto_Final_CircleGuard.pdf` — Binary asset.

- `Proyecto_Final_CircleGuard.tex` — LaTeX source for the CircleGuard final project report.

- `README.md` — Project overview, setup guide, and architecture summary.

- `build.gradle.kts` — Root Gradle build configuration for all microservices.

## deploy

- `deploy/README.md` — Deployment layout for CI/CD tools, isolated gateway environments, and operational scripts.

- `deploy/compose/dev/.env.example` — Environment variable template for the dev stack.

- `deploy/compose/dev/docker-compose.yml` — Docker Compose definition for the dev environment.

- `deploy/compose/prod/.env.example` — Environment variable template for the prod stack.

- `deploy/compose/prod/docker-compose.yml` — Docker Compose definition for the prod environment.

- `deploy/compose/stage/.env.example` — Environment variable template for the stage stack.

- `deploy/compose/stage/docker-compose.yml` — Docker Compose definition for the stage environment.

- `deploy/compose/tools/.env.example` — Repository file.

- `deploy/compose/tools/docker-compose.yml` — Docker Compose definition for the tools environment.

- `deploy/compose/tools/ingress/default.conf.template` — Nginx ingress template that exposes the dev gateway as the application entry point.

- `deploy/compose/tools/jenkins/Dockerfile` — Jenkins controller image with Docker CLI, Node, and Sonar scanner.

- `deploy/compose/tools/jenkins/casc/jenkins.yaml` — Jenkins Configuration as Code for security, credentials, library, and job.

- `deploy/compose/tools/jenkins/plugins.txt` — Repository file.

- `deploy/scripts/deploy.sh` — Deploys one image tag to an isolated environment and waits for the whole stack.

- `deploy/scripts/rollback.sh` — Rolls back an environment to a previous image tag.

- `deploy/scripts/seed-status.sh` — Seeds Redis with a visitor status for smoke and end-to-end tests.

- `deploy/scripts/seed-visitor.sh` — Seeds Neo4j with the visitors an end-to-end run is going to promote.

- `deploy/scripts/smoke-test.sh` — Runs health and authorization smoke checks against an environment.

## design-assets

- `design-assets/map-editor-prototype.html` — Design asset or prototype: map-editor-prototype.

## (root)

- `docker-compose.dev.yml` — Local development Docker Compose stack for shared infrastructure.

## docs

- `docs/FILE_INDEX.md` — English purpose of every tracked repository file.

- `docs/README.md` — Documentation index for architecture, infrastructure, standards, and evidence.

- `docs/agil/README.md` — Agile artifacts index and working agreements.

- `docs/agil/backlog.md` — Product backlog with acceptance criteria, estimates, and sprint status.

- `docs/agil/sprint-0/planning.md` — Sprint 0 goal, capacity, committed items, and risks.

- `docs/agil/sprint-0/retrospective.md` — Sprint 0 retrospective and improvement actions.

- `docs/agil/sprint-1/planning.md` — Sprint 1 goal, capacity, committed items, and risks.

- `docs/agil/sprint-1/review.md` — Sprint 1 review, demonstrated increment, and pending work.

- `docs/agil/sprint-1/retrospective.md` — Sprint 1 retrospective and improvement actions.

- `docs/arquitectura/README.md` — Container architecture, ports, environments, and design decisions.

- `docs/arquitectura/pipeline.md` — CI/CD pipeline architecture and dev-to-prod promotion flow.

- `docs/change-management.md` — Change request, approval, release, and rollback policy.

- `docs/release-notes/README.md` — Release note template and semver publishing rules.

- `docs/arquitectura/diagnostico-auth-service.md` — Documentation file: diagnostico-auth-service.

- `docs/circleguard_visual_foundation_options_1773955992819.png` — Binary asset.

- `docs/custom_components_design_mesh_bg_1773957718048.png` — Binary asset.

- `docs/informe/E1.2-CircleGuard-Infraestructura.md` — E1.2 infrastructure report with AWS, CI/CD, idempotency, cost, and evidence.

- `docs/informe/E1.2-Idempotencia.docx` — Binary asset.

- `docs/informe/E1.2-Informe-Final.html` — Repository file.

- `docs/informe/E1.2-Informe-Final.md` — Repository file.

- `docs/informe/E1.2-Informe-Final.pdf` — Binary asset.

- `docs/informe/GW-CI-02-Integracion-Redis.md` — GW-CI-02 report on gateway-Redis integration testing with Testcontainers.

- `docs/informe/GW-CI-02-Validacion-Testcontainers.docx` — Binary asset: GW-CI-02 technical validation report with embedded captures.

- `docs/informe/Gateway-Cobertura.md` — Gateway coverage, integration, E2E, and quality evidence report.

- `docs/informe/HU-ID-03.md` — Identity service Docker packaging, CI integration, semver, and acceptance evidence report.

- `docs/informe/P1.1-Branching-GitHub-Flow.html` — Repository file.

- `docs/informe/P1.1-Branching-GitHub-Flow.md` — Repository file.

- `docs/informe/P1.1-Branching-GitHub-Flow.pdf` — Binary asset.

- `docs/informe/evidencia/README.md` — Evidence screenshots for the E1.2 Markdown report.

- `docs/informe/evidencia/HU-ID-03/README.md` — Evidence checklist for the identity Docker and CI report.

- `docs/informe/evidencia/HU-ID-03/validacion-local.md` — Local test, coverage, Docker, and runtime validation results.

- `docs/informe/evidencia/HU-PROMO-04/README.md` — Evidence index for the promotion deployment, end-to-end run, and rollback.

- `docs/informe/evidencia/github/github-branches.png` — Binary asset.

- `docs/informe/evidencia/github/github-commit-history.png` — Binary asset.

- `docs/informe/evidencia/github/github-network-graph.png` — Binary asset.

- `docs/informe/evidencia/github/github-pr-approval.png` — Binary asset.

- `docs/informe/evidencia/github/github-pr-checks.png` — Binary asset.

- `docs/informe/evidencia/github/github-pr-conversation.png` — Binary asset.

- `docs/informe/evidencia/github/github-pr-template.png` — Binary asset.

- `docs/informe/evidencia/github/github-ruleset-main.png` — Binary asset.

- `docs/informe/evidencia/gw-ci-02/3-runs.txt` — Output of three consecutive gateway integration test runs.

- `docs/informe/evidencia/gw-ci-02/junit-integrationTest.xml` — JUnit XML report for the gateway Redis integration tests.

- `docs/informe/evidencia/gw-ci-02/redis-container.log.txt` — Log of the ephemeral Redis container used by the gateway integration tests.

- `docs/informe/evidencia/gw-ci-02/post-rebase/3-runs.txt` — Output of the three post-rebase integration runs.

- `docs/informe/evidencia/gw-ci-02/post-rebase/junit-integrationTest.xml` — JUnit XML report of the post-rebase verification.

- `docs/informe/evidencia/gw-ci-02/post-rebase/redis-container.log.txt` — Redis container log of the post-rebase verification.

- `docs/informe/evidencia/gw-ci-02/post-rebase/run1.log.txt` — Full Gradle log of the first post-rebase run.

- `docs/informe/evidencia/gw-ci-02/post-rebase/run2.log.txt` — Full Gradle log of the second post-rebase run.

- `docs/informe/evidencia/gw-ci-02/post-rebase/run3.log.txt` — Full Gradle log of the third post-rebase run.

- `docs/informe/evidencia/gw-ci-02/validacion/00-portada.png` — Cover image of the GW-CI-02 validation report.

- `docs/informe/evidencia/gw-ci-02/validacion/01-dependencias.png` — Capture of the Testcontainers dependencies in build.gradle.kts.

- `docs/informe/evidencia/gw-ci-02/validacion/02-clase-base.png` — Capture of AbstractRedisIntegrationTest.

- `docs/informe/evidencia/gw-ci-02/validacion/03-perfil.png` — Capture of application-test.yml.

- `docs/informe/evidencia/gw-ci-02/validacion/04-tarea.png` — Capture of the integrationTest Gradle task.

- `docs/informe/evidencia/gw-ci-02/validacion/05-jenkinsfile.png` — Capture of the Integration Test Jenkins stage.

- `docs/informe/evidencia/gw-ci-02/validacion/06-ci-helper.png` — Capture of the ci.integrationTest helper.

- `docs/informe/evidencia/gw-ci-02/validacion/07-unitarias.png` — Capture of the unit test and coverage run.

- `docs/informe/evidencia/gw-ci-02/validacion/08-integracion.png` — Capture of the integration test run.

- `docs/informe/evidencia/gw-ci-02/validacion/09-tres-corridas.png` — Capture of the three consecutive integration runs.

- `docs/informe/evidencia/gw-ci-02/validacion/10-docker.png` — Capture of the final container cleanup check.

- `docs/informe/evidencia/gw-ci-02/validacion/11-git-log.png` — Capture of the GW-CI-02 commit history.

- `docs/informe/evidencia/gw-ci-02/validacion/12-junit.png` — Capture of the integration JUnit report summary.

- `docs/informe/evidencia/gw-ci-02/validacion/13-ciclo-contenedor.png` — Diagram of the Redis container lifecycle.

- `docs/informe/evidencia/gw-ci-02/validacion/14-pipeline.png` — Diagram of the pipeline stage position.

- `docs/informe/evidencia/gw-ci-02/validacion/15-tres-corridas-post-rebase.png` — Capture of the three consecutive post-rebase integration runs.

- `docs/informe/evidencia/gw-ci-02/validacion/16-reporte-junit-html.png` — Capture of the JUnit HTML report (9 tests, 0 failures).

- `docs/informe/evidencia/gw-ci-02/validacion/17-detalle-pruebas-junit.png` — Capture of the nine integration test cases.

- `docs/informe/evidencia/gw-ci-02/validacion/18-corrida-integrationtest.png` — Capture of a complete integrationTest run.

- `docs/informe/evidencia/idempotencia-cli.png` — Binary asset.

- `docs/informe/evidencia/jenkins-pr15.png` — Binary asset.

- `docs/informe/evidencia/jenkins-professor.png` — Binary asset.

- `docs/informe/evidencia/jira/jira-backlog.png` — Binary asset.

- `docs/informe/evidencia/jira/jira-historias.png` — Binary asset.

- `docs/informe/evidencia/jira/jira-retrospectiva.png` — Binary asset.

- `docs/informe/evidencia/jira/jira-sprint-0.png` — Binary asset.

- `docs/informe/evidencia/jira/jira-sprint-1.png` — Binary asset.

- `docs/informe/evidencia/nexus-professor.png` — Binary asset.

- `docs/informe/evidencia/nexus-repositories.png` — Binary asset.

- `docs/informe/evidencia/sonarqube-professor.png` — Binary asset.

- `docs/informe/evidencia/sonarqube-quality-gate.png` — Binary asset.

- `docs/infra/aws-free-tier-validation.md` — AWS four-host sizing, exposure, and one-week cost validation.

- `docs/infra/ci-cd-setup.md` — Four-host CI/CD setup, exposure, credentials, and gateway pipeline.

- `docs/infra/environments.md` — Four-host topology and isolated dev, stage, and prod environments.

- `docs/infra/evidence/README.md` — Checklist of CI/CD evidence required for the gateway delivery.

- `docs/infra/evidence/idempotency/README.md` — Evidence that the CircleGuard bootstrap converges on repeated execution.

- `docs/infra/evidence/idempotency/dev.containers.after.txt` — Repository file.

- `docs/infra/evidence/idempotency/dev.containers.before.txt` — Repository file.

- `docs/infra/evidence/idempotency/diffs.txt` — Repository file.

- `docs/infra/evidence/idempotency/eips.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/eips.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/files.after` — Repository file.

- `docs/infra/evidence/idempotency/files.before` — Repository file.

- `docs/infra/evidence/idempotency/instances.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/instances.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/keys.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/keys.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/nexus-repos.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/nexus-repos.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/nexus-users.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/nexus-users.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/prod.containers.after.txt` — Repository file.

- `docs/infra/evidence/idempotency/prod.containers.before.txt` — Repository file.

- `docs/infra/evidence/idempotency/remote-env.after` — Repository file.

- `docs/infra/evidence/idempotency/remote-env.before` — Repository file.

- `docs/infra/evidence/idempotency/sgs.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/sgs.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/sonar-projects.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/sonar-projects.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/sonar-users.after.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/sonar-users.before.json` — JSON configuration or data file.

- `docs/infra/evidence/idempotency/stage.containers.after.txt` — Repository file.

- `docs/infra/evidence/idempotency/stage.containers.before.txt` — Repository file.

- `docs/infra/evidence/idempotency/tools.containers.after.txt` — Repository file.

- `docs/infra/evidence/idempotency/tools.containers.before.txt` — Repository file.

- `docs/infra/runbook.md` — Operations runbook for four hosts, private deployment, and cost controls.

- `docs/infra/tutorial-e1.2.md` — Step-by-step tutorial to clean, provision, verify, and operate the four-host gateway platform.

- `docs/map_editor_ui_mockup_1773983148035.png` — Binary asset.

- `docs/media__1773957579350.png` — Binary asset.

- `docs/media__1773957579382.png` — Binary asset.

- `docs/media__1773957579532.png` — Binary asset.

- `docs/media__1773957579552.png` — Binary asset.

- `docs/responsive_layout_admin_vs_mobile_sketch_1773958263536.png` — Binary asset.

- `docs/standards/documentation.md` — <one sentence describing the file purpose>

- `docs/user_journey_flows_sketch_1773957181837.png` — Binary asset.

- `docs/ux-design-directions.html` — Design document prototype: ux-design-directions.

- `docs/ux_design_directions_full_page_1773956578819.png` — Binary asset.

- `docs/ux_patterns_button_hierarchy_feedback_sketch_1773958065252.png` — Binary asset.

- `docs/visual-foundation.html` — Design document prototype: visual-foundation.

- `docs/visual_foundation_full_page_1773956281166.png` — Binary asset.

## gradle

- `gradle/wrapper/gradle-wrapper.jar` — Binary asset.

- `gradle/wrapper/gradle-wrapper.properties` — Gradle wrapper configuration.

## (root)

- `gradlew` — Generated Gradle wrapper launcher.

- `gradlew.bat` — Generated Gradle wrapper launcher.

## infra

- `infra/README.md` — Four-host AWS bootstrap, private registry, ingress, and cost controls.

- `infra/bootstrap/bootstrap-all.sh` — Orchestrates four-host provisioning, tools setup, and per-environment preparation.

- `infra/bootstrap/bootstrap-aws.sh` — Provisions four EC2 hosts, two security groups, one tools Elastic IP, and the SSH key pair.

- `infra/bootstrap/bootstrap-check.sh` — Validates AWS and local prerequisites without creating resources.

- `infra/bootstrap/bootstrap-common.sh` — Shared idempotent helpers for logging, Docker, secrets, SSH, and Compose.

- `infra/bootstrap/bootstrap-destroy.sh` — Destroys the four hosts, tools Elastic IP, security groups, key pair, and local state.

- `infra/bootstrap/bootstrap-envs.sh` — Prepares one isolated environment host (dev, stage, or prod).

- `infra/bootstrap/bootstrap-ips.sh` — Prints private and public IPs and public tool URLs.

- `infra/bootstrap/bootstrap-start.sh` — Starts the four provisioned instances and refreshes ephemeral public IPs.

- `infra/bootstrap/bootstrap-stop.sh` — Stops the four provisioned instances to reduce compute cost.

- `infra/bootstrap/bootstrap-tools.sh` — Provisions Jenkins, SonarQube, Nexus, the private registry, and the ingress on cg-tools.

- `infra/bootstrap/configure-sonar-identity.sh` — Creates the identity Sonar project and assigns the coverage quality gate with an admin credential.

## (root)

- `init-db.sql` — Database bootstrap script for local development.

- `logo-icesi.png` — Binary asset.

## mobile

- `mobile/.gitignore` — Mobile application file: .gitignore.

- `mobile/README.md` — Expo mobile client structure, configuration, and test commands.

- `mobile/app.json` — JSON configuration or data file.

- `mobile/app/(tabs)/_layout.tsx` — Expo screen or layout for the mobile app: _layout.

- `mobile/app/(tabs)/admin/_layout.tsx` — Expo screen or layout for the mobile app: _layout.

- `mobile/app/(tabs)/admin/analytics.tsx` — Expo screen or layout for the mobile app: analytics.

- `mobile/app/(tabs)/admin/index.tsx` — Expo screen or layout for the mobile app: index.

- `mobile/app/(tabs)/circles.tsx` — Expo screen or layout for the mobile app: circles.

- `mobile/app/(tabs)/index.tsx` — Expo screen or layout for the mobile app: index.

- `mobile/app/(tabs)/report.tsx` — Expo screen or layout for the mobile app: report.

- `mobile/app/(tabs)/scan.tsx` — Expo screen or layout for the mobile app: scan.

- `mobile/app/+html.tsx` — Expo screen or layout for the mobile app: +html.

- `mobile/app/+not-found.tsx` — Expo screen or layout for the mobile app: +not-found.

- `mobile/app/_layout.tsx` — Expo screen or layout for the mobile app: _layout.

- `mobile/app/admin/index.tsx` — Expo screen or layout for the mobile app: index.

- `mobile/app/admin/intake.tsx` — Expo screen or layout for the mobile app: intake.

- `mobile/app/admin/spatial/[buildingId]/floors/[floorId].tsx` — Expo screen or layout for the mobile app: [floorId].

- `mobile/app/admin/spatial/_layout.tsx` — Expo screen or layout for the mobile app: _layout.

- `mobile/app/admin/spatial/index.tsx` — Expo screen or layout for the mobile app: index.

- `mobile/app/enroll.tsx` — Expo screen or layout for the mobile app: enroll.

- `mobile/app/global.css` — Expo screen or layout for the mobile app: global.

- `mobile/app/login.tsx` — Expo screen or layout for the mobile app: login.

- `mobile/app/modal.tsx` — Expo screen or layout for the mobile app: modal.

- `mobile/app/questionnaire.tsx` — Expo screen or layout for the mobile app: questionnaire.

- `mobile/app/visitor.tsx` — Expo screen or layout for the mobile app: visitor.

- `mobile/assets/fonts/SpaceMono-Regular.ttf` — Binary asset.

- `mobile/assets/images/android-icon-background.png` — Binary asset.

- `mobile/assets/images/android-icon-foreground.png` — Binary asset.

- `mobile/assets/images/android-icon-monochrome.png` — Binary asset.

- `mobile/assets/images/favicon.png` — Binary asset.

- `mobile/assets/images/icon.png` — Binary asset.

- `mobile/assets/images/splash-icon.png` — Binary asset.

- `mobile/babel.config.js` — Mobile application file: babel.config.

- `mobile/components/DynamicForm.tsx` — Reusable React Native component: Dynamic Form.

- `mobile/components/EditScreenInfo.tsx` — Reusable React Native component: Edit Screen Info.

- `mobile/components/ExternalLink.tsx` — Reusable React Native component: External Link.

- `mobile/components/MeshBackground.tsx` — Reusable React Native component: Mesh Background.

- `mobile/components/QuestionInput.tsx` — Reusable React Native component: Question Input.

- `mobile/components/ScanResultOverlay.tsx` — Reusable React Native component: Scan Result Overlay.

- `mobile/components/StyledText.tsx` — Reusable React Native component: Styled Text.

- `mobile/components/Themed.tsx` — Reusable React Native component: Themed.

- `mobile/components/__tests__/DynamicForm.test.tsx` — Reusable React Native component: Dynamic Form.test.

- `mobile/components/useClientOnlyValue.ts` — Reusable React Native component: use Client Only Value.

- `mobile/components/useClientOnlyValue.web.ts` — Reusable React Native component: use Client Only Value.web.

- `mobile/components/useColorScheme.ts` — Reusable React Native component: use Color Scheme.

- `mobile/components/useColorScheme.web.ts` — Reusable React Native component: use Color Scheme.web.

- `mobile/constants/Colors.ts` — Mobile constants module: Colors.

- `mobile/constants/Config.ts` — Mobile constants module: Config.

- `mobile/context/AuthContext.tsx` — React context provider for the mobile app: Auth Context.

- `mobile/hooks/useAuth.ts` — React hook for the mobile app: use Auth.

- `mobile/hooks/useBiometricAuth.ts` — React hook for the mobile app: use Biometric Auth.

- `mobile/hooks/useCircles.ts` — React hook for the mobile app: use Circles.

- `mobile/hooks/useGateValidation.ts` — React hook for the mobile app: use Gate Validation.

- `mobile/hooks/useMeshStats.ts` — React hook for the mobile app: use Mesh Stats.

- `mobile/hooks/usePermission.ts` — React hook for the mobile app: use Permission.

- `mobile/hooks/useQrToken.test.ts` — React hook for the mobile app: use Qr Token.test.

- `mobile/hooks/useQrToken.ts` — React hook for the mobile app: use Qr Token.

- `mobile/hooks/useSpatial.ts` — React hook for the mobile app: use Spatial.

- `mobile/metro.config.js` — Mobile application file: metro.config.

- `mobile/nativewind-env.d.ts` — Mobile application file: nativewind-env.d.

- `mobile/package-lock.json` — Dependency lockfile; generated by npm.

- `mobile/package.json` — JSON configuration or data file.

- `mobile/tailwind.config.js` — Mobile application file: tailwind.config.

- `mobile/tsconfig.json` — JSON configuration or data file.

- `mobile/utils/proximityScanner.ts` — Mobile utility module: proximity Scanner.

- `mobile/utils/proximityTask.ts` — Mobile utility module: proximity Task.

- `mobile/utils/storage.ts` — Mobile utility module: storage.

## scratch

- `scratch/run_seed.py` — Repository file.

- `scratch/verify_dept.py` — Repository file.

## services

- `services/README.md` — Overview of CircleGuard microservices, build commands, and documentation conventions.

- `services/circleguard-auth-service/build.gradle.kts` — Build configuration for the auth service.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/AuthServiceApplication.java` — Java source for the auth service: Auth Service Application.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/client/IdentityClient.java` — integration client for the auth service: Identity Client.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/client/package-info.java` — Package com.circleguard.auth.client for the auth service (client layer).

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/controller/LoginController.java` — HTTP controller for the auth service: Login Controller.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/controller/QrTokenController.java` — HTTP controller for the auth service: Qr Token Controller.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/controller/UserController.java` — HTTP controller for the auth service: User Controller.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/controller/package-info.java` — Package com.circleguard.auth.controller for the auth service (controller layer).

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/model/LocalUser.java` — data model for the auth service: Local User.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/model/Permission.java` — data model for the auth service: Permission.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/model/Role.java` — data model for the auth service: Role.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/model/package-info.java` — Package com.circleguard.auth.model for the auth service (model layer).

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/package-info.java` — Package com.circleguard.auth for the auth service (auth layer).

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/repository/LocalUserRepository.java` — persistence repository for the auth service: Local User Repository.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/repository/package-info.java` — Package com.circleguard.auth.repository for the auth service (repository layer).

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/DualChainAuthenticationProvider.java` — security component for the auth service: Dual Chain Authentication Provider.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/JwtAuthenticationFilter.java` — security component for the auth service: Jwt Authentication Filter.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/SecurityConfig.java` — security component for the auth service: Security Config.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/package-info.java` — Package com.circleguard.auth.security for the auth service (security layer).

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/service/CustomUserDetailsService.java` — business service for the auth service: Custom User Details Service.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/service/JwtTokenService.java` — business service for the auth service: Jwt Token Service.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/service/QrTokenService.java` — business service for the auth service: Qr Token Service.

- `services/circleguard-auth-service/src/main/java/com/circleguard/auth/service/package-info.java` — Package com.circleguard.auth.service for the auth service (service layer).

- `services/circleguard-auth-service/src/main/resources/application.yml` — Runtime resource for the auth service: application.yml.

- `services/circleguard-auth-service/src/main/resources/db/migration/V1__initial_auth_schema.sql` — Database migration V1__initial_auth_schema for the auth service.

- `services/circleguard-auth-service/src/main/resources/db/migration/V2__seed_test_users.sql` — Database migration V2__seed_test_users for the auth service.

- `services/circleguard-auth-service/src/main/resources/db/migration/V3__fix_user_passwords.sql` — Database migration V3__fix_user_passwords for the auth service.

- `services/circleguard-auth-service/src/main/resources/db/migration/V4__add_alert_permission.sql` — Database migration V4__add_alert_permission for the auth service.

- `services/circleguard-auth-service/src/main/resources/db/migration/V5__add_dashboard_permission.sql` — Database migration V5__add_dashboard_permission for the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/AuthIntegrationTest.java` — Unit or integration tests for Auth Integration Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/client/IdentityClientTest.java` — Unit or integration tests for Identity Client Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/controller/LoginControllerTest.java` — Unit or integration tests for Login Controller Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/controller/QrTokenControllerTest.java` — Unit or integration tests for Qr Token Controller Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/controller/UserControllerTest.java` — Unit or integration tests for User Controller Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/security/DualChainAuthenticationProviderTest.java` — Unit or integration tests for Dual Chain Authentication Provider Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/security/JwtAuthenticationFilterTest.java` — Unit or integration tests for Jwt Authentication Filter Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/service/CustomUserDetailsServiceTest.java` — Unit or integration tests for Custom User Details Service Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/service/JwtTokenServiceTest.java` — Unit or integration tests for Jwt Token Service Test in the auth service.

- `services/circleguard-auth-service/src/test/java/com/circleguard/auth/service/QrTokenServiceTest.java` — Unit or integration tests for Qr Token Service Test in the auth service.

- `services/circleguard-auth-service/src/test/resources/application.yml` — Unit or integration tests for application in the auth service.

- `services/circleguard-dashboard-service/build.gradle.kts` — Build configuration for the dashboard service.

- `services/circleguard-dashboard-service/Dockerfile` — Multi-stage, non-root dashboard image with healthcheck.

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/DashboardApplication.java` — Java source for the dashboard service: Dashboard Application.

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/client/PromotionClient.java` — integration client for the dashboard service: Promotion Client.

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/client/package-info.java` — Package com.circleguard.dashboard.client for the dashboard service (client layer).

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/controller/AnalyticsController.java` — HTTP controller for the dashboard service: Analytics Controller.

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/controller/package-info.java` — Package com.circleguard.dashboard.controller for the dashboard service (controller layer).

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/package-info.java` — Package com.circleguard.dashboard for the dashboard service (dashboard layer).

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/AnalyticsService.java` — business service for the dashboard service: Analytics Service.

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/KAnonymityFilter.java` — business service for the dashboard service: K Anonymity Filter.

- `services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/package-info.java` — Package com.circleguard.dashboard.service for the dashboard service (service layer).

- `services/circleguard-dashboard-service/src/main/resources/application.yml` — Runtime resource for the dashboard service: application.yml.

- `services/circleguard-dashboard-service/src/main/resources/db/migration/V1__initial_dashboard_schema.sql` — Database migration V1__initial_dashboard_schema for the dashboard service.

- `services/circleguard-dashboard-service/src/test/java/com/circleguard/dashboard/controller/AnalyticsControllerTest.java` — Unit or integration tests for Analytics Controller Test in the dashboard service.

- `services/circleguard-file-service/build.gradle.kts` — Build configuration for the file service.

- `services/circleguard-file-service/Dockerfile` — Multi-stage, non-root file image with healthcheck.

- `services/circleguard-file-service/src/main/java/com/circleguard/file/FileApplication.java` — Java source for the file service: File Application.

- `services/circleguard-file-service/src/main/java/com/circleguard/file/controller/FileUploadController.java` — HTTP controller for the file service: File Upload Controller.

- `services/circleguard-file-service/src/main/java/com/circleguard/file/controller/package-info.java` — Package com.circleguard.file.controller for the file service (controller layer).

- `services/circleguard-file-service/src/main/java/com/circleguard/file/package-info.java` — Package com.circleguard.file for the file service (file layer).

- `services/circleguard-file-service/src/main/java/com/circleguard/file/service/FileStorageService.java` — business service for the file service: File Storage Service.

- `services/circleguard-file-service/src/main/java/com/circleguard/file/service/package-info.java` — Package com.circleguard.file.service for the file service (service layer).

- `services/circleguard-file-service/src/main/resources/application.yml` — Runtime resource for the file service: application.yml.

- `services/circleguard-file-service/src/test/java/com/circleguard/file/controller/FileUploadControllerTest.java` — Unit or integration tests for File Upload Controller Test in the file service.

- `services/circleguard-form-service/README.md` — Service documentation for form.

- `services/circleguard-form-service/build.gradle.kts` — Build configuration for the form service.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/FormApplication.java` — Java source for the form service: Form Application.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/controller/AttachmentController.java` — HTTP controller for the form service: Attachment Controller.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/controller/CertificateValidationController.java` — HTTP controller for the form service: Certificate Validation Controller.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/controller/HealthSurveyController.java` — HTTP controller for the form service: Health Survey Controller.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/controller/QuestionnaireController.java` — HTTP controller for the form service: Questionnaire Controller.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/controller/package-info.java` — Package com.circleguard.form.controller for the form service (controller layer).

- `services/circleguard-form-service/src/main/java/com/circleguard/form/model/HealthSurvey.java` — data model for the form service: Health Survey.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/model/Question.java` — data model for the form service: Question.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/model/QuestionType.java` — data model for the form service: Question Type.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/model/Questionnaire.java` — data model for the form service: Questionnaire.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/model/ValidationStatus.java` — data model for the form service: Validation Status.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/model/package-info.java` — Package com.circleguard.form.model for the form service (model layer).

- `services/circleguard-form-service/src/main/java/com/circleguard/form/package-info.java` — Package com.circleguard.form for the form service (form layer).

- `services/circleguard-form-service/src/main/java/com/circleguard/form/repository/HealthSurveyRepository.java` — persistence repository for the form service: Health Survey Repository.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/repository/QuestionRepository.java` — persistence repository for the form service: Question Repository.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/repository/QuestionnaireRepository.java` — persistence repository for the form service: Questionnaire Repository.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/repository/package-info.java` — Package com.circleguard.form.repository for the form service (repository layer).

- `services/circleguard-form-service/src/main/java/com/circleguard/form/service/HealthSurveyService.java` — business service for the form service: Health Survey Service.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/service/QuestionnaireService.java` — business service for the form service: Questionnaire Service.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/service/StorageService.java` — business service for the form service: Storage Service.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/service/SymptomMapper.java` — business service for the form service: Symptom Mapper.

- `services/circleguard-form-service/src/main/java/com/circleguard/form/service/package-info.java` — Package com.circleguard.form.service for the form service (service layer).

- `services/circleguard-form-service/src/main/resources/application.yml` — Runtime resource for the form service: application.yml.

- `services/circleguard-form-service/src/main/resources/db/migration/V1__initial_survey_schema.sql` — Database migration V1__initial_survey_schema for the form service.

- `services/circleguard-form-service/src/main/resources/db/migration/V2__dynamic_questionnaire_schema.sql` — Database migration V2__dynamic_questionnaire_schema for the form service.

- `services/circleguard-form-service/src/main/resources/db/migration/V3__add_responses_to_survey.sql` — Database migration V3__add_responses_to_survey for the form service.

- `services/circleguard-form-service/src/main/resources/db/migration/V4__add_attachment_to_survey.sql` — Database migration V4__add_attachment_to_survey for the form service.

- `services/circleguard-form-service/src/main/resources/db/migration/V5__add_validation_status_to_survey.sql` — Database migration V5__add_validation_status_to_survey for the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/controller/AttachmentControllerTest.java` — Unit or integration tests for Attachment Controller Test in the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/controller/CertificateValidationControllerTest.java` — Unit or integration tests for Certificate Validation Controller Test in the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/controller/HealthSurveyControllerTest.java` — Unit or integration tests for Health Survey Controller Test in the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/controller/QuestionnaireControllerTest.java` — Unit or integration tests for Questionnaire Controller Test in the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/integration/FormPromotionKafkaIntegrationTest.java` — Unit or integration tests for Form Promotion Kafka Integration Test in the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/service/HealthSurveyServiceTest.java` — Unit or integration tests for Health Survey Service Test in the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/service/QuestionnaireServiceTest.java` — Unit or integration tests for Questionnaire Service Test in the form service.

- `services/circleguard-form-service/src/test/java/com/circleguard/form/service/SymptomMapperTest.java` — Unit or integration tests for Symptom Mapper Test in the form service.

- `services/circleguard-gateway-service/Dockerfile` — Container image definition for the gateway service.

- `services/circleguard-gateway-service/README_GATEWAY_ANALISIS.pdf` — Binary asset.

- `services/circleguard-gateway-service/build.gradle.kts` — Build configuration for the gateway service.

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/GatewayServiceApplication.java` — Java source for the gateway service: Gateway Service Application.

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/config/ApiExceptionHandler.java` — configuration for the gateway service: Api Exception Handler.

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/config/SecurityConfig.java` — configuration for the gateway service: Security Config.

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/config/package-info.java` — Package com.circleguard.gateway.config for the gateway service (config layer).

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/controller/GateController.java` — HTTP controller for the gateway service: Gate Controller.

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/controller/package-info.java` — Package com.circleguard.gateway.controller for the gateway service (controller layer).

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/package-info.java` — Package com.circleguard.gateway for the gateway service (gateway layer).

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/security/JwtAuthenticationFilter.java` — security component for the gateway service: Jwt Authentication Filter.

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/security/package-info.java` — Package com.circleguard.gateway.security for the gateway service (security layer).

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java` — business service for the gateway service: Qr Validation Service.

- `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/service/package-info.java` — Package com.circleguard.gateway.service for the gateway service (service layer).

- `services/circleguard-gateway-service/src/main/resources/application.yml` — Runtime resource for the gateway service: application.yml.

- `services/circleguard-gateway-service/src/test/java/com/circleguard/gateway/controller/GateControllerTest.java` — Unit or integration tests for Gate Controller Test in the gateway service.

- `services/circleguard-gateway-service/src/test/java/com/circleguard/gateway/integration/AbstractRedisIntegrationTest.java` — Base class that starts an ephemeral Redis container for gateway integration tests.

- `services/circleguard-gateway-service/src/test/java/com/circleguard/gateway/integration/GatewayRedisIntegrationTest.java` — Integration test for gateway QR validation against a real Redis container.

- `services/circleguard-gateway-service/src/test/java/com/circleguard/gateway/security/JwtAuthenticationFilterTest.java` — Unit or integration tests for Jwt Authentication Filter Test in the gateway service.

- `services/circleguard-gateway-service/src/test/java/com/circleguard/gateway/service/QrValidationServiceTest.java` — Unit or integration tests for Qr Validation Service Test in the gateway service.

- `services/circleguard-gateway-service/src/test/resources/application-test.yml` — Test profile for gateway integration tests backed by an ephemeral Redis container.

- `services/circleguard-gateway-service/src/test/resources/docker-java.properties` — Docker Java API version required by Testcontainers on modern Docker engines.

- `services/circleguard-identity-service/build.gradle.kts` — Build configuration for the identity service.

- `services/circleguard-identity-service/Dockerfile` — Multi-stage immutable image with non-root runtime and healthcheck.

- `services/circleguard-identity-service/sonar-project.properties` — SonarQube project and JaCoCo coverage settings for the identity service.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/IdentityServiceApplication.java` — Java source for the identity service: Identity Service Application.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/config/JwtAuthenticationFilter.java` — configuration for the identity service: Jwt Authentication Filter.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/config/SecurityConfig.java` — configuration for the identity service: Security Config.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/config/package-info.java` — Package com.circleguard.identity.config for the identity service (config layer).

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/controller/IdentityVaultController.java` — HTTP controller for the identity service: Identity Vault Controller.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/controller/package-info.java` — Package com.circleguard.identity.controller for the identity service (controller layer).

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/event/IdentityAccessEvent.java` — event contract for the identity service: Identity Access Event.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/event/package-info.java` — Package com.circleguard.identity.event for the identity service (event layer).

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/model/IdentityMapping.java` — data model for the identity service: Identity Mapping.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/model/package-info.java` — Package com.circleguard.identity.model for the identity service (model layer).

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/package-info.java` — Package com.circleguard.identity for the identity service (identity layer).

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/repository/IdentityMappingRepository.java` — persistence repository for the identity service: Identity Mapping Repository.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/repository/package-info.java` — Package com.circleguard.identity.repository for the identity service (repository layer).

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/service/IdentityVaultService.java` — business service for the identity service: Identity Vault Service.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/service/package-info.java` — Package com.circleguard.identity.service for the identity service (service layer).

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/util/IdentityEncryptionConverter.java` — utility for the identity service: Identity Encryption Converter.

- `services/circleguard-identity-service/src/main/java/com/circleguard/identity/util/package-info.java` — Package com.circleguard.identity.util for the identity service (util layer).

- `services/circleguard-identity-service/src/main/resources/application.yml` — Runtime resource for the identity service: application.yml.

- `services/circleguard-identity-service/src/main/resources/db/migration/V1__initial_identity_schema.sql` — Database migration V1__initial_identity_schema for the identity service.

- `services/circleguard-identity-service/src/test/java/com/circleguard/identity/controller/IdentityVaultControllerTest.java` — Unit or integration tests for Identity Vault Controller Test in the identity service.

- `services/circleguard-identity-service/src/test/java/com/circleguard/identity/repository/IdentityMappingRepositoryTest.java` — Unit or integration tests for Identity Mapping Repository Test in the identity service.

- `services/circleguard-identity-service/src/test/java/com/circleguard/identity/service/IdentityVaultServiceTest.java` — Unit tests for identity mapping creation, reuse, and restricted lookup.

- `services/circleguard-identity-service/src/test/java/com/circleguard/identity/util/IdentityEncryptionConverterTest.java` — Unit or integration tests for Identity Encryption Converter Test in the identity service.

- `services/circleguard-identity-service/src/test/resources/application.yml` — Unit or integration tests for application in the identity service.

- `services/circleguard-notification-service/build.gradle.kts` — Build configuration for the notification service.

- `services/circleguard-notification-service/Dockerfile` — Multi-stage, non-root notification image with healthcheck.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/NotificationApplication.java` — Java source for the notification service: Notification Application.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/package-info.java` — Package com.circleguard.notification for the notification service (notification layer).

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/AuditLogService.java` — business service for the notification service: Audit Log Service.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/CircleFencedListener.java` — business service for the notification service: Circle Fenced Listener.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/EmailService.java` — business service for the notification service: Email Service.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/EmailServiceImpl.java` — business service for the notification service: Email Service Impl.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/ExposureNotificationListener.java` — business service for the notification service: Exposure Notification Listener.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/LmsService.java` — business service for the notification service: Lms Service.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/LmsServiceImpl.java` — business service for the notification service: Lms Service Impl.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/NotificationDispatcher.java` — business service for the notification service: Notification Dispatcher.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/PriorityAlertListener.java` — business service for the notification service: Priority Alert Listener.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/PushService.java` — business service for the notification service: Push Service.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/PushServiceImpl.java` — business service for the notification service: Push Service Impl.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/RoomReservationService.java` — business service for the notification service: Room Reservation Service.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/RoomReservationServiceImpl.java` — business service for the notification service: Room Reservation Service Impl.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/SmsService.java` — business service for the notification service: Sms Service.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/SmsServiceImpl.java` — business service for the notification service: Sms Service Impl.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/TemplateService.java` — business service for the notification service: Template Service.

- `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/package-info.java` — Package com.circleguard.notification.service for the notification service (service layer).

- `services/circleguard-notification-service/src/main/resources/application.yml` — Runtime resource for the notification service: application.yml.

- `services/circleguard-notification-service/src/main/resources/templates/health_alert.ftl` — Notification template for the notification service.

- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/ExposureNotificationListenerTest.java` — Unit or integration tests for Exposure Notification Listener Test in the notification service.

- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/LmsServiceTest.java` — Unit or integration tests for Lms Service Test in the notification service.

- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/NotificationDispatcherTest.java` — Unit or integration tests for Notification Dispatcher Test in the notification service.

- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/NotificationRetryTest.java` — Unit or integration tests for Notification Retry Test in the notification service.

- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/PriorityAlertListenerTest.java` — Unit or integration tests for Priority Alert Listener Test in the notification service.

- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/RoomReservationServiceTest.java` — Unit or integration tests for Room Reservation Service Test in the notification service.

- `services/circleguard-notification-service/src/test/java/com/circleguard/notification/service/TemplateServiceTest.java` — Unit or integration tests for Template Service Test in the notification service.

- `services/circleguard-promotion-service/build.gradle.kts` — Build configuration for the promotion service.

- `services/circleguard-promotion-service/Dockerfile` — Multi-stage, non-root promotion image with healthcheck.

- `services/circleguard-promotion-service/sonar-project.properties` — SonarQube project settings and JaCoCo coverage path for promotion.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/PromotionApplication.java` — Java source for the promotion service: Promotion Application.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/CacheConfig.java` — configuration for the promotion service: Cache Config.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/DataLayerConfig.java` — configuration for the promotion service: Data Layer Config.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/JpaConfig.java` — configuration for the promotion service: Jpa Config.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/Neo4jConfig.java` — configuration for the promotion service: Neo4j Config.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/Neo4jSchemaConfig.java` — configuration for the promotion service: Neo4j Schema Config.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/package-info.java` — Package com.circleguard.promotion.config for the promotion service (config layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/AccessPointController.java` — HTTP controller for the promotion service: Access Point Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/AdminController.java` — HTTP controller for the promotion service: Admin Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/BuildingController.java` — HTTP controller for the promotion service: Building Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/CircleController.java` — HTTP controller for the promotion service: Circle Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/EncounterController.java` — HTTP controller for the promotion service: Encounter Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/FloorController.java` — HTTP controller for the promotion service: Floor Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/HealthStatsController.java` — HTTP controller for the promotion service: Health Stats Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/HealthStatusController.java` — HTTP controller for the promotion service: Health Status Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/LocationSignalController.java` — HTTP controller for the promotion service: Location Signal Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/MeshController.java` — HTTP controller for the promotion service: Mesh Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/SessionHandshakeController.java` — HTTP controller for the promotion service: Session Handshake Controller.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/controller/package-info.java` — Package com.circleguard.promotion.controller for the promotion service (controller layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/dto/AccessPointDTO.java` — data transfer object for the promotion service: Access Point D T O.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/dto/BuildingDTO.java` — data transfer object for the promotion service: Building D T O.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/dto/FloorDTO.java` — data transfer object for the promotion service: Floor D T O.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/dto/package-info.java` — Package com.circleguard.promotion.dto for the promotion service (dto layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/exception/FenceException.java` — exception handling for the promotion service: Fence Exception.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/exception/package-info.java` — Package com.circleguard.promotion.exception for the promotion service (exception layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java` — Java source for the promotion service: Survey Listener.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/package-info.java` — Package com.circleguard.promotion.listener for the promotion service (listener layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/AccessPoint.java` — data model for the promotion service: Access Point.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/Building.java` — data model for the promotion service: Building.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/Floor.java` — data model for the promotion service: Floor.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/graph/CircleNode.java` — data model for the promotion service: Circle Node.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/graph/EncounterRelationship.java` — data model for the promotion service: Encounter Relationship.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/graph/UserNode.java` — data model for the promotion service: User Node.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/graph/package-info.java` — Package com.circleguard.promotion.model.graph for the promotion service (graph layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/jpa/SystemSettings.java` — data model for the promotion service: System Settings.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/jpa/package-info.java` — Package com.circleguard.promotion.model.jpa for the promotion service (jpa layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/model/package-info.java` — Package com.circleguard.promotion.model for the promotion service (model layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/package-info.java` — Package com.circleguard.promotion for the promotion service (promotion layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/graph/CircleNodeRepository.java` — persistence repository for the promotion service: Circle Node Repository.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/graph/UserNodeRepository.java` — persistence repository for the promotion service: User Node Repository.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/graph/package-info.java` — Package com.circleguard.promotion.repository.graph for the promotion service (graph layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/jpa/AccessPointRepository.java` — persistence repository for the promotion service: Access Point Repository.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/jpa/BuildingRepository.java` — persistence repository for the promotion service: Building Repository.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/jpa/FloorRepository.java` — persistence repository for the promotion service: Floor Repository.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/jpa/SystemSettingsRepository.java` — persistence repository for the promotion service: System Settings Repository.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/jpa/package-info.java` — Package com.circleguard.promotion.repository.jpa for the promotion service (jpa layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/security/JwtAuthenticationFilter.java` — security component for the promotion service: Jwt Authentication Filter.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/security/SecurityConfig.java` — security component for the promotion service: Security Config.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/security/package-info.java` — Package com.circleguard.promotion.security for the promotion service (security layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/AccessPointService.java` — business service for the promotion service: Access Point Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/AutoCircleService.java` — business service for the promotion service: Auto Circle Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/BuildingService.java` — business service for the promotion service: Building Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/CircleService.java` — business service for the promotion service: Circle Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/FloorService.java` — business service for the promotion service: Floor Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/GraphService.java` — business service for the promotion service: Graph Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java` — business service for the promotion service: Health Status Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/LocationResolutionService.java` — business service for the promotion service: Location Resolution Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/MacSessionRegistry.java` — business service for the promotion service: Mac Session Registry.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/SpatialService.java` — business service for the promotion service: Spatial Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/StatusLifecycleService.java` — business service for the promotion service: Status Lifecycle Service.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/package-info.java` — Package com.circleguard.promotion.service for the promotion service (service layer).

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/task/GraphCleanupTask.java` — Java source for the promotion service: Graph Cleanup Task.

- `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/task/package-info.java` — Package com.circleguard.promotion.task for the promotion service (task layer).

- `services/circleguard-promotion-service/src/main/resources/application.yml` — Runtime resource for the promotion service: application.yml.

- `services/circleguard-promotion-service/src/main/resources/db/migration/V1__initial_spatial_schema.sql` — Database migration V1__initial_spatial_schema for the promotion service.

- `services/circleguard-promotion-service/src/main/resources/db/migration/V2__add_status_timing_settings.sql` — Database migration V2__add_status_timing_settings for the promotion service.

- `services/circleguard-promotion-service/src/main/resources/db/migration/V3__extend_building_coordinates.sql` — Database migration V3__extend_building_coordinates for the promotion service.

- `services/circleguard-promotion-service/src/main/resources/seed_faculties.cypher` — Neo4j seed or query script for the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/controller/HealthStatusControllerTest.java` — Unit or integration tests for Health Status Controller Test in the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/listener/SurveyListenerTest.java` — Unit or integration tests for Survey Listener Test in the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/performance/PromotionPerformanceTest.java` — Unit or integration tests for Promotion Performance Test in the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/service/AdministrativeCorrectionTest.java` — Unit or integration tests for Administrative Correction Test in the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/service/FloorServiceTest.java` — Unit or integration tests for Floor Service Test in the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/service/HealthStatusReevaluationTest.java` — Unit or integration tests for Health Status Reevaluation Test in the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/service/HealthStatusServiceTest.java` — Unit or integration tests for Health Status Service Test in the promotion service.

- `services/circleguard-promotion-service/src/test/java/com/circleguard/promotion/service/StatusLifecycleTest.java` — Unit or integration tests for Status Lifecycle Test in the promotion service.

- `services/circleguard-promotion-service/src/test/resources/application-test.yml` — Unit or integration tests for application-test in the promotion service.

## (root)

- `settings.gradle.kts` — Gradle multi-project settings and module registration.

- `sonar-project.properties` — SonarQube project keys, sources, and coverage report configuration.

## tests

- `tests/README.md` — Test suites, end-to-end execution, and required environment variables.

- `tests/e2e/access-validation.spec.ts` — End-to-end test configuration or scenario: access-validation.spec.

- `tests/e2e/capture-evidence.sh` — Captures public evidence screenshots with Playwright when URLs are available.

- `tests/e2e/capture-professor.mjs` — Captures professor read-only evidence from Jenkins, SonarQube, and Nexus.

- `tests/e2e/package-lock.json` — Dependency lockfile; generated by npm.

- `tests/e2e/package.json` — JSON configuration or data file.

- `tests/e2e/playwright.config.ts` — End-to-end test configuration or scenario: playwright.config.

- `tests/e2e/promotion-flow.spec.ts` — End-to-end scenario: questionnaire event through Kafka into the promotion status.

## jenkins

- `jenkins/vars/ci.groovy` — Shared Jenkins pipeline helpers for build, analysis, publish, deploy, and notify.
- `jenkins/vars/ciServices.groovy` — Service catalog and selective execution (auto, all, or one service).
- `jenkins/vars/ciVersion.groovy` — Semantic version resolution and Git tag creation from Conventional Commits.
- `jenkins/vars/ciNotify.groovy` — GitHub and Slack failure notifications.
