# Secrete și chei: inventar, separare, rotație (planul de securitate, faza 2)

Principiul (*Software Security for Developers*, cap. 2.2, p. 24–27; p. 57, 112): **o cheie pentru fiecare treabă**,
păstrată într-un serviciu de credențiale, nu în fișiere, și **rotită** după un plan scris.

## 1. Inventar

| Secret | Ce face | Unde e folosit | Ce se întâmplă la rotație | Cât de des |
|---|---|---|---|---|
| `JWT_SECRET` | semnează cookie-ul de sesiune (tokenul de acces, 15 min) | backend `JwtService` | toți sunt delogați; refresh-ul (Redis) rămâne valid, deci reintră singuri | anual sau la suspiciune |
| `EMAIL_TOKEN_SECRET` | semnează linkurile: verificare email, resetare parolă, ștergere cont, export GDPR, invitații pe rol | backend `SecretKeys` → `VerificationTokenService`, `InviteTokenService` | linkurile deja trimise nu mai merg (expiră oricum în ≤ 7 zile); invitațiile pe rol trebuie re-generate | anual sau la suspiciune |
| `TOTP_ENC_KEY` | criptează secretele 2FA (AES-256-GCM, format `v1:`) | backend `TotpSecretCipher` | **nimeni nu își refă 2FA-ul**: cheia veche merge în `TOTP_ENC_KEY_PREVIOUS`, la pornire totul se re-criptează (`TotpKeyMigration`) | anual sau la suspiciune |
| `POSTGRES_PASSWORD` | parola bazei | postgres, backend, tutor | `ALTER USER` + repornire backend și tutor | anual |
| `REDIS_PASSWORD` | parola Redis | redis, backend | repornirea întregii stive; sesiunile rămân în Redis (AOF) | anual |
| `GOOGLE_CLIENT_SECRET` | login cu Google | backend | se generează în Google Cloud Console, apoi repornire | la suspiciune |
| `SMTP_PASS` | trimiterea emailurilor | backend | parolă nouă de aplicație Gmail / relay, apoi repornire | la suspiciune |
| `TUTOR_SERVICE_TOKEN` | backend → tutor | backend + tutor (aceeași valoare) | ambele repornite împreună | anual |
| `METRICS_TOKEN` | Prometheus → `/actuator/prometheus` | backend + `monitoring/secrets/metrics-token` | ambele actualizate | anual |
| `OPENAI_API_KEY`, `./secrets/gcp-vertex.json` | furnizorii AI | backend, tutor | cheie nouă la furnizor, cea veche revocată | la suspiciune; JSON-ul Gemini anual |
| `GRAFANA_PASSWORD` | Grafana (doar 127.0.0.1) | grafana | din UI-ul Grafana | anual |
| cheia privată `age` a backupurilor | decriptează backupurile | **doar la tine** (manager de parole) și în secretul GitHub al testului de restaurare | o cheie nouă: `BACKUP_AGE_RECIPIENT` nou; backupurile vechi rămân pe cheia veche | la suspiciune |

Reguli:
- fiecare valoare se generează cu `openssl rand -hex 32`;
- niciuna nu se repetă: aplicația **refuză să pornească** dacă `EMAIL_TOKEN_SECRET` sau `TOTP_ENC_KEY` sunt mai scurte de 32 de caractere, egale cu `JWT_SECRET` sau egale între ele.

## 2. Deploy-ul fazei 2 (o singură dată)

1. Pe server, adaugă în `.env.prod` (sau direct în SSM, vezi §3) două chei noi:
   ```bash
   echo "EMAIL_TOKEN_SECRET=$(openssl rand -hex 32)" | sudo tee -a .env.prod >/dev/null
   echo "TOTP_ENC_KEY=$(openssl rand -hex 32)" | sudo tee -a .env.prod >/dev/null
   ```
   **`JWT_SECRET` rămâne neschimbat la acest deploy**: din el se citesc secretele 2FA vechi, ca să fie re-criptate.
2. Faci deploy-ul versiunii noi. `docker-compose.prod.yml` refuză să pornească fără cele două chei.
3. Verifici în log că secretele 2FA au trecut pe cheia nouă:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod logs backend | grep "TOTP key migration"
   ```
   Te aștepți la „N secret(s) re-encrypted with TOTP_ENC_KEY, 0 unreadable”. Un admin intră cu codul din aplicație, ca de obicei.
4. **La 7 zile după deploy** pui `ACCEPT_LEGACY_LINK_TOKENS=false` și repornești backendul. Până atunci, linkurile trimise
   înainte de deploy, semnate cu `JWT_SECRET`, încă merg; toate expiră în maximum 7 zile.
5. Abia de acum se poate roti `JWT_SECRET` fără să strice 2FA-ul cuiva.

## 3. Mutarea secretelor în AWS SSM Parameter Store

Rezultatul: secretele nu mai stau în clar pe discul serverului. Rămân criptate cu KMS în SSM, cu istoric de versiuni și
audit în CloudTrail. `.env.prod` păstrează doar setările care nu sunt secrete.

1. **IAM** (în consolă): pe rolul instanței EC2 adaugi o politică cu
   `ssm:GetParametersByPath` și `ssm:PutParameter` pe `arn:aws:ssm:eu-central-1:<cont>:parameter/math-lms/prod*`.
   Cu cheia implicită `aws/ssm` nu e nevoie de altceva; cu o cheie KMS proprie adaugi `kms:Decrypt` și `kms:Encrypt` pe ea.
   `PutParameter` îl poți scoate după pasul 2.
2. **Copierea**: `./deploy/secrets-to-ssm.sh` arată ce ar scrie (doar numele și lungimea, niciodată valoarea), iar
   `./deploy/secrets-to-ssm.sh --apply` le scrie.
3. **Pornirea cu secretele din SSM**: orice comandă `docker compose` trece prin wrapper:
   ```bash
   ./deploy/with-ssm-secrets.sh docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-build
   ```
   Același lucru pentru `logs`, `exec` și `ps`: compose cere variabilele obligatorii chiar și pentru ele. Ca alias:
   `alias dc='./deploy/with-ssm-secrets.sh docker compose -f docker-compose.prod.yml --env-file .env.prod'`.
4. **Cron-ul de backup** trebuie trecut și el prin wrapper, altfel `backup.sh` nu mai poate rula `docker compose exec`:
   ```cron
   15 3 * * *  cd /opt/math-lms && ./deploy/with-ssm-secrets.sh ./deploy/backup.sh /var/backups/math-lms >> /var/log/math-lms-backup.log 2>&1
   ```
5. **Scoaterea secretelor din `.env.prod`**, numai după ce pasul 3 a pornit stiva și site-ul răspunde: păstrezi întâi o copie
   criptată (`age -r <cheia ta publică> .env.prod > ~/env.prod.age`), apoi ștergi din fișier liniile secretelor din §1.
   Copia `.env.prod` de pe laptop o ștergi sau o muți în managerul de parole.

## 4. Rotația, pas cu pas

**`TOTP_ENC_KEY`** (fără re-înrolare):
`TOTP_ENC_KEY_PREVIOUS` = valoarea veche, `TOTP_ENC_KEY` = una nouă, apoi repornești backendul. În log apare
„re-encrypted”; după aceea golești `TOTP_ENC_KEY_PREVIOUS` și repornești.

**`EMAIL_TOKEN_SECRET`**: o valoare nouă și repornire. Nu are o variantă „previous”: linkurile trăiesc puțin (≤ 7 zile).
Linkurile de email deja trimise nu mai merg (oamenii cer altul), iar invitațiile pe rol active le re-generezi.

**`JWT_SECRET`**: o valoare nouă și repornire; utilizatorii reintră singuri prin refresh. Faci asta numai după ce pasul 3
din §2 a arătat că secretele 2FA sunt pe cheia nouă.

**La suspiciune de scurgere** (cartea, p. 100: o cheie furată înseamnă că le presupui pe toate furate): rotești toate
secretele din §1, apoi „Deconectează-mă peste tot” pentru conturile de staff. Verifici jurnalul și alertele.

## 5. Calendar

- **La 7 zile după deploy-ul fazei 2:** `ACCEPT_LEGACY_LINK_TOKENS=false`.
- **Anual (de exemplu, în ianuarie):** `JWT_SECRET`, `EMAIL_TOKEN_SECRET`, `TOTP_ENC_KEY`, parolele Postgres și Redis,
  `TUTOR_SERVICE_TOKEN`, `METRICS_TOKEN`, JSON-ul Gemini.
- **După orice incident sau om plecat din proiect:** toate.
