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
# Backend — 416 teste (necesită Docker pentru Testcontainers)
cd math-lms/backend && ./mvnw test

# Doar suita de conținut (Faza 2)
./mvnw -Dtest='ro.mathlms.content.*' test

# Frontend — `npm run build` include `tsc -b`, deci prinde și erorile de tipuri
cd math-lms/frontend && npm run build
```

Nu există runner de teste FE (fără vitest) — regula tests-first e doar pentru Java.

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

### 13. Părinte — copiii mei (`/parent`, ca `parinte@mathlms.local`)
Prerechizit: adminul a legat elevul de părinte la `/admin/links`. Buton pe Dashboard: **Copiii mei**.
- Lista conține DOAR elevii legați de acest cont; fără nimeni legat → „Niciun elev nu este legat încă…”
- **Vezi testele** → încercările copilului (status, dată, puncte) → **Vezi rezultatul** (aceeași vedere ca elevului, formulată pentru părinte: „Răspunsul elevului”)
- Părintele nu vede copilul altui părinte: `/api/parent/children/{id}/attempts` pe un id străin → **403**; la fel pentru un attempt străin pus sub id-ul propriului copil și pentru id-uri inexistente (nu se poate „ghici” ce există). Elev/admin pe `/api/parent/**` → 403; părinte neaprobat → 403
- Un părinte vede deocamdată TOT conținutul (`/content`) — se restrânge la clasele copiilor într-o fază următoare

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
