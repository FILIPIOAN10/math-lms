# Monitoring (Phase 6.3)

Prometheus scrapes `/actuator/prometheus` (bearer token), evaluates `alert-rules.yml`, Grafana shows
`grafana/dashboards/mathlms.json`, Alertmanager groups the alerts.

## Local
```bash
# 1. a token for the scrape (the same value in both places, no trailing newline)
openssl rand -hex 32
#    -> backend/.env : METRICS_TOKEN=<token>        (restart the backend)
#    -> monitoring/secrets/metrics-token : <token>  (gitignored)
docker compose -f docker-compose.monitoring.yml up -d
```
- Prometheus <http://localhost:9090> → **Status → Targets**: `math-lms` must be **UP**
- Grafana <http://localhost:3000> (admin / admin) → dashboard *Math LMS — Service Overview*
- Alertmanager <http://localhost:9093>

## What is measured
Standard (no code): HTTP rate/latency/errors, JVM, Hikari pool, CPU. Custom: `security_failed_logins_total`,
`rate_limit_blocked_total{rule}`, `outbox_events{status="pending|dead"}`.

## Alerts
BackendDown · HighErrorRate · HighLatencyP95 · DatabaseConnectionPoolHigh · JVMHeapUsageHigh ·
FailedLoginBruteForce · RateLimitSpike · OutboxDeadLetters · OutboxBacklog

`/actuator/prometheus` is sensitive: it needs the token, and the production nginx never proxies `/actuator`.
