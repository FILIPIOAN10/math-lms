# Tutor AI — unde am rămas

Fișier de reluare între sesiuni. **Actualizat: 2026-10-15** (după pasul 3). Planul complet cu „de ce" la fiecare pas e în
`tutor/ROADMAP.md` (local, nu e în git); povestea pentru interviu e în `tutor/interview/` (local, nu e în git).

**Ca să reiei într-o sesiune nouă, spune:** „continuăm `tutor/`, citește `tutor/CHECKLIST.md`; următorul pas e …".

## Starea pașilor

| Pas | Ce | Stare |
|---|---|---|
| 0 | pgvector în Docker-ul existent, V36, pachetul Python `tutor` | ✅ comis, pe GitHub |
| 1 | Teoria: poze/PDF → text (vision), capitole + secțiuni în LMS (V37, V38), text afișat în aplicație cu figuri | ✅ comis, pe GitHub |
| 2 | Chunking pe titluri, embeddings, `tutor_chunks`, idempotent | ✅ comis, pe GitHub |
| 3 | Retrieval: vector / fulltext / BM25 / hybrid / MMR / loc din test, set de aur, prag de refuz | ✅ făcut, **necomis** |
| 4 | Lanț de ajutor pe 3 trepte (LCEL), grounded, cu citări și refuz | ⬜ următorul |
| 5 | CLI de dezvoltare (`python -m tutor.cli`: enunț → treapta 1/2/3, „similar", istoric limitat) | ⬜ |
| 6 | Exerciții similare + verificare cu sympy (tool calling) | ⬜ |
| 7 | Evaluare completă: set de aur extins, faithfulness, cost per apel | ⬜ |
| 8 | Serviciu FastAPI intern + Docker | ⬜ |
| 9 | Proxy Spring `/api/admin/tutor/*` (doar ADMIN, rate limit, audit, flag `tutor.enabled`) | ⬜ |
| 10 | „Laborator Tutor" + buton „Sugerează indicii" în frontend | ⬜ |
| 11 | CI, Azure OpenAI, pachetul de interviu | ⬜ |
| 12 | (opțional) ajutor live pentru elev | ⬜ |

## Starea codului

- Branch `feat/tutor-ai`; ultimul commit **`e37620f`** e pe GitHub (`origin/feat/tutor-ai`).
- **Necomis (pasul 3):** `src/tutor/retrieval.py`, `src/tutor/evaluate.py`, `tests/test_retrieval.py`, `eval/golden.jsonl`,
  `eval/report.md`, `src/tutor/chunking.py` + `tests/test_chunking.py` (corecția `_moves_forward`: „7. Criteriul…" dintr-o
  listă nu mai e luat drept secțiunea 7), `pyproject.toml` + `uv.lock` (+`snowballstemmer`), acest fișier.
- `structura_proiect.txt` din rădăcină nu e al proiectului tutor; nu intră în commit.
- Commit/push **doar când spui tu**.

## Ce e încărcat acum

- Doar **capitolul I** (cod `AI`): 14 pagini transcrise, 42 de chunk-uri în `tutor_chunks`, textul publicat în 23 de secțiuni din LMS.
- Capitolele II–X (algebră) și geometria (GI–GVII): neîncărcate.
- Modelele: chat `gpt-6-luna`, embeddings `text-embedding-3-small` (1536).

## Rezultatele pasului 3 (capitolul I, 24 de întrebări cu răspuns în manual)

| strategie | hit@1 | hit@3 | MRR |
|---|---|---|---|
| vector | 88% | 92% | 0,90 |
| BM25 (rădăcini RO) | 71% | 75% | 0,75 |
| full-text Postgres | 38% | 54% | 0,47 |
| hybrid (RRF) | 79% | 88% | 0,83 |
| MMR | 88% | 88% | 0,89 |
| **loc din test, altfel vector** | **96%** | **100%** | **0,98** |

Decizia pentru pasul 4: **locul din test când itemul îl are, altfel vector (k = 4), refuz peste distanța ≈ 0,46**
(97% corect pe cele 30 de întrebări). Atenție: setul de aur e mic (n = 30) și scris de Claude; cifrele sunt orientative.
Raportul complet, regenerat la fiecare rulare: `eval/report.md`.

## Comenzi (din folderul `tutor/`)

```powershell
uv run pytest -q                                          # 64 de teste; au nevoie de Postgres pe :5433 (Docker pornit)
uv run python -m tutor.check_db                           # pgvector + tabele
uv run python -X utf8 -m tutor.build_chapter AII --dry-run          # ce ar face, fără apeluri plătite
uv run python -X utf8 -m tutor.build_chapter AII --max-usd 0.10     # transcrie, taie, embedează, publică textul în LMS
uv run python -X utf8 -m tutor.evaluate --write-report eval/report.md   # regenerează tabelul de retrieval
cd interview; node build_story.js                         # reface documentul Word
```

## De făcut de tine

- [ ] Pornești Docker Desktop înainte de orice comandă de mai sus.
- [ ] Ștergi din textul secțiunii `AIS1` prefixul de WhatsApp `[13:17, 15.10.2026] Ioan:` (e în text, nu în carte).
- [ ] Încarci PDF-ul următorului capitol („Adaugă PDF" pe pagina capitolului), apoi rulezi `build_chapter` pentru codul lui.
- [ ] Scrii 10–15 întrebări proprii pentru setul de aur (pasul 7) — setul actual e scris de Claude.
- [ ] Primele 3 variante de test EN prin importul AI existent (ca să avem itemuri reale pentru pasul 4).

## Unde sunt lucrurile

| Ce | Unde |
|---|---|
| Cheia OpenAI | `tutor/.env` și `backend/.env` (ignorate de git; **nu se lipește în chat**) |
| Manualul (poze, transcrieri) | `tutor/data/`, `backend/uploads/en-chapters/` — local, drepturi de autor, repo public |
| Migrări | `backend/src/main/resources/db/migration/V36`–`V38` |
| Capitole + secțiuni (Java) | `EnChapter*.java`, `EnChapterService.java`, `EnChapterTree.java` |
| Pagini din frontend | `EnChapterPage.tsx`, `AdminEnChapterPage.tsx`, `AdminEnChaptersPage.tsx`, `lib/pdfPages.ts` |
| Hartă pagini → capitole | `src/tutor/page_map.py` (din Cuprinsul manualului) |
| Setul de aur + raport | `tutor/eval/` |

## Capcane de mediu (le-am călcat deja)

- PowerShell: `Get-Content | Set-Content -Encoding utf8` strică UTF-8 și adaugă BOM; folosește `python -X utf8` sau editorul.
- Codurile paginilor sunt doar litere + cifre, ≤ 10 caractere (`AIS5P5`), altfel formularul din LMS le respinge.
- Chunk-urile se leagă de cea mai apropiată pagină LMS **existentă**; creează paginile înainte de a rula `build_chapter`.
- Maven loghează corpuri mari de request; filtrează ieșirea: `^\[(INFO|ERROR)\] (Tests run:|BUILD)`.
- Scrierile admin din API cer antetul `X-XSRF-TOKEN`.
- Nu opri backendul (:8080) sau Vite (:5173) ale tale. Backendul trebuie repornit din IntelliJ doar când se schimbă cod Java.
- Migrările V36–V38 sunt deja aplicate pe baza dev (verificat 2026-10-15).
