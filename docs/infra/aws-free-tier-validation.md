<!-- CG-DOC: AWS instance sizing, cost validation, and budget guidance for one week. -->
# AWS Resource Validation - CircleGuard

## Objective

Validate the AWS resources required to run the Part 1 CI/CD platform for one week with a USD 120 credit budget.

## Approved topology

| Instance | Type | vCPU | RAM | Disk | Components |
|---|---|---:|---:|---:|---|
| `cg-tools` | `m7i-flex.xlarge` | 4 | 16 GB | 60 GB gp3 | Jenkins, SonarQube, PostgreSQL, Nexus |
| `cg-envs` | `t3.medium` | 2 | 4 GB | 40 GB gp3 | dev, stage, and prod: gateway + Redis |

`m7i-flex.xlarge` is the default for `cg-tools` because SonarQube, Nexus, and Jenkins together need more than 8 GB of memory for stable builds.

## Free Tier clarification

- Free Tier covers `t3.micro` and `t2.micro`, not `t3.medium` or `m7i-flex.xlarge`.
- `t3.micro` has 1 GB of RAM and cannot run SonarQube, Nexus, and Jenkins together.
- The USD 120 credit is the real budget for this deployment.

## Cost estimate

Assumptions: `us-east-1`, Linux, On-Demand, 168 hours, 100 GB gp3, and two public IPv4 addresses.

| Resource | Rate | Weekly cost |
|---|---:|---:|
| `m7i-flex.xlarge` | ~USD 0.192/hour | ~USD 32.30 |
| `t3.medium` | ~USD 0.0416/hour | ~USD 7.00 |
| EBS 100 GB gp3 | ~USD 0.08/GB-month | ~USD 1.85 |
| 2 public IPv4 | ~USD 0.005/hour each | ~USD 1.70 |
| **Total** | | **~USD 42.85** |

Remaining budget from USD 120: **~USD 77**.

Cheaper alternatives:

| Configuration | Weekly compute | Weekly total |
|---|---:|---:|
| `m7i-flex.large` + `t3.medium` | ~USD 23.10 | ~USD 26.65 |
| `t3.large` + `t3.medium` | ~USD 21.00 | ~USD 24.55 |

The cheaper configurations use 8 GB for the tools host and rely on swap during Jenkins and SonarQube workloads.

## Checklist

- [ ] Active AWS account with credits.
- [ ] Region set to `us-east-1`.
- [ ] AWS Budget alert created (recommended: USD 60).
- [ ] `ADMIN_CIDR` defined.
- [ ] Optional `TEACHER_CIDR` defined.
- [ ] Key pair stored outside the repository.
- [ ] `m7i-flex.xlarge` and `t3.medium` available in the region.

## Verification commands

```bash
export AWS_REGION=us-east-1
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
./infra/bootstrap-check.sh
```

## Required evidence

- Output of `aws sts get-caller-identity`.
- Output of `aws ec2 describe-instance-types`.
- AWS Budgets screenshot.
- Credit availability screenshot.

## Cost controls

Stop the instances outside working hours:

```bash
./infra/bootstrap-stop.sh
./infra/bootstrap-start.sh
```

Stopping instances stops compute charges. EBS volumes and allocated Elastic IP addresses continue to incur charges.
