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

## 4. Backup și restaurare (Pasul 6.7; criptat și în afara serverului din 2026-10-25)

Ce nu se poate reconstrui: **baza de date** (conturi, note) și **pozele elevilor** (volumul `uploads`; sunt date ale unor minori).
De aceea backup-ul **nu se scrie niciodată necriptat** și **nu stă doar pe server**: `deploy/backup.sh` refuză să ruleze fără
`BACKUP_AGE_RECIPIENT` și `BACKUP_S3_BUCKET`.

**O singură dată:**
1. Pe calculatorul tău (nu pe server): `age-keygen -o math-lms-backup.txt`. Fișierul e **cheia privată** — o pui în managerul de parole
   și în secretul GitHub `BACKUP_AGE_IDENTITY`; nu ajunge niciodată pe server. Linia `# public key: age1…` merge în `.env.prod` ca
   `BACKUP_AGE_RECIPIENT`. Fără cheia privată backup-urile nu se mai pot citi — păstreaz-o în două locuri.
2. AWS → S3 → bucket nou `math-lms-backups-<ceva unic>` (separat de cel cu fișiere): Block all public access, Versioning pornit,
   Management → Lifecycle rule: șterge versiunile curente după **90 de zile** și pe cele vechi după 30. În `.env.prod`:
   `BACKUP_S3_BUCKET=math-lms-backups-…`. Rolul EC2 (§9) primește în plus `s3:PutObject`/`s3:GetObject` pe `…/backups/*`.
3. `sudo ./deploy/server-setup.sh` instalează `age` și `awscli` și cron-ul zilnic.

```bash
./deploy/backup.sh /var/backups/math-lms   # dump + poze: verificate pe drum, criptate înainte de disc, urcate în S3, 7 zile local
```
Cron zilnic: `15 3 * * *  cd /opt/math-lms && ./deploy/backup.sh /var/backups/math-lms >> /var/log/math-lms-backup.log 2>&1`.
Serverul nu poate decripta ce scrie (n-are cheia privată), deci un server sau un bucket furat nu dezvăluie nimic. Backup-urile vechi,
necriptate (`.dump`, `.tar.gz`) nu se șterg automat: logul te avertizează zilnic până le ștergi tu.

**Testul de restaurare, lunar și automat** (`.github/workflows/restore-test.yml`, pe 1 ale lunii; și manual din Actions → Run workflow):
ia cel mai nou backup din S3, îl decriptează, îl restaurează într-un Postgres de unică folosință și verifică: conturi > 0, schema la zi,
arhiva de poze lizibilă, backup-ul mai nou de 2 zile. Nu atinge serverul. Secrete GitHub: `BACKUP_AGE_IDENTITY` (conținutul cheii
private), `BACKUP_S3_BUCKET`, `AWS_RESTORE_TEST_ROLE_ARN` (un rol IAM cu `s3:GetObject`/`s3:ListBucket` doar pe bucket-ul de backup,
asumabil prin OIDC de repo-ul ăsta). Manual, de pe calculatorul tău:
`AGE_IDENTITY=math-lms-backup.txt BACKUP_S3_BUCKET=… ./deploy/restore-test.sh`.

**Restaurarea pe server** (înlocuiește baza și pozele curente, cere confirmare, oprește aplicația cât durează): copiezi cheia privată
pe server doar pentru restaurare și o ștergi după.
```bash
AGE_IDENTITY=~/math-lms-backup.txt ./deploy/restore.sh backups/db-AAAALLZZ-HHMMSS.dump.age backups/uploads-AAAALLZZ-HHMMSS.tar.gz.age
```

### Autentificarea în doi pași (F8)
`TWO_FACTOR_REQUIRED_FOR_ADMIN` e `true` implicit: la primul login după release, fiecare administrator își activează aplicația de
autentificare din „Contul meu” (paginile de profesor se deschid după aceea) și își păstrează cele 10 coduri de recuperare. Secretele
sunt criptate cu o cheie derivată din `JWT_SECRET`: dacă schimbi `JWT_SECRET`, toți își activează din nou aplicația. Admin blocat
(telefon pierdut, fără coduri): pe server, `UPDATE users SET totp_secret = NULL, totp_last_step = NULL, totp_enabled_at = NULL WHERE email = '…';`
apoi își activează din nou.

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

## 9. Fișierele în S3 (F11) — poze elevi, imagini subiecte, capitole EN, pagini import AI

Implicit fișierele stau în volumul `uploads` de pe disc. Cu `FILE_STORAGE_PROVIDER=s3` stau într-un **bucket S3 privat**
(cheia = `uploads/<folder>/<nume>`, aceleași nume ca pe disc; baza nu se schimbă). Nimic nu e public: aplicația citește fișierul și
îl servește ea, după ce verifică cine îl cere.

**O singură dată, în AWS** (aceeași regiune ca EC2, ex. `eu-central-1`):
1. S3 → Create bucket `math-lms-files-<ceva unic>`: **Block all public access** bifat, **Bucket Versioning** pornit (un fișier șters din
   greșeală se recuperează), criptare implicită SSE-S3.
2. IAM → Roles → rolul instanței EC2 (dacă nu are: Create role → EC2) → politică inline:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
         "Resource": "arn:aws:s3:::math-lms-files-NUME/uploads/*" },
       { "Effect": "Allow", "Action": "s3:ListBucket", "Resource": "arn:aws:s3:::math-lms-files-NUME" }
     ]
   }
   ```
   EC2 → instanța → Actions → Security → Modify IAM role → rolul de mai sus.
3. **Containerul trebuie să ajungă la credențialele instanței**: EC2 → instanța → Actions → Instance settings → Modify instance
   metadata options → *Metadata response hop limit* = **2** (cu 1, un container Docker nu le primește și S3 răspunde „Access Denied”).
   Din CLI: `aws ec2 modify-instance-metadata-options --instance-id i-… --http-put-response-hop-limit 2 --http-tokens required`.

**Pe server:**
```bash
sudo apt-get install -y awscli          # o singură dată
cd /opt/math-lms && S3_BUCKET=math-lms-files-NUME ./deploy/s3-migrate-uploads.sh
```
Scriptul copiază tot volumul în bucket și se oprește cu eroare dacă în bucket sunt mai puține obiecte decât fișiere. Apoi în `.env.prod`:
`FILE_STORAGE_PROVIDER=s3`, `S3_BUCKET=math-lms-files-NUME`, `S3_REGION=eu-central-1`, și `docker compose … up -d backend`.
**Verifică** o poză de elev și o imagine de subiect în aplicație; abia după câteva zile poți goli volumul `uploads`.
Înapoi pe disc: `FILE_STORAGE_PROVIDER=local` (fișierele noi din S3 se copiază înapoi cu `aws s3 sync s3://…/uploads ./uploads`).

## 10. Tutorul: „Sugerează indicii (AI)” (serviciul `tutor/`)

Butonul din editorul întrebării cere tutorului un indiciu, un indiciu mai mare și rezolvarea, scrise **doar** din paginile
capitolului EN, cu pagina din manual la fiecare. Profesorul adaugă ce păstrează și salvează; elevii nu declanșează niciun apel.
Se trimite la Gemini doar textul întrebării și pasajele din manual — niciun nume, niciun cont. Cost: ~0,0006 $ o întrebare.

Containerul `tutor` nu are port publicat: îl apelează doar backend-ul, pe rețeaua internă, cu un token comun.

**Pornire** (în `.env.prod`, apoi `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`):
```bash
COMPOSE_PROFILES=tutor              # cu monitorizare: COMPOSE_PROFILES=tutor,monitoring
TUTOR_ENABLED=true
TUTOR_SERVICE_TOKEN=...             # openssl rand -hex 32 — același șir merge la backend și la tutor
# același GEMINI_PROJECT și aceeași cheie JSON (./secrets/gcp-vertex.json) ca la importul din poze
```
La un release (tag `v*`), workflow-ul de deploy pune și `TUTOR_IMAGE` și trage imaginea tutorului doar dacă profilul e pornit.

**Atenție la `POSTGRES_PASSWORD`:** tutorul îl pune într-un URL (`postgresql+psycopg://user:parola@postgres/…`). Caracterele
`@ : / # ? %` îl strică — folosește o parolă doar din litere și cifre (`openssl rand -hex 24`) sau codific-o în URL.

**Capitolele trebuie construite pe server** — fără ele tabela `tutor_chunks` e goală și tutorul răspunde doar „Nu am asta în
manual.”. Pozele capitolului (încărcate din pagina capitolului EN, „Adaugă PDF”) stau în volumul `uploads`, pe care tutorul îl vede
doar pentru citire:
```bash
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"
$COMPOSE exec tutor ls /app/uploads/en-chapters | head            # pozele se văd?
$COMPOSE exec tutor python -m tutor.build_chapter AI --dry-run    # ce ar face, fără apeluri plătite
$COMPOSE exec tutor python -m tutor.build_chapter AI --max-usd 0.10
```
`build_chapter` transcrie pozele (o singură dată pe poză), le taie în pasaje, face embeddings și scrie textul în paginile
capitolului **doar unde profesorul nu a scris deja**. Merge numai cu `FILE_STORAGE_PROVIDER=local`: cu S3 pozele nu sunt pe disc
(neacoperit încă).

**Verificare:** `$COMPOSE exec tutor python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8001/health').read())"`
→ `{"status":"ok"}`; apoi în aplicație, pe o întrebare salvată din „SUBIECTUL I”, „Sugerează indicii (AI)”.
„Tutorul nu răspunde acum” = containerul nu rulează; „Tutorul nu e configurat pe server” = tokenul lipsește sau diferă.

**Oprire:** `TUTOR_ENABLED=false` (butonul dispare) și scoți `tutor` din `COMPOSE_PROFILES`.
