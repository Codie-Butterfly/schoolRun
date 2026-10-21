# School Run GitHub Actions deployment

Repository: https://github.com/Codie-Butterfly/schoolRun

Status: workflow prepared; VPS provisioning, secrets and first deployment remain pending.

The backend runs separately from Chef Tarie and Hyperfeeds on their VPS, 62.171.128.245. It uses `/opt/school-run`, a dedicated Docker Compose project, and a persistent data directory. Port 9200 is bound only to VPS loopback. The existing HTTPS reverse proxy must route `/school-run/` to it before phone APKs are built. The proposed API URL is `https://62.171.128.245/school-run/api`, subject to verification of the VPS certificate and proxy configuration.

## Repository

Use a private backend repository with this directory at its root. Exclude `data`, `target`, credentials, private uploads and nested development data. The workflow runs Java 17 Maven tests on pull requests and main. Successful main builds publish an immutable commit-tagged Docker Hub image and deploy through the production environment.

## GitHub configuration

Repository secrets: `DOCKER_USERNAME`, `DOCKER_PASSWORD` (needed by build and deploy).
Production environment secrets: `SERVER_IP`, `SCP_USERNAME`, `SCP_PASSWORD`, `SSH_KNOWN_HOSTS` (verified VPS SSH host entry).
Set repository variable `DEPLOY_ENABLED=true` only after the VPS, secrets and HTTPS proxy are ready. Until then pushes run tests without publishing or deploying.

Production environment variable: `SCHOOLRUN_HEALTH_URL` (the verified HTTPS URL ending `/school-run/api/health`).
These can use the same deployment accounts as Hyperfeeds, but existing secret values cannot be read back from GitHub. Enter them again or use organisation secrets granting access to both repositories. Never commit secret values. SSH host verification remains enabled.

## One-time VPS provisioning

Inspect existing services and port usage first. Create `/opt/school-run/data`, owned by UID/GID 10001, and root-owned `.env` mode 0600. Keep Paynow disabled for testing:

```
PAYNOW_ENABLED=false
PAYNOW_TEST_MODE=true
SUBSCRIPTION_UNIT=CHILD
SUBSCRIPTION_PRICE=1
SUBSCRIPTION_CURRENCY=USD
```

Choose explicitly whether to migrate the current local test database and uploaded documents or begin empty. Copy H2 only while its source backend is stopped. Do not put the data in GitHub or a Docker image. The existing H2 schema and tests are retained for this test deployment; a PostgreSQL migration is separate work.

Merge `deploy/nginx-location.conf` into the existing HTTPS server without replacing other applications' locations. Run `nginx -t` before reload. Confirm the HTTPS certificate is trusted by Android; do not disable certificate validation.

## Deployment and rollback

The deploy script pulls the new image before stopping School Run, backs up its stopped database and uploads, then replaces only its API container. Health checks gate success. On failure it restores the previous image and data snapshot when available. Backups contain private information and remain in `/opt/school-run/backups` with restricted access. No global Docker prune or other application's restart is performed.

First deployment needs no real Paynow credentials. Build the phone APK only after both local and external HTTPS health checks succeed. Supply that HTTPS API URL through `API_BASE_URL`.
