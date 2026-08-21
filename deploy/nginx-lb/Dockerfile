# nginx load-balancer image for the on-premises production target (ADR-0002).
# The upstream weights are managed at runtime by scripts/bluegreen-deploy.sh via
# `docker compose cp <conf> loadbalancer:/etc/nginx/conf.d/default.conf` + reload,
# which avoids fragile single-file bind mounts (Docker Desktop / WSL2).
#
# 1.27.4 pinned: >= 1.27.3 required for the OSS `resolve` upstream parameter used by
# deploy/nginx-bluegreen.conf (runtime DNS re-resolution of the fleet containers).
FROM nginx:1.27.4-alpine
COPY deploy/nginx-bluegreen.conf /etc/nginx/conf.d/default.conf
