# Spotpobre API — Operational Release Runbook

How to operate this service in production without having written the code. This runbook targets
the current production platform (**on-premises bare metal: Docker Compose + NGINX blue/green +
LocalStack**, ADR-0002). The legacy AWS/ECS path is summarised at the end.

Sources of truth: `deploy/README.md` (manifests + recorded exercise),
`docs/adr/0002-onprem-bare-metal-platform.md` (platform decision).

---

## 0. Topology and entry points

```
operator/host :8080 ──► nginx LB (spotpobre-lb)
                          ├── blue:8081   (current fleet, default 100%)
                          └── green:8082  (next fleet, out of rotation)
blue/green ──► LocalStack (DynamoDB + S3, spotpobre-localstack) + Redis (spotpobre-redis)
```

- All app routes live under `/api/v1/*`. `POST /api/v1/auth/register` and
  `/api/v1/auth/authenticate` return a JWT; pass it as `Authorization: Bearer <token>`.
- Probes (no auth): `/actuator/health/liveness`, `/actuator/health/readiness`.
  Every other `/actuator/**` route requires authentication.
- Working directory for all commands below: repository root.
- Stack commands use `docker compose -f deploy/docker-compose.bluegreen.yml` (alias `$DC` below)
  with `deploy/.env` present (copy from `deploy/.env.example`; never committed).

```sh
DC="docker compose -f deploy/docker-compose.bluegreen.yml"
```

## 1. Deploy a new version

```sh
# 1) Build the new image once; it must be scanned by CI before reaching this host.
docker build -t spotpobre-api:vNEXT .

# 2) Health-gated canary rollout (green gate → 10% / 30 s observation → cutover).
./scripts/bluegreen-deploy.sh spotpobre-api:vNEXT
```

The script aborts automatically back to blue 100% if green fails its readiness gate or degrades
during the canary window. On success green serves 100%; blue stays running at 0% for instant
rollback.

Post-cutover verification:

```sh
curl -s http://localhost:8080/actuator/health/readiness          # 200
$DC ps                                                          # blue up (0%), green up (100%)
```

## 2. Roll back

```sh
./scripts/bluegreen-rollback.sh     # traffic → blue instantly; green stopped
curl -s http://localhost:8080/actuator/health/readiness          # expect PASS line from script
```

Notes:

- Rollback is safe at any point after a cutover because blue is left running.
- If blue was already stopped/recreated manually, restart it first
  (`$DC up -d blue`) and wait for `(healthy)` before rolling back.
- The NGINX upstream re-resolves fleet IPs every 10 s (`resolver ... valid=10s` + `resolve`),
  so recreated containers are picked up without manual reloads. If you ever edit
  `deploy/nginx-bluegreen.conf`, apply with:
  `$DC cp deploy/nginx-bluegreen.conf loadbalancer:/etc/nginx/conf.d/default.conf && $DC exec loadbalancer nginx -s reload`.

## 3. Rotate secrets

All secrets live in `deploy/.env` (gitignored). Rotation procedure for the JWT signing secret:

```sh
# 1) Generate and stage the new value
NEW_SECRET="$(openssl rand -base64 48)"

# 2) Rolling restart one fleet at a time (zero-downtime):
#    a) put the new secret into the environment of ONE fleet via an override file or direct edit,
#    b) recreate that fleet, wait healthy, verify auth still works, then do the other fleet.
JWT_SECRET="$NEW_SECRET" $DC up -d --no-deps --force-recreate green
./scripts/bluegreen-deploy.sh            # shift traffic to green (old image tag = current)
JWT_SECRET="$NEW_SECRET" $DC up -d --no-deps --force-recreate blue
```

- Old tokens stay valid until `jwt.expiration` elapses (default 1 h); clients re-authenticate
  transparently. No downtime.
- Never put the production secret in Git, images or `application.yaml` (its `jwt.secret` is a
  dev-only example). If a secret leaks, rotate immediately and treat outstanding tokens as
  compromised until expiry.
- Changing other `.env` values (endpoints, bucket, rate limits) follows the same one-fleet-at-a-time
  pattern. `ProdConfigValidator` fails fast on incomplete contracts — check
  `docker logs spotpobre-blue` if a recreated fleet exits.

## 4. When readiness is DOWN

Readiness gates on DynamoDB + S3 only (Redis is a cache and deliberately excluded).

```sh
curl -s http://localhost:8080/actuator/health/readiness | jq .
# {"status":"DOWN","components":{"dynamoDb":{...},"readinessState":{...},"s3":{...}}}
```

1. Identify the failing component from the response details (authenticate for full detail).
2. `dynamoDb` DOWN:
   - Is LocalStack running? `$DC ps` / `docker logs spotpobre-localstack`.
   - Do tables exist? `./scripts/seed-localstack.sh` is idempotent — run it to repair schema.
3. `s3` DOWN:
   - Bucket missing? Seed script recreates it.
   - Credentials? In the on-premises target these are the LocalStack dummy keys from
     `deploy/.env` (`AWS_CREDENTIALS_SOURCE=static`). A validator abort at startup means the
     contract is incomplete — see logs.
4. While a component is DOWN the LB marks fleets unhealthy within ~10 s (`max_fails=3`) and
   returns 502/504 instead of routing to a broken backend. Once the dependency recovers,
   readiness returns UP automatically and traffic resumes — no restart needed.
5. If BOTH fleets are down but dependencies are fine: `$DC up -d` restores them; they flip
   healthy on their own once probes pass.

## 5. Incident response

**Container crash-looping**

```sh
$DC ps                                  # state + health
docker logs --tail 200 spotpobre-blue   # exit reason / stack trace
```

- Startup abort mentioning `ProdConfigValidator`: the env contract in `deploy/.env` is incomplete
  or contradictory (e.g. `AWS_CREDENTIALS_SOURCE=static` without keys). Fix `.env`, recreate.
- `OOMKilled`: check `docker inspect` `State.OOMKilled`; the compose limits are 800 m RAM per
  fleet — investigate a memory leak before raising limits.

**LB returns 502/504 while fleets look healthy**

- `$DC ps` — both fleets `(healthy)`?
- `docker logs --tail 50 spotpobre-lb` — which upstream IP timed out?
- Stale IP should self-heal within 10 s (runtime resolver). If it persists >30 s:
  reload the LB config as shown in §2.

**Redis outage (authenticated requests fail while readiness is UP)**

Known accepted behaviour: the auth cache has no outage fallback yet (see AGENTS.md technical
debt). Symptoms: `RedisConnectionFailureException` in app logs, 500s on authenticated calls,
probes stay green. Mitigations: restore Redis (`$DC restart redis`); severity is user-visible
auth failures, not data loss.

**LocalStack outage (data plane)**

App keeps running but every DynamoDB/S3 operation fails → readiness DOWN → LB sheds traffic.
Restore the container, then run `./scripts/seed-localstack.sh` if volumes were lost. Data written
to emulated services is ephemeral by design — durability is an operational responsibility
(host-level backups of the LocalStack/Redis volumes; there is no AWS migration path since
2026-08-23).

**Graceful shutdown verification** (after any change to shutdown config)

```sh
./scripts/shutdown-under-load-test.sh   # expects all six criteria PASS
```

## 6. Routine operations

| Task | Command |
| :--- | :--- |
| Stack status | `$DC ps` |
| App logs | `docker logs -f spotpobre-blue` (or `-green`) |
| LB logs | `docker logs -f spotpobre-lb` |
| Current traffic split | `$DC exec loadbalancer cat /etc/nginx/conf.d/default.conf \| grep server` |
| Host reboot recovery | Containers use `restart: unless-stopped`; verify with `$DC ps` after boot |
| Free disk | `docker system df`; prune build cache with care (`docker builder prune`) |

## Appendix — legacy AWS/ECS path (historical record only)

The versioned manifests in `deploy/` (`stack.yaml`, `codedeploy.yaml`, `appspec.yaml`,
`task-definition.json`) describe the abandoned ADR-0001 target (ECS Fargate + ALB weighted
blue/green + CodeDeploy canary 10%/5 min with automatic rollback alarms). They are kept for
archaeology only — there is no plan to execute them. Full
procedure: `deploy/README.md` §2. Operational deltas vs. this runbook: deploys go through
`aws deploy create-deployment` (CodeDeploy performs the gated traffic shift), rollback is
`aws deploy stop-deployment --auto-rollback-enabled` (or automatic via alarms), secrets rotate in
Secrets Manager followed by `aws ecs update-service --force-new-deployment`, and readiness-DOWN
triage starts at IAM task-role permissions instead of LocalStack containers.
