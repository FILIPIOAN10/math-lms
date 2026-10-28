# Tutor AI — unde am rămas

Fișier de reluare între sesiuni. **Actualizat: 2026-10-28** (pașii 0–5 și 8–10 făcuți; 4, 5, 8–10 pe branch-ul `feat/tutor-hints`, nepus pe GitHub). Planul complet cu „de ce" la
fiecare pas e în `tutor/ROADMAP.md` (local, nu e în git); povestea pentru interviu e în `tutor/interview/` (local, nu e în git).

**Ca să reiei într-o sesiune nouă, spune:** „continuăm `tutor/`, citește `tutor/CHECKLIST.md`; următorul pas e …".

## Pe scurt

Construim, în `math-lms/tutor/`, un tutor AI de matematică (clasa a 8-a, Evaluarea Națională) care demonstrează RAG + LangChain,
**măsurat și demonstrabil la interviu**. Ajutorul e pentru profesor (el aprobă fiecare sugestie), nu direct pentru elev.

**9 din 13 pași făcuți** (0–5, 8–10): teoria e în baza de date ca text și ca vectori, căutarea ei e comparată pe cifre, lanțul
scrie indicii pe 3 trepte doar din manual (sau spune „Nu am asta în manual"), iar un CLI cu conversație rescrie continuările
scurte în întrebări complete. Din 2026-10-28 lanțul e în produs: serviciu FastAPI intern, proxy Spring și butonul
„Sugerează indicii (AI)” în editorul întrebării. Urmează exercițiile similare cu verificare sympy (pasul 6).

## Starea pașilor

| Pas | Ce | Stare | Unde |
|---|---|---|---|
| 0 | pgvector în Docker-ul existent, V36, pachetul Python `tutor` | ✅ | `main` (PR #4) |
| 1 | Teoria: poze/PDF → text (vision), capitole + secțiuni în LMS (V37, V38), text afișat în aplicație cu figuri | ✅ | `main` (PR #4) |
| 2 | Chunking pe titluri, embeddings, `tutor_chunks`, idempotent | ✅ | `main` (PR #4) |
| 3 | Retrieval: vector / fulltext / BM25 / hybrid / MMR / loc din test, set de aur, prag de refuz | ✅ | PR #5 (deschis, CI verde) |
| 4 | Lanț de ajutor pe 3 trepte (LCEL), grounded, cu citări și refuz | ✅ | comis local pe `feat/tutor-help-chain`, **nepus** |
| 5 | CLI de dezvoltare (`python -m tutor.cli`: enunț → treapta 1/2/3, `/mai`, istoric limitat, continuări rescrise) | ✅ | comis local pe `feat/tutor-help-chain`, **nepus** |
| 6 | Exerciții similare + verificare cu sympy (tool calling); aici intră și comanda „similar" din CLI | ⬜ **următorul** | |
| 7 | Evaluare completă: set de aur extins, faithfulness, cost per apel | ⬜ | |
| 8 | Serviciu FastAPI intern + Docker | ✅ | `feat/tutor-hints` (`249a50c`) |
| 9 | Proxy Spring `POST /api/admin/quiz-items/{id}/tutor-hints` (proprietarul întrebării, rate limit, flag `app.tutor.enabled`) | ✅ | `feat/tutor-hints` (`b0639ce`) |
| 10 | Buton „Sugerează indicii (AI)” în editorul întrebării (fără „Laborator Tutor” separat) | ✅ | `feat/tutor-hints` (`5d1e6f4`) |
| 11 | CI, Azure OpenAI, pachetul de interviu | ⬜ | |
| 12 | (opțional) ajutor live pentru elev | ⬜ | |

## Ce s-a făcut, pe pași

### Pasul 0 — fundația ✅
- [x] Imaginea Postgres din `docker-compose.yml` și `docker-compose.prod.yml` → `pgvector/pgvector:pg16` (backup înainte, reindexare după)
- [x] Migrarea `V36__tutor_chunks.sql`: extensia `vector`, `tutor_photo_transcripts`, `tutor_chunks` (embedding 1536, index HNSW cosinus)
- [x] Testele de integrare pe aceeași imagine printr-o singură constantă (`TestImages.POSTGRES`)
- [x] Pachetul Python `tutor` (config din variabile de mediu, `check_db`, teste)

### Pasul 1 — teoria intră ✅
- [x] Lista de capitole refăcută după manual: **17 capitole, 183 de pagini** (capitole, secțiuni, puncte); coduri doar litere + cifre (`AIS5P5`)
- [x] 22 de poziții din test (`en_chapter_slots`, ex. `I.1`) legate de paginile potrivite
- [x] V37 (sursa chunk-ului, pagina din carte) și V38 (`parent_id`, `book_page`) — idempotente, aplicate pe baza dev
- [x] Un capitol = un PDF; „Adaugă PDF" îl desparte în pagini în browser (pdf.js); până la 40 de poze
- [x] Transcrierea: `tutor.transcribe` (vision, JSON strict, `store:false`, plafon de cost, `--dry-run`) → Markdown + LaTeX
- [x] `tutor.build_chapter <COD>`: pozele din LMS → transcriere (cache pe poză) → chunk-uri → text în pagina capitolului (nu suprascrie textul profesorului)
- [x] Elevul citește **text**, nu poze: formule KaTeX, tabele, figuri decupate din poză, Cuprins cu legături, titluri verzi Times New Roman 16pt
- [x] Limita textului a urcat de la 10.000 la 60.000 de caractere

### Pasul 2 — chunking + embeddings ✅
- [x] `page_map.py` (pagină → capitol din Cuprins), `chunking.py` (tăiere la titluri numerotate → cod LMS, ≤ 1.400 de caractere, tabelele rămân întregi)
- [x] `store.py` (upsert idempotent, hash pe text), `llm.py` (`OpenAIEmbeddings` — punctul de schimb spre Azure), `ingest.py`
- [x] Corecția `_moves_forward`: „7. Criteriul…" dintr-o listă nu mai e luat drept secțiunea 7

### Pasul 3 — retrieval ✅
- [x] `retrieval.py`: vector, fulltext Postgres, **BM25 scris de mână** (rădăcini românești fără diacritice), hybrid (RRF), MMR, `slot_baseline`, `ChunkRetriever` (LangChain `BaseRetriever`)
- [x] `evaluate.py` + `eval/golden.jsonl` (30 de întrebări) → `eval/report.md`
- [x] Prag de refuz măsurat (exact 0,4646; pus la 0,48 la pasul 4) și politica aleasă (vezi mai jos)
- [x] 64 de teste Python verzi

### Pasul 4 — lanțul de ajutor pe 3 trepte ✅ (2026-10-16, comis local)
- [x] `help_chain.py`: lanț LCEL — retrieval (loc din test, altfel vector k = 4) → refuz dacă nu e nimic apropiat → prompt → model cu JSON strict → verificarea citărilor → `HelpResult`
- [x] Prompt „grounded": doar pasajele numerotate, enunțul e doar date (nu poate închide secțiunea din prompt), un nivel pe cerere, treapta N vede treptele 1..N-1
- [x] Trei filtre de refuz: distanța (modelul nici nu e apelat) → `covered=false` al modelului → răspuns fără citare validă (nu se afișează)
- [x] Pe 3–5 itemuri reale: `20 − 2·(7 − 2)` → 10 și `100 : (5 + 5) · 3` → 30 (corecte, cu pagina citată); aria cercului → „Nu am asta în manual"; injecție în enunț → ignorată
- [x] `evaluate_help.py`: cap-coadă cu modelul real pe cele 30 de întrebări → `eval/help-report.md` (**30 din 30** cu pragul 0,48; cu 0,46 rotunjit: 29 din 30)
- [x] `retrieve_for_exercise` în `retrieval.py` (politica măsurată) + 24 de teste noi (fără rețea) → 88 de teste Python verzi
- [x] Pragul pus la **0,48**, în golul dintre grupuri (0,4646 acoperită cea mai îndepărtată / 0,5057 din afară a doua ca apropiere); `n04` (0,4048) e oprită de model
- ✗ `temperature=0`: modelul îl respinge; stabilitatea vine din `reasoning.effort=low` + schema strictă + filtre (aceleași decizii la 2 rulări)
- [ ] Rămas pentru pașii următori: treapta 3 nu e verificată automat (sympy, pasul 6); pragul e reglat pe aceleași 30 de întrebări (pasul 7 îl verifică pe întrebări noi)

### Pasul 5 — CLI + conversație ✅ (2026-10-16, comis local)
- [x] `conversation.py`: clasa `Conversation` (exercițiul deschis, treptele date, ultimele 6 mesaje); fără nimic de terminal în ea, ca s-o refolosească FastAPI la pasul 8
- [x] Continuările scurte (≤ 200 de caractere, când există istoric) sunt rescrise în întrebări complete **înainte** de căutare; mesajele lungi rămân neatinse; rescrierea goală cade pe textul scris
- [x] `cli.py`: `/mai`, `/loc`, `/pasaje`, `/istoric`, `/nou`, `/ajutor`, `/iesire`; arată `(am înțeles: …)`; merge și cu stdin pentru demo scriptat
- [x] `HelpService.step()` (o singură treaptă) + `chat_options`, `passage_lines`/`step_lines`/`refusal_line` scoase în funcții, ca să le refolosească CLI-ul
- [x] 29 de teste noi (model fals + rescriitor fals, fără rețea) → **117 verzi**; `tests/fakes.py` e modulul comun de simulatoare
- [x] Sesiune reală de 5 mesaje (~0,0014 $): fără rescriere, continuările ar fi fost refuzate (0,509 / 0,486 > 0,48); rescrise au trecut (0,338 / 0,379)
- [ ] Rămas: calitatea rescrierii nu e măsurată (doar 2 exemple reale) → pasul 7; comanda „similar" → pasul 6

### Pașii 8–10 — tutorul în produs ✅ (2026-10-28, branch `feat/tutor-hints`)
- [x] `api.py`: `POST /help` (enunț ≤ 4000, loc `I.1`, trepte 1–3) → treptele cu paginile, sau refuzul; `GET /health`. Token `X-Tutor-Token` (comparat în timp constant); fără `SERVICE_TOKEN` setat → 503 la orice cerere. Textul pasajelor nu iese din bază (doar capitol + pagină).
- [x] `Dockerfile` (uv, dependențe blocate, utilizator non-root, healthcheck) + serviciul `tutor` în `docker-compose.yml`, profil opt-in, legat de localhost.
- [x] Spring `ro.mathlms.tutor`: `TutorClient` (timeout 55 s < nginx 60 s, mesaje în română), `TutorHintService` (întrebarea ca text cu variante/cerințe; locul din test doar dacă profesorul nu a ales capitolul), `TutorController` (aceeași verificare ca la editare, 404 când e oprit), regulă de rate limit 60/zi/profesor.
- [x] Frontend: `TutorHintsPanel` în „Mai multe opțiuni” → „Adaugă ca indiciu” pe fiecare treaptă, „Pune la rezolvare” doar dacă rezolvarea e goală și nu e întrebare deschisă (acolo e baremul). Nimic nu se aplică singur.
- [x] Teste: 11 Python (`test_api.py`), 11 backend, 5 frontend; suitele întregi verzi: 128 Python, 1154 backend, 594 frontend.
- [x] Live: I.1 `20 − 2·(7 − 2)` → 3 trepte corecte, pagina 14 citată, 0,000567 $; aria cercului → refuz `too_far` (0,508) fără apel la model, prin clientul Java real; token greșit → mesaj de configurare.
- ✗ Abatere de la plan: fără rând în jurnal (o propunere nu schimbă nimic; salvarea întrebării e deja jurnalizată), fără cache și fără „Laborator Tutor”.
- [ ] Rămas: containerul în `docker-compose.prod.yml`, testele Python în CI (pasul 11), ecranul neverificat în browser (backend-ul de dev rula codul vechi).

### Pașii 6, 7, 11, 12 ⬜
Detaliile și „de ce" sunt în `tutor/ROADMAP.md`. Pe scurt: 6 exerciții similare + sympy (curățat înainte de `sympify`) ·
7 evaluare completă, faithfulness, cost per apel · 8 FastAPI intern + Docker · 9 proxy Spring doar ADMIN, cu rate limit,
audit, flag `tutor.enabled` și cache · 10 „Laborator Tutor" în frontend · 11 CI, Azure, pachetul de interviu · 12 opțional.

## Starea codului și a git-ului

- Pașii 0–2 sunt în `main` (PR [#4](https://github.com/FILIPIOAN10/math-lms/pull/4), combinat 2026-10-15, `b925503`).
- Pasul 3: PR [#5](https://github.com/FILIPIOAN10/math-lms/pull/5), `feat/tutor-ai` → `main`, **8 din 8 verificări verzi, fără conflicte** (2026-10-16), încă deschis.
  Comit-urile lui: `03587db` (corecția de chunking), `e791976` (pasul 3), `88e2297` (checklist).
- **2026-10-28:** branch nou `feat/tutor-hints` din `main` (PR #5 e combinat), cu `feat/tutor-help-chain` combinat în el (`67c7ab7`), apoi pașii 8–10 (`249a50c`, `b0639ce`, `5d1e6f4`). **Nepus pe GitHub**; la final un singur PR din `feat/tutor-hints`.
- (istoric) **Pașii 4 și 5** sunt pe branch-ul local `feat/tutor-help-chain` (creat din `feat/tutor-ai`, deci conține și commit-urile din PR #5):
  `547a2fd` (politica de retrieval ca funcție, pragul exact în raport) și `a8a8d0a` (lanțul de ajutor, evaluarea lui, teste, prag 0,48).
  Pasul 5 = `conversation.py`, `cli.py`, `tests/fakes.py`, `test_conversation.py`, `test_cli.py` și mici modificări în `help_chain.py`/`config.py`.
  **Nimic din 4–5 nu e împins.** Când îl împingi: combini întâi PR #5, apoi deschizi PR din `feat/tutor-help-chain` (va arăta doar pasul 4).
- Ce nu e în `main`: `git log origin/main..HEAD --oneline` · ce nu e pe GitHub: `git log origin/feat/tutor-ai..HEAD --oneline`.
- `structura_proiect.txt` din rădăcină nu e al proiectului tutor; nu intră în commit.
- CI-ul din PR cere **Dependency graph** activat în setările repo-ului (activat de tine 2026-10-15; fără el pică pasul dependency-review).
- Commit/push **doar când spui tu**.

## Ce e încărcat acum

- Doar **capitolul I** (cod `AI`): 14 pagini transcrise, 42 de chunk-uri în `tutor_chunks`, textul publicat în 23 de secțiuni din LMS.
- Capitolele II–X (algebră) și geometria (GI–GVII): neîncărcate.
- Modelele: chat `gpt-6-luna` ($0,10 / $0,50 la un milion de tokeni), embeddings `text-embedding-3-small` (1536).

## Costuri măsurate (bugetul e 5 $)

| Ce | Cost |
|---|---|
| Capitolul I întreg (14 poze, transcriere) | ≈ 0,0145 $ |
| Recitirea figurilor | ≈ 0,0034 $ |
| Embeddings pentru 42 de chunk-uri | sub un cent |
| Evaluarea retrieval-ului (30 de întrebări) | sub un cent |
| Evaluarea cap-coadă a lanțului, cu modelul real (30 de întrebări) | ≈ 0,0055 $ |
| Un exercițiu, cele 3 trepte | ≈ 0,0006 $ |
| O sesiune de 5 mesaje în CLI (cu 2 rescrieri) | ≈ 0,0014 $ |

## Rezultatele pasului 3 (capitolul I, 24 de întrebări cu răspuns în manual)

| strategie | hit@1 | hit@3 | MRR |
|---|---|---|---|
| vector | 88% | 92% | 0,90 |
| BM25 (rădăcini RO) | 71% | 75% | 0,75 |
| full-text Postgres | 38% | 54% | 0,47 |
| hybrid (RRF) | 79% | 88% | 0,83 |
| MMR | 88% | 88% | 0,89 |
| **loc din test, altfel vector** | **96%** | **100%** | **0,98** |

- Hybrid **nu bate** vectorul pe acest corpus mic (cuvintele comune adaugă zgomot).
- La cele 4 itemuri în stil EN, vectorul singur: 50% hit@1; legătura „poziție din test → pagină": 100%.
- Refuz: întrebările din manual au cel mai apropiat chunk la 0,25–0,465, cele din afara lui la 0,40–0,68; pragul exact 0,4646 → 97% corect.

**Decizia (pasul 4):** locul din test când itemul îl are, altfel vector (k = 4), refuz peste distanța **0,48**.
Pragul exact din evaluare era 0,4646; rotunjit la 0,46 refuza o întrebare acoperită (c08), deci nu-l rotunji în jos.
Distanța singură nu separă grupurile (`n04`, din afară, e la 0,4048, mai aproape decât `c08`, acoperită, la 0,4646) —
al doilea filtru e `covered=false` al modelului. Atenție: setul de aur e mic (n = 30), scris de Claude, iar pragul e
reglat pe el; cifrele sunt orientative. Rapoarte: `eval/report.md` (retrieval), `eval/help-report.md` (cap-coadă).

## Decizii luate (nu le redeschidem fără motiv)

- Serviciu Python (FastAPI + LangChain) în `math-lms/tutor/`, **în același repo**; Spring îl apelează, browserul niciodată.
- **Același Postgres**, cu imaginea pgvector; schema o ține Flyway.
- OpenAI acum, **Azure OpenAI** prin schimbarea configurării (`llm.py` e punctul de schimb).
- Ajutor **pentru profesor, cu omul în buclă**: sugestii de indicii pe 3 trepte, aprobate înainte să le vadă elevii; butonul pentru elev e doar pasul 12.
- Scop de demo: 3–5 itemuri de algebră + setul de aur, nu toate cele 30 de variante.
- Teoria vine din manualul tău, afișată ca text în aplicație; **manualul nu intră în git** (repo public): `tutor/data/`, `tutor/interview/`, `tutor/.env`, `tutor/ROADMAP.md` sunt ignorate.
- Textul scris de profesor nu e suprascris niciodată (`build_chapter` scrie doar unde `notes` e gol; `--overwrite` e explicit).

## Povestea pentru interviu

Word-ul `tutor/interview/Tutor-AI-povestea-proiectului.docx` are etapele 0–5 (cu tabele de rezultate la 3, 4 și 5) și o secțiune
**„How this maps to the job ad"**: fiecare cerință din anunț → ce arată proiectul → unde se oprește (cloud nedeplasat încă,
fără Kubernetes, FastAPI/proxy încă nefăcute). Se reface cu `cd interview; node build_story.js`; la fiecare etapă nouă adaugi un
obiect în `STAGES`, treci rândul din `OVERVIEW` pe „Done" și, dacă s-a schimbat ceva, actualizezi `JOB_AD`.
Anunțul actual cere „AWS, Azure sau Google Cloud" (nu doar Azure) și baze de date relaționale **și** non-relaționale.

## De făcut de tine

- [ ] Combini PR-ul [#5](https://github.com/FILIPIOAN10/math-lms/pull/5) când ești mulțumit.
- [ ] Pornești Docker Desktop înainte de orice comandă de mai jos.
- [ ] Ștergi din textul secțiunii `AIS1` prefixul de WhatsApp `[13:17, 15.10.2026] Ioan:` (e în text, nu în carte).
- [ ] Încarci PDF-ul următorului capitol („Adaugă PDF" pe pagina capitolului), apoi rulezi `build_chapter` pentru codul lui.
- [ ] Scrii 10–15 întrebări proprii pentru setul de aur (pasul 7) — setul actual e scris de Claude.
- [ ] Primele 3 variante de test EN prin importul AI existent (ca să avem itemuri reale pentru pasul 4).

## Comenzi (din folderul `tutor/`)

```powershell
uv run pytest -q                                          # 117 teste; au nevoie de Postgres pe :5433 (Docker pornit)
uv run python -m tutor.check_db                           # pgvector + tabele
uv run python -X utf8 -m tutor.cli --show-passages                  # conversație: enunț, /mai, continuări scurte, /ajutor
uv run python -X utf8 -m tutor.help_chain "Calculează 20 - 2·(7 - 2)" --slot I.1 --show-passages   # ajutor pe 3 trepte (~0,0006 $)
uv run python -X utf8 -m tutor.evaluate_help --write-report eval/help-report.md   # răspunde/refuză, 30 de întrebări (~0,0055 $)
uv run python -X utf8 -m tutor.build_chapter AII --dry-run          # ce ar face, fără apeluri plătite
uv run python -X utf8 -m tutor.build_chapter AII --max-usd 0.10     # transcrie, taie, embedează, publică textul în LMS
uv run python -X utf8 -m tutor.evaluate --write-report eval/report.md   # regenerează tabelul de retrieval
cd interview; node build_story.js                         # reface documentul Word
```

## Unde sunt lucrurile

| Ce | Unde |
|---|---|
| Cheia OpenAI | `tutor/.env` și `backend/.env` (ignorate de git; **nu se lipește în chat**) |
| Manualul (poze, transcrieri) | `tutor/data/`, `backend/uploads/en-chapters/` — local, drepturi de autor, repo public |
| Migrări | `backend/src/main/resources/db/migration/V36`–`V38` |
| Capitole + secțiuni (Java) | `EnChapter*.java`, `EnChapterService.java`, `EnChapterTree.java` |
| Pagini din frontend | `EnChapterPage.tsx`, `AdminEnChapterPage.tsx`, `AdminEnChaptersPage.tsx`, `lib/pdfPages.ts` |
| Hartă pagini → capitole | `src/tutor/page_map.py` (din Cuprinsul manualului) |
| Căutare + evaluare | `src/tutor/retrieval.py`, `src/tutor/evaluate.py`, `eval/` |
| Lanțul de ajutor + evaluarea lui | `src/tutor/help_chain.py`, `src/tutor/evaluate_help.py` |
| Conversația + CLI | `src/tutor/conversation.py`, `src/tutor/cli.py`; simulatoarele de test: `tests/fakes.py` |

## Capcane de mediu (le-am călcat deja)

- PowerShell: `Get-Content | Set-Content -Encoding utf8` strică UTF-8 și adaugă BOM; folosește `python -X utf8` sau editorul.
- Codurile paginilor sunt doar litere + cifre, ≤ 10 caractere (`AIS5P5`), altfel formularul din LMS le respinge.
- Chunk-urile se leagă de cea mai apropiată pagină LMS **existentă**; creează paginile înainte de a rula `build_chapter`.
- Biblioteca `rank_bm25` dă scor 0 pe un corpus de 2 chunk-uri (IDF = log 1); de aceea BM25 e scris de mână.
- Testele partajează simulatoarele din `tests/fakes.py` (se importă simplu: `from fakes import ...`).
- Modelul `gpt-6-luna` respinge `temperature` („Unsupported parameter"); nu-l pune în setări.
- Șabloanele de prompt LangChain tratează `{...}` ca variabile: în textul fix al promptului nu pune LaTeX cu acolade (valorile inserate sunt în regulă).
- PowerShell nu are heredoc (`<<'EOF'`); pentru scripturi Python scurte folosește Bash sau un fișier.
- Maven loghează corpuri mari de request; filtrează ieșirea: `^\[(INFO|ERROR)\] (Tests run:|BUILD)`.
- Scrierile admin din API cer antetul `X-XSRF-TOKEN`.
- Nu opri backendul (:8080) sau Vite (:5173) ale tale. Backendul trebuie repornit din IntelliJ doar când se schimbă cod Java.
- Migrările V36–V38 sunt deja aplicate pe baza dev (verificat 2026-10-15).
