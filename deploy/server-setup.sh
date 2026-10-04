#!/usr/bin/env bash
# One-time setup of a fresh Debian/Ubuntu server for the production stack (docs/DEPLOY.md §7).
#
# Run it as root from a directory that contains docker-compose.prod.yml, monitoring/ and deploy/
# (plus your filled-in .env.prod, either next to docker-compose.prod.yml or already in /opt/math-lms):
#
#   sudo DOMAIN=mathlms.example.ro DEPLOY_USER=deploy DEPLOY_PUBKEY="ssh-ed25519 AAAA... ci" \
#        GHCR_USER=filipioan10 GHCR_TOKEN=ghp_xxx  ./deploy/server-setup.sh --start
#
# Everything is optional except .env.prod. What each input does:
#   DOMAIN         public host name -> runs Caddy (automatic HTTPS) in front of nginx, binds nginx to 127.0.0.1:8080
#   DEPLOY_USER    the account GitHub Actions deploys as (created if missing, added to the docker group, owns DEPLOY_PATH)
#   DEPLOY_PUBKEY  the PUBLIC half of the SSH key you store as the DEPLOY_SSH_KEY secret (the private half never touches this server)
#   GHCR_USER / GHCR_TOKEN   a read:packages token so the server can pull the private images from ghcr.io
#   BACKUP_REMOTE  user@host:/path -> a second cron job copies the backups OFF this server with rsync (you set up that SSH key)
#   DEPLOY_PATH (default /opt/math-lms) · BACKUP_DIR (default /var/backups/math-lms)
# Flag --start pulls the images and starts the stack (+ monitoring), then waits for it to answer.
#
# Safe to re-run: every step checks before it changes anything.
set -euo pipefail

SRC="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/math-lms}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/math-lms}"
DOMAIN="${DOMAIN:-}"
DEPLOY_USER="${DEPLOY_USER:-}"
RUN_USER="${DEPLOY_USER:-root}"
START=false
[ "${1:-}" = "--start" ] && START=true

log() { echo "==> $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "run as root (sudo)"
[ -f "$SRC/docker-compose.prod.yml" ] || die "docker-compose.prod.yml not found next to deploy/ (looked in $SRC)"

# ---------------------------------------------------------------------------------------------- 1. Docker
if docker compose version >/dev/null 2>&1; then
  log "Docker + Compose already installed ($(docker --version))"
else
  . /etc/os-release
  case "${ID:-}" in debian|ubuntu) ;; *) die "automatic Docker install supports Debian/Ubuntu only (this is ${ID:-unknown}); install Docker + Compose v2 yourself and re-run" ;; esac
  log "Installing Docker Engine + Compose plugin from Docker's official apt repository"
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL "https://download.docker.com/linux/$ID/gpg" -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/$ID ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
fi

# ---------------------------------------------------------------------------------------------- 2. deploy user
if [ -n "$DEPLOY_USER" ]; then
  id "$DEPLOY_USER" >/dev/null 2>&1 || { log "Creating user $DEPLOY_USER"; useradd -m -s /bin/bash "$DEPLOY_USER"; }
  usermod -aG docker "$DEPLOY_USER"
  HOME_DIR="$(getent passwd "$DEPLOY_USER" | cut -d: -f6)"
  if [ -n "${DEPLOY_PUBKEY:-}" ]; then
    install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$HOME_DIR/.ssh"
    touch "$HOME_DIR/.ssh/authorized_keys"
    grep -qxF "$DEPLOY_PUBKEY" "$HOME_DIR/.ssh/authorized_keys" || echo "$DEPLOY_PUBKEY" >> "$HOME_DIR/.ssh/authorized_keys"
    chmod 600 "$HOME_DIR/.ssh/authorized_keys"
    chown "$DEPLOY_USER:$DEPLOY_USER" "$HOME_DIR/.ssh/authorized_keys"
    log "Public key authorised for $DEPLOY_USER"
  else
    echo "note: no DEPLOY_PUBKEY given - add the public key to $HOME_DIR/.ssh/authorized_keys yourself"
  fi
fi

# ---------------------------------------------------------------------------------------------- 3. files
log "Copying compose file, monitoring/ and deploy/ to $DEPLOY_PATH"
mkdir -p "$DEPLOY_PATH"
if [ "$SRC" != "$DEPLOY_PATH" ]; then
  # monitoring/secrets is excluded on purpose: it holds the DEV token; the prod one is written from .env.prod below.
  tar -C "$SRC" --exclude=monitoring/secrets -cf - docker-compose.prod.yml monitoring deploy | tar -C "$DEPLOY_PATH" -xf -
fi
mkdir -p "$DEPLOY_PATH/monitoring/secrets"
chmod +x "$DEPLOY_PATH"/deploy/*.sh

ENV="$DEPLOY_PATH/.env.prod"
if [ ! -f "$ENV" ]; then
  [ -f "$SRC/.env.prod" ] || die ".env.prod not found in $DEPLOY_PATH or $SRC - copy yours there first (scp), it is never part of the repo"
  cp "$SRC/.env.prod" "$ENV"
fi
chmod 600 "$ENV"
sed -i 's/\r$//' "$ENV"   # a file edited on Windows has CRLF line endings; the values must not end in 

get() { grep -m1 "^$1=" "$ENV" | cut -d= -f2- || true; }
set_var() { if grep -q "^$1=" "$ENV"; then sed -i "s|^$1=.*|$1=$2|" "$ENV"; else echo "$1=$2" >> "$ENV"; fi; }

# ---------------------------------------------------------------------------------------------- 4. validate .env.prod
log "Checking $ENV"
problems=()
for k in POSTGRES_PASSWORD REDIS_PASSWORD JWT_SECRET GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET ADMIN_EMAILS \
         METRICS_TOKEN GRAFANA_PASSWORD BACKEND_IMAGE FRONTEND_IMAGE; do
  [ -n "$(get "$k")" ] || problems+=("$k is empty")
done
jwt="$(get JWT_SECRET)"
[ "${#jwt}" -ge 32 ] || problems+=("JWT_SECRET must be at least 32 characters")
case "$(get FRONTEND_BASE_URL)" in https://*.example.*|"") problems+=("FRONTEND_BASE_URL is still the placeholder") ;; https://*) ;; *) problems+=("FRONTEND_BASE_URL must start with https://") ;; esac
[ "$(get COOKIE_SECURE)" = "true" ] || problems+=("COOKIE_SECURE must be true in production")
if [ "${#problems[@]}" -gt 0 ]; then
  printf 'ERROR: %s\n' "${problems[@]}" >&2
  die "fix $ENV and re-run"
fi
if [ -n "$DOMAIN" ] && [ "$(get FRONTEND_BASE_URL)" != "https://$DOMAIN" ]; then
  echo "warning: FRONTEND_BASE_URL is $(get FRONTEND_BASE_URL) but DOMAIN is $DOMAIN - links in emails and the Google redirect URI must use the same address"
fi
[ "$(get NOTIFY_RESULT_READY)" = "true" ] && [ -z "$(get SMTP_USER)" ] && echo "warning: NOTIFY_RESULT_READY=true but SMTP_USER is empty"

# Prometheus reads the metrics bearer token from a file (no trailing newline).
printf '%s' "$(get METRICS_TOKEN)" > "$DEPLOY_PATH/monitoring/secrets/metrics-token"
chmod 644 "$DEPLOY_PATH/monitoring/secrets/metrics-token"

# ---------------------------------------------------------------------------------------------- 5. HTTPS proxy (Caddy)
if [ -n "$DOMAIN" ]; then
  [ "$(get HTTP_PORT)" = "80" ] && set_var HTTP_PORT 8080   # Caddy takes 80/443 on the host
  set_var HTTP_BIND 127.0.0.1                                # plain HTTP only reachable through Caddy
fi
PORT="$(get HTTP_PORT)"; PORT="${PORT:-80}"

chown -R "$RUN_USER:$RUN_USER" "$DEPLOY_PATH"

# ---------------------------------------------------------------------------------------------- 6. GHCR login
if [ -n "${GHCR_USER:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
  log "docker login ghcr.io as $RUN_USER"
  RUN_HOME="$(getent passwd "$RUN_USER" | cut -d: -f6)"
  runuser -u "$RUN_USER" -- env HOME="$RUN_HOME" docker login ghcr.io -u "$GHCR_USER" --password-stdin <<<"$GHCR_TOKEN" >/dev/null
else
  echo "note: GHCR_USER/GHCR_TOKEN not given - run 'docker login ghcr.io' as $RUN_USER yourself (images are private)"
fi

# ---------------------------------------------------------------------------------------------- 7. backups (cron)
log "Installing the daily backup cron job"
mkdir -p "$BACKUP_DIR"
touch /var/log/math-lms-backup.log
chown "$RUN_USER:$RUN_USER" "$BACKUP_DIR" /var/log/math-lms-backup.log
{
  echo "# Managed by deploy/server-setup.sh"
  echo "15 3 * * * $RUN_USER cd $DEPLOY_PATH && ./deploy/backup.sh $BACKUP_DIR >> /var/log/math-lms-backup.log 2>&1"
  if [ -n "${BACKUP_REMOTE:-}" ]; then
    echo "45 3 * * * $RUN_USER rsync -a -e 'ssh -o BatchMode=yes' $BACKUP_DIR/ $BACKUP_REMOTE/ >> /var/log/math-lms-backup.log 2>&1"
  fi
} > /etc/cron.d/math-lms-backup
chmod 644 /etc/cron.d/math-lms-backup
if [ -n "${BACKUP_REMOTE:-}" ]; then
  command -v rsync >/dev/null || { apt-get update -qq && apt-get install -y -qq rsync; }
  echo "note: off-server copy goes to $BACKUP_REMOTE - give $RUN_USER an SSH key that can log in there (BatchMode: no password prompts)"
else
  echo "note: BACKUP_REMOTE not set - backups stay on THIS disk until you copy them elsewhere"
fi

# ---------------------------------------------------------------------------------------------- 8. start
if $START; then
  log "Pulling images and starting the stack"
  runuser -u "$RUN_USER" -- bash -c "cd '$DEPLOY_PATH' && docker compose -f docker-compose.prod.yml --env-file .env.prod pull backend frontend && docker compose -f docker-compose.prod.yml --env-file .env.prod --profile monitoring up -d --no-build"

  if [ -n "$DOMAIN" ]; then
    log "Starting Caddy for $DOMAIN (certificates are issued automatically; ports 80 and 443 must be open and the DNS A record must point here)"
    cat > "$DEPLOY_PATH/Caddyfile" <<EOF
$DOMAIN {
	encode gzip
	reverse_proxy 127.0.0.1:$PORT
}
EOF
    docker rm -f caddy >/dev/null 2>&1 || true
    docker run -d --name caddy --restart unless-stopped --network host \
      -v "$DEPLOY_PATH/Caddyfile:/etc/caddy/Caddyfile:ro" -v caddy_data:/data -v caddy_config:/config caddy:2 >/dev/null
  fi

  log "Waiting for the app (through nginx on :$PORT)"
  for i in $(seq 1 36); do
    CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/api/auth/me" || echo 000)"
    if [ "$CODE" = "401" ] || [ "$CODE" = "200" ]; then log "healthy (HTTP $CODE)"; break; fi
    echo "attempt $i: HTTP $CODE, retrying in 5s"; sleep 5
    [ "$i" = 36 ] && die "the stack did not become healthy - check: docker compose -f $DEPLOY_PATH/docker-compose.prod.yml logs backend"
  done
fi

cat <<EOF

Done. Next:
  - GitHub -> Settings -> Environments -> production -> secrets: DEPLOY_HOST (this server), DEPLOY_USER=$RUN_USER, DEPLOY_SSH_KEY (the PRIVATE key whose public half is authorised above)
    (+ DEPLOY_PATH only if you changed it from /opt/math-lms)
  - Google Cloud Console -> Authorized redirect URI: https://${DOMAIN:-<your-domain>}/login/oauth2/code/google
  - Test a restore once:  cd $DEPLOY_PATH && ./deploy/backup.sh $BACKUP_DIR && ./deploy/restore.sh ...
  - Monitoring is on localhost only:  ssh -L 3000:localhost:3000 <server>   (Grafana)
EOF
