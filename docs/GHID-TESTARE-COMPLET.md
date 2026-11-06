# Ghid complet de testare — math-lms

Toate funcționalitățile aplicației, de verificat de mână, în ordinea riscului. Actualizat pe 2026-10-29 pe `main` = `a4e2581`
(migrări V1–V45). Inventarul vine din cod: 36 de rute în `frontend/src/App.tsx`, 36 de controllere, 11 joburi programate.

Pentru detaliile vechi (date de test pas cu pas, emailuri reale, observabilitate) rămâne `docs/TESTING.md` — aici trimit la
el cu „TESTING §N”. Ghidul ăsta e lista completă; bifezi `[ ]` → `[x]`.

**Cum citești un pas:** `[ ]` acțiune → **Aștepți:** ce trebuie să vezi. Dacă vezi altceva, notează pasul (ex. `C3`) și ce a apărut.

**Risc:** 🔴 critic (cont, drepturi, note, date care se pierd) · 🟠 ridicat (fluxul principal elev/profesor) · 🟡 mediu · ⚪ scăzut.

---

## 0. Pregătire

### 0.1 Pornire (3 ferestre PowerShell în `math-lms/`)

PowerShell 5 nu are `&&`: întâi `cd`, apoi comanda, pe rânduri separate.

| Fereastra | Comandă | Gata când |
|---|---|---|
| 1 | `docker compose up -d postgres redis` | containerele `Running` |
| 2 | `cd backend` apoi `.\mvnw spring-boot:run` | `Started BackendApplication` |
| 3 | `cd frontend` apoi `npm run dev` | `Local: http://localhost:5173/` |

Opțional, pentru secțiunea M (indicii AI): fereastra 4, `cd tutor && uv run uvicorn …` (vezi `tutor/CHECKLIST.md`).

### 0.2 `backend/.env` pentru testare locală

| Cheie | Valoare pentru testare | De ce |
|---|---|---|
| `COOKIE_SECURE` | `false` | pe `http://localhost` cookie-ul `Secure` nu se trimite → ai fi delogat mereu |
| `TWO_FACTOR_REQUIRED_FOR_ADMIN` | `false` pentru E2E și testarea zilnică; `true` doar când testezi B4.1 / B4.6 | cu `true`, `admin@` fără 2FA primește 403 `TWO_FACTOR_SETUP_REQUIRED` și toate E2E de profesor pică |
| `AI_IMPORT_ENABLED` / `AI_PROVIDER` | `true` / `fake` | import AI fără cost (secțiunea L) |
| `AI_GRADING_ENABLED` / `AI_GRADING_PROVIDER` | `true` / `fake` | barem + propuneri AI fără cost (I6) |
| `TUTOR_ENABLED` | `true` doar cu tutorul pornit | butonul „Sugerează indicii (AI)” (F9) |

După orice schimbare în `.env`: Ctrl+C în fereastra 2 și pornești din nou.

### 0.3 Date de start

```powershell
# PowerShell, din math-lms/ — adaugă conturile de test dacă lipsesc (nu șterge nimic; parola Admin123!)
Get-Content -Raw deploy\e2e-seed.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms
```

⚠️ `deploy\dev-reset.sql` golește **tot** conținutul și șterge **orice cont** care nu e în lista de mai jos, inclusiv
conturile tale reale de admin. Rulează-l doar când vrei intenționat o bază goală.

| Cont | Rol | Folosit în |
|---|---|---|
| `admin@mathlms.local` | ADMIN | profesor principal, aprobări, jurnal, capitole EN (fără 2FA) |
| `admin2fa@mathlms.local` | ADMIN | doar pentru 2FA (B4); `TwoFactorE2eTest` îi activează 2FA → îl resetezi cu `dev-cleanup-e2e.sql` |
| `profesor@mathlms.local` (Ion Profesor) | TEACHER | izolare profesor (D), folosit de `TeacherScopeE2eTest` |
| `student.activ@mathlms.local` (Ana) | STUDENT | elev |
| `parinte@mathlms.local` (Maria) | PARENT | părinte |
| `student.nou@mathlms.local` (Radu) | cerut STUDENT, PENDING | aprobare |
| *(creat în C1)* `profesor2@…` | TEACHER | izolare între profesori (D) |

Ține deschise **3 ferestre de browser separate** (normal, Incognito, alt browser) — câte una pe rol, ca să nu-și strice
cookie-urile între ele.

---

## 1. Testele automate (înainte de testarea manuală)

```powershell
cd backend;  .\mvnw test                    # ~1100 teste, cu Testcontainers (Docker pornit)
cd frontend; npm test; npm run build        # Vitest ~600 teste + tsc
cd tutor;    uv run pytest -q               # tutorul (Postgres pe :5433)
cd backend;  .\mvnw test "-Dgroups=e2e" "-DexcludedGroups="   # 16 E2E Selenium, pe stack-ul pornit (0.1)
# un singur E2E, cu browserul vizibil:
.\mvnw test "-Dtest=QuizReloadE2eTest" "-Dgroups=e2e" "-DexcludedGroups=" "-De2e.headless=false"
```

- [ ] toate verzi → **Aștepți:** `BUILD SUCCESS`, `Tests … passed`, `npm run build` fără erori.
- [ ] **înainte și după** E2E: curăță datele lăsate (altfel încurcă testarea manuală, iar `TwoFactorE2eTest` pică a doua oară):
  `Get-Content -Raw deploy\dev-cleanup-e2e.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms`

Avertismentele `Unable to find CDP implementation matching 156` sunt inofensive.

### Ce acoperă deja E2E (pașii marcați 🤖 nu mai trebuie făcuți de mână la fiecare rundă)

| Test E2E | Pași din ghid |
|---|---|
| `LoginE2eTest` (2 teste) | B1.1, B1.2 |
| `TwoFactorE2eTest` | B4.2–B4.4 |
| `SessionsE2eTest` | B5.1, B5.2 |
| `AuthorizationE2eTest` | A1, A2, A3, A4, A7 |
| `TeacherScopeE2eTest` | D1, D2, D5 |
| `ClassInviteLinkE2eTest` | C2 (fără crearea contului — ar trimite email) |
| `PublishAndAssignE2eTest` | F7 (publicare), H1 |
| `QuizFlowE2eTest`, `QuizVisibilityE2eTest` | G1, vizibilitatea testelor pe clasă |
| `QuizReloadE2eTest` | G2 |
| `OfflineAnswersE2eTest` | G3 |
| `DoubleSubmitE2eTest` | G4 |
| `GradeByItemAndJournalE2eTest` | I2 (cifre), I4, I5, I6 (finalizare), N1 |
| `NewGradeAndMistakesE2eTest` | G9, G10, G11 |
| `ParentE2eTest` | K1, K2 |

Instabil cunoscut: `VerificationTokenServiceTest.rejectsTamperedToken` pică rar → rerulezi.

---

## A. Matricea de drepturi 🔴

Cel mai important test: un rol nu ajunge unde nu are voie. Scrii adresa direct în bara browserului.

| Pagina | ADMIN | TEACHER | STUDENT | PARENT | fără cont |
|---|---|---|---|---|---|
| `/admin/students`, `/admin/content`, `/admin/quizzes`, `/admin/grading` | ✅ | ✅ (doar ale lui) | ⛔ | ⛔ | → `/login` |
| `/admin/journal` | ✅ | ⛔ | ⛔ | ⛔ | → `/login` |
| `/admin/en-chapters`, `/admin/en-chapters/legaturi` | ✅ | ⛔ | ⛔ | ⛔ | → `/login` |
| `/quizzes`, `/review`, `/progress` | — | — | ✅ | ⛔ | → `/login` |
| `/parent`, `/parent/children/:id` | — | — | ⛔ | ✅ (doar copiii lui) | → `/login` |
| `/capitole`, `/content`, `/account` | ✅ | ✅ | ✅ | ✅ | → `/login` |
| `/login`, `/register`, `/forgot-password` | publice | | | | ✅ |

- [ ] **A1** 🤖 Ca Ana, deschide `/admin/grading` → **Aștepți:** nu vezi pagina de corectură (redirect/„fără acces”), nu o listă goală.
- [ ] **A2** 🤖 Ca Maria, deschide `/quizzes` → **Aștepți:** blocat.
- [ ] **A3** 🤖 Ca Maria, deschide `/parent/children/<id-ul unui elev care NU e copilul ei>` → **Aștepți:** „Acesta nu e copilul tău”, fără numele copilului și fără secțiuni goale (Pe capitole etc.).
- [ ] **A4** 🤖 Ca Ana, deschide `/quizzes/attempts/<id-ul încercării altui elev>/result` → **Aștepți:** „Nu am găsit ce cauți — poate a fost șters sau nu ai acces la el.” (IDOR), fără titlul testului.
- [ ] **A5** Delogat, deschide `/admin/quizzes` → **Aștepți:** `/login`; după login revii unde voiai.
- [ ] **A6** Adresă inexistentă `/ceva-gresit` → **Aștepți:** pagina 404 cu link spre Acasă.
- [ ] **A7** 🤖 API direct (DevTools → Console, ca Ana): `fetch('/api/admin/audit-log').then(r=>r.status)` → **Aștepți:** `403`.
- [ ] **A8** Meniul fiecărui rol: ADMIN = Acasă/Clase/Teste/Corectură/Elevi; TEACHER = la fel, fără numărul de aprobări;
  STUDENT = Acasă/Testele mele/Recapitulare/Progresul meu/Capitole EN/Conținut; PARENT = Acasă/Copiii mei/Capitole EN/Conținut.

---

## B. Autentificare și cont 🔴

### B1. Login / logout
- [ ] **B1.1** 🤖 Login corect (email + parolă) → **Aștepți:** Acasă pe rolul tău.
- [ ] **B1.2** 🤖 Parolă greșită → **Aștepți:** mesaj generic, *același* ca pentru email inexistent (nu dezvăluie dacă există contul).
- [ ] **B1.3** Logout din meniu → apeși Înapoi în browser → **Aștepți:** nu revii în cont.
- [ ] **B1.4** Rate limit: 6 loginuri greșite într-un minut → **Aștepți:** la al 6-lea, „prea multe încercări” (429), revine după 1 min.
- [ ] **B1.5** Sesiune lungă: lasă tab-ul deschis peste durata tokenului (sau șterge cookie-ul de acces din DevTools → Application,
  păstrând refresh-ul) și dă click → **Aștepți:** continui fără să fii delogat (refresh silențios).

### B2. Înregistrare + confirmare email
- [ ] **B2.1** `/register` fără link de invitație → **Aștepți:** cont în așteptare → după confirmarea emailului, pagina `/pending`.
- [ ] **B2.2** Linkul de confirmare din email → **Aștepți:** `/verify-email` cu succes; al doilea click pe același link → refuzat.
- [ ] **B2.3** Parolă slabă / email invalid în formular → **Aștepți:** eroare lângă câmp, fără cerere trimisă.
- [ ] **B2.4** Rate limit: 6 înregistrări în aceeași oră de pe același IP → **Aștepți:** 429.

### B3. Parolă uitată / schimbată
- [ ] **B3.1** `/forgot-password` cu email existent și cu unul inexistent → **Aștepți:** același mesaj în ambele cazuri.
- [ ] **B3.2** Linkul din email → `/reset-password` → parolă nouă → **Aștepți:** loginul merge doar cu cea nouă; linkul nu mai merge a doua oară.
- [ ] **B3.3** Contul meu → Parola: parola veche greșită → eroare; corectă → schimbată; sesiunile de pe celelalte dispozitive dispar din „Dispozitive conectate” (acolo ești delogat cel
  târziu într-o oră, când expiră cookie-ul de acces).
- [ ] **B3.4** Login cu Google (tab Google) → **Aștepți:** intri în contul cu același email (merge doar pe `localhost`, nu pe IP din rețea).

### B4. Autentificare în doi pași (2FA, V43) — admin/profesor

Folosește `admin2fa@mathlms.local`, nu `admin@` (E2E-urile au nevoie de `admin@` fără 2FA). B4.1 și B4.6 cer
`TWO_FACTOR_REQUIRED_FOR_ADMIN=true` + backend repornit; pune-l înapoi pe `false` după.
- [ ] **B4.1** Admin fără 2FA (cu `TWO_FACTOR_REQUIRED_FOR_ADMIN=true`) → **Aștepți:** cardul „Activează autentificarea în doi
  pași” în loc de paginile de profesor; doar „Contul meu” merge.
- [ ] **B4.2** 🤖 Contul meu → scanezi QR-ul cu Google/Microsoft Authenticator → codul de 6 cifre → **Aștepți:** codurile de
  recuperare, afișate o singură dată → „Le-am salvat”. Notează 2 coduri.
- [ ] **B4.3** 🤖 Logout → login → **Aștepți:** se cere codul; cod greșit → refuzat; cod corect → intri.
- [ ] **B4.4** 🤖 Login cu un cod de recuperare → intri; **același** cod a doua oară → refuzat.
- [ ] **B4.5** 11 coduri greșite într-un minut → 429.
- [ ] **B4.6** Dezactivare 2FA (dacă ești admin cu 2FA obligatoriu) → **Aștepți:** paginile de profesor se blochează din nou.

### B5. Dispozitive conectate
- [ ] **B5.1** 🤖 Loghează-te ca admin în 2 browsere → Contul meu → Dispozitive conectate → **Aștepți:** 2 sesiuni.
- [ ] **B5.2** 🤖 Revocă sesiunea celuilalt browser → acolo dai click → **Aștepți:** delogat.
- [ ] **B5.3** „Deconectează-te de peste tot” → **Aștepți:** toate browserele delogate.

### B6. GDPR — datele tale și ștergerea contului
- [ ] **B6.1** Contul meu → Datele tale → cere exportul → **Aștepți:** email cu link; descarci un ZIP cu `manifest.json`
  (datele tale) și folderul `photos/` (pozele tale). Linkul folosit a doua oară / după expirare → refuzat.
- [ ] **B6.2** Ca Ana: Șterge contul → parola → email de confirmare → click → **Aștepți:** contul nu mai poate face login;
  la profesor, notele ei rămân, dar fără nume (anonimizate). **Fă asta pe un cont de test creat special, nu pe Ana.**
- [ ] **B6.3** Șterge contul unui părinte → **Aștepți:** copilul rămâne neatins, doar legătura dispare.

---

## C. Invitații și conturi noi 🔴

- [ ] **C1** (ADMIN) Elevi → „Invitații pentru profesori” → generezi link → deschis Incognito → înregistrare `profesor2@…` →
  **Aștepți:** cont TEACHER după confirmarea emailului; linkul e de o singură utilizare (a doua deschidere → indisponibil).
- [ ] **C2** 🤖 (ADMIN/TEACHER) Clase → rândul clasei → **Invită elevi** → link pentru N elevi → înregistrare → **Aștepți:**
  elevul intră direct în clasă, fără aprobare; pagina de înregistrare spune „Te alături clasei …”.
- [ ] **C3** Oprește linkul clasei → aceeași adresă → **Aștepți:** „Link indisponibil”.
- [ ] **C4** Pe pagina elevului → **Invită părintele** → părintele se înregistrează → **Aștepți:** îl vede pe elev la „Copiii mei”.
- [ ] **C5** (ADMIN) Link general cu aprobare → cont nou → **Aștepți:** apare la „Conturi de aprobat”; Aprobă ca STUDENT →
  Radu poate intra; Respinge (pe alt cont) → nu poate intra. (detalii: TESTING §3, §4a)
- [ ] **C6** (ADMIN) Leagă manual un părinte de un elev → Maria îl vede. (TESTING §4)
- [ ] **C7** (TEACHER) Încearcă să aprobi un cont → **Aștepți:** nu ai cardul / API-ul răspunde 403 (aprobarea e doar a adminului).

---

## D. Profesori separați (TEACHER, V40) 🔴

Pregătire: profesor2 din C1, cu 2FA dacă e cerut. Adminul are clasa „7A” și un test „T-admin”.

- [ ] **D1** 🤖 Ca profesor2 → Clase, Teste, Corectură, Elevi → **Aștepți:** nu vede nimic al adminului (nici 7A, nici T-admin, nici elevii lui).
- [ ] **D2** 🤖 Profesor2 creează clasa „8B” și testul „T-prof2” → ca admin → **Aștepți:** adminul le vede pe toate (și pe ale lui profesor2).
- [ ] **D3** Profesor2, prin API, deschide `/api/admin/quizzes/<id T-admin>` → **Aștepți:** 403/404, nu datele.
- [ ] **D4** (ADMIN) Mută clasa „8B” la alt profesor (schimbă profesorul clasei) → **Aștepți:** profesor2 n-o mai vede; noul profesor da.
- [ ] **D5** 🤖 Profesor2 nu are în meniu Jurnal și Capitole EN; adresele directe → blocat.

---

## E. Conținut: clase, cărți, capitole, exerciții 🟡

(TESTING §8, §9)
- [ ] **E1** Creează clasă → carte → capitol → exercițiu (cu LaTeX, ex. `$\frac{1}{2}$`) → **Aștepți:** formula randată (KaTeX).
- [ ] **E2** Editează fiecare → modificarea se vede la elev în `/content`.
- [ ] **E3** Înscrie Ana în clasă; scoate-o → **Aștepți:** pierde accesul la cărțile clasei.
- [ ] **E4** Șterge o clasă care are elevi / un capitol cu test → **Aștepți:** mesaj specific (de ce nu se poate, 409), nu „eroare”.
- [ ] **E5** Ca Ana, `/content` → vede doar clasele în care e înscrisă.
- [ ] **E6** Lista de pași de start pe Acasă (profesor nou): Creează o clasă → Invită elevii → Creează un test → Dă-l ca temă →
  **Aștepți:** fiecare se bifează după ce îl faci.

---

## F. Teste — editorul profesorului 🟠

(TESTING §10, §11a–c, §19, §21)
- [ ] **F1** Teste → Adaugă test → întrebare **grilă** (variante a)–d), una corectă) + întrebare **deschisă** cu punctaj.
- [ ] **F2** Grilă fără variantă corectă / punctaj 0 → **Aștepți:** eroare clară, nu se salvează.
- [ ] **F3** Structură EN: secțiuni SUBIECTUL I/II/III, cerințe a)/b) cu puncte pe cerință → **Aștepți:** totalul se adună corect.
- [ ] **F4** Imagine sub enunț: încarcă figură, decupează-o cu săgețile → **Aștepți:** apare sub enunț la elev.
- [ ] **F5** Cronometru (ex. 2 min) + test de practică + indicii progresive (indiciu 1, 2, rezolvare).
- [ ] **F6** Copiază testul → **Aștepți:** copie ciornă, fără încercări.
- [ ] **F7** Publică / Retrage publicarea; șterge un test *cu* încercări → **Aștepți:** refuzat cu mesaj.
- [ ] **F8** Dintr-un capitol: **Creează test din capitol** → test precompletat din exercițiile capitolului.
- [ ] **F9** (cu tutorul pornit) **Sugerează indicii (AI)** pe o întrebare → **Aștepți:** indiciu, indiciu mai mare, rezolvare, cu
  paginile capitolului EN folosite; tutorul oprit → mesaj „indisponibil”, nu crash.
- [ ] **F10** Statistici test (`/admin/quizzes/:id/stats`) după ce 2 elevi l-au dat → media și procentul pe întrebare corecte.

---

## G. Elevul dă testul 🔴🟠

(TESTING §11, §11a, §11b, §24)
- [ ] **G1** Ana → Testele mele → începe testul → răspunde la grilă și la deschisă → Predă → **Aștepți:** dialog de confirmare cu
  ce ai lăsat necompletat; după predare, grila e notată imediat, deschisa „în corectare”.
- [ ] **G2** 🤖 Autosalvare: răspunzi la 2 întrebări → reîncarci pagina (F5) → **Aștepți:** apare din nou ecranul de început;
  „Începe testul” **reia aceeași încercare**, cu răspunsurile bifate (nu creează una nouă). Observație UX: butonul ar putea
  spune „Continuă testul” în cazul ăsta.
- [ ] **G3** 🤖 Offline (F2): DevTools → Network → Offline → răspunzi → **Aștepți:** mesaj că răspunsurile așteaptă; Online →
  se trimit singure, fără pierderi.
- [ ] **G4** 🤖 Predare dublă: Network pe „Slow 3G” → Trimite lucrarea → confirmi de 2 ori → **Aștepți:** dialogul se închide
  la primul click, butonul arată „Se trimite...” și e blocat; la final o singură încercare (Testele mele).
- [ ] **G5** Cronometru: lasă să expire → **Aștepți:** testul se închide singur (jobul de expirare, ~30 s), cu ce era salvat.
  Schimbarea orei pe calculator nu prelungește timpul (timer pe server).
- [ ] **G6** Poze: la deschisă, până la 5 poze (și butonul „Fă o poză” pe telefon) → a 6-a → **Aștepți:** refuzată; ștergi una → poți adăuga.
- [ ] **G7** Fișier care nu e imagine / foarte mare → **Aștepți:** mesaj clar.
- [ ] **G8** Mod practică: indiciile se deschid pe rând; nota nu intră în progres ca test.
- [ ] **G9** 🤖 Rezultat: nota 1–10, punctaj pe întrebare, comentariul profesorului, pozele tale.
- [ ] **G10** 🤖 „Ai o notă nouă” pe Acasă + numărul de lângă Testele mele → deschizi rezultatul → **Aștepți:** dispare „Nou”.
- [ ] **G11** 🤖 „Exersează ce ai greșit” → practică doar cu întrebările greșite.

---

## H. Teme cu termen 🟠

(TESTING §11d)
- [ ] **H1** 🤖 Publică → „Dă-l și ca temă” → clasa + termen → **Aștepți:** Ana o vede la „De făcut” pe Acasă.
- [ ] **H2** Termen trecut → Ana o predă totuși → **Aștepți:** predarea e primită, dar marcată „întârziat” la profesor și la părinte.
- [ ] **H3** Mută termenul (reprogramare) și închide / redeschide tema din pagina Teme.
- [ ] **H4** Memento: temă cu termen în curând → **Aștepți:** emailul de reamintire pleacă (jobul rulează la ~15 min) — o singură
  dată, nu la fiecare rulare.
- [ ] **H5** Maria vede temele copilului și dacă le-a făcut.

---

## I. Corectura 🔴

(TESTING §12, §12d, §32)
- [ ] **I1** Corectură → lucrarea Anei → punctaj pe întrebarea deschisă → **Aștepți:** se salvează singur (autosave), fără buton.
- [ ] **I2** 🤖 (cifre) Tastatura pe butoanele de punctaj (focus pe ele, nu pe card): cifră = punctajul, salvat, focus pe
  următoarea întrebare; ←/→ ±1 și Home/End = 0/maxim, salvate când ieși din grup (Tab); Enter = ce e ales (sau propunerea AI) și mai departe.
- [ ] **I3** Poze: vizualizatorul cu ←/→ între poze, **R** rotește.
- [ ] **I4** 🤖 Banca de comentarii: scrii comentariul → „⭐ Salvează ca frecvent” → apare ca buton sub căsuță → pe altă lucrare
  îl inserezi cu un click; × îl scoate (cu „Anulează”). Enter în căsuță doar trece la rând nou.
- [ ] **I5** 🤖 **Corectează pe întrebare** (aceeași întrebare la toți) → „Salvează și următoarea” → **Finalizează lucrările complete (N)**.
- [ ] **I6** Finalizare → dialogul „Finalizezi nota?” → **Aștepți:** Ana primește emailul „rezultatul e gata” (outbox, ~5–15 s) și vede nota.
- [ ] **I7** Punctaj peste maxim / negativ (prin API) → **Aștepți:** 400, nu se salvează.
- [ ] **I8** AI (cu `AI_GRADING_*=fake`): barem din poze pe un test EN → propuneri de punctaj la Subiectul III → **Aștepți:** AI
  doar propune; nota rămâne a profesorului până o confirmi.
- [ ] **I9** Ca Ana, în timpul corecturii → rezultatul arată „în corectare”, nu un punctaj parțial.

---

## J. Progres, hărți, recapitulare 🟡

(TESTING §13a, §16, §17, §30)
- [ ] **J1** Ana → Progresul meu → graficul în timp, după 2–3 teste.
- [ ] **J2** Harta pe capitole: verde/galben/roșu după rezultate; profesorul o vede pe clasă (`/admin/classes/:id/mastery`).
- [ ] **J3** Harta pe capitolele EN la elev, părinte, profesor (clasă și elev).
- [ ] **J4** Recapitulare (repetiție spațiată): întrebările greșite revin; răspuns corect → revin mai rar; nimic azi → mesaj „gata pe azi”.
- [ ] **J5** Pagina unui elev (`/admin/students/:id`): încercări, teme, progres, hartă, recapitulare — toate ale lui.
- [ ] **J6** Cache: după o notă nouă, statisticile și progresul se actualizează (nu rămân vechi).

---

## K. Părintele 🟠

(TESTING §13)
- [ ] **K1** 🤖 Maria → Copiii mei → Ana → încercări, teme, progres, hărți, recapitulare.
- [ ] **K2** 🤖 Deschide un rezultat → **Aștepți:** doar citire; niciun buton de dat test / corectat.
- [ ] **K3** Părinte cu 2 copii → fiecare separat, datele nu se amestecă.

---

## L. Capitole EN 🟡 (doar ADMIN)

(TESTING §27–31)
- [ ] **L1** `/admin/en-chapters` → creezi pagina unui capitol (A1–A11, G1–G9): text, poze (max 40), modele rezolvate cu imagine.
- [ ] **L2** Mută capitolul în listă; șterge o poză / un model.
- [ ] **L3** Pozițiile din subiect (sloturi) → legarea capitol ↔ poziție; `/admin/en-chapters/legaturi` → legături automate.
- [ ] **L4** Pe un test EN: „AI propune capitolul” → bifezi ce păstrezi → **Aștepți:** se salvează doar ce ai bifat.
- [ ] **L5** Ca Ana: greșește o întrebare EN → butonul spre pagina capitolului → `/capitole/:id` cu materia.

---

## M. Import din poze/PDF cu AI 🟡

(TESTING §29; cere `AI_IMPORT_ENABLED=true`)
- [ ] **M1** Teste → Import AI → încarci 2 poze (sau un PDF scanat) → **Aștepți:** pagini în coadă → citite (fake: întrebări inventate).
- [ ] **M2** Deschizi importul → corectezi enunțurile, decupajul figurilor → creezi testul → **Aștepți:** test ciornă cu întrebările.
- [ ] **M3** O pagină eșuată → **Reîncearcă** → se recitește doar pagina aceea.
- [ ] **M4** Aceeași poză încărcată a doua oară → **Aștepți:** refolosită, fără cost nou (V45).
- [ ] **M5** Limita zilnică (`AI_DAILY_PAGE_LIMIT`, pune `2` ca să testezi) → a 3-a pagină → mesaj de limită.
- [ ] **M6** Șterge un import → dispare împreună cu pozele.

---

## N. Jurnal (audit, ADMIN) 🟡

(TESTING §22)
- [ ] **N1** După corecturi, publicări, înscrieri → `/admin/journal` → filtre Corectură/Teste/Teme/Conținut/Elevi și părinți.
- [ ] **N2** Fiecare rând: cine, ce, când. O acțiune eșuată (refuzată) nu apare ca reușită.

---

## O. Interfață, telefon, PWA ⚪🟡

(TESTING §14, §25)
- [ ] **O1** Contul meu → Aspect → temă întunecată → **Aștepți:** toate paginile lizibile, fără text alb pe alb; fără clipire albă la reîncărcare.
- [ ] **O2** DevTools → modul telefon (375 px): meniul de jos, „Mai mult”, butoane ușor de apăsat, fără scroll orizontal.
- [ ] **O3** La încărcare: schelete gri, nu „nu ai nimic” înainte să vină datele.
- [ ] **O4** Backend oprit → **Aștepți:** mesaj de eroare cu „Încearcă din nou”, nu pagină albă.
- [ ] **O5** PWA: în Chrome „Instalează aplicația” → se deschide ca aplicație; iconița x²; `/api` nu e luat din cache (date proaspete).
- [ ] **O6** Formule matematice și Markdown în enunțuri, rezultate, capitole.

---

## P. Sistem și producție 🟠

(TESTING §12c–12i, `docs/DEPLOY.md`)
- [ ] **P1** `http://localhost:8080/actuator/health` → `UP`.
- [ ] **P2** Headere de securitate pe orice răspuns (DevTools → Network): CSP, `X-Content-Type-Options`, etc. (TESTING §12f).
- [ ] **P3** Fiecare răspuns are `X-Request-Id`, același id ca în log-ul backend-ului pentru cererea aceea.
- [ ] **P4** Outbox: oprește SMTP-ul / pune un host greșit → finalizează o notă → pornește-l înapoi → **Aștepți:** emailul pleacă
  după reîncercare, nu se pierde.
- [ ] **P5** `/actuator/prometheus` fără token → refuzat; cu `METRICS_TOKEN` → metrici.
- [ ] **P6** Joburile programate — de urmărit în log că rulează fără erori:

| Job | La cât | Ce verifici |
|---|---|---|
| `AttemptExpiryJob` | 30 s | testele cu timp expirat se închid (G5) |
| `AssignmentReminderJob` | 15 min | memento o singură dată (H4) |
| `OutboxDispatcher` / `OutboxPurger` | 5 s / 03:30 | emailurile pleacă (I6, P4) |
| `AiImportWorker`, `BaremWorker`, `AiGradingWorker` | 3 s | cozile AI (M, I8) |
| `GdprExportPurgeJob` | 04:15 | exporturile vechi dispar (B6.1) |
| `IdempotencyPurger` | 03:45 | cheile vechi de predare dispar |

- [ ] **P7** Stack-ul de producție local: `docker compose -f docker-compose.prod.yml up` → aplicația merge prin nginx.
- [ ] **P8** Backup + restaurare: `deploy/backup.sh` → `deploy/restore-test.sh` → **Aștepți:** baza restaurată are aceleași date.
- [ ] **P9** Pe server (https://math-lms.duckdns.org): login, un test dat, o corectură — după fiecare release.

---

## Q. Ce lipsește din testele automate (recomandări)

Acoperit deja (tabelul din secțiunea 1): drepturi A1–A4/A7, profesor D1/D2/D5, 2FA, sesiuni, offline, predare dublă,
reîncărcare, corectura cu tastatura + banca de comentarii. Rămas, în ordinea priorității:

| # | Ce | Pași | Risc | Cum |
|---|---|---|---|---|
| 1 | Termen depășit la temă | H2 | 🟠 | E2E: temă cu termen trecut prin API → Ana predă → „întârziat” la profesor și părinte |
| 2 | Profesorul invită părintele | C4 | 🟠 | E2E: link de părinte → legătura apare la „Copiii mei” |
| 3 | Înregistrare completă cu link de clasă | C2, B2 | 🔴 | cere un SMTP de test (Mailpit în compose); deblochează și B6, H4, I6 (email) |
| 4 | Izolare profesor prin API | D3, D4 | 🔴 | E2E: `fetch('/api/admin/quizzes/<id străin>')` ca profesor → 403/404 |
| 5 | Cronometru | G5 | 🟠 | doar cu o proprietate de test pentru timer scurt (altfel ~90 s de așteptat) |
| 6 | Tema întunecată / telefon | O1, O2 | ⚪ | un smoke E2E la 375 px și cu tema întunecată |

Igienă: `TwoFactorE2eTest` nu se poate rula de 2 ori fără `dev-cleanup-e2e.sql`; workflow-ul `e2e.yml` pornește doar manual —
merită și un `schedule` (de ex. noaptea).

Nu merită E2E (acoperit de integrare/unit): rate limit (B1.4, B2.4), headere (P2), outbox (P4), joburi (P6), import AI (M), hărți (J2, J3).

---

## Raport de test (completezi la final)

| Data | Commit | Secțiuni trecute | Picate (cod pas + ce ai văzut) |
|---|---|---|---|
| | | | |
