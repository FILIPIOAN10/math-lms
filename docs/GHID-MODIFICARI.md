# Ghid: de la o modificare pe calculator până pe site-ul live

Pentru orice modificare — interfață (frontend), server (backend), bază de date — drumul e același:

```
1. pornești local  →  2. modifici + testezi local  →  3. commit + push  →  4. CI verde
→  5. tag (release)  →  6. pornești serverul AWS  →  7. pui versiunea pe server  →  8. verifici  →  9. oprești serverul
```

Pașii 1–5 nu au nevoie de server: **serverul AWS poate fi oprit tot timpul cât lucrezi.** Îl pornești doar la pasul 6.

Toate comenzile sunt pentru **Git Bash**, rulate din folderul `math-lms` (dacă nu scrie altfel).

---

## 1. Pornești aplicația pe calculator

1. Pornește **Docker Desktop** și așteaptă până scrie „Engine running”.
2. Deschide **3 ferestre Git Bash** în `math-lms`:

| Fereastra | Comanda | Gata când vezi |
|---|---|---|
| 1 — baza de date + Redis | `docker compose up -d postgres redis` | `Started` / `Running` (comanda se termină) |
| 2 — backend | `cd backend && ./mvnw spring-boot:run` | `Started BackendApplication` (rămâne deschisă) |
| 3 — frontend | `cd frontend && npm run dev` | `Local: http://localhost:5173/` (rămâne deschisă) |

3. Deschide **http://localhost:5173**. Conturi de test (parola tuturor `Admin123!`):
   `admin@mathlms.local` (profesor), `student.activ@mathlms.local` (elev), `parinte@mathlms.local` (părinte).

**Importul din poze, local** — se alege în `backend/.env`:
- `AI_PROVIDER=fake` → întrebări inventate, gratuit (pentru tot ce nu e chiar importul);
- `AI_PROVIDER=gemini` → Google, din credit; mai trebuie `AI_IMPORT_ENABLED=true`, `GEMINI_PROJECT=math-lms-507017`,
  `GEMINI_CREDENTIALS_FILE=C:/Users/Filip/.ssh/math-lms-vertex.json`.

După ce schimbi `backend/.env`, oprește backend-ul (Ctrl+C în fereastra 2) și pornește-l din nou.

---

## 2. Modifici și testezi local

- **Frontend** (`frontend/src/...`): se vede **imediat** în browser, fără repornire.
- **Backend** (`backend/src/main/java/...`): oprește backend-ul (Ctrl+C) și pornește-l din nou (`./mvnw spring-boot:run`).
- **Bază de date**: o tabelă/coloană nouă = un fișier nou `backend/src/main/resources/db/migration/V40__ce_face.sql`
  (numărul următor după ultimul `V…`). Se aplică singur la pornirea backend-ului.
  - **Nu modifica și nu șterge niciodată o migrare care a rulat deja** (local sau pe server) — fă una nouă.

Încearcă modificarea în aplicație (ca profesor / elev / părinte, după caz), apoi rulează testele automate
(Docker pornit):

```bash
cd backend && ./mvnw clean verify
```
```bash
cd frontend && npm run lint && npm test && npm run build
```

`clean` contează: șterge build-ul vechi, ca un fișier care lipsește din cod (ex. o migrare) să nu fie „ascuns”
de o copie veche din `target/`.

---

## 3. Commit + push

```bash
git status
```
Verifică lista: **fiecare fișier nou** pe care l-ai creat trebuie să apară. Apoi:
```bash
git add <fișierele tale>
git commit -m "Ce ai schimbat, pe scurt"
git push origin main
```

Nu pune niciodată în git: `.env`, `.env.prod`, chei (`*.json` de la Google, `*.pem`), parole.

---

## 4. Aștepți CI-ul verde

GitHub → repo `math-lms` → **Actions** → rularea **CI** pentru commit-ul tău trebuie să fie ✅ (~5–8 min).

Dacă e ❌: deschide rularea → jobul roșu → caută primele linii cu `ERROR` / `FAIL`. Repari local, commit, push din nou.
Până nu e verde, nu treci mai departe.

**Nu e nevoie de release după fiecare commit.** Poți face mai multe commit-uri (pașii 2–4 de mai multe ori) și un singur
release la final, când vrei să ajungă online.

---

## 5. Release (tag)

Vezi ultima versiune:
```bash
git tag --sort=-v:refname | head -1
```
Pune următorul număr — **reparații**: `v0.4.2 → v0.4.3`; **funcții noi**: `v0.4.3 → v0.5.0`. Fiecare număr se folosește
o singură dată.

```bash
git tag v0.4.3 && git push origin v0.4.3
```

GitHub → **Actions** → **Deploy to Production** → așteaptă ✅ (~5 min). Construiește și scanează imaginile
`math-lms-backend:0.4.3` și `math-lms-frontend:0.4.3`. Ultimul pas („Deploy”) scrie un avertisment că lipsesc secretele de
deploy — e normal: punerea pe server o faci tu, la pasul 7.

Dacă release-ul e ❌ la „Trivy”: o bibliotecă are o problemă de securitate cu reparație disponibilă — se actualizează
biblioteca, commit, push, tag nou.

---

## 6. Pornești serverul AWS

1. https://console.aws.amazon.com → sus în dreapta regiunea **Europe (Frankfurt)** → caută **EC2** → **Instances**.
2. Bifează `math-lms-prod` → **Instance state** → **Start instance**.
3. Așteaptă în coloana **Instance state**: 🟢 **Running**, și la **Status check** verde (1–2 min; ⟳ ca să reîmprospătezi).
4. Mai așteaptă ~1 minut (pornesc aplicația, baza de date, HTTPS — singure), apoi:

```bash
curl -s -o /dev/null -w "%{http_code}\n" --max-time 10 https://math-lms.duckdns.org/api/auth/me
```
`401` = merge. `000` = încă pornește (mai încearcă peste un minut).

---

## 7. Pui versiunea nouă pe server

Schimbă doar numărul de la `VERSIUNE` (fără `v`):

```bash
VERSIUNE=0.4.3; ssh -i ~/.ssh/math-lms-aws.pem ubuntu@3.124.57.45 "cd /opt/math-lms && sudo sed -i 's|^BACKEND_IMAGE=.*|BACKEND_IMAGE=ghcr.io/filipioan10/math-lms-backend:$VERSIUNE|; s|^FRONTEND_IMAGE=.*|FRONTEND_IMAGE=ghcr.io/filipioan10/math-lms-frontend:$VERSIUNE|' .env.prod && sudo docker compose -f docker-compose.prod.yml --env-file .env.prod pull backend frontend && sudo docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-build"
```

Migrările noi (`V40…`) se aplică singure când pornește backend-ul.

**Doar în două cazuri mai e un pas:**
- s-a schimbat **`docker-compose.prod.yml`** → îl copiezi pe server înainte de comanda de mai sus:
  ```bash
  scp -i ~/.ssh/math-lms-aws.pem docker-compose.prod.yml ubuntu@3.124.57.45:~/docker-compose.prod.yml
  ```
  ```bash
  ssh -i ~/.ssh/math-lms-aws.pem ubuntu@3.124.57.45 "sudo cp ~/docker-compose.prod.yml /opt/math-lms/docker-compose.prod.yml"
  ```
- e nevoie de o **setare nouă** → o pui direct pe server (nu copia fișierul local peste cel de pe server!):
  ```bash
  ssh -t -i ~/.ssh/math-lms-aws.pem ubuntu@3.124.57.45 "sudo nano /opt/math-lms/.env.prod"
  ```
  (Ctrl+O, Enter = salvează; Ctrl+X = ieși), apoi comanda de la pasul 7.

---

## 8. Verifici

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://math-lms.duckdns.org/api/auth/me
```
```bash
ssh -i ~/.ssh/math-lms-aws.pem ubuntu@3.124.57.45 "cd /opt/math-lms && sudo docker compose -f docker-compose.prod.yml --env-file .env.prod ps --format '{{.Service}} {{.Image}} {{.Status}}'"
```
Backend și frontend trebuie să arate versiunea nouă și **healthy**. Apoi încearcă modificarea pe https://math-lms.duckdns.org.

Dacă ceva nu merge, ultimele 100 de linii din logul serverului:
```bash
ssh -i ~/.ssh/math-lms-aws.pem ubuntu@3.124.57.45 "cd /opt/math-lms && sudo docker compose -f docker-compose.prod.yml --env-file .env.prod logs --tail 100 backend"
```

**Revii la versiunea de dinainte** cu aceeași comandă de la pasul 7, cu numărul vechi (ex. `VERSIUNE=0.4.2`).
Baza de date nu se întoarce în timp: de aceea o migrare doar **adaugă** (coloane noi, tabele noi), nu șterge sau redenumește.

---

## 9. Oprești serverul (seara / când nu-l folosești)

EC2 → **Instances** → bifează `math-lms-prod` → **Instance state** → **Stop instance** → **Stop**.
În ~1 minut: 🔴 **Stopped**.

- Oprit = **nu plătești serverul** (doar discul și IP-ul fix, câțiva dolari pe lună); datele rămân.
- Cât e oprit: site-ul nu răspunde, elevii nu pot intra, backup-ul de noapte nu rulează.
- ⚠️ **Niciodată „Terminate instance”** — asta șterge serverul cu tot cu date.

**Cum vezi dacă e oprit:** EC2 → Instances → coloana **Instance state**: 🔴 Stopped / 🟡 Stopping, Pending / 🟢 Running.

---

## Oprești și aplicația de pe calculator

- Backend și frontend: **Ctrl+C** în ferestrele lor.
- Baza de date + Redis: `docker compose stop` (datele rămân; **nu** `down -v`, care le șterge).
- Docker Desktop: îl poți închide.

---

## Pe scurt (de pus lângă monitor)

| Vreau să… | Fac |
|---|---|
| lucrez | Docker Desktop → `docker compose up -d postgres redis` → `./mvnw spring-boot:run` → `npm run dev` → localhost:5173 |
| verific că n-am stricat nimic | `./mvnw clean verify` + `npm run lint && npm test && npm run build` |
| salvez în GitHub | `git status` → `git add` → `git commit -m "..."` → `git push origin main` → CI ✅ |
| fac o versiune | `git tag v0.X.Y && git push origin v0.X.Y` → Deploy to Production ✅ |
| o pun online | EC2 Start → `curl` = 401 → comanda `VERSIUNE=0.X.Y` → `curl` + `ps` |
| mă întorc la versiunea veche | comanda `VERSIUNE=` cu numărul vechi |
| închid serverul | EC2 → Stop instance |

Când termini și **Faza 6** (cele 3 secrete GitHub: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`), pasul 7 se face singur la
fiecare tag — cu revenire automată dacă versiunea nouă nu pornește. Detalii în [DEPLOY.md](DEPLOY.md) §3.
