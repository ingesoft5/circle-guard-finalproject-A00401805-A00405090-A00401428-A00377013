<!-- CG-DOC: Checklist of CI/CD evidence required for the gateway delivery. -->
# CI/CD Evidence

Store screenshots and command outputs in this directory:

- [ ] Gateway pipeline with a green build.
- [ ] SonarQube quality gate approved.
- [ ] Nexus containing the JAR and the image in `circleguard-docker`.
- [ ] `docker ps` output on `cg-tools` and `cg-envs`.
- [ ] Health checks for `dev`, `stage`, and `prod`.
- [ ] Rollback executed through `ROLLBACK_TAG`.
- [ ] URLs and read-only user for the instructor.

Evidence files must not contain passwords or private keys.
