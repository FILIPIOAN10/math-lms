# Testing guide

Cum rulezi și testezi math-lms: stack local, date de test, teste automate și fluxuri manuale
per feature. Actualizat la 2026-10-11 (migrări V1–V23).

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

### B2. Set de date de test (≈10 minute, pornind de la zero)

Pornește de la o bază curată (șterge TOT conținutul și quiz-urile, păstrează cele 4 conturi ACTIVE, parola `Admin123!`):

```bash
# PowerShell, din math-lms/
Get-Content -Raw deploy\dev-reset.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms
```

Apoi, logat ca **admin**, construiește datele de mai jos (fiecare pas exersează și un feature):

| # | Unde | Ce creezi |
|---|---|---|
| D1 | Conținut → **Adaugă clasă** | **Clasa a 8-a** și **Clasa a 9-a** (a 9-a rămâne fără elevi, intenționat) |
| D2 | Clasa a 8-a → **Elevi** | înscrie **Ana** și **Radu** (înscriere în masă) |
| D3 | Clasa a 8-a → **Adaugă carte** | **Algebra 8** → capitolul **Fracții** → 2 exerciții cu LaTeX (`$\frac{3}{4}+\frac{1}{8}$`) |
| D4 | **Elevi** | leagă **Maria Parinte → Ana** |
| D5 | **Quiz-uri → Adaugă quiz** | **Fracții — exersare**: toți elevii, fără limită, **„Permite practică” bifat**, **Capitol: Fracții** (pentru harta pe capitole) |
| D6 | în editorul lui → **Importă din text** | lipește blocul **A** |
| D7 | **Adaugă quiz** | **Simulare EN**: Clasa a 8-a, **10 minute**, fără practică, **Capitol: Fracții** → importă blocul **B**; apoi la subiectul 3 alege **Capitol** = un al doilea capitol (ex. adaugă „Procente” în Algebra 8) |
| D8 | **Adaugă quiz** | **Ciornă goală** — fără subiecte |
| D9 | | publică **Fracții — exersare** și **Simulare EN** |
| D10 | Simulare EN → **Dă ca temă** | Clasa a 8-a, termen **mâine** |
| D11 | Fracții — exersare → **Dă ca temă** | Clasa a 8-a, termen **peste 2 ore** |

Ține la îndemână 1–2 poze (JPG) cu o rezolvare scrisă de mână.

Blocul **A**:
```
1. Cât face $\frac{1}{2}+\frac{1}{4}$? (2p)
a) $\frac{2}{6}$
*b) $\frac{3}{4}$
c) $\frac{1}{8}$
R: Aduci la numitorul 4: $\frac{2}{4}+\frac{1}{4}=\frac{3}{4}$
I: Care e numitorul comun?
I: $\frac{1}{2}=\frac{2}{4}$

2. Simplifică $\frac{6}{8}$. (1p)
*a) $\frac{3}{4}$
b) $\frac{2}{3}$
```

Blocul **B**:
```
1. Media aritmetică a numerelor 4, 8 și 12 este: (2p)
a) 6
*b) 8
c) 12

2. Perimetrul unui pătrat cu latura de 5 cm este: (3p)
a) 10 cm
*b) 20 cm

3. Un produs costă 200 lei. După o reducere de 15%, cât costă? (5p)
R: $200 - 30 = 170$ lei (5p)
```

Formatul importului: `*` marchează varianta corectă, `(Np)` dă punctajul, `R:` = rezolvare/barem, `I:` = indiciu (max 5),
un subiect fără variante = subiect deschis (cu poză).

## C. Testare automată

Rulează asta înainte de orice commit.

```bash
# Backend — 870 de teste (necesită Docker pentru Testcontainers)
cd math-lms/backend && ./mvnw test

# Doar suita de conținut (Faza 2)
./mvnw -Dtest='ro.mathlms.content.*' test

# Frontend — `npm run build` include `tsc -b`, deci prinde și erorile de tipuri
cd math-lms/frontend && npm test && npm run build
```

Frontend: **Vitest + Testing Library** (`npm test`, 264 de teste în 37 de fișiere): clientul API (header CSRF doar pe scrieri, refresh silențios la 401 cu o singură rotație pentru cereri paralele, fără refresh la login greșit), `AppShell` + paginile de start pe rol, `TakeQuizPage` (practică, indicii, navigator, dialogul de predare, cronometru, poze), `AttemptResultView` (nota, comentariile profesorului, poza proprie), `AdminGradingPage`, `AdminQuizzesPage` (meniul ⋯, editorul pe URL), `StudentQuizListPage`, `AccountPage` + `EraseAccountPage`, `RegisterPage` (previzualizarea linkului), `AdminLinksPage` (Invită părintele), `ClassInviteDialog`, `ChapterQuizDialog`, `PageHeader`, `RowMenu`, nota 1–10 (`grades.ts`), `RoleRoute`, `LoginPage`. Rulează și în CI. Fluxurile cap-coadă rămân în suita E2E (Selenium).

O rulare izolată poate pica rar în `VerificationTokenServiceTest.rejectsTamperedToken` (test instabil, cunoscut: modifică
ultimele 2 caractere ale semnăturii, care uneori nu schimbă semnătura decodată) — rerulează; fixul e în așteptare.

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
- Confirmările sunt dialogul aplicației (`ConfirmDialog`), nu `window.confirm`: `Page.acceptConfirm()` apasă `[data-testid=confirm-yes]`.
  Fluxul așteaptă și textul „Poză încărcată” după upload — nu-l scoate din UI fără să schimbi `TakeQuizPage.java`.
- În CI suita rulează **manual** (Actions → „E2E (Selenium)” → Run workflow), pe stack-ul de producție (`docker-compose.prod.yml`).
- Logout-ul se face prin butonul din UI, nu `deleteAllCookies()`: cookie-ul de refresh e limitat la
  o cale sub `/api/auth`, iar SPA-ul ar reloga silențios utilizatorul.
- **Datele lăsate de E2E** (quiz-uri „E2E flow/clasa/parinte <număr>”, clase „E2E clasa <număr>” în care e înscrisă Ana)
  încurcă testarea manuală — ex. Ana pare să vadă „toate” quiz-urile de clasă. Curățare (doar baza de **dev**, într-o
  singură tranzacție, păstrează conturile și restul datelor), din `math-lms/`:
  PowerShell `Get-Content -Raw deploy\dev-cleanup-e2e.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms`
  (Git Bash: `... psql -U mathlms -d mathlms < deploy/dev-cleanup-e2e.sql`). Pozele încercărilor șterse rămân în
  `backend/uploads/quiz-photos/` (orfane, inofensive).

## D. Testare manuală, per feature

### 1. Login email/parolă (`/login`, tab „Email și parolă")
- parolă greșită → „Email sau parolă greșite" (401)
- cont pending/respins → mesaj specific (403, ales după `body`)
- admin corect → dashboard

### 2. Guard-uri / rutare (merge și fără backend)
- `/` neautentificat → `/login`
- `/admin/students` (și vechile `/admin/pending`, `/admin/links`, care duc acolo) ca non-admin → redirect acasă; neautentificat → `/login`
- cont autentificat dar non-ACTIVE → `/pending`

### 3. Admin — aprobare / respingere (`/admin/students`, cardul „Conturi de aprobat”, ca admin)
Cardul apare doar cât timp așteaptă cineva (rolul e scris în română: Elev / Părinte / Profesor).
- schimbă rolul din select → **Aprobă** → rândul dispare; în DB `status=ACTIVE` + `role` = ce-ai ales
- **Respinge** → `status=REJECTED`

### 4. Admin — legare părinte (`/admin/students`, ca admin)
- alege un părinte pentru un student → **Leagă** → „Părinte curent" se actualizează; în DB `parent_id` setat
- endpointul: `GET /api/admin/users?role=STUDENT|PARENT`, apoi `POST /api/admin/users/{id}/link-parent`

### 4a. Admin — link general cu aprobare (`/admin/students`, cardul „Link general (cu aprobare)”, jos, ca admin)
Din bara de sus: **Elevi**. Înregistrarea e doar pe bază de invitație — pe `/login` nu există (intenționat) un buton „Creează cont”.
Calea simplă (fără aprobare) e la §18; linkul general rămâne pentru cazurile în care nu știi încă clasa.
- alege **Rol** (Elev / Părinte — nu se pot genera invitații de admin din UI) → **Generează link** → apare „Link de invitație” + „Pentru rolul: …”
- **Copiază** → „Copiat ✓”; dacă browserul refuză clipboard-ul, linkul rămâne selectat și apare „apasă Ctrl+C”
- linkul e valabil **7 zile** și poate fi folosit de mai multe persoane (token semnat, nu se stochează); un link nou nu le invalidează pe cele vechi
- endpointul: `POST /api/admin/invites {"role":"STUDENT"}` → `{role, url}`

### 5. Register + verify email (`/register?token=…`)
- fără token → „Invitație necesară"
- token-ul: din cardul de invitații (§4a) → link `/register?token=…`; deschide-l într-o fereastră privată (altfel ești încă logat ca admin)
- după submit → „Verifică-ți emailul"; linkul de confirmare ajunge pe SMTP-ul real din `.env` — folosește o adresă reală pe care o citești, nu `@mathlms.local`
- după confirmare contul e `PENDING_APPROVAL` cu rolul din invitație → apare la **Elevi** → „Conturi de aprobat” → **Aprobă**

### 6. Forgot / reset parolă (`/forgot-password`, `/reset-password?token=…`)
- mereu „dacă există un cont…" (anti-enumerare); linkul de reset vine pe email

### 7. Google login (tab Google)
- OAuth real; merge doar cu emailuri din `ADMIN_EMAILS` / `ALLOWED_EMAILS`, sau via invite link

### 8. Admin — gestionare conținut (`/admin/content`, ca admin)
Din bara de sus: **Clase**. Navigare arborescentă cu breadcrumb.
- **Adaugă clasă** → dialog (nume + descriere) → apare în listă
- **Deschide** o clasă → **Adaugă carte**; deschide cartea → **Adaugă capitol**; deschide capitolul → **Adaugă exercițiu**
- La exercițiu: enunț + soluție pot conține LaTeX (`$x^2+1$`, `$$\frac{a}{b}$$`) + dificultate
- **Editează** / **Șterge** pe fiecare rând (ștergerea cere confirmare). Blochează ștergerea (409, mesajul spune ce și cum): clasa cu cărți sau cu quiz-uri destinate ei, cartea cu capitole, capitolul cu exerciții. Elevii înscriși **nu** blochează: se dezînscriu odată cu clasa. Un quiz cu încercări ale elevilor nu se poate șterge (pierderea notelor) → „Depublică”
- Reset complet al bazei de dev (doar cele 4 conturi seed, toate ACTIVE; zero clase/quiz-uri): `Get-Content -Raw deploy\dev-reset.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms`
- **Elevi** pe o clasă → dialog roster: alege un elev activ → **Adaugă**; **Scoate** pentru dezînscriere
- **Optimistic locking**: dacă doi admini editează același exercițiu, al doilea „Salvează" dă 409 („a fost modificat de altcineva")

### 9. Elev / oricine activ — răsfoire conținut (`/content`)
Buton pe Dashboard: **Conținut**. Read-only.
- Drill-down Clase → Cărți → Capitole → Exerciții, cu breadcrumb pentru a urca
- Exercițiile arată enunțul randat cu **KaTeX**; **Vezi soluția** dezvăluie soluția (tot KaTeX) + badge de dificultate
- Un cont PENDING nu ajunge aici (guard `STATUS_ACTIVE`)
- **Elev**: vede DOAR clasele în care e înscris (`GET /api/me/classes`, iar `GET /api/classes` îi întoarce tot doar clasele lui). Cartea/capitolul/exercițiul unei alte clase → **404** (nu 403), pe fiecare nivel: `/api/classes/{id}`, `/api/books/{id}`, `/api/chapters/{id}`, `/api/exercises/{id}` și listele lor (`ContentAccess`); neînscris nicăieri → „Nu ești înscris în nicio clasă”. Adminul/părintele văd toate clasele (Step 2.4a)

### 10. Admin — quiz builder (`/admin/quizzes`, ca admin)
- lista: **titlul** deschide editorul; pe fiecare rând **o singură acțiune principală** — „Dă ca temă” (publicat) sau „Publică” (ciornă);
  restul în meniul **⋯** (Statistici, Editează detaliile, Copiază, Depublică, apoi **Șterge** cu roșu, după un separator; merge și din tastatură)
- editorul are adresă proprie **`/admin/quizzes/:id`**: refresh, Back și bookmark rămân pe același quiz; un id inexistent → eroarea + „Înapoi la quiz-uri”
- **Adaugă quiz** te duce direct în editor → **Adaugă subiect**: grilă (pornește cu 4 variante, exact una corectă) sau deschis (punctaj + barem);
  „Salvează și adaugă încă unul” ține dialogul deschis; antetul arată „n subiecte · Total: x p”
- **Importă din text** adaugă un test întreg dintr-o dată (formatul e la §B2)
- **Copiază** (din ⋯) creează o copie editabilă și o deschide; **Publică** e dezactivat pe un quiz fără subiecte
- după ce un elev a dat quiz-ul, punctajele, tipul și varianta corectă sunt **înghețate** (textele se pot corecta); pentru altă variantă → Copiază
- **Publică** — doar quiz-urile publicate apar la elevi
- **Pentru clasa** (în dialogul quiz-ului): „Toți elevii” (implicit) sau o clasă — un quiz de clasă apare DOAR elevilor înscriși în ea; lista adminului arată „Clasa: …” / „Toți elevii”. Un elev care ghicește id-ul unui quiz al altei clase primește 404 (Step 2.4b, migrare V13)

### 11. Elev — dă un test (`/quizzes`, ca `student.activ@mathlms.local`)
Din bara de sus: **Testele mele**.
- temele nepredate apar DOAR la „Teme”, nu și a doua oară la „Teste disponibile”
- un test deja dat arată „✓ Dat · 8 / 11 p · nota 7,55” (sau „Predat — în corectare”), cu **Vezi rezultatul** (principal) și **Dă din nou**
- pagina de start (nu creează nimic): numele testului, descrierea, „3 subiecte · 10 puncte · ⏱ 10 minute” (sau „Fără limită de timp”),
  la teme „Temă — termen: … (peste 2 ore)”
- în timpul testului: bara de jos numerotează subiectele (umplute = cu răspuns), click = sari la subiect;
  „Trimite lucrarea” deschide dialogul: cu subiecte goale → „Ai 2 subiecte fără răspuns — Subiectele 2, 3…” + **Mergi la subiectul 2** / **Trimite oricum**
- subiect deschis: butonul „📷 Încarcă poza rezolvării” → miniatura pozei + „✓ Poză încărcată”; altă poză → „Înlocuiește poza”, miniatura se schimbă
- „Teste disponibile” → **Începe** (sau **Continuă** dacă ai unul în lucru) → **Începe testul**
- bifează o variantă → „x / n răspunsuri salvate” crește; **reîncarcă pagina** → Continuă → variantele și poza revin
- subiect deschis → alege o poză (una mare de pe telefon e micșorată automat la ≤2000 px înainte de upload)
- **Trimite lucrarea** (avertizează dacă ai subiecte fără răspuns) → pagina de rezultat: grile ✓/✗ cu răspunsul
  corect, subiectul deschis „în corectare”, **Arată baremul**
- ca admin sau părinte, `/quizzes` te trimite acasă (RoleRoute)

### 11a. Quiz cronometrat — timer pe server (`/admin/quizzes`, apoi `/quizzes`)
**Ca admin:** la „Adaugă quiz” / „Editează” completează **Limită de timp (minute)** (1–600; gol = fără limită). Lista arată „⏱ 45 min”.
**Ca elev:** în „Teste disponibile” apare „⏱ 45 minute”; pe pagina testului scrie că cronometrul pornește la „Începe testul”.
- bară sus „⏱ Timp rămas: mm:ss” — **chihlimbar** în ultimele 5 minute, **roșie** în ultimul minut (cititorul de ecran anunță o dată la fiecare prag);
  la **0:00** lucrarea se trimite singură cu ce era salvat → pagina de rezultat
- serverul e sursa adevărului: după termen + 30 s grație, orice răspuns/poză e respins cu **409** („Timpul pentru acest test a expirat”);
  `submit` rămâne permis (se notează ce s-a salvat la timp)
- ai închis tab-ul? jobul `AttemptExpiryJob` (la 30 s) trimite încercarea expirată singur — fără browser
- revii pe un test expirat → încercarea veche e trimisă/notată, apoi începe una nouă cu timp întreg
- modificarea limitei unui quiz nu schimbă încercările deja pornite (termenul e copiat la start)

Verificare rapidă fără să aștepți: pornește o încercare, apoi
`docker exec -i mathlms-postgres psql -U mathlms -d mathlms -c "update quiz_attempts set deadline_at = now() - interval '2 minutes' where id = <id>"`
→ un `PUT …/responses/…` dă 409, iar în ≤ ~30–45 s încercarea devine GRADED/SUBMITTED fără nicio acțiune.
Config (opțional): `app.quiz.expiry-job.enabled` (true), `.interval-ms` (30000), `.initial-delay-ms` (20000).

### 11b. Mod practică (E2) — `/admin/quizzes`, apoi `/quizzes`
**Ca admin:** în dialogul quiz-ului bifează **Permite practică** (implicit nebifat). Lista arată „· practică permisă”. Practica *dezvăluie* răspunsurile,
de aceea e opt-in per quiz: bifeaz-o doar pentru materiale de exersare, nu pentru un test care trebuie să rămână corect.
**Ca elev:** pe quiz-urile bifate apare lângă **Începe** butonul **Exersează** (sau **Continuă practica**).
- pagina de start: „Mod practică … nu se acordă notă, nu există cronometru”; bandă violet „Mod practică” sus; **niciun cronometru**, chiar dacă quiz-ul are limită
- bifezi o variantă → imediat „✓ Corect!” / „✗ Greșit”, varianta corectă conturată verde **și marcată ✓**, greșeala roșie **și marcată ✗** (nu doar prin culoare), plus **Rezolvarea** (baremul) randată cu KaTeX; poți schimba răspunsul și feedback-ul se actualizează
- subiect deschis → după poză apare baremul „compară-l cu rezolvarea ta” (nu există verdict automat)
- **Termină practica** → pagina de rezultat: insigna „Practică”, „fără notă”, „Grile corecte: n din m” — fără scor
- reîncarci pagina → „Continuă practica” → răspunsurile și feedback-ul revin
- un test cronometrat și o practică pot fi deschise simultan pe același quiz; fiecare mod își reia propria încercare
- practica **nu apare** în graficul de progres (elev/părinte), în statisticile profesorului, în coada de corectură și nu trimite emailuri
- în **testul** notat nimic nu s-a schimbat: răspunsul se salvează (204, fără corp), niciun feedback până la trimitere
- pe un quiz fără opt-in: `POST /api/quiz/quizzes/{id}/attempts?mode=PRACTICE` → 400 „Acest quiz nu permite modul practică”

Verificare rapidă prin API (ca elev, cu cookie): practică → `PUT …/responses/…` răspunde `200 {"correct":false,"correctOptionId":…,"solution":"…"}`;
test → `204` fără corp.

### 11c. Indicii progresive + rezolvare explicată (E3) — `/admin/quizzes`, apoi practică
**Ca admin:** în dialogul unui subiect (grilă sau deschis): **Indicii progresive** — până la 5 casete, „Adaugă indiciu”, × ca să ștergi; ordinea e cea afișată
(de la vag la concret, LaTeX între `$…$`). Casetele goale se ignoră. Câmpul **Rezolvare explicată / Barem** există acum și pe grile (înainte doar pe cele deschise).
**Ca elev, în practică** (`/quizzes/:id/take?mode=practice`, quiz cu „Permite practică”):
- sub subiect: butonul **Vrei un indiciu? (0/3)** → apare „Indiciul 1 din 3”; apoi **Încă un indiciu (1/3)**…; la ultimul butonul dispare
- indiciile vin **pe rând** (serverul refuză sărirea peste: `PUT …/items/{item}/hints/{n}` cu n > folosite+1 → 400 „Indiciile se dezvăluie pe rând”), nu costă puncte
- reîncarci pagina → indiciile deja dezvăluite revin; **nu** apare niciun „răspuns” sau rezolvare doar pentru că ai cerut un indiciu
- după ce răspunzi, apare rezolvarea explicată (ca la 11b); în rezultat: „Indicii folosite: 2 din 3” pe subiect și în total
- **în testul notat** nu există indicii: ecranul nu primește nici numărul lor (`hintCount` = 0) și `PUT …/hints/1` pe o încercare de test → 400 „doar în modul practică”
- ștergerea unui subiect/quiz șterge și indiciile lui; modificarea subiectului înlocuiește lista de indicii

### 11d. Teme cu termen limită (E7) — `/admin/assignments`, apoi `/quizzes`
**Ca admin:** **Teste** → fila **Teme** → **Dă o temă**: alegi un quiz **publicat**, o clasă și un termen (data+ora ta locală). Lista arată, per temă, „Predate 3/12 · cu întârziere 1 · în lucru 2 · neîncepute 6”.
**Stare** deschide tabelul pe elevi (neînceput / în lucru / predat în corectare / predat și notat, când a predat, nota); **Termen** mută termenul; **Șterge** scoate tema.
- reguli: quiz-ul trebuie publicat; un quiz destinat altei clase nu poate fi dat acestei clase; termenul trebuie să fie în viitor (altfel 400); același quiz nu se dă de două ori aceleiași clase (409 — modifică termenul)
- **Termenul e „moale”**: după el elevul poate preda în continuare, dar predarea apare „cu întârziere”; nu se blochează nimic
- progresul **nu se stochează**, e dedus din încercările elevului: contează doar un **test** (nu o practică) pornit după crearea temei; prima predare e cea care contează (o reluare ulterioară nu face o predare la timp „întârziată”)
- **Ca elev** (înscris în clasa respectivă): pe `/quizzes`, secțiunea **Teme** deasupra „Teste disponibile”, cele nepredate întâi, după termen: insigna „De făcut / În lucru / Termen depășit / Predată / Predată cu întârziere”, „Termen: … (peste 3 zile)”, butonul **Începe / Continuă / Vezi rezultatul**
- ștergerea quiz-ului sau a clasei șterge și temele lui (CASCADE)
- **Reamintire pe email** (implicit OPRITĂ: `NOTIFY_ASSIGNMENT_REMINDER=true`, cere SMTP real): cu ≤ 24 h înainte de termen, **o singură dată per temă**, fiecare elev care n-a predat primește un email cu linkul testului; mutarea termenului reîarmează reamintirea.
  **Părintele legat de elev primește și el o copie** („Ana are de predat tema …”, cu linkul spre pagina copilului), doar dacă copilul n-a predat
  nici la momentul trimiterii; `NOTIFY_ASSIGNMENT_REMINDER_PARENTS=false` = doar elevii. Un părinte dezlegat/șters între timp nu primește nimic. Verificare: `select event_type, status from outbox_event where event_type='ASSIGNMENT_REMINDER_EMAIL'`.
  Config (opțional): `app.assignments.reminder-lead-hours` (24), `.reminder-interval-ms` (900000 = 15 min).

Verificare rapidă prin API (ca admin): `POST /api/admin/assignments {"quizId":…,"schoolClassId":…,"dueAt":"2026-10-12T15:00:00Z"}` → 201 cu rezumatul; ca elev: `GET /api/quiz/assignments`.

### 12. Profesor — corectură (`/admin/grading`, ca admin)
Din bara de sus: **Corectură** (numărul roșu = lucrări de corectat).
- „De corectat” → **Corectează** → grilele (corectate automat), poza elevului (click = mărime completă), baremul
- per subiect deschis: punctaj + **Comentariu pentru elev (opțional)** → **Salvează nota și comentariul** → „✓ notat cu X p”;
  punctaj peste valoarea subiectului → eroare
- **Comentariu pentru toată lucrarea (opțional)** → **Salvează comentariul** → „✓ Comentariu salvat”; merge și pe o lucrare deja
  notată (inclusiv una doar cu grile, notată automat la predare); gol = șterge comentariul; max 2000 de caractere
- **Finalizează nota** se deblochează când toate subiectele deschise au punctaj → dialog „Finalizezi nota?” → lucrarea trece la „Notate”
- lista „Notate” arată „9 / 10 p · nota 9,10”
- ca elev: rezultatul arată „9 / 10 puncte”, „Nota 9,10 · 90%”, **ambele comentarii** (chenar albastru) și propria poză
- **nota** = 1 + 9 × procent (un punct „din oficiu”), cu 2 zecimale: 0% → 1, 50% → 5,50, 100% → 10; quiz fără puncte → fără notă

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
Prerechizit: adminul a legat elevul de părinte la **Elevi** (`/admin/students`). Din bara de sus: **Copiii mei**.
- Lista conține DOAR elevii legați de acest cont; fără nimeni legat → „Niciun elev nu este legat încă…”
- pe **Acasă**, cardul fiecărui copil arată „2 teme de predat · 1 cu termenul depășit” (sau „Nicio temă de predat.”)
- **Vezi testele** (pagina copilului, titlul = numele copilului) → secțiunea **Teme**: temele clasei cu insigna (De făcut / În lucru / Termen depășit /
  Predată / Predată cu întârziere) și termenul — **fără** butoane Începe/Continuă (părintele nu dă testul); la cele predate **Vezi rezultatul**.
  API: `GET /api/parent/children/{id}/assignments` (copil străin → 403)
- apoi încercările copilului (status, dată, „x / max p · nota n”) → **Vezi rezultatul** (aceeași vedere ca elevului, formulată pentru părinte: „Răspunsul elevului”), **cu comentariile profesorului, dar fără poza** (nu există endpoint de părinte pentru poze)
- Părintele nu vede copilul altui părinte: `/api/parent/children/{id}/attempts` pe un id străin → **403**; la fel pentru un attempt străin pus sub id-ul propriului copil și pentru id-uri inexistente (nu se poate „ghici” ce există). Elev/admin pe `/api/parent/**` → 403; părinte neaprobat → 403
- Un părinte vede în `/content` DOAR clasele în care e înscris vreunul dintre copiii lui (`GET /api/classes` îi întoarce doar acelea; cartea/capitolul/exercițiul altei clase → 404). Fără copii legați → „niciun elev legat”; elevul/părintele nu pot ghici id-uri din alte clase

### 14. Navigare + pagini de start pe rol (toate rolurile)
- bara de sus (sticky) pe orice pagină după login, **în afară de pagina în care dai un test** (acolo cronometrul e sus)
- linkurile fiecărui rol: **Elev** Acasă · Testele mele · Progresul meu · Conținut; **Părinte** Acasă · Copiii mei · Conținut;
  **Profesor** Acasă · Clase · Teste · Corectură · Elevi (T6) — pagina curentă e evidențiată; **Teste** rămâne evidențiat și pe Teme
  (filele „Quiz-uri | Teme” sus), **Elevi** și pe pagina unui elev, **Clase** și pe harta pe capitole
- la profesor: număr roșu pe **Corectură** (lucrări de corectat) și **Elevi** (conturi de aprobat), recitit la fiecare schimbare de pagină
- dreapta: numele, rolul în română (Profesor/Elev/Părinte — niciodată `ADMIN`), butonul **Ieșire**
- pe telefon linkurile trec pe un rând separat și se derulează orizontal
- titlul tab-ului se schimbă pe fiecare pagină („Corectură · Math LMS”, „Rezultat · Simulare EN · Math LMS”)
- **Acasă — elev:** „De făcut” (temele nepredate, cu Începe/Continuă), „Teste începute” (teste neterminate care nu sunt teme),
  „Rezultate” (ultima notă `x / max puncte · nota n`, câte lucrări sunt în corectare)
- **Acasă — profesor:** 3 plăci cu numere (lucrări de corectat, conturi de aprobat, teme deschise) care duc la pagina respectivă;
  „Termene” (temele deschise + cele depășite în ultimele 7 zile la care lipsesc elevi, cu „x / y predate”; clasă fără elevi →
  „Niciun elev în clasă”); „De corectat” (cine așteaptă nota)
- **Acasă — părinte:** un card per copil cu ultima notă și câte teste sunt în corectare; fără copii legați → explicația
- toate confirmările (ștergeri, predarea lucrării, finalizarea notei) sunt dialogul aplicației, nu fereastra browserului;
  Escape / click în afară = Anulează

### 15. Contul meu + GDPR (`/account`, orice rol)
Din bara de sus: click pe numele tău („Contul meu” pe telefon).
- **Profil**: nume, email, rolul în română, metoda de conectare („Email și parolă”, „Google” sau ambele)
- **Parola** (cont cu parolă): parola actuală + nouă (min. 8, max. 72) + repetare → „✓ Parola a fost schimbată”; **celelalte dispozitive
  sunt deconectate**, acesta rămâne conectat. Parolă actuală greșită → „Parola actuală nu e corectă.” (400); aceeași parolă → „trebuie să fie
  diferită”; parolele noi diferite → mesaj fără cerere la server. Limită: 5 încercări / 15 min / utilizator (429)
- cont doar-Google: fără formular, doar **Setează o parolă** → `/forgot-password`
- **Dispozitive conectate**: fiecare sesiune (browser, IP, ultima folosire), „Acest dispozitiv” marcat; **Deconectează** pe altele;
  **Deconectează-te de peste tot** (cu confirmare) → te scoate și de aici
- **Datele tale** → **Descarcă datele mele** → `datele-mele-mathlms.zip` (profil, clase, încercări cu răspunsuri, note, comentariile
  profesorului, pozele) — `GET /api/users/gdpr/export`
- **Șterge contul** (elev/părinte): parola (lipsește la doar-Google) → confirmare → „Ți-am trimis un email” (cere **SMTP real** și o adresă
  pe care o citești); parolă greșită → „Parola nu e corectă.”. Limită: 3 cereri / oră
- linkul din email duce la **`/erase-account?token=…`**: deschiderea NU șterge nimic (scannerele de email deschid linkuri), abia
  **Șterge definitiv contul** o face → „Contul a fost șters” + delogare. Token expirat/folosit → „Linkul a expirat…”; fără token → „Link invalid”
- **profesorul** nu-și poate șterge contul din aplicație (explicat pe pagină; serverul răspunde 409) — fără el nimeni nu mai corectează/aprobă
- ce face ștergerea: anonimizează contul (email `erased-…@erased.invalid`, fără parolă), șterge înscrierile, pozele și **comentariile
  profesorului**, revocă toate sesiunile; notele rămân în statistici, fără nume

### 16. Harta pe capitole (E6) — profesor, elev, părinte
**Etichetare (profesor):** în dialogul quiz-ului **Capitol (pentru harta pe capitole)** — opțional; la un quiz de clasă apar doar capitolele
clasei (serverul refuză altul: „Quiz-ul e pentru …, dar capitolul … e din …”). În dialogul unui subiect **Capitol**: „Ca la quiz (Fracții)”
(moștenește) sau alt capitol (teste mixte). Editorul arată „Capitol: …” sus și o etichetă albastră pe subiectele cu capitol propriu.
Eticheta se poate schimba **și după ce elevii au dat quiz-ul** (nu schimbă note, doar harta). Ștergerea unui capitol doar scoate eticheta.
**Calcul** (deciziile tale): teste notate **și** practică; pentru fiecare întrebare contează **doar ultimul răspuns** al elevului;
nivel = puncte obținute / puncte posibile pe întrebările capitolului. Un test predat numără și întrebările lăsate goale (0);
un răspuns deschis încă necorectat nu contează până la notare; un test început și nepredat nu contează; practica contează pe loc.
Culori: **Stăpânit** ≥ 80%, **În lucru** 50–79%, **De recapitulat** < 50%, **Fără date** (gri) — mereu cu procentul și cuvântul, nu doar culoarea.
- **Profesor:** Conținut → pe rândul clasei **Pe capitole** → `/admin/classes/:id/mastery`: tabel elevi × capitole (grupate pe cărți), fiecare
  celulă „73%” colorată, ultimul rând **Media clasei** (media elevilor care au date). Pe telefon tabelul se derulează lateral, numele rămân fixe.
  Fără capitole → explicație; fără date → „Leagă quiz-urile de capitole…”
- **Elev:** **Progresul meu** → secțiunea **Pe capitole** (toate capitolele claselor lui, grupate pe carte; bară + „85% · Stăpânit” + „4 întrebări”)
- **Părinte:** pagina copilului → **Pe capitole** (aceeași vedere)
- Test rapid: Ana greșește 1. la Simulare EN (test) → Fracții scade; apoi exersează Fracții — exersare și răspunde corect → Fracții urcă imediat
- API: elev `GET /api/quiz/mastery`; părinte `GET /api/parent/children/{id}/mastery` (copil străin → 403); profesor
  `GET /api/admin/classes/{id}/mastery` și `GET /api/admin/chapters` (lista pentru etichetare); elev pe `/api/admin/...` → 403

### 17. Recapitulare zilnică — repetiție spațiată (E4)
**Reguli (deciziile tale):** intră în recapitulare **orice grilă** la care elevul a răspuns într-o **practică** sau într-un **test predat**
(niciodată dintr-un test început și nepredat, niciodată un subiect deschis). **Cutii Leitner**: răspuns corect = cutia următoare
(1 → 3 → 7 → 14 → 30 de zile), greșit = înapoi în cutia 1 (revine mâine); un răspuns corect *înainte* de termen nu urcă cutia (repetarea
unei practici nu sare cutii). **Maxim 10 pe zi**, cele mai vechi primele; un quiz depublicat iese din recapitulare. Zilele sunt ale școlii
(Europe/Bucharest). Nimic despre coadă nu se stochează: cutia și termenul fiecărei întrebări se recalculează din răspunsuri (V22 = doar
răspunsurile din recapitulare). Răspunsurile din recapitulare contează și pe **harta pe capitole** (ca practica) și apar în **exportul GDPR**.
- **Elev:** meniul **Recapitulare** (`/review`): „Azi: 2 / 6 · Seria: 3 zile 🔥 · Stăpânite: 1 din 8”; o întrebare odată („cutia 2 din 5”),
  **Verifică** → ✓/✗ pe variante + rezolvarea + „Urcă în cutia 3 · revine pe 18 oct.” → **Următoarea** / **Termină** → „Gata pe azi! 🎉”.
  Fără nimic încă: „apar aici după ce exersezi sau predai un test”; la zi: „Nimic de recapitulat azi”. Pe **Acasă**: „6 întrebări de
  recapitulat azi · seria 2 zile” + **Începe recapitularea** (sau „Gata pe azi ✓”); cardul lipsește până există întrebări
- **Părinte:** pe cardul copilului: „Recapitulare: făcută azi ✓ · seria 4 zile” / „mai are 6 azi”
- **Profesor:** Conținut → **Pe capitole** → sub tabel, **Recapitulare zilnică**: Elev · Azi (făcute / de făcut) · Seria · Stăpânite · Ultima
- Test rapid (cu B2): Ana exersează Fracții — exersare (greșește 1., nimerește 2.) → mâine 1. e în recapitulare, 2. peste 3 zile.
  Ca să nu aștepți o zi, mută practica în trecut (ziua vine din ceasul serverului):
  `docker exec -i mathlms-postgres psql -U mathlms -d mathlms -c "update quiz_attempts set started_at = started_at - interval '1 day' where mode='PRACTICE'"`
- API: elev `GET /api/quiz/review/today`, `POST /api/quiz/review/items/{itemId}/answer {"optionId":…}` (întrebare nescadentă azi → 409);
  părinte `GET /api/parent/children/{id}/review`; profesor `GET /api/admin/classes/{id}/review`

### 18. Invitație pe clasă și pentru părinte — fără aprobare (T1)
**De ce:** înainte, un elev nou cerea 6 pași pe 3 pagini (link → cont → email → **aprobi** → **înscrii** → **legi părintele**).
Acum linkul știe unde intră omul: după confirmarea emailului contul e **activ**, **înscris în clasă** / **legat de elev** — profesorul nu mai face nimic.
**Reguli (decizia ta: „plafonat + revocabil”):** link stocat în baza de date (V23 `invite_links`), valabil **7 zile**, cu un număr maxim
de folosiri (clasă: implicit **30**, 1–200; părinte: **1**), oprit oricând cu **Oprește linkul**. Folosirea se ia atomic la înregistrare
(două persoane simultan nu pot depăși plafonul); dacă emailul e deja folosit, folosirea se dă înapoi. Cine s-a înregistrat **înainte**
de oprire/expirare își termină intrarea. Linkurile vechi „pe rol” (§4a) merg ca înainte — contul așteaptă aprobarea.
- **Profesor — elevi:** Conținut → pe rândul clasei **Invită elevi** → „Câți elevi îl pot folosi” (30) → **Generează link nou** → **Copiază**.
  Lista arată fiecare link activ: „Folosit 2 / 30 · expiră …”, „plin — generează altul” când s-a umplut, **Oprește linkul**
- **Profesor — părinte:** Elevi → tabelul „Elevii” → **Invită părintele** pe un elev fără părinte → link de o singură folosință
  sub rând. Un link nou îl anulează pe cel nefolosit. Elev cu părinte deja legat → butonul lipsește (serverul: 409 „are deja un părinte legat”)
- **Elevul / părintele** deschide linkul (fereastră privată): sus „**Te alături clasei Clasa a 8-a**” (sau „Cont de părinte”) → completează →
  „Deschide-l și contul tău e gata” → linkul din email → „Email confirmat și contul e activ” → login direct pe pagina de start (nu `/pending`),
  clasa apare la Conținut / copilul la „Copiii mei”
- **Link mort** (oprit, expirat, plin, inexistent): pagina spune motivul **înainte** de formular („Link indisponibil — … a fost oprit de profesor”)
- **Clasa ștearsă** între înregistrare și confirmare → contul ajunge la „Conturi de aprobat” (ca înainte), nu se pierde
- **Google** printr-un link de clasă/părinte: contul e activ și înscris/legat imediat (Google a verificat deja emailul)
- Confirmarea emailului cere SMTP-ul real din `.env` și o adresă pe care o citești (ca la §5); fluxul complet fără email e acoperit
  de `InviteLinkIntegrationTest` (link → înregistrare → confirmare → activ + înscris / legat; plafon, oprire, expirare)
- API: profesor `GET|POST /api/admin/classes/{id}/invites` (`{"maxUses":25}` opțional), `POST /api/admin/users/{elevId}/parent-invite`,
  `DELETE /api/admin/invite-links/{id}`; public `GET /api/public/invites/preview?token=…` → `{role, className, direct}` sau **410** cu motivul
- Verificare în DB: `select id, kind, uses, max_uses, expires_at, revoked_at from invite_links order by id desc;`

### 19. Quiz din capitol (T2)
**De ce:** exercițiile din Conținut și subiectele din quiz-uri erau două bănci separate — scriai totul de două ori.
- Conținut → clasă → carte → capitol cu exerciții → **Creează quiz din capitol** (dezactivat când capitolul n-are exerciții)
- dialogul: titlu („Fracții — test”), toate exercițiile bifate (Selectează / Deselectează tot), **Puncte pe subiect** (1–100) → **Creează quiz (n)**
- se deschide editorul `/admin/quizzes/:id` pe o **ciornă**: pentru clasa capitolului, cu **Capitol** = capitolul (harta E6), fiecare exercițiu =
  **subiect deschis** (poză) cu soluția exercițiului ca **barem**, în ordinea din capitol (nu în ordinea bifelor)
- e o **copie**: modificările din quiz nu ating exercițiile și invers; o grilă se face din editor (alt tip de subiect → adaugă-l nou)
- API: `POST /api/admin/chapters/{id}/quiz {"title":…,"exerciseIds":[…],"points":2}` → 201 + rezumatul quiz-ului; exercițiu din alt capitol → 400
- Teste: `QuizFromChapterIntegrationTest`, `QuizAdminServiceTest`, `ChapterQuizDialog.test.tsx`

### 20. Pagina unui elev (T5) — `/admin/students/:id`, ca admin
**De ce:** ca să vorbești despre Ana nu mai treci prin 4 pagini (Teme, Corectură, Pe capitole, Recapitulare).
- Ajungi aici din: **Elevi** (numele din tabel), tabelul **Pe capitole** al clasei, starea unei teme (**Teste → Teme → Stare**), lista
  **Elevi** a unei clase (Clase → Elevi)
- sus: **Clase** (link spre harta clasei), **Părinte** (nume + email, sau „niciunul legat · Invită părintele”), rândul de **Recapitulare**
  („făcută azi ✓ · seria 3 zile”, apare după prima întrebare în recapitulare)
- apoi **Teme** (aceleași insigne ca la elev, fără Începe/Continuă), **Pe capitole**, **Progres în timp**, **Lucrări**
- fiecare lucrare predată are **Corectează** (dacă așteaptă nota) sau **Vezi lucrarea** → `/admin/grading?attempt=<id>`: ecranul de corectură
  se deschide direct pe ea; „Corectură” din breadcrumb te întoarce la coadă
- un id care nu e de elev (părinte, profesor, inexistent) → 404 „Nu există elevul …”
- API: `GET /api/admin/students/{id}` (+ `/attempts`, `/assignments`, `/progress`, `/mastery`, `/review`); părinte/elev → 403
- Teste: `AdminStudentIntegrationTest`, `AdminStudentPage.test.tsx`

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
