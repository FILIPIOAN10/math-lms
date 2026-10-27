# Math LMS

A learning platform for middle-school maths in Romania, with the national exam (Evaluarea Națională, "EN") in mind.
Teachers build tests with LaTeX, assign homework, grade photographed handwritten work and see each student's
weak chapters at a glance. Students take timed tests, practise with progressive hints and get a daily
spaced-repetition review. Parents follow their child's homework, grades and progress.

**Stack:** Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Redis 7 · React 19 · TypeScript · Vite · Tailwind CSS 4 · KaTeX ·
Docker · GitHub Actions

> The user interface is in Romanian (its users are Romanian teachers, students and parents); the code, commits and
> this README are in English. Every name in the screenshots is fictional demo data.

---

## Screenshots

### Teacher

| | |
|---|---|
| ![Teacher dashboard](docs/screenshots/01-teacher-dashboard.png) | ![EN mock exam in the test builder](docs/screenshots/02-quiz-builder-en.png) |
| **Dashboard.** What needs attention today: work to grade, accounts to approve, homework deadlines and how many students have handed in. | **Test builder.** An EN-format mock exam: *Subiectul I* multiple choice and *Subiectul al III-lea* with requirements a)/b), each with its own points and marking scheme. Each question is linked to its EN chapter (A1…G9). |
| ![Grading a photographed answer](docs/screenshots/03-grading-photo.png) | ![Class mastery heatmap](docs/screenshots/04-class-mastery.png) |
| **Grading.** The student's photo of a handwritten solution, the marking scheme next to each requirement, one-click points per requirement, a bank of reusable comments, and "finish and go to the next one". Multiple-choice questions are graded automatically. | **Mastery per chapter.** Each student × each chapter, from their latest answer to every question linked to that chapter (tests and practice). Below it: who is keeping up with the daily review. |

<p align="center">
  <img src="docs/screenshots/05-quiz-stats.png" alt="Per-question test statistics" width="70%"><br>
  <sub><b>Test statistics.</b> Score distribution and the success rate per question, with the hardest one flagged.</sub>
</p>

### Student

| | |
|---|---|
| ![Student dashboard](docs/screenshots/06-student-dashboard.png) | ![Taking the timed mock exam](docs/screenshots/07-take-exam.png) |
| **Dashboard.** New grades, homework to do, today's review and the latest result. | **Taking a test.** The countdown comes from the server (it can't be paused by fiddling with the clock), answers are saved as you go and queued offline, and the bottom bar jumps between questions (I.1 … III.1). |
| ![Practice mode with hints](docs/screenshots/08-practice-hints.png) | ![Result with teacher feedback](docs/screenshots/09-result-feedback.png) |
| **Practice mode.** Answers are checked on the spot without a grade, and hints are revealed one at a time. | **Result.** Points per question, the correct answer, the marking scheme, the teacher's comment, and "practise what you got wrong". Wrong multiple-choice answers come back in the daily review. |
| ![Progress over time](docs/screenshots/10-progress.png) | ![EN chapter page](docs/screenshots/11-en-chapter.png) |
| **Progress.** Mastery per chapter plus a chart of every graded test. | **EN chapters.** Theory and worked examples for each of the 20 EN chapters; a wrong EN question links straight to its chapter. |

### Parent and mobile

| | | |
|---|---|---|
| ![Parent view of a child](docs/screenshots/12-parent-child.png) | ![Mobile dashboard](docs/screenshots/13-mobile-dashboard.png) | ![Mobile daily review](docs/screenshots/14-mobile-review.png) |
| **Parent.** The child's homework, chapter mastery, progress and every graded test. | **Phone (PWA).** Installable, with a hand-written service worker. | **Daily review.** Spaced repetition: wrong answers come back the next day, known ones after 3, 7, 14 and 30 days. |

---

## Features

**Teaching**
- Classes, books, chapters and exercises; every text field accepts LaTeX (`$\frac{3}{4}$`), rendered with KaTeX.
- A test builder with multiple-choice and open questions, points, marking schemes, up to 5 progressive hints and an
  image under the statement. "Import from text" turns a pasted block (`*` = correct answer, `(2p)`, `R:` solution,
  `I:` hint) into questions.
- EN exam format: *SUBIECTUL I / II / III* sections, requirements a)/b) with their own points, and links from each
  question to its EN chapter (set automatically from the question's position, editable).
- Homework with deadlines and a "who handed in" status per class; optional reminders by email.
- **Test from photos:** photograph 1–5 textbook pages and an OpenAI vision model extracts the exercises into a draft
  test, which the teacher reviews before anything is saved (off by default; there is a fake reader for development).

**Assessment**
- Timed tests with a server-side deadline, autosave, and an offline answer queue that syncs when the connection returns.
- Open answers: up to 5 photos per question, taken straight from the phone camera.
- Grading per attempt or per question across a whole class, with autosave, a comment bank, and per-requirement
  points for EN problems.
- Statistics per test, mastery per chapter (student, class and parent views), and progress over time.
- Practice mode and "practise only what you got wrong".
- Spaced-repetition daily review (Leitner boxes 1–5) with a streak counter.

**Accounts and privacy**
- Email + password and Google sign-in; new accounts wait for the teacher's approval, or join directly through a class
  or parent invite link.
- Roles: teacher (admin), student, parent (linked to one or more children).
- GDPR: data export and two-step account erasure from "My account".
- An audit journal of the teacher's changes (who changed what, and when), written in the same transaction as the change.

## Architecture

```mermaid
flowchart LR
  B["Browser / PWA<br/>React 19 + Vite"] -->|"HTTPS · /api"| N["nginx<br/>static files + reverse proxy"]
  N --> S["Spring Boot 3.5<br/>REST API"]
  S --> P[("PostgreSQL 16<br/>Flyway V1–V38")]
  S --> R[("Redis 7<br/>refresh sessions · cache · rate limits")]
  S -->|"outbox → SMTP"| M["Email"]
  S -.->|"optional"| O["OpenAI<br/>textbook photo reader"]
  T["tutor/ (Python, in progress)<br/>FastAPI + LangChain + pgvector"] -.-> P
```

- **Backend** (`backend/`, package `ro.mathlms`): modules by feature (`quiz`, `grading`, `assignment`, `mastery`,
  `review`, `enchapter`, `ingest`, `parent`, `gdpr`, `audit`, …). The schema is managed only by Flyway; JPA uses
  `ddl-auto: validate`.
- **Auth:** a JWT access token in an HttpOnly cookie, refresh tokens rotated and stored in Redis (one session per
  device), cookie double-submit CSRF, BCrypt, Google OAuth2, and Redis-backed rate limiting on sensitive endpoints.
- **Reliability:** emails go through a transactional outbox (an SMTP outage never loses a message or fails a
  request); Redis caches the statistics and progress aggregates and evicts them after commit.
- **Frontend** (`frontend/`): React 19 + TypeScript, react-router 7, Tailwind CSS 4 with Base UI components,
  KaTeX, and a hand-written service worker that never caches `/api`.
- **Observability:** Actuator + Micrometer → Prometheus, Grafana dashboards and Alertmanager rules
  (`monitoring/`), and structured logs with a correlation ID.
- **`tutor/`:** a Python RAG service in progress (FastAPI, LangChain, pgvector) that will suggest hints to the
  teacher, grounded in the EN chapters' theory. See `tutor/ROADMAP.md`.

## Running locally

You need Docker Desktop, JDK 21 and Node.js 20+.

```bash
# 1. Postgres (port 5433) and Redis (port 6379)
docker compose up -d

# 2. Backend on :8080. It reads backend/.env (copy backend/.env.example and fill it in)
cd backend && ./mvnw spring-boot:run

# 3. Frontend on :5173, proxying /api to :8080
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. Flyway creates the schema on first start. `docs/TESTING.md` §B has a one-command seed
for four dev accounts (password `Admin123!`) and a ten-minute walkthrough that builds a full test dataset through
the UI.

Minimum `backend/.env`: `JWT_SECRET` (`openssl rand -base64 48`), `ADMIN_EMAILS` (the teacher's address) and
`COOKIE_SECURE=false` for plain http. Google sign-in, SMTP and the OpenAI reader are optional; see
`backend/.env.example`.

## Tests

| Layer | Tools | Count |
|---|---|---|
| Backend unit + integration | JUnit 5, Spring Boot Test, **Testcontainers** (real Postgres + Redis) | ~1,050 tests |
| Frontend | Vitest, Testing Library | ~470 tests |
| End to end | Selenium in Chrome against the full production stack (`@Tag("e2e")`) | 9 flows |
| Tutor service | pytest | ~60 tests |

```bash
cd backend && ./mvnw verify        # unit + integration (needs Docker), JaCoCo report
cd frontend && npm run lint && npm test
```

`docs/TESTING.md` also has a manual test script per feature.

## CI/CD and deployment

GitHub Actions:
- **CI** on every push and PR: backend `mvn verify` with Testcontainers and JaCoCo; frontend lint, Vitest and build;
  gitleaks secret scanning and a Trivy scan.
- **CodeQL** static analysis.
- **E2E** (manual): builds both images and runs the Selenium suite against `docker-compose.prod.yml`
  (nginx + backend + Postgres + Redis).
- **Release:** pushing a `v*` tag builds the images, pushes them to GHCR and deploys over SSH, gated by a Trivy
  image scan.

Production runs as `docker-compose.prod.yml` behind nginx. `docs/DEPLOY.md` covers server setup, secrets, backups
(`deploy/backup.sh`, `deploy/restore.sh`) and rollback.

## Project layout

```
backend/      Spring Boot API (src/main/java/ro/mathlms/*, Flyway migrations in src/main/resources/db/migration)
frontend/     React SPA + PWA
tutor/        Python RAG tutor service (in progress)
deploy/       server setup, backup/restore, dev reset and seed SQL
monitoring/   Prometheus, Alertmanager and Grafana config
docs/         TESTING.md, DEPLOY.md, SECURITY-AUDIT.md, EN-capitole.md, screenshots/
```

## Documentation

- [docs/TESTING.md](docs/TESTING.md): running the stack, test accounts, automated tests, and a manual test per feature (Romanian)
- [docs/DEPLOY.md](docs/DEPLOY.md): production deployment
- [docs/SECURITY-AUDIT.md](docs/SECURITY-AUDIT.md): the security audit and its fixes
- [docs/EN-capitole.md](docs/EN-capitole.md): the 20 EN chapters and how each exam question maps to them
