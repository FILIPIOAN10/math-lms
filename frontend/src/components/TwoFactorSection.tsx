import { useEffect, useId, useState, type FormEvent } from 'react'
import QRCode from 'qrcode'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { HomeSection } from '@/components/home/HomeBits'
import { useAuth } from '@/context/AuthContext'
import {
  disableTwoFactor,
  enableTwoFactor,
  getTwoFactorStatus,
  regenerateRecoveryCodes,
  startTwoFactorSetup,
  type TwoFactorStatus,
} from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { LoadingCards } from '@/components/LoadingCards'

const okText = 'text-sm text-emerald-700 dark:text-emerald-300'

/** A 6-digit code field (also takes a recovery code where that is allowed). */
function CodeField({ label, value, onChange, disabled }: {
  label: string; value: string; onChange: (v: string) => void; disabled?: boolean
}) {
  const id = useId()
  return (
    <div className="max-w-xs space-y-1">
      <label htmlFor={id} className="text-sm font-medium">{label}</label>
      <Input id={id} inputMode="numeric" autoComplete="one-time-code" value={value} disabled={disabled}
             onChange={(e) => onChange(e.target.value)} />
    </div>
  )
}

/** The ten recovery codes, shown once, with a copy button. */
function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const [copied, setCopied] = useState(false)
  return (
    <div className="space-y-3" data-testid="recovery-codes">
      <p className="font-medium">Codurile de recuperare — le vezi doar acum</p>
      <p className="text-muted-foreground">
        Fiecare merge o singură dată, în locul codului din aplicație, dacă pierzi telefonul. Păstrează-le în managerul de
        parole sau tipărite, nu în același telefon.
      </p>
      <ul className="grid grid-cols-2 gap-1 font-mono text-sm">
        {codes.map((c) => <li key={c}>{c}</li>)}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" onClick={() => void navigator.clipboard?.writeText(codes.join('\n')).then(() => setCopied(true))}>
          {copied ? 'Copiate ✓' : 'Copiază codurile'}
        </Button>
        <Button onClick={onDone}>Le-am salvat</Button>
      </div>
    </div>
  )
}

/**
 * F8 on "Contul meu", for staff: turn on two-step sign-in (scan the QR code, type the app's first code, save the
 * recovery codes), get new recovery codes, or — for a teacher — turn it off. Required for an admin, who sees why.
 */
export function TwoFactorSection() {
  const { refreshUser } = useAuth()
  const [status, setStatus] = useState<TwoFactorStatus | null>(null)
  const [setup, setSetup] = useState<{ secret: string; qr: string } | null>(null)
  const [code, setCode] = useState('')
  const [codes, setCodes] = useState<string[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const reload = () => getTwoFactorStatus().then(setStatus).catch((e) => setError(errorMessage(e)))
  useEffect(() => {
    void reload()
  }, [])

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    setDone(null)
    try {
      await action()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const begin = () => run(async () => {
    const started = await startTwoFactorSetup()
    setSetup({ secret: started.secret, qr: await QRCode.toDataURL(started.otpauthUri, { margin: 1, width: 192 }) })
    setCode('')
  })

  const enable = (event: FormEvent) => {
    event.preventDefault()
    return run(async () => {
      setCodes(await enableTwoFactor(code.trim()))
      setSetup(null)
      setCode('')
      await reload()
      await refreshUser() // an admin's staff pages open now
    })
  }

  const newCodes = () => run(async () => {
    setCodes(await regenerateRecoveryCodes(code.trim()))
    setCode('')
    await reload()
  })

  const turnOff = () => run(async () => {
    await disableTwoFactor(code.trim())
    setCode('')
    setDone('Autentificarea în doi pași a fost oprită.')
    await reload()
    await refreshUser()
  })

  return (
    <HomeSection title="Autentificare în doi pași">
      <Card>
        <CardContent className="space-y-3 py-4 text-sm" data-testid="two-factor-section">
          {codes ? (
            <RecoveryCodes codes={codes} onDone={() => setCodes(null)} />
          ) : !status ? (
            <LoadingCards />
          ) : status.enabled ? (
            <>
              <p className={okText}>✓ Activă. Coduri de recuperare rămase: {status.recoveryCodesLeft} din 10.</p>
              <p className="text-muted-foreground">
                Pentru coduri noi{status.required ? '' : ' sau ca s-o oprești'}, scrie codul de acum din aplicație.
              </p>
              <CodeField label="Codul din aplicație" value={code} onChange={setCode} disabled={busy} />
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" onClick={() => void newCodes()} disabled={busy || code.trim() === ''}>
                  Coduri de recuperare noi
                </Button>
                {!status.required && (
                  <Button variant="destructive" onClick={() => void turnOff()} disabled={busy || code.trim() === ''}>
                    Oprește
                  </Button>
                )}
              </div>
            </>
          ) : setup ? (
            <form className="space-y-3" onSubmit={(e) => void enable(e)}>
              <p>1. Scanează codul cu aplicația de autentificare (Google Authenticator, Microsoft Authenticator…).</p>
              <img src={setup.qr} alt="Cod QR pentru aplicația de autentificare" width={192} height={192}
                   className="rounded border bg-white p-1" />
              <p className="text-muted-foreground">
                Nu poți scana? Adaugă manual cheia: <code className="break-all font-mono" data-testid="two-factor-secret">{setup.secret}</code>
              </p>
              <p>2. Scrie codul de 6 cifre pe care îl arată aplicația:</p>
              <CodeField label="Codul din aplicație" value={code} onChange={setCode} disabled={busy} />
              <Button type="submit" disabled={busy || code.trim() === ''}>Activează</Button>
            </form>
          ) : (
            <>
              <p className="text-muted-foreground">
                La conectare îți cerem, pe lângă parolă (sau Google), un cod din aplicația de pe telefon. Cine îți află
                parola tot nu intră.
                {status.required && <strong> Pentru administratori e obligatorie: paginile de profesor se deschid după ce o activezi.</strong>}
              </p>
              <Button onClick={() => void begin()} disabled={busy}>Activează autentificarea în doi pași</Button>
            </>
          )}
          {error && <p role="alert" className="text-destructive">{error}</p>}
          {done && <p role="status" className={okText}>{done}</p>}
        </CardContent>
      </Card>
    </HomeSection>
  )
}
