<!-- CG-DOC: Evidence that the CircleGuard bootstrap converges on repeated execution. -->
# Idempotency Evidence

## Result

`diff_lines=0`

A second full execution of `./infra/bootstrap/bootstrap-all.sh` produced no changes in:

- EC2 instance IDs, states, private IPs, public IPs, and types.
- Elastic IPs.
- Security Groups and rules.
- Key pairs.
- Local and remote `.env` hashes.
- Container IDs and images.
- Nexus repositories and users.
- SonarQube projects and users.

## Run

- Branch: `test/HU-INFRA-01-idempotencia`
- Region: `us-east-2`
- Bootstrap exit code: `0`
- Evidence date: 8 November 2026

## Files

- `files.before` / `files.after`: hashes of safe local state files.
- `remote-env.before` / `remote-env.after`: hashes of remote `.env` files.
- `instances.*.json`: normalized EC2 state.
- `eips.*.json`: normalized Elastic IP state.
- `sgs.*.json`: normalized Security Group state.
- `keys.*.json`: key pair names.
- `*.containers.*.txt`: container IDs and images per host.
- `nexus-*.json`: repositories and users.
- `sonar-*.json`: projects and users.
- `diffs.txt`: empty if idempotency passed.
- `bootstrap-final-run.log`: second execution log.

## Notes

No passwords, tokens, or private keys are included. `files.*` and `remote-env.*` contain SHA-256 hashes only.
