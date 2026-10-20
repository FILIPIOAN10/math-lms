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

**Spring Boot 4 (după v0.3.0)**: backend-ul rulează pe Spring Boot 4.1.1 / Spring Framework 7.0.9 / Jackson 3. Nicio variabilă din
`.env.prod` nu s-a schimbat. Singura cheie nouă e în `application.yml`, nu în env: `spring.jackson.use-jackson2-defaults: true`,
care păstrează JSON-ul API-ului identic cu cel de dinainte. Nu există migrări noi, iar formatul din Redis e același (sesiunile sunt
string-uri, cache-ul are același JSON), deci un rollback la imaginea v0.3.0 rămâne sigur.

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
- [ ] SMTP real (`SMTP_*`) înainte de `NOTIFY_RESULT_READY=true` și `NOTIFY_ASSIGNMENT_REMINDER=true`
- [ ] Backup-ul rulează zilnic **și** a fost testată o restaurare
- [ ] `/actuator/prometheus` NU e accesibil din exterior (`curl https://<domeniu>/actuator/prometheus` → 404)
- [ ] GitHub: branch protection pe `main` (CI verde obligatoriu), secrete de deploy puse doar pe mediul `production`

## 7. Setup rapid pe un server nou (`deploy/server-setup.sh`)

Automatizează §1, §2 și §4: instalează Docker + Compose (Debian/Ubuntu), creează utilizatorul de deploy și îi autorizează cheia
publică, copiază fișierele în `/opt/math-lms`, **validează `.env.prod`** (refuză dacă lipsesc Google/ADMIN_EMAILS/URL-ul public sau
`JWT_SECRET` e prea scurt), scrie tokenul Prometheus, face `docker login ghcr.io`, instalează cron-ul zilnic de backup și, opțional,
pornește totul + Caddy (HTTPS automat). E idempotent: îl poți rula din nou după ce corectezi `.env.prod`.

De pe calculatorul tău (Git Bash, din `math-lms/`):

```bash
# 1. O singură dată: cheia cu care GitHub Actions intră pe server. Partea PRIVATĂ merge în secretul DEPLOY_SSH_KEY, cea publică pe server.
ssh-keygen -t ed25519 -f deploy_key -N "" -C "github-actions-math-lms"

# 2. Trimite fișierele (fără monitoring/secrets = tokenul de DEV) și .env.prod (completat, gitignorat)
tar czf - --exclude=monitoring/secrets docker-compose.prod.yml deploy monitoring \
  | ssh USER@SERVER "rm -rf ~/mathlms-setup && mkdir ~/mathlms-setup && tar xzf - -C ~/mathlms-setup"
scp .env.prod USER@SERVER:~/mathlms-setup/.env.prod

# 3. Rulează setup-ul (tokenul GHCR = Personal access token cu doar read:packages; citește-l fără să rămână în istoric)
read -s -p "GHCR token: " GHCR_TOKEN; echo
ssh -t USER@SERVER "sudo DOMAIN=mathlms.exemplu.ro DEPLOY_USER=deploy DEPLOY_PUBKEY='$(cat deploy_key.pub)' \
  GHCR_USER=filipioan10 GHCR_TOKEN='$GHCR_TOKEN' BACKUP_REMOTE=user@alt-server:/backups/math-lms \
  bash ~/mathlms-setup/deploy/server-setup.sh --start"
```

Înainte de pasul 3: înregistrarea DNS `A` a domeniului trebuie să arate spre server și porturile 80/443 să fie deschise (Caddy cere certificatul
singur). Cu `DOMAIN`, scriptul pune `HTTP_PORT=8080` + `HTTP_BIND=127.0.0.1` în `.env.prod`, deci portul HTTP simplu nu e accesibil din exterior.
Ce rămâne manual: Google redirect URI, secretele GitHub (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` = conținutul lui `deploy_key`), cheia SSH
pentru `BACKUP_REMOTE`, SMTP real și testarea unei restaurări.

> Verificat într-un container Ubuntu cu `docker` simulat (validare, copiere, utilizator + cheie, cron, Caddyfile, re-rulare); instalarea reală a Docker
> și emiterea certificatului nu au putut fi încercate fără un server real — la prima rulare urmărește output-ul.

## 8. Test din poze cu Gemini (Google Cloud)

Importul „test din poze” (Faza 3) poate citi paginile cu **Gemini pe Google Cloud** (Vertex AI, redenumit „Agent Platform”). Costul se
plătește din proiectul Google Cloud, deci și din creditul de probă de 300$. O cheie din AI Studio **nu** e acoperită de acel credit pentru
conturile deschise după 2 martie 2026, de aceea folosim un cont de serviciu, nu o cheie API.

**O singură dată, în Google Cloud Console** (proiectul aplicației, ex. `math-lms-507017`):
1. APIs & Services → Library → **Agent Platform API** (`aiplatform.googleapis.com`) → Enable.
2. IAM & Admin → Service accounts → Create: `math-lms-reader`, rol **Agent Platform user** (fostul „Vertex AI User”); nimic la pasul 3.
3. Contul creat → Keys → Add key → JSON. Fișierul e o parolă: nu intră niciodată în git, în chat sau în imagine.
4. Billing → Budgets & alerts: un buget (ex. 50$) cu alertă pe email.

**Pe server** (cheia stă lângă `docker-compose.prod.yml`, în `secrets/`, montat read-only în container la `/app/secrets`; utilizatorul
din containerul backend are `uid=100 gid=101`, deci fișierul îi aparține lui):

```bash
scp -i ~/.ssh/math-lms-aws.pem ~/.ssh/math-lms-vertex.json ubuntu@SERVER:~/gcp-vertex.json
ssh -i ~/.ssh/math-lms-aws.pem ubuntu@SERVER "sudo mkdir -p /opt/math-lms/secrets && sudo mv ~/gcp-vertex.json /opt/math-lms/secrets/gcp-vertex.json && sudo chown 100:101 /opt/math-lms/secrets/gcp-vertex.json && sudo chmod 400 /opt/math-lms/secrets/gcp-vertex.json"
```

În `/opt/math-lms/.env.prod` (editat pe server, `sudo nano`): `AI_IMPORT_ENABLED=true`, `AI_PROVIDER=gemini`,
`GEMINI_PROJECT=<id-ul proiectului>`; opțional `GEMINI_LOCATION` (implicit `global`) și `GEMINI_MODEL` (implicit `gemini-3.8-flash`).
`GEMINI_CREDENTIALS_FILE` are deja valoarea corectă în compose (`/app/secrets/gcp-vertex.json`). Apoi `up -d backend`.

- **Regiunea:** `global` are modelele noi (2026-10: `gemini-3.8-flash`, `gemini-4-argon`), dar Google poate procesa cererea oriunde;
  `europe-west1` ține procesarea în UE, dar are doar `gemini-2.5-*`. Se trimit doar poze cu pagini de manual, niciodată date ale elevilor.
- **Erorile** apar profesorului în română, pe ecranul importului (cheie lipsă / respinsă, rol lipsă, model inexistent în regiune, credit
  epuizat); detaliile tehnice sunt în logul backend-ului (`Gemini answered …`).
- **Cheia nu e în backup** (`deploy/backup.sh` salvează baza și pozele): dacă se pierde, creezi alta din Google Cloud și o ștergi pe cea veche.
