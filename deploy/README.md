# Spotpobre API — Deployment manifests (S8)

Versioned deployment manifests for the platform chosen in `docs/adr/0001-production-platform.md`:
**ECS Fargate + ECR + ALB + Secrets Manager + ECS Task Role + CodeDeploy blue/green**.

| File                    | Purpose                                                                 |
| :---------------------- | :---------------------------------------------------------------------- |
| `stack.yaml`            | CloudFormation: VPC, security groups, IAM roles, ALB + target group + HTTPS listener, ECS cluster, task definition, service, autoscaling. Single stack, applies cleanly to staging/production. |
| `task-definition.json`  | Reference task definition in native ECS JSON (used for manual `register-task-definition` and the CodeDeploy AppSpec in S9). `REPLACE_WITH_*` placeholders — resolve them before use. |

## Pre-requisites

- The **image pushed to ECR, referenced by digest** (immutable, supply-chain S3): `docker push`
  tags the digest; use `<account>.dkr.ecr.<region>.amazonaws.com/spotpobre-api@sha256:...`.
- A **Secrets Manager secret** with the JWT signing secret (dev example only; production must be a
  strong random value): `aws secretsmanager create-secret --name /spotpobre/${ENV}/jwt-secret \
  --secret-string "$(openssl rand -base64 48)"`
- An **ACM certificate** in the target region for the HTTPS listener.
- The S3 bucket (`spotpobre-songs`), DynamoDB tables and a reachable Redis already provisioned
  (tables per the README "Configure LocalStack" block — same schema in production).

## Apply

```sh
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
```

Expected outputs: `LoadBalancerDnsName`, `ClusterName`, `ServiceName`, `TaskDefinitionFamily`.

## Runtime contract honoured by the stack

| Concern                | Manifest setting                                                        |
| :--------------------- | :---------------------------------------------------------------------- |
| Workload identity      | `TaskRole` (IAM) grants DynamoDB + S3; **no** static `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` anywhere. The app uses `DefaultCredentialsProvider` (`AwsCredentialsProviderResolver`). |
| Secrets                | `JWT_SECRET` injected from Secrets Manager via `Secrets: [{ValueFrom: JwtSecretArn}]`; never in the image or plain env. |
| Non-root               | `User: 10001:10001`, `ReadonlyRootFilesystem: true`.                     |
| Resource limits        | `Cpu: 512`, `Memory: 1024`, Fargate runtime.                             |
| Probes                 | ECS container `healthCheck` on `/actuator/health/readiness` (`curl`, start period 60s) **and** ALB target-group health check on the same path (`Matcher: 200`). |
| Graceful shutdown      | `HealthCheckGracePeriodSeconds: 90`; ALB deregistration aligns with the app's 30s graceful drain (S7). |
| Autoscaling            | Target-tracking on average CPU (70%), 1–3 tasks.                         |
| Structured logs        | `SPRING_PROFILES_ACTIVE=prod,json`, `awslogs` driver to `/ecs/spotpobre-${ENV}`. |
| Immutability           | `ImageDigest` parameter pins the ECR image by digest; a new release = a new digest + stack update. |

## Updating to a new image

1. Push the new image to ECR (digest-pinned).
2. Re-run `aws cloudformation deploy` with the new `ImageDigest`. The task definition family is
   updated and the service redeploys.
3. For **blue/green**, the CodeDeploy deployment group (S9) performs the health-gated traffic
   shift instead of a rolling update.

## Blue/green note

The ECS service uses `DeploymentController: CODE_DEPLOY`. The ALB `TargetGroup` above is the
"blue" target. The CodeDeploy AppSpec (S9) defines the blue/green pair, health gates, rollback
alarms and the observation window.