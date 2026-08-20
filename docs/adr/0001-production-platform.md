# ADR-0001: Production platform, secrets and rollout strategy

**Status:** Accepted
**Date:** 2026-08-19
**Deciders:** Spotpobre API maintainers
**Context:** Runtime & Deployment epic (S1)
**Companion:** `tasks/runtime-deployment-spec.md`

---

## Context

Spotpobre API is a Spring Boot 3.5 service backed by AWS DynamoDB and S3, deployed today only as a
Docker image with an env-var contract (`application-prod.yaml`). Before writing deployment manifests
the project must choose a single production platform, image registry, load balancer, secret store,
identity model and rollout strategy. The choice must be coherent with the existing AWS backing
services and must remove long-lived access keys from production.

## Decision

Adopt the AWS-native, serverless-friendly option:

| Concern            | Decision                                              |
| ------------------ | ----------------------------------------------------- |
| Compute            | **AWS ECS Fargate**                                   |
| Image registry     | **Amazon ECR**                                        |
| Load balancing     | **Application Load Balancer (ALB)**                   |
| Secrets            | **AWS Secrets Manager** (non-sensitive config via SSM Parameter Store if desired) |
| Identity           | **ECS Task Role (IAM)** — no static `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in production |
| Rollout            | **ECS Service + CodeDeploy blue/green** (ALB target-group shifting) |

### Why these choices

1. **Coherence with the existing AWS stack.** The application already talks to DynamoDB and S3.
   Staying on AWS keeps a single control plane for identity (IAM), networking (VPC) and monitoring.
2. **Fargate removes server management.** No EC2 patching, capacity planning or node groups; the
   service is a container image + task definition, matching the project's small-operations profile.
3. **ECS Task Role is the natural workload identity.** The AWS SDK in this codebase already uses
   `DefaultCredentialsProvider`-compatible configuration via `AwsProperties`; on Fargate that
   resolves to the task role with no code change and no secrets in the environment.
4. **Secrets Manager fits the existing env-var contract.** `JWT_SECRET` (and any future secrets)
   stay out of the image and manifests; the task definition injects them from Secrets Manager at
   runtime via `valueFrom`, so `application-prod.yaml` needs no code change.
5. **CodeDeploy blue/green on ECS uses the ALB's native health checks.** Health gates and rollback
   are expressed as ALB target-group health + CloudWatch alarms, reusing the readiness model from
   Step 6 (S6) rather than inventing a second mechanism.
6. **Kubernetes was considered and rejected for now.** It would add a control plane to operate
   (EKS) for a single small service, and the app has no multi-container or mesh requirements. The
   ADR can be revisited if the service grows into a fleet.

## Consequences

### Positive

- Long-lived static AWS keys disappear from production; the task role grants least-privilege access.
- Secrets are externalised and rotatable without rebuilding the image.
- Blue/green deploys give instant rollback via ALB target-group shift + CodeDeploy.
- Everything (image, secrets, identity, rollout) lives in AWS — a single cloud footprint.

### Negative / accepted trade-offs

- Vendor lock-in to AWS (acceptable: backing services are already AWS).
- Blue/green requires two target groups per service (double capacity during deploy).
- Fargate per-vCPU pricing is higher than running on EC2 instances; fine for this scale, resource
  limits must still be set in the task definition.

### Follow-ups required by this ADR

- S4/S5: fail-fast `ProdConfigValidator` for every required value; integrate Secrets Manager
  references into the task definition; document rotation.
- S6/S7: readiness model consumed by ALB health checks; probe failure/recovery tests.
- S9/S10: versioned ECS manifests + CodeDeploy blue/green config with health gates/rollback alarms.
- S11: staging deploy + rollback exercise on the real platform.

---

## Alternatives considered

- **ECS EC2 launch type** — rejected: requires managing a cluster/instances; Fargate is simpler for
  a single service.
- **Kubernetes (EKS)** — rejected for now (see rationale above); the ADR would be revisited before
  a multi-service architecture.
- **Lambda + API Gateway** — rejected: the app is a long-running Spring Boot service; a full
  rewrite to a serverless model is out of scope for this epic.
- **HashiCorp Vault** — rejected: adds an external system to operate; Secrets Manager is already
  available and integrates natively with ECS task definitions.