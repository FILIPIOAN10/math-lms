# Testing guide

Cum rulezi și testezi math-lms: stack local, teste automate și fluxuri manuale
per feature. Acoperă tot ce e construit până la Faza 1.6 (auth + onboarding).

## A. Pornește stack-ul

Ai nevoie de Docker Desktop pornit (Postgres + Redis + Testcontainers).

```bash
# 1. Infra (Postgres pe 5433, Redis pe 6379)
docker compose -f math-lms/docker-compose.yml up -d

# 2. Backend (Spring Boot pe 8080) — citește math-lms/backend/.env
cd math-lms/backend && ./mvnw spring-boot:run

# 3. Frontend (Vite pe 5173, proxy /api -> 8080)
cd math-lms/frontend && npm run dev
```

- Frontend: http://localhost:5173
- Health backend: http://localhost:8080/actuator/health

## B. Conturi de test

Un set de conturi de dev (parola tuturor = `Admin123!`). Seed într-o singură comandă
(hash-ul BCrypt e pentru `Admin123!`; `ON CONFLICT` îl face idempotent):

```bash
docker exec -i mathlms-postgres psql -U mathlms -d mathlms <<'SQL'
INSERT INTO users (email, full_name, role, password, email_verified, status, requested_role) VALUES
 ('admin@mathlms.local',        'Prof Admin',    'ADMIN',   '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL),
 ('parinte@mathlms.local',      'Maria Parinte', 'PARENT',  '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL),
 ('student.activ@mathlms.local','Ana Student',   'STUDENT', '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL),
 ('student.nou@mathlms.local',  'Radu Nou',       NULL,     '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'PENDING_APPROVAL', 'STUDENT')
ON CONFLICT (email) DO NOTHING;
SQL
```

| Email | Rol | Stare |
|---|---|---|
| `admin@mathlms.local` | ADMIN | ACTIVE |
| `parinte@mathlms.local` | PARENT | ACTIVE |
| `student.activ@mathlms.local` | STUDENT | ACTIVE |
| `student.nou@mathlms.local` | (cerut STUDENT) | PENDING_APPROVAL |

Reset un cont la starea pending (ca să repeți fluxul de aprobare):

```bash
docker exec -i mathlms-postgres psql -U mathlms -d mathlms -c \
 "UPDATE users SET status='PENDING_APPROVAL', role=NULL, parent_id=NULL WHERE email='student.nou@mathlms.local';"
```

Șterge datele de test:

```bash
docker exec -i mathlms-postgres psql -U mathlms -d mathlms -c \
 "DELETE FROM users WHERE email LIKE '%mathlms.local';"
```

## C. Testare automată

Rulează asta înainte de orice commit.

```bash
# Backend — 523 teste (necesită Docker pentru Testcontainers)
cd math-lms/backend && ./mvnw test

# Doar suita de conținut (Faza 2)
./mvnw -Dtest='ro.mathlms.content.*' test

# Frontend — `npm run build` include `tsc -b`, deci prinde și erorile de tipuri
cd math-lms/frontend && npm test && npm run build
```

Frontend: **Vitest + Testing Library** (`npm test`, 28 teste): clientul API (header CSRF doar pe scrieri, refresh silențios la 401 cu o singură rotație pentru cereri paralele, fără refresh la login greșit), `ProgressChart`, `AttemptResultView` (formulare elev/părinte, bareme), `RoleRoute` (toate redirecturile), mesajele de eroare ale `LoginPage` (401/403/429). Rulează și în CI. Fluxurile cap-coadă rămân în suita E2E (Selenium).

La finalul suitei backend poate apărea `Surefire is going to kill self fork JVM` — e inofensiv
(build-ul rămâne SUCCESS): testele de repository își pornesc fiecare propriul container Postgres,
iar la oprire Hikari închide lent conexiunile spre containerele deja oprite.

### Teste E2E (Selenium, Chrome) — `@Tag("e2e")`

Rulează într-un browser real împotriva stack-ului **deja pornit** (secțiunea A: Postgres + Redis,
backend `:8080`, Vite `:5173`) și a conturilor seed din secțiunea B. Sunt excluse din `./mvnw test`
(și din CI); se rulează explicit, din `backend/`:

```bash
./mvnw test "-Dgroups=e2e" "-DexcludedGroups="                    # toate
./mvnw test "-Dgroups=e2e" "-DexcludedGroups=" "-Dtest=QuizFlowE2eTest"   # doar fluxul complet
./mvnw test "-Dgroups=e2e" "-DexcludedGroups=" "-De2e.headless=false"     # Chrome vizibil, ca să urmărești
```

- `QuizFlowE2eTest`: quiz creat prin REST (`ApiSeeder`) → elevul îl dă (grilă + poză) → profesorul
  corectează → elevul vede 13 / 15. Fiecare rulare creează un quiz nou „E2E flow <timp>" (nu se șterg;
  un quiz cu încercări nu poate fi șters).
- `LoginE2eTest`: login admin/elev + logout real prin UI.
- `QuizVisibilityE2eTest`: quiz asignat unei clase → invizibil elevului neînscris, apare după înscriere.
- `QuizFlowE2eTest` verifică și statisticile profesorului (media `13.0 / 15 p (87%)`) și `/progress` al elevului (`13 / 15 (87%)`).
- `ParentE2eTest`: adminul leagă elevul de părinte, elevul predă un test → părintele (`parinte@mathlms.local`) îl vede la „Copiii mei” → elev → rezultat.
- Parametri opționali: `-De2e.baseUrl=…` (implicit `http://localhost:5173`), `-De2e.apiUrl=…` (`http://localhost:8080`).
- Selectorii sunt atributele `data-testid` din frontend; Page Objects în `backend/src/test/java/ro/mathlms/e2e/`.
- Logout-ul se face prin butonul din UI, nu `deleteAllCookies()`: cookie-ul de refresh e limitat la
  o cale sub `/api/auth`, iar SPA-ul ar reloga silențios utilizatorul.

## D. Testare manuală, per feature

### 1. Login email/parolă (`/login`, tab „Email și parolă")
- parolă greșită → „Email sau parolă greșite" (401)
- cont pending/respins → mesaj specific (403, ales după `body`)
- admin corect → dashboard

### 2. Guard-uri / rutare (merge și fără backend)
- `/` neautentificat → `/login`
- `/admin/pending` sau `/admin/links` ca non-admin → redirect acasă; neautentificat → `/login`
- cont autentificat dar non-ACTIVE → `/pending`

### 3. Admin — aprobare / respingere (`/admin/pending`, ca admin)
- schimbă rolul din select → **Aprobă** → rândul dispare; în DB `status=ACTIVE` + `role` = ce-ai ales
- **Respinge** → `status=REJECTED`

### 4. Admin — legare părinte (`/admin/links`, ca admin)
- alege un părinte pentru un student → **Leagă** → „Părinte curent" se actualizează; în DB `parent_id` setat
- endpointul: `GET /api/admin/users?role=STUDENT|PARENT`, apoi `POST /api/admin/users/{id}/link-parent`

### 5. Register + verify email (`/register?token=…`)
- fără token → „Invitație necesară"
- token-ul: `POST /api/admin/invites` (ca admin) → link `/register?token=…`
- după submit → „Verifică-ți emailul"; linkul de confirmare ajunge pe SMTP-ul real din `.env`

### 6. Forgot / reset parolă (`/forgot-password`, `/reset-password?token=…`)
- mereu „dacă există un cont…" (anti-enumerare); linkul de reset vine pe email

### 7. Google login (tab Google)
- OAuth real; merge doar cu emailuri din `ADMIN_EMAILS` / `ALLOWED_EMAILS`, sau via invite link

### 8. Admin — gestionare conținut (`/admin/content`, ca admin)
Buton pe Dashboard: **Gestionează conținut**. Navigare arborescentă cu breadcrumb.
- **Adaugă clasă** → dialog (nume + descriere) → apare în listă
- **Deschide** o clasă → **Adaugă carte**; deschide cartea → **Adaugă capitol**; deschide capitolul → **Adaugă exercițiu**
- La exercițiu: enunț + soluție pot conține LaTeX (`$x^2+1$`, `$$\frac{a}{b}$$`) + dificultate
- **Editează** / **Șterge** pe fiecare rând (ștergerea cere confirmare; o clasă/carte/capitol cu copii dă 409 — șterge întâi copiii)
- **Elevi** pe o clasă → dialog roster: alege un elev activ → **Adaugă**; **Scoate** pentru dezînscriere
- **Optimistic locking**: dacă doi admini editează același exercițiu, al doilea „Salvează" dă 409 („a fost modificat de altcineva")

### 9. Elev / oricine activ — răsfoire conținut (`/content`)
Buton pe Dashboard: **Conținut**. Read-only.
- Drill-down Clase → Cărți → Capitole → Exerciții, cu breadcrumb pentru a urca
- Exercițiile arată enunțul randat cu **KaTeX**; **Vezi soluția** dezvăluie soluția (tot KaTeX) + badge de dificultate
- Un cont PENDING nu ajunge aici (guard `STATUS_ACTIVE`)
- **Elev**: vede DOAR clasele în care e înscris (`GET /api/me/classes`, iar `GET /api/classes` îi întoarce tot doar clasele lui). Cartea/capitolul/exercițiul unei alte clase → **404** (nu 403), pe fiecare nivel: `/api/classes/{id}`, `/api/books/{id}`, `/api/chapters/{id}`, `/api/exercises/{id}` și listele lor (`ContentAccess`); neînscris nicăieri → „Nu ești înscris în nicio clasă”. Adminul/părintele văd toate clasele (Step 2.4a)

### 10. Admin — quiz builder (`/admin/quizzes`, ca admin)
- **Adaugă quiz** → **Deschide** → **Adaugă subiect**: grilă (≥2 variante, exact una corectă) sau deschis (punctaj + barem)
- **Publică** — doar quiz-urile publicate apar la elevi
- **Pentru clasa** (în dialogul quiz-ului): „Toți elevii” (implicit) sau o clasă — un quiz de clasă apare DOAR elevilor înscriși în ea; lista adminului arată „Clasa: …” / „Toți elevii”. Un elev care ghicește id-ul unui quiz al altei clase primește 404 (Step 2.4b, migrare V13)

### 11. Elev — dă un test (`/quizzes`, ca `student.activ@mathlms.local`)
Buton pe Dashboard: **Testele mele**.
- „Teste disponibile” → **Începe** (sau **Continuă** dacă ai unul în lucru) → **Începe testul**
- bifează o variantă → „x / n răspunsuri salvate” crește; **reîncarcă pagina** → Continuă → variantele și poza revin
- subiect deschis → alege o poză (una mare de pe telefon e micșorată automat la ≤2000 px înainte de upload)
- **Trimite lucrarea** (avertizează dacă ai subiecte fără răspuns) → pagina de rezultat: grile ✓/✗ cu răspunsul
  corect, subiectul deschis „în corectare”, **Arată baremul**
- ca admin sau părinte, `/quizzes` te trimite acasă (RoleRoute)

### 12. Profesor — corectură (`/admin/grading`, ca admin)
Buton pe Dashboard: **Corectură**.
- „De corectat” → **Corectează** → grilele (corectate automat), poza elevului (click = mărime completă), baremul
- punctaj peste valoarea subiectului → eroare; punctaj valid → **Salvează punctajul** → „✓ notat cu X p”
- **Finalizează nota** se deblochează când toate subiectele deschise au punctaj → lucrarea trece la „Notate”
- ca elev, rezultatul arată acum nota finală (`X / max puncte`)

### 12h. Stack-ul de producție (Docker) — `docs/DEPLOY.md`
`docker-compose.prod.yml`: nginx (singurul port publicat) + backend + Postgres + Redis (+ profil `monitoring`). Imaginile: `backend/Dockerfile` (Maven → JRE alpine, utilizator non-root, healthcheck pe `/actuator/health`) și `frontend/Dockerfile` (Vite → nginx, proxy `/api` + `/oauth2` + `/login/oauth2`, `/actuator` → 404, aceleași headere de securitate ca API-ul).
- **Pornire de test pe calculatorul tău** (fără să atingi dev-ul; folosește volume separate `mathlms-prod_*`): scrie un `.env.prod.test` cu valori de probă (vezi `.env.prod.example`; `HTTP_PORT=8088`, `COOKIE_SECURE=false`, `RATE_LIMIT_ENABLED=false`) și rulează `docker compose -f docker-compose.prod.yml --env-file .env.prod.test --profile monitoring up -d --build`; la final `... down -v`
- **Seed conturi de test** (doar pentru acest test, niciodată în producție reală): `docker compose -f docker-compose.prod.yml --env-file .env.prod.test exec -T postgres psql -U mathlms -d mathlms < deploy/e2e-seed.sql`
- **E2E în Chrome împotriva stack-ului de producție** (verifică și CSP-ul real): `cd backend && ./mvnw test -Dgroups=e2e -DexcludedGroups= -De2e.baseUrl=http://localhost:8088 -De2e.apiUrl=http://localhost:8088` — cele 5 teste trec (login, flux quiz cu poză, părinte, vizibilitate pe clasă)
- **Backup + restore** (testat): `ENV_FILE=.env.prod.test ./deploy/backup.sh ./bk` (pg_dump + poze, verifică ambele fișiere) → strici datele → `ENV_FILE=.env.prod.test ./deploy/restore.sh bk/db-….dump bk/uploads-….tar.gz --yes` → numele, răspunsurile, nota și poza revin; aplicația repornește singură
- **Rate limiting prin nginx**: cu `RATE_LIMIT_ENABLED=true`, 7 login-uri greșite cu `X-Forwarded-For: 203.0.113.5` → `401×5` apoi `429`; alt client (`203.0.113.9`) are găleata lui. `/actuator/*` prin nginx → 404
- **Verificări rapide**: `curl -i http://localhost:8088/` (SPA + headere), `.../quizzes` (fallback SPA), `.../api/auth/me` → 401, `.../healthz` → `ok`; Prometheus http://localhost:9090/targets → `math-lms` **UP**
- **Descoperit la testare**: `/actuator/health` devenea `DOWN` când SMTP nu era configurat/disponibil (indicatorul de mail) și containerul era marcat nesănătos — dezactivat (`management.health.mail.enabled=false`; emailurile oricum merg prin outbox cu reîncercări). Testat de `HealthIndicatorsTest`

### 12i. Loguri structurate + correlation ID
Fiecare cerere primește un id (`CorrelationIdFilter`, primul filtru): nginx generează `$request_id` și îl trimite ca `X-Request-Id`; backend-ul îl pune în MDC (`requestId`), îl scrie pe **fiecare linie de log** (`INFO [<id>] ...`) și îl întoarce în header-ul răspunsului. Un id nesigur (spații, newline, prea lung/scurt) e înlocuit, nu crezut; după cerere se șterge din MDC (thread-urile se reutilizează).
- Dev: format text; în producție compose-ul setează `LOG_FORMAT=logstash` → un obiect JSON pe linie (`@timestamp`, `level`, `logger_name`, `message`, `requestId`, …) bun pentru Loki/ELK
- Verificare: `curl -si http://localhost:8088/api/auth/me | grep -i x-request-id` apoi `docker logs <backend> | grep <id>` (pornește backend-ul cu `SECURITY_LOG_LEVEL=DEBUG` ca să vezi linii per cerere) și `docker logs <frontend> | grep rid=<id>` — același id în răspuns, în logul backend-ului și în access-log-ul nginx
- Teste: `CorrelationIdFilterTest` (id păstrat / generat / înlocuit / curățat și la eroare)

### 12g. Observabilitate (Prometheus + Grafana + Alertmanager) — `monitoring/README.md`
`/actuator/prometheus` (Micrometer) cere header-ul `Authorization: Bearer <METRICS_TOKEN>`; fără token → 401, utilizator logat oarecare → 403, token nesetat → blocat pentru toți. `/actuator/health` și `/info` rămân publice. Metrici proprii: `security_failed_logins_total`, `rate_limit_blocked_total{rule}`, `outbox_events{status="pending|dead"}` (dead = un email de rezultat care nu va mai pleca fără om).
- **Backend-ul de dezvoltare trebuie repornit complet o dată** (`Ctrl+C`, `.\mvnw.cmd spring-boot:run`) pentru că am adăugat dependența `micrometer-registry-prometheus`; restartul automat devtools nu încarcă jar-uri noi (până atunci `/actuator/prometheus` dă 404 pe dev). Pe stack-ul Docker de producție e deja verificat: ținta Prometheus `math-lms` = UP
- Pornire stack (local): token în `backend/.env` (`METRICS_TOKEN=`) ȘI în `monitoring/secrets/metrics-token` (același șir, gitignorat) → `docker compose -f docker-compose.monitoring.yml up -d`. Prometheus http://localhost:9090 → **Status → Targets: `math-lms` = UP**; Grafana http://localhost:3000 (admin/admin) → „Math LMS — Service Overview"; Alertmanager http://localhost:9093
- 9 alerte: BackendDown, HighErrorRate, HighLatencyP95, DatabaseConnectionPoolHigh, JVMHeapUsageHigh (doar heap: celelalte pool-uri au max=-1), FailedLoginBruteForce, RateLimitSpike, OutboxDeadLetters, OutboxBacklog. Validate cu `promtool check rules` / `check config` și `amtool check-config`
- Test: `PrometheusEndpointIntegrationTest` (token, serii: `jvm_memory_used_bytes`, `hikaricp_connections_active`, `http_server_requests_seconds_bucket`, `security_failed_logins_total`, `outbox_events`)

### 12f. Headere de securitate
Fiecare răspuns al API-ului poartă: `Content-Security-Policy` (`default-src 'self'`; `style-src 'self' 'unsafe-inline'` pentru KaTeX; `img-src 'self' data: blob:` pentru previzualizarea pozei; `frame-ancestors 'none'`), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` (fără geolocation/microfon/plăți; camera rămâne permisă pentru poza rezolvării) și `Strict-Transport-Security` (1 an) **doar pe HTTPS**.
- Verificare: `curl -s -D - -o /dev/null http://localhost:8080/actuator/health` (HSTS apare doar în spatele TLS)
- HTML-ul SPA-ului îl servește nginx în producție, care trimite același set (`deploy/nginx.conf`, Pasul 6.4); serverul Vite de dezvoltare nu pune CSP

### 12e. Rate limiting (429 + Retry-After)
Filtru Redis cu fereastră fixă, regulile în `RateLimitConfig` (prima care se potrivește câștigă): `POST /api/auth/login` **5/min/IP**, `forgot-password` 3/15 min, `reset-password` 5/15 min, `register` 5/oră, `refresh` 30/min (SPA-ul îl apelează la fiecare 401), upload poză **20/min/utilizator**. Peste limită → `429` + `Retry-After` + `X-RateLimit-Limit/Remaining`, corp text românesc; cererea nici nu ajunge la logica de login.
- INCR+EXPIRE într-un singur script Lua (atomic). **Fail-open**: Redis căzut = nicio limitare, nu 500
- `RATE_LIMIT_ENABLED` (implicit `true`). **În `backend/.env`-ul de dezvoltare e `false`** — suita E2E se loghează de zeci de ori/minut de la același IP. Pentru a vedea limitarea live: scoate linia / pune `true`, repornește backend-ul și rulează de 6 ori `curl -s -o /dev/null -w "%{http_code}
" -H "Content-Type: application/json" -d '{"email":"x@y.ro","password":"z"}' http://localhost:8080/api/auth/login` → 401×5 apoi **429**. Cheile: `docker exec mathlms-redis redis-cli --scan --pattern "rate_limit:*"`
- `RATE_LIMIT_TRUST_XFF`: `true` DOAR în spatele nginx-ului tău (care setează `X-Forwarded-For`); altfel oricine își poate falsifica găleata, iar în spatele unui proxy cu `false` toți utilizatorii împart aceeași găleată
- Pagina de login arată „Prea multe încercări…” la 429. Testele rulează cu limitele oprite (`src/test/resources/application.properties`); `RateLimitIntegrationTest` le pornește: a 6-a încercare de login de la aceeași adresă → 429, altă adresă neafectată

### 12d. Email „rezultatul e gata” (outbox tranzacțional)
Când o lucrare devine `GRADED` (finalizare de profesor SAU auto-corectare la submit) se pun în tabela `outbox_event` — **în aceeași tranzacție cu nota** — câte un eveniment `RESULT_READY_EMAIL` pe destinatar: unul pentru elev și, dacă adminul a legat un părinte, unul pentru părinte. Un dispatcher (`@Scheduled`, la 5 s) le trimite cu `FOR UPDATE SKIP LOCKED`; eșec SMTP → reîncercare cu backoff exponențial (30 s, 60 s, 120 s … max 1 h), după 8 încercări `DEAD` (necesită om). Payload = doar ids (fără date personale → GDPR curat); un eveniment per destinatar ca o reîncercare să nu retrimită cuiva care a primit deja.
- **IMPLICIT OPRIT**: `NOTIFY_RESULT_READY=true` în `.env` îl pornește (altfel, cu SMTP-ul real din `.env`, testele/E2E ar încerca să scrie la `@mathlms.local`). Oprit → niciun rând în outbox, niciun email
- Verificare manuală (cu SMTP real): pornește cu `NOTIFY_RESULT_READY=true`, notează o lucrare → `select event_type, status, attempts, last_error from outbox_event order by id desc;` (`PENDING` → `DONE` în ~5 s); emailul elevului trimite la `/quizzes/attempts/{id}/result`, al părintelui la `/parent/children/{id}/attempts/{id}`
- `DEAD` = `select * from outbox_event where status='DEAD'`; după ce repari cauza: `update outbox_event set status='PENDING', attempts=0, next_attempt_at=now() where id=…`
- Rândurile `DONE` mai vechi de 14 zile se șterg zilnic (03:30); cele `DEAD` rămân
- Teste: `OutboxIntegrationTest` (publish fără tranzacție → eroare; rollback → niciun rând; retry → dead-letter; purge), `ResultReadyNotificationIntegrationTest` (notare → 2 evenimente → processor → 2 emailuri; SMTP căzut → rămâne PENDING). Testele rulează cu `app.outbox.enabled=false` (`src/test/resources/application.properties`) și apelează `OutboxProcessor` direct

### 12c. Cache Redis pe agregate (statistici + progres)
`QuizStatsService.getStats` (cache `quizStats`, cheie = id quiz) și `QuizAttemptService.getProgress` (cache `progress`, cheie = email elev) sunt cache-uite în Redis, prefix `mathlms:`, TTL 10 min ca plasă de siguranță. Valorile sunt JSON simplu, fără nume de clase.
- Evict **după commit** (`AfterCommitCacheEvictor`): când o lucrare devine `GRADED` (finalizare de către profesor SAU auto-corectare la submit, dacă quiz-ul n-are subiecte deschise) → se șterg `progress[email]` și `quizStats[quizId]`; orice modificare de subiecte/quiz șterge ambele cache-uri integral (se schimbă punctajul maxim). La rollback nu se șterge nimic
- Redis căzut = cache dezactivat, nu 500 (erorile se loghează și valoarea se recalculează)
- Verificare manuală: după ce deschizi `/admin/quizzes/{id}/stats` apare cheia: `docker exec mathlms-redis redis-cli --scan --pattern "mathlms*"` (`mathlms:quizStats::<id>`); `redis-cli ttl <cheie>` ≈ 600
- Testat pe Redis+Postgres reale: `QuizCachingIntegrationTest` (citit → în cache → notare → evict → citire nouă vede nota)

### 12b. Profesor — statistici pe quiz (`/admin/quizzes/:id/stats`, ca admin)
Buton **Statistici** pe fiecare quiz din `/admin/quizzes`. Doar lucrările **notate** (`GRADED`) contează.
- **Lucrări notate**, **Media** (`13.0 / 15 p (87%)`), **Distribuția notelor** pe 5 intervale de câte 20% (bare), apoi **Pe subiecte**
- La grile: % de elevi care au răspuns corect (o grilă lăsată necompletată contează greșit); subiectul cu cel mai mic procent e marcat „cel mai greu” (când sunt ≥2 grile cu date). La subiectele deschise: punctajul mediu
- Fără nicio lucrare notată → „Niciun elev nu a fost notat încă…”; elev/părinte pe `GET /api/admin/quizzes/{id}/stats` → 403; quiz inexistent → 404
- Calcul: două query-uri agregate pe tot quiz-ul (`findGradedScoresByQuizId`, `findItemStatsByQuizId`), nu câte unul per lucrare

### 13a. Progres în timp (`/progress` pentru elev; pe pagina copilului pentru părinte)
Buton pe Dashboard (elev): **Progresul meu**. Graficul arată doar testele **notate** (`GRADED`), cele mai vechi primele, procent din punctajul maxim al fiecărui test; sub grafic, același lucru ca tabel (Test · Data · Nota `13 / 15 (87%)`).
- API: elev `GET /api/quiz/progress`; părinte `GET /api/parent/children/{id}/progress` (copil străin → 403, ca restul API-ului de părinte)
- Fără teste notate → „Nu există încă teste notate…”. Punctajul maxim vine dintr-un singur query agregat pe toate quiz-urile implicate

### 13. Părinte — copiii mei (`/parent`, ca `parinte@mathlms.local`)
Prerechizit: adminul a legat elevul de părinte la `/admin/links`. Buton pe Dashboard: **Copiii mei**.
- Lista conține DOAR elevii legați de acest cont; fără nimeni legat → „Niciun elev nu este legat încă…”
- **Vezi testele** → încercările copilului (status, dată, puncte) → **Vezi rezultatul** (aceeași vedere ca elevului, formulată pentru părinte: „Răspunsul elevului”)
- Părintele nu vede copilul altui părinte: `/api/parent/children/{id}/attempts` pe un id străin → **403**; la fel pentru un attempt străin pus sub id-ul propriului copil și pentru id-uri inexistente (nu se poate „ghici” ce există). Elev/admin pe `/api/parent/**` → 403; părinte neaprobat → 403
- Un părinte vede în `/content` DOAR clasele în care e înscris vreunul dintre copiii lui (`GET /api/classes` îi întoarce doar acelea; cartea/capitolul/exercițiul altei clase → 404). Fără copii legați → „niciun elev legat”; elevul/părintele nu pot ghici id-uri din alte clase

### Verificare rapidă prin API (ca admin, cu cookie)
Scrierile (POST/PUT/DELETE, în afară de `/api/auth/**`) cer header-ul `X-XSRF-TOKEN` egal cu cookie-ul
`XSRF-TOKEN` (CSRF double-submit), altfel 403.
```bash
# login și salvează cookie-urile (inclusiv XSRF-TOKEN)
curl -s -c /tmp/c.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@mathlms.local","password":"Admin123!"}' -o /dev/null
XSRF=$(grep XSRF-TOKEN /tmp/c.txt | awk '{print $7}')
# creează o clasă
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/admin/classes \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"name":"Clasa test","description":null}'
# coada de corectură
curl -s -b /tmp/c.txt "http://localhost:8080/api/admin/quiz/attempts?status=SUBMITTED"
# listează clasele
curl -s -b /tmp/c.txt http://localhost:8080/api/classes
```

## E. Debugging — unde te uiți când pică ceva

```bash
# DB
docker exec -it mathlms-postgres psql -U mathlms -d mathlms
#   \dt                                       — tabele
#   SELECT id,email,role,status,parent_id FROM users;
#   SELECT * FROM school_classes; SELECT * FROM books; SELECT * FROM chapters;
#   SELECT id,chapter_id,left(statement,40),difficulty,version FROM exercises;
#   SELECT * FROM enrollments;
#   SELECT id,quiz_id,student_id,status,score FROM quiz_attempts;
#   SELECT attempt_id,item_id,selected_option_id,image_key,awarded_points,correct FROM item_responses;

# Backend logs: consola unde rulează spring-boot:run (Spring Security e pe DEBUG)
```

- **Browser DevTools → Network**: apelurile `/api/...` (status + body al răspunsului)
- **Browser DevTools → Console**: erori JS / din `AuthContext`

## Note

- CSRF e activ (cookie double-submit): SPA-ul trimite automat `X-XSRF-TOKEN` din `api.ts`; excepții:
  `/api/auth/**`, `/api/public/**`, `/oauth2/**`, `/login/**`. Un GET autentificat NU trebuie să atingă
  cookie-ul `XSRF-TOKEN` (vezi `CsrfTokenLifecycleTest`).
- Pozele elevilor stau în `backend/uploads/quiz-photos/` (ignorat de git).
- `open-in-view=false` — accesul lazy la relații (ex. `user.parent`) trebuie făcut în tranzacție
  sau via `join fetch` (vezi `UserRepository.findByStatusAndRoleFetchParent`).
- Cookie: `MATHLMS_TOKEN` (HttpOnly, SameSite=Lax, 60 min).
