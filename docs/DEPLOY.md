# Deploy în producție

Tot stack-ul pornește dintr-un singur fișier: `docker-compose.prod.yml`. Acest ghid acoperă pregătirea
serverului, prima pornire, release-urile automate, rollback-ul, backup-ul și monitorizarea.

```
Internet ─ HTTPS ─► [proxy TLS: Caddy / Traefik / load balancer cloud]
                          │  X-Forwarded-Proto, X-Forwarded-For
                          ▼
                   nginx (frontend)  :80  ← singurul port publicat
                    ├─ /            → SPA (fișiere statice)
                    ├─ /api, /oauth2, /login/oauth2 → backend:8080
                    └─ /actuator    → 404 (nu se expune niciodată)
                          │ rețea privată compose
        backend (Spring Boot) ── postgres (date) ── redis (sesiuni, cache, limitări)
              │ volum `uploads` = pozele elevilor
        [profil monitoring] prometheus · alertmanager · grafana  (doar 127.0.0.1)
```

## 1. Pregătirea serverului (o singură dată)

1. Un server Linux cu **Docker + Docker Compose v2**. Minim ~2 GB RAM (JVM + Postgres + Redis + nginx).
2. Un director, ex. `/opt/math-lms`, cu: `docker-compose.prod.yml`, directorul `monitoring/`, directorul `deploy/`
   și fișierul `.env.prod` (vezi mai jos). Cel mai simplu: `git clone` al repo-ului acolo.
3. **`.env.prod`**: `cp .env.prod.example .env.prod` și completează TOATE valorile (secretele: `openssl rand -base64 48`).
   Fișierul e în `.gitignore` — nu se comite niciodată.
4. **HTTPS (obligatoriu)**: cookie-ul de login e `Secure` (`COOKIE_SECURE=true`), deci fără HTTPS nu merge login-ul.
   Pune în fața nginx-ului un proxy TLS care setează `X-Forwarded-Proto: https`. Exemplu cu Caddy (certificat automat):

   ```
   mathlms.exemplu.ro {
       reverse_proxy localhost:8080     # HTTP_PORT din .env.prod
   }
   ```
5. **Google OAuth**: în Google Cloud Console adaugă la *Authorized redirect URIs*:
   `https://<domeniul-tău>/login/oauth2/code/google` și pune `FRONTEND_BASE_URL=https://<domeniul-tău>`.
6. **Monitorizare** (opțional): `openssl rand -hex 32` → același șir în `.env.prod` (`METRICS_TOKEN=`) și în
   `monitoring/secrets/metrics-token` (fără newline).

## 2. Prima pornire

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod --profile monitoring up -d   # + monitorizare
```

Flyway creează schema la prima pornire a backend-ului. Primul profesor: emailul lui trebuie să fie în `ADMIN_EMAILS`;
se loghează cu Google și primește automat rolul ADMIN. Verifică: `curl -i http://localhost:<HTTP_PORT>/api/auth/me` → `401`
(aplicația răspunde prin nginx) și `docker compose ... ps` → toate `healthy`.

## 3. Release-uri automate (GitHub Actions)

`git tag v1.2.0 && git push origin v1.2.0` pornește `deploy.yml`:
1. **build-push.yml**: construiește imaginile `ghcr.io/<owner>/math-lms-backend` și `-frontend` (taguri `1.2.0`, `1.2`, `latest`, sha),
   le scanează cu Trivy (CRITICAL/HIGH cu fix disponibil blochează) și le publică în GHCR;
2. **deploy** (mediul `production`): prin SSH, în `DEPLOY_PATH`: salvează `.env.prod` ca `.env.prod.previous`, pune noile imagini
   în `.env.prod`, `pull` + `up -d --no-build`, apoi **așteaptă să răspundă `/api/auth/me` prin nginx** (max ~3 min).
   Dacă nu devine sănătos → **rollback automat** la imaginile anterioare și workflow-ul eșuează.

Secrete GitHub (Settings → Secrets → Actions, mediul `production`): `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`
(opțional `DEPLOY_PATH`, implicit `/opt/math-lms`). Fără ele, imaginile se publică dar nu se face deploy (avertisment, nu eroare).
Pe server, o singură dată: `docker login ghcr.io` cu un token `read:packages` (dacă pachetele sunt private).

**Rollback manual**: pe server `cp .env.prod.previous .env.prod && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-build`.
Rollback-ul nu anulează migrările Flyway deja aplicate — migrările se scriu compatibil cu versiunea anterioară (adaugă coloane nullable,
nu redenumi/șterge în același release).

## 4. Backup și restaurare (Pasul 6.7)

Ce nu se poate reconstrui: **baza de date** (conturi, note) și **pozele elevilor** (volumul `uploads`; sunt date ale unor minori).

```bash
./deploy/backup.sh /var/backups/math-lms        # pg_dump + arhivă poze, verifică ambele fișiere, șterge ce e mai vechi de 14 zile
```
Cron zilnic: `15 3 * * *  cd /opt/math-lms && ./deploy/backup.sh /var/backups/math-lms >> /var/log/math-lms-backup.log 2>&1`.
**Copiază backup-urile și în afara serverului** (alt calculator / stocare obiect): un backup pe același disc dispare odată cu discul.

```bash
./deploy/restore.sh backups/db-AAAALLZZ-HHMMSS.dump backups/uploads-AAAALLZZ-HHMMSS.tar.gz
```
Restaurarea **înlocuiește** baza și pozele curente (cere confirmare), oprește aplicația cât durează și o repornește. **Testeaz-o cel puțin o dată**
înainte de a ai nevoie de ea: ciclul backup → ștergere → restore a fost verificat pe stack-ul de test (vezi `docs/TESTING.md`, secțiunea 12h).

## 5. Monitorizare

Profilul `monitoring` pornește Prometheus (`127.0.0.1:9090`), Alertmanager (`127.0.0.1:9093`) și Grafana (`127.0.0.1:3000`) — accesibile
doar prin tunel SSH: `ssh -L 3000:localhost:3000 server`. Detalii și lista alertelor: `monitoring/README.md`. Pentru alerte pe email,
completează `monitoring/alertmanager.yml` (exemplu comentat în fișier).

## 6. Lista de verificare la producție (Pasul 6.8)

- [ ] `COOKIE_SECURE=true` și site-ul servit **doar** prin HTTPS
- [ ] `JWT_SECRET` **nou** (nu cel din dezvoltare), minim 32 de caractere
- [ ] Redirect URI-ul Google de producție în Google Console; `FRONTEND_BASE_URL` = URL-ul public (linkurile din emailuri)
- [ ] `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `GRAFANA_PASSWORD` puternice și diferite
- [ ] `SECURITY_LOG_LEVEL=INFO` (implicit); `RATE_LIMIT_ENABLED=true`
- [ ] SMTP real (`SMTP_*`) înainte de `NOTIFY_RESULT_READY=true`
- [ ] Backup-ul rulează zilnic **și** a fost testată o restaurare
- [ ] `/actuator/prometheus` NU e accesibil din exterior (`curl https://<domeniu>/actuator/prometheus` → 404)
- [ ] GitHub: branch protection pe `main` (CI verde obligatoriu), secrete de deploy puse doar pe mediul `production`
