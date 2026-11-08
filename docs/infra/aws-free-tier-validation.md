<!-- CG-DOC: AWS resource sizing, cost validation, and permission checklist. -->
# AWS Resource Validation - CircleGuard

## Objective

Validate whether the AWS account can host the Part 1 CI/CD platform on two EC2 instances.

## Approved topology

| Instance | Type | vCPU | RAM | Disk | Components |
|---|---|---:|---:|---:|---|
| `cg-tools` | `t3.large` | 2 | 8 GB | 60 GB gp3 | Jenkins, SonarQube, PostgreSQL, Nexus |
| `cg-envs` | `t3.medium` | 2 | 4 GB | 40 GB gp3 | dev, stage, and prod: gateway + Redis |

## Checklist

- [ ] Active AWS account with a payment method or available credits.
- [ ] Region selected (`AWS_REGION`).
- [ ] Free tier or credit status verified in the AWS console.
- [ ] AWS Academy/Educate credits or an approved budget.
- [ ] Budget alert created in AWS Budgets.
- [ ] Quota available for `t3.large` and `t3.medium`.
- [ ] Key pair stored outside the repository.
- [ ] Administrative CIDR defined (`ADMIN_CIDR`).
- [ ] Instructor IP available for security groups.

## Cost estimate

| Resource | 24/7 | 12 h/day |
|---|---:|---:|
| `cg-tools` t3.large | ~US$60/month | ~US$22/month |
| `cg-envs` t3.medium | ~US$30/month | ~US$11/month |
| EBS 100 GB gp3 | ~US$8/month | ~US$8/month |
| **Total** | **~US$98/month** | **~US$41/month** |

> Strict free tier is not enough: `t3.micro` has 1 GB of RAM and cannot host SonarQube, Nexus, and Jenkins together. Credits or a budget are required.

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
- Free tier or credit availability screenshot.
