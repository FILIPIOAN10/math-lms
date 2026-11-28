#!/usr/bin/env bash
# Locks down a Debian/Ubuntu server (docs/DEPLOY.md §7). On AWS the Security Group was the firewall; most other hosts
# (Contabo, Hetzner, any VPS) hand you a machine open on every port, with root logging in by password. After this:
#   - a firewall (ufw) lets in only SSH, 80 and 443 (Caddy needs 80 for the certificate and the redirect to https);
#   - SSH takes keys only - no passwords, root only with a key;
#   - security updates install themselves every day.
#
#   sudo ./deploy/server-harden.sh        # deploy/server-setup.sh runs it too (HARDEN=false skips it there)
#
# It never locks you out: the SSH port is allowed before the firewall turns on, and password logins are switched off
# only when the account you log in with already has a key in ~/.ssh/authorized_keys. Safe to re-run.
#   SSH_PORT     an extra port to keep open (default: this session's port and the one sshd reports, else 22)
#   ADMIN_USER   the account you log in with (default: the one that ran sudo, else root)
#
# Docker publishes container ports through its own iptables rules, ahead of ufw: that is why docker-compose.prod.yml
# binds Prometheus/Grafana/Alertmanager to 127.0.0.1, and server-setup.sh binds nginx there too once Caddy runs.
set -euo pipefail

log() { echo "==> $*"; }
warn() { echo "warning: $*" >&2; }
die() { echo "ERROR: $*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "run as root (sudo)"
command -v apt-get >/dev/null || die "Debian/Ubuntu only (apt-get not found)"

# ---------------------------------------------------------------------------------------------- 1. packages
missing=()
for pkg in ufw unattended-upgrades; do dpkg -s "$pkg" >/dev/null 2>&1 || missing+=("$pkg"); done
if [ "${#missing[@]}" -gt 0 ]; then
  log "Installing ${missing[*]}"
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${missing[@]}" >/dev/null
fi

# ---------------------------------------------------------------------------------------------- 2. firewall
mkdir -p /run/sshd   # `sshd -T` / `sshd -t` refuse to run without it on a socket-activated sshd (Ubuntu 24.04)
candidates=()
[ -n "${SSH_PORT:-}" ] && candidates+=("$SSH_PORT")
[ -n "${SSH_CONNECTION:-}" ] && candidates+=("${SSH_CONNECTION##* }")   # the port this very session came in on
if command -v sshd >/dev/null; then
  while read -r port; do candidates+=("$port"); done < <(sshd -T 2>/dev/null | awk '$1 == "port" { print $2 }')
fi
mapfile -t ssh_ports < <(printf '%s\n' "${candidates[@]+"${candidates[@]}"}" | grep -E '^[0-9]+$' | sort -un)
[ "${#ssh_ports[@]}" -gt 0 ] || ssh_ports=(22)

for port in "${ssh_ports[@]}"; do ufw allow "$port/tcp" comment 'ssh' >/dev/null; done   # SSH first, always
ufw allow 80/tcp comment 'http: certificate + redirect to https' >/dev/null
ufw allow 443/tcp comment 'https' >/dev/null
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw --force enable >/dev/null
log "Firewall on: incoming only on ${ssh_ports[*]} (ssh), 80 and 443   (check: sudo ufw status verbose)"

# ---------------------------------------------------------------------------------------------- 3. SSH: keys only
ADMIN_USER="${ADMIN_USER:-${SUDO_USER:-root}}"
admin_home="$(getent passwd "$ADMIN_USER" | cut -d: -f6 || true)"
conf=/etc/ssh/sshd_config.d/00-math-lms.conf
if ! command -v sshd >/dev/null; then
  warn "no OpenSSH server here - nothing to change for SSH"
elif ! grep -qE '^[[:space:]]*Include[[:space:]]+/etc/ssh/sshd_config\.d/' /etc/ssh/sshd_config 2>/dev/null; then
  warn "this sshd does not read /etc/ssh/sshd_config.d - set 'PasswordAuthentication no' in /etc/ssh/sshd_config yourself"
elif [ -z "$admin_home" ] || ! grep -qE '^(ssh-|ecdsa-|sk-)' "$admin_home/.ssh/authorized_keys" 2>/dev/null; then
  warn "$ADMIN_USER has no key in ~/.ssh/authorized_keys - password logins stay ON so you are not locked out."
  warn "  From your PC: ssh-copy-id $ADMIN_USER@<server>  (or paste your .pub into that file), check that a key login works, re-run."
else
  # sshd keeps the FIRST value it reads and the files load in name order: 00- beats e.g. 50-cloud-init.conf.
  cat > "$conf" <<'EOF'
# Managed by deploy/server-harden.sh - keys only. sshd keeps the first value it reads, so this file sorts first.
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
  chmod 644 "$conf"
  if ! sshd -t; then
    rm -f "$conf"
    warn "sshd rejected the new settings - removed them, SSH is unchanged"
  else
    systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || true   # open sessions stay connected
    if [ "$(sshd -T 2>/dev/null | awk '$1 == "passwordauthentication" { print $2 }')" = "no" ]; then
      log "SSH: keys only ($ADMIN_USER has a key; root may log in with a key, never with a password)"
    else
      warn "sshd still allows passwords - a file that sorts before $conf sets PasswordAuthentication"
    fi
  fi
fi

# ---------------------------------------------------------------------------------------------- 4. security updates
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
// Managed by deploy/server-harden.sh: refresh the package lists and install security updates every day.
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
log "Security updates install themselves daily (a new kernel still needs 'sudo reboot' when /var/run/reboot-required exists)"
