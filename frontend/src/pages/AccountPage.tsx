import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { HomeSection } from '@/components/home/HomeBits'
import { PageHeader } from '@/components/PageHeader'
import { useConfirm } from '@/components/useConfirm'
import { useAuth } from '@/context/AuthContext'
import {
  ApiError,
  changePassword,
  downloadMyData,
  getAccount,
  listSessions,
  logoutAll,
  requestAccountErasure,
  revokeSession,
  type Account,
  type DeviceSession,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { roleLabel } from '@/lib/roles'

const okText = 'text-sm text-emerald-700 dark:text-emerald-300'

function Field({ label, ...props }: { label: string } & React.ComponentProps<typeof Input>) {
  const id = useId()
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-sm font-medium">{label}</label>
      <Input id={id} {...props} />
    </div>
  )
}

// ---------- Profile ----------

function signInMethods(account: Account): string {
  if (account.hasPassword && account.googleLinked) return 'Email și parolă, Google'
  if (account.googleLinked) return 'Google'
  return 'Email și parolă'
}

function Profile({ account }: { account: Account }) {
  return (
    <HomeSection title="Profil">
      <Card>
        <CardContent className="grid gap-x-6 gap-y-2 py-4 text-sm sm:grid-cols-[auto_1fr]">
          <span className="text-muted-foreground">Nume</span>
          <span className="font-medium">{account.fullName}</span>
          <span className="text-muted-foreground">Email</span>
          <span className="break-all">{account.email}</span>
          <span className="text-muted-foreground">Rol</span>
          <span>{roleLabel(account.role)}</span>
          <span className="text-muted-foreground">Conectare</span>
          <span>{signInMethods(account)}</span>
        </CardContent>
      </Card>
    </HomeSection>
  )
}

// ---------- Password ----------

function PasswordSection({ account, onChanged }: { account: Account; onChanged: () => void }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setDone(false)
    if (next.length < 8) {
      setError('Parola nouă trebuie să aibă cel puțin 8 caractere.')
      return
    }
    if (next !== repeat) {
      setError('Parolele noi nu coincid.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await changePassword(current, next)
      setCurrent('')
      setNext('')
      setRepeat('')
      setDone(true)
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <HomeSection title="Parola">
      <Card>
        <CardContent className="py-4">
          {!account.hasPassword ? (
            <div className="space-y-3 text-sm">
              <p className="text-muted-foreground">
                Te conectezi cu Google, deci contul nu are o parolă proprie. Dacă vrei să te poți conecta și cu email și parolă,
                setează una prin emailul de resetare.
              </p>
              <Link to="/forgot-password" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                Setează o parolă
              </Link>
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-3" noValidate>
              <Field label="Parola actuală" type="password" autoComplete="current-password" value={current}
                     onChange={(e) => setCurrent(e.target.value)} disabled={busy} />
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Parola nouă" type="password" autoComplete="new-password" value={next}
                       onChange={(e) => setNext(e.target.value)} disabled={busy} />
                <Field label="Repetă parola nouă" type="password" autoComplete="new-password" value={repeat}
                       onChange={(e) => setRepeat(e.target.value)} disabled={busy} />
              </div>
              <p className="text-xs text-muted-foreground">
                Cel puțin 8 caractere. După schimbare, celelalte dispozitive sunt deconectate; acesta rămâne conectat.
              </p>
              {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
              {done && <p role="status" className={okText}>✓ Parola a fost schimbată. Celelalte dispozitive au fost deconectate.</p>}
              <Button type="submit" disabled={busy || current === '' || next === ''}>
                {busy ? 'Se schimbă...' : 'Schimbă parola'}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </HomeSection>
  )
}

// ---------- Sessions ----------

function SessionsSection({ sessions, reload, onLogoutAll }: {
  sessions: DeviceSession[] | null
  reload: () => Promise<void>
  onLogoutAll: () => void
}) {
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function revoke(s: DeviceSession) {
    setBusy(s.sessionId)
    setError(null)
    try {
      await revokeSession(s.sessionId)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <HomeSection
      title="Dispozitive conectate"
      action={<Button size="sm" variant="outline" onClick={onLogoutAll}>Deconectează-te de peste tot</Button>}
    >
      <Card>
        <CardContent className="divide-y py-1">
          {sessions === null ? (
            <p className="py-3 text-sm text-muted-foreground">Se încarcă...</p>
          ) : sessions.length === 0 ? (
            <p className="py-3 text-sm text-muted-foreground">Nicio sesiune activă.</p>
          ) : (
            sessions.map((s) => (
              <div key={s.sessionId} data-testid="session-row" className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-3">
                <div className="min-w-0 text-sm">
                  <p className="font-medium">
                    {s.deviceInfo}
                    {s.current && (
                      <span className="ml-2 rounded-md bg-emerald-500/15 px-1.5 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                        Acest dispozitiv
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    IP {s.ipAddress} · folosit ultima dată {formatDate(new Date(s.lastUsedAt).toISOString())}
                  </p>
                </div>
                {!s.current && (
                  <Button size="sm" variant="ghost" onClick={() => revoke(s)} disabled={busy === s.sessionId}
                          aria-label={`Deconectează ${s.deviceInfo}`}>
                    Deconectează
                  </Button>
                )}
              </div>
            ))
          )}
          {error && <p role="alert" className="py-2 text-sm text-destructive">{error}</p>}
        </CardContent>
      </Card>
    </HomeSection>
  )
}

// ---------- Data export ----------

function DataSection() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function download() {
    setBusy(true)
    setError(null)
    try {
      const blob = await downloadMyData()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'datele-mele-mathlms.zip'
      a.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <HomeSection title="Datele tale">
      <Card>
        <CardContent className="space-y-3 py-4 text-sm">
          <p className="text-muted-foreground">
            O arhivă ZIP cu tot ce știe aplicația despre tine: profilul, clasele, testele date cu răspunsurile și notele,
            comentariile profesorului și pozele încărcate.
          </p>
          {error && <p role="alert" className="text-destructive">{error}</p>}
          <Button variant="outline" onClick={download} disabled={busy}>
            {busy ? 'Se pregătește...' : 'Descarcă datele mele'}
          </Button>
        </CardContent>
      </Card>
    </HomeSection>
  )
}

// ---------- Erasure ----------

function EraseSection({ account, confirm }: {
  account: Account
  confirm: ReturnType<typeof useConfirm>['confirm']
}) {
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  async function request() {
    const yes = await confirm({
      title: 'Ștergi contul?',
      body: 'Îți trimitem un email cu un link de confirmare. Contul se șterge abia când deschizi linkul.',
      confirmLabel: 'Trimite emailul',
      destructive: true,
    })
    if (!yes) return
    setBusy(true)
    setError(null)
    try {
      await requestAccountErasure(account.hasPassword ? password : null)
      setPassword('')
      setSent(true)
    } catch (e) {
      // This endpoint answers a wrong password with 401 (it re-checks the credentials, like a login).
      setError(e instanceof ApiError && e.status === 401 ? 'Parola nu e corectă.' : errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <HomeSection title="Șterge contul">
      <Card className="ring-destructive/30">
        <CardContent className="space-y-3 py-4 text-sm">
          {account.role === 'ADMIN' ? (
            <p className="text-muted-foreground">
              Contul de profesor nu se poate șterge din aplicație: fără el nimeni nu mai poate corecta lucrări sau aproba conturi.
            </p>
          ) : (
            <>
              <p className="text-muted-foreground">
                Datele tale personale se șterg: numele, emailul, parola, înscrierile, pozele și comentariile profesorului.
                Notele rămân în statisticile clasei, dar fără numele tău. Nu se poate anula.
              </p>
              {account.hasPassword && (
                <div className="max-w-xs">
                  <Field label="Parola (pentru confirmare)" type="password" autoComplete="current-password" value={password}
                         onChange={(e) => setPassword(e.target.value)} disabled={busy} />
                </div>
              )}
              {error && <p role="alert" className="text-destructive">{error}</p>}
              {sent && (
                <p role="status" className={okText}>
                  ✓ Ți-am trimis un email la {account.email}. Deschide linkul din el ca să confirmi ștergerea.
                </p>
              )}
              <Button variant="destructive" onClick={request} disabled={busy || (account.hasPassword && password === '')}>
                Șterge contul
              </Button>
            </>
          )}
        </CardContent>
      </Card>
    </HomeSection>
  )
}

// ---------- Page ----------

/**
 * "Contul meu", for every role: who you are, the password, the devices signed in, your data (GDPR Art. 15) and
 * deleting the account (Art. 17, confirmed by email). Everything acts on the signed-in account only.
 */
export function AccountPage() {
  const { logout } = useAuth()
  const { confirm, dialog } = useConfirm()
  const [account, setAccount] = useState<Account | null>(null)
  const [sessions, setSessions] = useState<DeviceSession[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reloadSessions = useCallback(
    () => listSessions().then(setSessions).catch((e) => setError(errorMessage(e))),
    [],
  )

  useEffect(() => {
    let cancelled = false
    getAccount()
      .then((a) => !cancelled && setAccount(a))
      .catch((e) => !cancelled && setError(errorMessage(e)))
    listSessions()
      .then((s) => !cancelled && setSessions(s))
      .catch(() => !cancelled && setSessions([]))
    return () => {
      cancelled = true
    }
  }, [])

  async function signOutEverywhere() {
    const yes = await confirm({
      title: 'Te deconectezi de peste tot?',
      body: 'Toate dispozitivele, inclusiv acesta, vor trebui să se conecteze din nou.',
      confirmLabel: 'Deconectează-te',
    })
    if (!yes) return
    try {
      await logoutAll()
    } catch (e) {
      setError(errorMessage(e))
      return
    }
    await logout().catch(() => undefined) // the server already ended every session; this clears the app's state
  }

  return (
    <div className="p-4">
      <div className="mx-auto max-w-3xl space-y-6">
        <PageHeader title="Contul meu" description="Datele contului, parola, dispozitivele conectate și datele tale." />
        {dialog}
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!account && !error && <p className="text-muted-foreground">Se încarcă...</p>}
        {account && (
          <>
            <Profile account={account} />
            <PasswordSection account={account} onChanged={() => void reloadSessions()} />
            <SessionsSection sessions={sessions} reload={reloadSessions} onLogoutAll={() => void signOutEverywhere()} />
            <DataSection />
            <EraseSection account={account} confirm={confirm} />
          </>
        )}
      </div>
    </div>
  )
}
