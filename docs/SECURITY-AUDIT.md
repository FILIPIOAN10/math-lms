# Security Audit — math-lms (2026-09-03)

**Scope:** whole repo at commit `acc6661` — Spring Boot backend (`ro.mathlms.*`) + React/Vite
frontend. Defensive review.
**Tools run:** `npm audit` (frontend), `mvn clean test` (311 tests green), manual review per the
OWASP-mapped checklist. `gitleaks`, `trivy`, `dependency-check`, `semgrep` were **not installed** on
this machine, so secret-scan of git history and Java-CVE scanning were done by manual inspection
instead — recommend wiring these into CI (see end).

## Summary

**0 critical · 1 high (fixed) · 3 medium · 1 low.** The app's security posture is solid: parameterised
data access (no SQL/JPQL injection surface), role from a signed invite (no privilege escalation via
request body), quiz ownership enforced server-side (no IDOR), XSS-safe LaTeX rendering, no tokens in
`localStorage`, non-enumerating login, and `.env` secrets correctly kept out of git. The one HIGH — an
auth cookie that was never marked `Secure` — is fixed in this pass. The mediums are hardening
(single-use email tokens, CSRF depth, prod log verbosity) rather than active holes.

## Findings (ranked)

### [HIGH — FIXED ✅] Auth cookie sent over plain HTTP — A02/A05
- **Where:** `auth/JwtCookieFactory.java` (was `cookie.setSecure(false)` hardcoded).
- **Issue:** the JWT session cookie had `Secure=false` with a `// TODO prod` note. In production over
  HTTPS the token would still ride along on any plain-HTTP request to the domain and be interceptable.
- **Fix (commit `acc6661`):** flag driven by `app.auth.cookie-secure`, **default `true`**; local
  plain-HTTP dev opts out with `COOKIE_SECURE=false` in `.env`. `HttpOnly` + `SameSite=Lax` unchanged.

### [MEDIUM] Email verification / password-reset tokens are not single-use — A04/A07
- **Where:** `auth/VerificationTokenService.java`.
- **Issue:** tokens are stateless signed JWS (24h verify / 1h reset TTL, `purpose` claim checked) but
  nothing binds them to one use. A reset token can be **replayed** any number of times within its hour,
  and stays valid **after** the password was already changed or the account already verified.
- **Impact:** a leaked reset link (email forward, browser history, referer) is reusable for up to an
  hour, and a used link is not burned.
- **Fix:** make them single-use without adding state by binding the signature to something that changes
  on use — e.g. include the current password hash (or a `passwordUpdatedAt`) in the token and re-check
  it on verify, so the first successful reset invalidates the token. (Or store a `jti` of consumed tokens.)

### [MEDIUM] CSRF disabled globally, relying only on SameSite=Lax — A05
- **Where:** `auth/SecurityConfig.java` (`.csrf(csrf -> csrf.disable())`).
- **Issue:** all state-changing endpoints use the cookie for auth with CSRF fully off. `SameSite=Lax`
  blocks cross-site POST/PUT/DELETE, so there's no concrete exploit today, but it's the *only* layer —
  no defense in depth. This matters more once refresh-token cookies are added.
- **Fix:** enable Spring's cookie CSRF (`CookieCsrfTokenRepository.withHttpOnlyFalse()`) with the auth
  endpoints as deliberate `ignoringRequestMatchers`, and have the SPA echo the `XSRF-TOKEN`. Revisit
  together with the refresh-token work.

### [MEDIUM] Verbose Spring Security logging in every profile — A09
- **Where:** `application.yml` → `logging.level.org.springframework.security: DEBUG`.
- **Issue:** DEBUG security logging is global (no prod override); in production it is noisy and can log
  authentication-flow detail. Combined with `format_sql: true`, prod logs get chatty.
- **Fix:** keep DEBUG only in a dev profile; default `INFO`/`WARN` for security in prod.

### [LOW] Frontend dependencies with moderate advisories — A06
- **Where:** `frontend` — `katex` (htmlData attribute validation) and `qs` (array-limit / isBuffer DoS).
- **Issue:** both **moderate**; `katex` output is admin-authored LaTeX (low reachability), `qs` is
  transitive.
- **Fix:** `npm audit fix` (verify the KaTeX bump doesn't change rendering). Not urgent.

## Not findings (checked, ruled out)

- **SQL/JPQL injection** — no `nativeQuery`, no string-built queries; all Spring Data derived or
  `:param` `@Query`. No `Runtime.exec`/`ProcessBuilder`.
- **IDOR (quiz)** — student attempt endpoints enforce `attempt.student.email == caller`
  (`requireOwnedInProgress`); item∈quiz and option∈item validated. Teacher/admin endpoints are ADMIN-only.
- **IDOR (content)** — Book/Chapter/Exercise/Class/Enrollment reads are shared curriculum (STATUS_ACTIVE),
  writes are ADMIN under `/api/admin/**`; nothing is per-user owned.
- **Privilege escalation / mass assignment** — registration role comes from a signed invite token, never
  the request body; user role changes are ADMIN-only.
- **Path traversal** — upload filenames are server-generated UUIDs; `FileService.loadImage` now guards
  `filePath.startsWith(base)`.
- **XSS** — `MathContent` sets `el.textContent` (text node) then KaTeX auto-render; no
  `dangerouslySetInnerHTML` anywhere.
- **Token theft via JS** — auth is an `HttpOnly` cookie; no JWT in `localStorage`/`sessionStorage`.
- **Login user-enumeration** — unknown email and wrong password both throw the same
  `BadCredentials("Invalid credentials")`; forgot-password always returns 200.
- **Secret leakage** — `backend/.env` (real Google/JWT/SMTP secrets) is gitignored and untracked; no
  `.env` committed anywhere. JWT secret comes from env; jjwt enforces ≥256-bit HS256 at boot and rejects
  `alg:none`.
- **CORS** — none configured (safe default; pin origins if added).
- **Actuator** — only `health,info` exposed; health details `when-authorized`.

## Known gaps (by roadmap, not defects)

- No refresh-token rotation / server-side revocation yet — logout and password-reset don't invalidate
  existing JWTs before expiry. **Being addressed next** (refresh-token rotation + Redis sessions).
- No login lockout / rate limiting on auth endpoints (brute-force). Consider adding.

## Recommended: wire scanners into CI

Since `gitleaks`/`trivy`/`dependency-check` aren't available locally, add them to CI (the
`github-actions-fullstack-cicd` skill ships gitleaks + Trivy fs + dependency-review + CodeQL jobs) so
every PR is scanned for secrets and CVEs automatically.
