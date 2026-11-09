<!-- CG-DOC: Evidence checklist for the E1.3 gateway image delivery. -->
# E1.3 Gateway Image Evidence

Store the real screenshots and command outputs for `GW-CI-03` in this directory.
Do not include passwords, tokens, or private keys.

## Required screenshots

| File | Evidence | Command or location |
|---|---|---|
| `01-docker-inspect-user.png` | Container user is not root | `docker inspect --format '{{.Config.User}}' <image>` |
| `02-container-id.png` | Runtime UID/GID | `docker run --rm <image> id` |
| `03-no-build-tools.png` | No JDK or Gradle in runtime | `docker run --rm <image> sh -c 'command -v javac \|\| echo absent; command -v gradle \|\| echo absent'` |
| `04-health-up.png` | Actuator health is UP on 8087 | `curl -s http://localhost:8087/actuator/health` |
| `05-compose-ps.png` | Gateway and Redis healthy | `docker compose -f deploy/compose/dev/docker-compose.yml ps` |
| `06-registry-tags.png` | Nexus tag list without `latest` | Nexus UI or `curl http://<registry>/v2/circleguard-docker/gateway/tags/list` |
| `07-registry-digest.png` | Published tag and digest | Nexus UI or `docker inspect --format '{{index .RepoDigests 0}}' <image>` |
| `08-image-size.png` | Final image size | `docker images <image>` |
| `09-jenkins-digest.png` | Pipeline log with tag and digest | Jenkins Docker/Publish stage |

## Required text logs

- `docker-build.log`
- `docker-inspect-user.txt`
- `container-id.txt`
- `no-build-tools.txt`
- `health-up.json`
- `compose-ps.txt`
- `compose-config-dev.txt`
- `compose-config-stage.txt`
- `compose-config-prod.txt`
- `registry-tags.json`
- `image-digest.txt`
- `image-size.txt`

## Notes

- Use tag `v1.0.0-local` for the local evidence and `vX.Y.Z` for the release.
- The registry is `<NEXUS_REGISTRY>` (Nexus Docker connector on port 5000).
- Trivy scanning is out of scope for E1.3 and belongs to Part 2.
