# Spotpobre API — Deployment manifests (S8 + S9)

Versioned deployment manifests for the platform chosen in `docs/adr/0001-production-platform.md`:
**ECS Fargate + ECR + ALB + Secrets Manager + ECS Task Role + CodeDeploy blue/green**.

| File                    | Purpose                                                                                              |
| :---------------------- | :--------------------------------------------------------------------------------------------------- |
| `stack.yaml`            | CloudFormation: VPC, security groups, IAM roles, ALB with **blue/green listener**, ECS cluster, task definition, `CODE_DEPLOY` service, autoscaling, **rollback alarms**. Single stack, applies cleanly to staging/production. |
| `codedeploy.yaml`       | CodeDeploy application + blue/green **deployment group** (`WITH_TRAFFIC_CONTROL`, canary config, rollback triggers). Applies **after** `stack.yaml`. |
| `appspec.yaml`          | Reference **ECS AppSpec** (blue/green) submitted inline to CodeDeploy. Substitute the task-definition ARN before use. |
| `task-definition.json`  | Reference task definition in native ECS JSON (used for manual `register-task-definition`). `REPLACE_WITH_*` placeholders — resolve them before use. |

## Pre-requisites

- The **image pushed to ECR, referenced by digest** (immutable, supply-chain S3): `docker push`
  tags the digest; use `<account>.dkr.ecr.<region>.amazonaws.com/spotpobre-api@sha256:...`.
- A **Secrets Manager secret** with the JWT signing secret (dev example only; production must be a
  strong random value): `aws secretsmanager create-secret --name /spotpobre/${ENV}/jwt-secret \
   --secret-string "$(openssl rand -base64 48)"`
- An **ACM certificate** in the target region for the HTTPS listener.
- The S3 bucket (`spotpobre-songs`), DynamoDB tables and a reachable Redis already provisioned
  (tables per the main README "Configure LocalStack" block — same schema in production).

## Apply order

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

# 2) Rollout: CodeDeploy application + blue/green deployment group.
aws cloudformation deploy \
  --stack-name spotpobre-${ENV}-codedeploy \
  --template-file deploy/codedeploy.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    Environment=${ENV} \
    ClusterName=$(aws cloudformation list-exports --query ...) \
    ServiceName=... \
    BlueTargetGroupArn=... \
    GreenTargetGroupArn=... \
    ListenerArn=... \
    Rollback5xxAlarmName=... \
    RollbackLatencyAlarmName=... \
    RollbackReadinessAlarmName=...
```

Expected outputs from `stack.yaml`: `LoadBalancerDnsName`, `ClusterName`, `ServiceName`,
`TaskDefinitionFamily`, `BlueTargetGroupArn`, `GreenTargetGroupArn`, `ListenerArn`,
`Rollback5xxAlarmName`, `RollbackLatencyAlarmName`, `RollbackReadinessAlarmName`
(each exported as `spotpobre-${Environment}::<Name>` for cross-stack reference).

## Runtime contract honoured by the stack

| Concern                | Manifest setting                                                                                 |
| :--------------------- | :------------------------------------------------------------------------------------------------ |
| Workload identity      | `TaskRole` (IAM) grants DynamoDB + S3; **no** static `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` anywhere. The app uses `DefaultCredentialsProvider` (`AwsCredentialsProviderResolver`). |
| Secrets                | `JWT_SECRET` injected from Secrets Manager via `Secrets: [{ValueFrom: JwtSecretArn}]`; never in the image or plain env. |
| Non-root               | `User: 10001:10001`, `ReadonlyRootFilesystem: true`.                                             |
| Resource limits        | `Cpu: 512`, `Memory: 1024`, Fargate runtime.                                                     |
| Probes                 | ECS container `healthCheck` on `/actuator/health/readiness` (`curl`, start period 60s) **and** ALB target-group health check on the same path (`Matcher: 200`). |
| Graceful shutdown      | `HealthCheckGracePeriodSeconds: 90`; ALB deregistration delay aligns with the app's 30s graceful drain (S7). |
| Autoscaling            | Target-tracking on average CPU (70%), 1–3 tasks.                                                 |
| Structured logs        | `SPRING_PROFILES_ACTIVE=prod,json`, `awslogs` driver to `/ecs/spotpobre-${ENV}`.                  |
| Immutability           | `ImageDigest` parameter pins the ECR image by digest; a new release = a new digest + stack update. |

## Rollout (blue/green)

The ECS service uses `DeploymentController: CODE_DEPLOY` with `WITH_TRAFFIC_CONTROL`. On each new
release:

1. CI registers a new ECS task definition for the digest-pinned image and writes the AppSpec
   (`deploy/appspec.yaml` with the task-definition ARN substituted) inline:
   ```sh
   aws deploy create-deployment \
     --application-name spotpobre-${ENV} \
     --deployment-group-name spotpobre-${ENV}-blue-green \
     --revision revisionType=AppSpecContent,appSpecContent="$(cat deploy/appspec.yaml)" \
     --region ${REGION}
   ```
2. CodeDeploy launches the **green** task set with the new task definition and registers it on the
   green target group (initially 0% traffic).
3. **Health gate before full traffic**: the deployment config
   `CodeDeployDefault.ECSCanary10Percent5Minutes` shifts **10%** of prod traffic to green and holds
   for **5 minutes** (the observation window). During this window the ALB target-group health check
   (`/actuator/health/readiness`, `Matcher: 200`) gates each host.
4. If all alarms stay green, CodeDeploy shifts 100% to green over the next step, then **terminates the
   blue task set** after `TerminationWaitTimeMinutes` (default 30).

### Automatic rollback criteria (defined in `deploy/codedeploy.yaml`)

CodeDeploy rolls back automatically when any trigger fires during the deployment:

| Trigger                  | Alarm (`deploy/stack.yaml`)            | Metric               | Condition (3/3 × 60s)        |
| :----------------------- | :------------------------------------- | :------------------- | :--------------------------- |
| Sustained 5xx            | `spotpobre-<env>-rollback-5xx`         | `HTTPCode_Target_5XX_Count` | Sum ≥ 10 / min              |
| Degraded latency         | `spotpobre-<env>-rollback-latency`     | `TargetResponseTime` | Avg ≥ 0.5 s                 |
| Readiness DOWN           | `spotpobre-<env>-rollback-readiness`   | `HealthyHostCount`   | Min < 1 (zero healthy hosts) |
| Deployment failure       | `CODE_DEPLOY_DEPLOYMENT_FAILURE`       | CodeDeploy lifecycle | Any CodeDeploy deployment error |

The readiness alarm is the dependency-failure signal: if DynamoDB/S3 is unavailable the readiness
probe returns non-200, hosts go unhealthy, the alarm trips and CodeDeploy rolls back.

## Observation window

- **Canary 10% / 5 min** (`CodeDeployDefault.ECSCanary10Percent5Minutes`): 5 minutes of 10% traffic to
  green — enough to expose 5xx, latency and readiness regressions before full cutover. Override with
  `DeploymentConfigName` (e.g. `CodeDeployDefault.ECSAllAtOnce` for urgent fixes,
  `CodeDeployDefault.ECSLinear10PercentEvery1Minutes` for slow shifts).
- After 100% shift, the blue task set is terminated after the configured wait; monitor the green
  target group for `HealthyHostCount` returning to the steady value.

## Rollback procedure

### Automatic
Rollout rolls back on any trigger above; the previous (blue) revision keeps serving.

### Manual (force a rollback early)
```sh
# Stop the in-flight deployment and roll back to the last known good revision.
aws deploy stop-deployment --deployment-id <id> --auto-rollback-enabled --region ${REGION}
# OR re-submit a deployment with the previous task definition ARN in deploy/appspec.yaml:
#   replace REPLACE_WITH_TASK_DEFINITION_ARN with the previous task-def ARN and re-run
#   aws deploy create-deployment --revision revisionType=AppSpecContent,appSpecContent=.
```
A rollback creates a new green task set with the old revision; verify `HealthyHostCount` recovers to
the steady value before closing.

## Staging exercise (S10)

Apply the two stacks to staging, then prove deploy + rollback end-to-end. This step needs real AWS
credentials for the staging account/profile — configure `AWS_PROFILE` (or env vars) before starting.

### 1. Deploy

```sh
# 1) Foundation stack (service + ALB blue/green pair + rollback alarms).
aws cloudformation deploy \
  --stack-name spotpobre-staging \
  --template-file deploy/stack.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    Environment=staging \
    ImageDigest=${ECR_IMAGE_DIGEST} \
    JwtSecretArn=arn:aws:secretsmanager:${REGION}:${ACCOUNT}:secret:/spotpobre/staging/jwt-secret \
    SslCertificateArn=${ACM_CERT_ARN} \
    BucketName=spotpobre-songs-staging \
    RedisHost=${REDIS_HOST} \
    Rollback5xxThreshold=10 \
    RollbackLatencyThresholdSeconds=0.5

# 2) Rollout stack (CodeDeploy application + blue/green deployment group). It imports
#    the service, target groups, listener and alarm names from the foundation stack,
#    so no per-export parameters are needed.
aws cloudformation deploy \
  --stack-name spotpobre-staging-codedeploy \
  --template-file deploy/codedeploy.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides Environment=staging
```

> Both stacks must reach `CREATE_COMPLETE`. The foundation stack exports each output as
> `spotpobre-${Environment}::<Name>`, which `codedeploy.yaml` imports automatically.
> **Warning:** you cannot delete the foundation stack while the codedeploy stack imports from it;
> delete the codedeploy stack first (CloudFormation enforces this via the `ImportValue` dependency).

### 2. Acceptance checks

| # | Check | Expected |
| - | :---- | :------- |
| 1 | Service stable: `aws ecs wait services-stable --cluster ... --services ...` | returns 0 |
| 2 | Open the ALB DNS name; `GET /actuator/health/liveness` | 200, `{"status":"UP"}` (no auth) |
| 3 | `GET /actuator/health/readiness` | 200 UP; readiness gate is green (DynamoDB + S3 reachable) |
| 4 | `GET /actuator/metrics` without a token | 401 (only probe paths are unauthenticated) |
| 5 | Initial blue/green deploy (no new revision) | blue TG healthy hosts > 0, green TG 0, listener 100/0 |

> **Dependency-failure probe (gate S6):** run only if an operator can tolerate downtime — temporarily
> deny the app's task role `s3:GetObject`, then `GET /actuator/health/readiness` must go 503 and the
> readiness alarm must stay green for healthy hosts while the role is denied, then recover to 200/UP
> when the deny is removed. (Skip in the first run; record result separately.)

### 3. Canary rollout exercise

```sh
aws deploy create-deployment \
  --application-name spotpobre-staging \
  --deployment-group-name spotpobre-staging-blue-green \
  --revision revisionType=AppSpecContent,appSpecContent="$(cat deploy/appspec.yaml | sed s/REPLACE_WITH_TASK_DEFINITION_ARN/$NEW_TASK_DEF_ARN/)" \
  --deployment-config-name CodeDeployDefault.ECSCanary10Percent5Minutes
```

Acceptance:
- `AfterAllowTestTraffic` window: green task set has `RUNNING` tasks but listener still 100/0.
- After the first 10% shift: `HealthyHostCount` on green > 0, no rollback alarm in ALARM, listener
  shows ~10% weight on green (via `aws elbv2 describe-listener-rules` / prod traffic route).
- Within `TerminationWaitTimeMinutes`: 100% green, blue task set `TERMINATED`.
- `aws deploy get-deployment --deployment-id <id>` shows `status: Succeeded`.

### 4. Rollback exercise

```sh
# Force an automatic rollback: raise the 5xx alarm threshold so a 5xx surge trips it mid-deploy,
# or manually stop a deployment during the 10% canary window:
aws deploy stop-deployment --deployment-id <id> --auto-rollback-enabled
```

Acceptance:
- During the 10% window the deployment is stopped → state `Stopped` / `rollback`, blue resumes as
  the healthy target group, traffic returns to 100% blue within the grace period.
- Confirm no 5xx spike on the blue fleet during rollback.

### Recorded result

> Staging exercise is **not executable in this environment** (no AWS credentials/config available
> — `aws sts get-caller-identity` returns "config profile not found"). Running steps 1–4 with the
> staging `AWS_PROFILE` is the definition-of-done for S10; attach the log + acceptance results here.



1. CI pushes the new image to ECR (digest-pinned) and registers a new task definition.
2. CI submits the AppSpec (with the new task-definition ARN) via `aws deploy create-deployment`
   (see Rollout above) — CodeDeploy performs the health-gated blue/green traffic shift.
3. Optionally update `stack.yaml`'s `ImageDigest` if the base stack should track the latest digest.
