# Spotpobre API — Deployment

Production deployment for **on-premises bare metal with LocalStack** (`docs/adr/0002-onprem-bare-metal-platform.md`).
The AWS-native manifests from ADR-0001 are kept at the bottom of this file as a **historical
record only** — the real-AWS migration is formally abandoned (2026-08-23, ADR-0002 update note).

---

## 1. Production target: Docker Compose + NGINX blue/green (ADR-0002)

| File                        | Purpose                                                                                         |
| :-------------------------- | :---------------------------------------------------------------------------------------------- |
| `docker-compose.bluegreen.yml` | Production stack: blue fleet (8081), green fleet (8082), NGINX LB (8080), LocalStack (DynamoDB+S3), Redis. Non-root containers, read-only root FS, resource limits, health checks. |
| `nginx-bluegreen.conf`      | LB config template (weighted upstream). Build-time default: blue 100%, green out of rotation.    |
| `nginx-lb/Dockerfile`       | NGINX 1.27.4 image (≥1.27.3 required for OSS upstream `resolve`).                                |
| `.env.example`              | The operator contract: every env var the `prod` profile requires. Copy to `.env` and fill in.     |

Scripts (repo root `scripts/`):

| Script                     | Purpose                                                                                          |
| :------------------------- | :----------------------------------------------------------------------------------------------- |
| `seed-localstack.sh`       | Provision DynamoDB tables + S3 bucket. Host AWS CLI first; falls back to `awslocal` inside the LocalStack container when the host CLI cannot talk to emulated S3. |
| `bluegreen-deploy.sh`      | Health-gated rollout: [optional new image tag] → green readiness gate → canary 10% (30s observation) → cutover 100%. |
| `bluegreen-rollback.sh`    | Instant rollback: traffic back to blue 100%, green stopped.                                       |
| `shutdown-under-load-test.sh` | Reproducible graceful-shutdown-under-load verification (S8).                                    |

### 1.1 First deployment

```sh
# 0) One-time: operator contract (never committed)
cp deploy/.env.example deploy/.env
#   edit deploy/.env: JWT_SECRET="$(openssl rand -base64 48)" etc.

# 1) Application image (non-root, digest-recorded by CI)
docker build -t spotpobre-api:local .

# 2) Stack (LocalStack + Redis first, fleets after they are healthy, LB last)
docker compose -f deploy/docker-compose.bluegreen.yml up -d

# 3) Provision AWS-emulation schema (tables + bucket)
./scripts/seed-localstack.sh

# 4) Fleets flip healthy once dependencies exist; start the LB if it waited on them
docker compose -f deploy/docker-compose.bluegreen.yml up -d loadbalancer

# 5) Smoke through the LB
curl -s http://localhost:8080/actuator/health/readiness          # 200
curl -s -X POST http://localhost:8080/api/v1/auth/register ...   # JWT returned
```

> On a **fresh volume**, fleets report unhealthy until step 3 exists — that is the S6 readiness
> gate working (DynamoDB/S3 missing → DOWN). They recover automatically once seeded.

### 1.2 Deploying a new version (blue/green canary)

```sh
docker build -t spotpobre-api:vNEXT .
./scripts/bluegreen-deploy.sh spotpobre-api:vNEXT
```

What it does (the stand-in for ALB weighted shifting / CodeDeploy canary):

1. Recreates **green** with the new image.
2. **Health gate**: green readiness must be 200 before any traffic moves.
3. **Canary**: green takes 10% of traffic for a 30 s observation window; any readiness loss
   aborts back to blue 100% automatically.
4. **Cutover**: green 100%, blue drained to 0% but left running for instant rollback.

Post-cutover verification used in the recorded exercise: stop blue briefly — the LB must keep
serving via green (readiness 200 + successful register), then start blue again.

### 1.3 Rollback

```sh
./scripts/bluegreen-rollback.sh
```

Traffic reverts to blue instantly (NGINX reload), green is stopped. Verify the script's final
`PASS: blue serving 100%`.

### 1.4 Runtime contract honoured by the compose stack

| Concern            | Setting                                                                                            |
| :----------------- | :------------------------------------------------------------------------------------------------- |
| Profile & secrets  | `SPRING_PROFILES_ACTIVE=prod`; all values from `deploy/.env` (gitignored). `JWT_SECRET` never in Git/images/manifests. |
| Credential source  | `AWS_CREDENTIALS_SOURCE=static` + LocalStack dummy keys (enforced coherent by `ProdConfigValidator`). `workload-identity` support is retained for portability but has no migration target since 2026-08-23. |
| Non-root           | App containers run UID/GID 10001 (verified in CI); `read_only: true` root FS + tmpfs `/tmp`.        |
| Resource limits    | Apps 800 m RAM / 1.5 CPU each; LocalStack 600 m; Redis 128 m; LB 128 m.                             |
| Probes             | Container healthcheck on `/actuator/health/readiness`; LB routes only to healthy fleets; non-probe `/actuator/**` stays authenticated. |
| Graceful shutdown  | `spring.lifecycle.timeout-per-shutdown-phase=30s` (S8); compose `stop` sends SIGTERM and waits.     |
| Restart policy     | `unless-stopped` on every service (survives host reboots).                                          |
| Immutable artefact | Deploys reference image tags/digests built once (`IMAGE_GREEN=spotpobre-api:vN`).                   |

### 1.5 NGINX LB notes (from the official docs)

- **No `weight=0`**: a fleet out of rotation uses `down`; active weights ≥ 1.
- **Upstream keepalive** is off by default before nginx 1.29.7 → explicit `keepalive 32;`
  plus `proxy_http_version 1.1` + empty `Connection` header.
- **Passive health checks**: `max_fails=3 fail_timeout=10s` marks a failing fleet down.
- **Runtime DNS re-resolution** (`resolver 127.0.0.11 valid=10s` + `zone` + `resolve`, OSS since
  1.27.3): recreated containers with new IPs are picked up within ~10 s without a reload — proven
  in the recorded exercise. Requires the pinned `nginx:1.27.4-alpine`.
- **`proxy_next_upstream error timeout`**: retries only transport-level failures; never duplicates
  non-idempotent POSTs across fleets.

### 1.6 Recorded staging exercise (S10/S11) — executed successfully

Executed end-to-end against LocalStack on the host (2026-08-21):

| # | Step | Result |
| - | :--- | :----- |
| 1 | `deploy/.env` created from example with random `JWT_SECRET`; image built | OK |
| 2 | Stack up; fleets unhealthy on fresh volume (readiness gate correct); seeded; fleets flipped healthy | OK |
| 3 | Smoke through LB: register returns JWT (prod profile, validator, secrets, DynamoDB GSI, Redis) | OK |
| 4 | `bluegreen-deploy.sh spotpobre-api:v2`: green recreated → gate → canary 10%/30 s → cutover | PASS |
| 5 | Cutover proof: blue stopped → LB still served readiness 200 + register via green | PASS |
| 6 | `bluegreen-rollback.sh`: traffic reverted, green stopped, `PASS: blue serving 100%` | PASS |
| 7 | LB resilience: blue recreated with a different IP while a squatter held the old one → LB served 200 without reload (`resolve` auto-healing) | PASS |

Defects found and fixed during the exercise: invalid `weight=0` (LB crash-looped), stale-IP routing
after manual fleet recreation (fixed by `resolve`), `docker compose stop --no-deps` misuse in the
rollback script, and a pre-existing wrong GSI key in `scripts/seed-localstack.sh` that silently
401-ed every login on freshly seeded volumes.

---

### 1.7 Perimeter, TLS and management-plane trust (spec section 11)

Locked topology (single host, on-premises bare metal):

- **Only 8080 is published** (NGINX LB). The blue (8081) and green (8082) fleets and the
  management port 9090 are internal to the compose network — nothing else is exposed.
- **Management plane**: `application-prod.yaml` moves actuator to internal port **9090**,
  health-only exposure with `show-details: never`. Healthchecks run container-internally
  (`curl localhost:9090/...`); the load balancer never proxies health endpoints publicly.
- **Redis and LocalStack are unpublished** from the host; applications reach them over the
  compose DNS names only. SES is part of the enabled LocalStack services for the
  production-shaped stack.

TLS termination and perimeter headers:

- The perimeter terminates TLS in front of NGINX (host-level reverse proxy / firewall
  forwarding 443 → 8080). NGINX itself listens plain HTTP inside the trusted segment; HSTS
  must be set at the TLS layer (`Strict-Transport-Security: max-age=63072000; includeSubDomains`).
- Forwarded-header trust: `RATE_LIMIT_TRUSTED_PROXY_CIDRS` must list ONLY the proxy segment
  that sets `X-Forwarded-For`/`Forwarded` (default `10.0.0.0/8`). Trusting `0.0.0.0/0` or
  `::/0` would let any client spoof rate-limit identities and is forbidden by the
  ClientAddressResolver contract (spoof attempts from untrusted peers are ignored).
- Never publish Redis/LocalStack/9090 through the perimeter firewall; if remote operator
  access is required, use an SSH tunnel or VPN, never a host port mapping.

## 2. HISTORICAL RECORD — AWS ECS Fargate manifests (ADR-0001, superseded)

> Kept verbatim as a historical record only; the real-AWS migration is formally abandoned
> (2026-08-23, ADR-0002 update note). Nothing in this
> section applies to the current on-premises production target. Files: `stack.yaml`,
> `codedeploy.yaml`, `appspec.yaml`, `task-definition.json`. See also
> `docs/adr/0001-production-platform.md` (status: superseded).

### 2.1 Files

| File                    | Purpose                                                                                              |
| :---------------------- | :--------------------------------------------------------------------------------------------------- |
| `stack.yaml`            | CloudFormation: VPC, security groups, IAM roles, ALB with **blue/green listener**, ECS cluster, task definition, `CODE_DEPLOY` service, autoscaling, **rollback alarms**. Single stack, applies cleanly to staging/production. |
| `codedeploy.yaml`       | CodeDeploy application + blue/green **deployment group** (`WITH_TRAFFIC_CONTROL`, canary config, rollback triggers). Applies **after** `stack.yaml`. |
| `appspec.yaml`          | Reference **ECS AppSpec** (blue/green) submitted inline to CodeDeploy. Substitute the task-definition ARN before use. |
| `task-definition.json`  | Reference task definition in native ECS JSON (used for manual `register-task-definition`). `REPLACE_WITH_*` placeholders — resolve them before use. |

### 2.2 Pre-requisites

- The **image pushed to ECR, referenced by digest** (immutable, supply-chain S3): `<account>.dkr.ecr.<region>.amazonaws.com/spotpobre-api@sha256:...`.
- A **Secrets Manager secret** with the JWT signing secret:
  `aws secretsmanager create-secret --name /spotpobre/${ENV}/jwt-secret --secret-string "$(openssl rand -base64 48)"`.
- An **ACM certificate** in the target region for the HTTPS listener.
- DynamoDB tables and S3 bucket provisioned (same schema as `scripts/seed-localstack.sh`).

### 2.3 Apply order

```sh
# 1) Foundation: networking, IAM, ECS service, ALB (blue/green target groups + weighted listener),
#    rollback alarms.
aws cloudformation deploy \
  --stack-name spotpobre-${ENV} \
  --template-file deploy/stack.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    Environment=${ENV} \
    ImageDigest=${ECR_IMAGE_DIGEST} \
    JwtSecretArn=arn:aws:secretsmanager:${REGION}:${ACCOUNT}:secret:/spotpobre/${ENV}/jwt-secret \
    SslCertificateArn=${ACM_CERT_ARN} \
    BucketName=spotpobre-songs \
    RedisHost=${REDIS_HOST} \
    DesiredCount=1 \
    MaxCapacity=3

# 2) Rollout: CodeDeploy application + blue/green deployment group. Imports foundation outputs.
aws cloudformation deploy \
  --stack-name spotpobre-${ENV}-codedeploy \
  --template-file deploy/codedeploy.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides Environment=${ENV}
```

Expected outputs from `stack.yaml`: `LoadBalancerDnsName`, `ClusterName`, `ServiceName`,
`TaskDefinitionFamily`, `BlueTargetGroupArn`, `GreenTargetGroupArn`, `ListenerArn`,
`Rollback5xxAlarmName`, `RollbackLatencyAlarmName`, `RollbackReadinessAlarmName`
(each exported as `spotpobre-${Environment}::<Name>` for cross-stack reference).

> You cannot delete the foundation stack while the codedeploy stack imports from it; delete the
> codedeploy stack first (CloudFormation enforces this via the `ImportValue` dependency).

### 2.4 Runtime contract honoured by the stack

| Concern                | Manifest setting                                                                                 |
| :--------------------- | :------------------------------------------------------------------------------------------------ |
| Workload identity      | `TaskRole` (IAM) grants DynamoDB + S3; **no** static keys anywhere. `DefaultCredentialsProvider` via `AwsCredentialsProviderResolver` (`AWS_CREDENTIALS_SOURCE=workload-identity`). |
| Secrets                | `JWT_SECRET` injected from Secrets Manager via `Secrets: [{ValueFrom: JwtSecretArn}]`.             |
| Non-root               | `User: 10001:10001`, `ReadonlyRootFilesystem: true`.                                               |
| Resource limits        | `Cpu: 512`, `Memory: 1024`, Fargate runtime.                                                       |
| Probes                 | ECS container `healthCheck` on `/actuator/health/readiness` **and** ALB target-group health check (`Matcher: 200`). |
| Graceful shutdown      | `HealthCheckGracePeriodSeconds: 90`; ALB deregistration delay aligns with the app's 30 s drain (S8). |
| Autoscaling            | Target-tracking on average CPU (70%), 1–3 tasks.                                                   |
| Structured logs        | `awslogs` driver to `/ecs/spotpobre-${ENV}`.                                                       |
| Immutability           | `ImageDigest` parameter pins the ECR image by digest.                                              |

### 2.5 Rollout (CodeDeploy blue/green)

1. Register the new task definition for the digest-pinned image and submit the AppSpec inline:
   ```sh
   aws deploy create-deployment \
     --application-name spotpobre-${ENV} \
     --deployment-group-name spotpobre-${ENV}-blue-green \
     --revision revisionType=AppSpecContent,appSpecContent="$(cat deploy/appspec.yaml)" \
     --deployment-config-name CodeDeployDefault.ECSCanary10Percent5Minutes \
     --region ${REGION}
   ```
2. CodeDeploy launches the **green** task set on the green target group (0% traffic).
3. **Health gate**: canary shifts **10%** and holds **5 minutes**; the ALB health check gates each host.
4. Alarms green → shift to 100%; blue task set terminated after `TerminationWaitTimeMinutes` (30).

### 2.6 Automatic rollback criteria (`deploy/codedeploy.yaml`)

| Trigger                  | Alarm                            | Metric                       | Condition (3/3 × 60s)         |
| :----------------------- | :------------------------------- | :--------------------------- | :---------------------------- |
| Sustained 5xx            | `spotpobre-<env>-rollback-5xx`   | `HTTPCode_Target_5XX_Count`  | Sum ≥ 10 / min                |
| Degraded latency         | `spotpobre-<env>-rollback-latency` | `TargetResponseTime`       | Avg ≥ 0.5 s                   |
| Readiness DOWN           | `spotpobre-<env>-rollback-readiness` | `HealthyHostCount`       | Min < 1 (zero healthy hosts)  |
| Deployment failure       | `CODE_DEPLOY_DEPLOYMENT_FAILURE` | CodeDeploy lifecycle         | Any deployment error          |

Manual early rollback: `aws deploy stop-deployment --deployment-id <id> --auto-rollback-enabled`.
