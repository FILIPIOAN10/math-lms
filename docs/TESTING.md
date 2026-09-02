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
# Backend — 217 teste (necesită Docker pentru Testcontainers)
cd math-lms/backend && ./mvnw test

# Doar suita de conținut (Faza 2)
./mvnw -Dtest='ro.mathlms.content.*' test

# Frontend — `npm run build` include `tsc -b`, deci prinde și erorile de tipuri
cd math-lms/frontend && npm run build
```

Nu există runner de teste FE (fără vitest) — regula tests-first e doar pentru Java.

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

### Verificare rapidă prin API (ca admin, cu cookie)
```bash
# login și salvează cookie-ul
curl -s -c /tmp/c.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@mathlms.local","password":"Admin123!"}' -o /dev/null
# creează o clasă
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/admin/classes \
  -H "Content-Type: application/json" -d '{"name":"Clasa test","description":null}'
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

# Backend logs: consola unde rulează spring-boot:run (Spring Security e pe DEBUG)
```

- **Browser DevTools → Network**: apelurile `/api/...` (status + body al răspunsului)
- **Browser DevTools → Console**: erori JS / din `AuthContext`

## Note

- CSRF e dezactivat (auth prin cookie JWT stateless), deci POST-urile nu au nevoie de token.
- `open-in-view=false` — accesul lazy la relații (ex. `user.parent`) trebuie făcut în tranzacție
  sau via `join fetch` (vezi `UserRepository.findByStatusAndRoleFetchParent`).
- Cookie: `MATHLMS_TOKEN` (HttpOnly, SameSite=Lax, 60 min).
