import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAuth } from '@/context/AuthContext'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

/** Turns a failed login into a message that is safe to show the user. */
function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Email sau parolă greșite.'
    }
    if (error.status === 429) {
      return 'Prea multe încercări. Așteaptă un minut și încearcă din nou.'
    }
    if (error.status === 403) {
      switch (error.body) {
        case 'EMAIL_NOT_VERIFIED':
        case 'PENDING_VERIFICATION':
          return 'Confirmă-ți adresa de email înainte de a te conecta.'
        case 'PENDING_APPROVAL':
          return 'Contul tău așteaptă aprobarea unui administrator.'
        case 'REJECTED':
          return 'Contul tău a fost respins. Contactează administratorul.'
        default:
          return 'Contul tău nu este activ încă.'
      }
    }
  }
  return 'A apărut o eroare. Încearcă din nou.'
}

function GoogleTab() {
  function signInWithGoogle() {
    window.location.href = '/oauth2/authorization/google'
  }

  return (
    <div className="flex flex-col gap-3">
      <Button className="w-full" onClick={signInWithGoogle}>
        Continuă cu Google
      </Button>
      <p className="text-center text-xs text-muted-foreground">
        Emailul e deja verificat de Google.
      </p>
    </div>
  )
}

/**
 * F8: the second step, after the password (or after Google, which lands on /login?step=2fa). Takes the 6-digit code
 * from the authenticator app, or one of the recovery codes. A 401 means the half-done sign-in is gone (5 minutes,
 * or too many tries): back to the start.
 */
function TwoFactorStep({ onRestart }: { onRestart: () => void }) {
  const { verifyTwoFactor } = useAuth()
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await verifyTwoFactor(code.trim())
      // On success the AuthProvider sets the user and the router redirects.
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError(err.body || 'Conectarea a expirat. Conectează-te din nou.')
        setCode('')
      } else if (err instanceof ApiError && err.status === 400) {
        setError('Codul nu se potrivește. Încearcă codul nou din aplicație.')
      } else {
        setError(loginErrorMessage(err))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate data-testid="two-factor-step">
      <p className="text-sm text-muted-foreground">
        Deschide aplicația de autentificare (Google Authenticator, Microsoft Authenticator…) și scrie codul de 6 cifre
        pentru Math LMS. Fără telefon? Folosește unul dintre codurile de recuperare.
      </p>
      <div className="flex flex-col gap-2">
        <Label htmlFor="two-factor-code">Codul din aplicație</Label>
        <Input
          id="two-factor-code"
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          required
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      <Button type="submit" className="w-full" disabled={submitting || code.trim() === ''}>
        {submitting ? 'Se verifică…' : 'Continuă'}
      </Button>
      <Button type="button" variant="ghost" className="w-full" onClick={onRestart}>
        Înapoi la conectare
      </Button>
    </form>
  )
}

function PasswordTab({ onNeedCode }: { onNeedCode: () => void }) {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const user = await login(email, password)
      // On success the AuthProvider sets the user and the router redirects; null = the authenticator code is next.
      if (user === null) onNeedCode()
    } catch (err) {
      setError(loginErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      <div className="flex flex-col gap-2">
        <Label htmlFor="email">Email</Label>
        <Input
          id="email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </div>
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <Label htmlFor="password">Parolă</Label>
          <Link
            to="/forgot-password"
            className="text-xs text-muted-foreground underline-offset-4 hover:underline"
          >
            Ai uitat parola?
          </Link>
        </div>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? 'Se conectează…' : 'Conectează-te'}
      </Button>
    </form>
  )
}

export function LoginPage() {
  usePageTitle('Autentificare')
  const [searchParams, setSearchParams] = useSearchParams()
  // Google sends a sign-in that still needs its code here with ?step=2fa; the password tab switches to it itself.
  const [needCode, setNeedCode] = useState(searchParams.get('step') === '2fa')

  function restart() {
    setNeedCode(false)
    setSearchParams({}, { replace: true })
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">Math LMS</CardTitle>
          <CardDescription>
            {needCode ? 'Autentificare în doi pași' : 'Autentifică-te ca să continui'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {needCode ? <TwoFactorStep onRestart={restart} /> : (
            <Tabs defaultValue="google">
              <TabsList>
                <TabsTrigger value="google">Google</TabsTrigger>
                <TabsTrigger value="password">Email și parolă</TabsTrigger>
              </TabsList>
              <TabsContent value="google">
                <GoogleTab />
              </TabsContent>
              <TabsContent value="password">
                <PasswordTab onNeedCode={() => setNeedCode(true)} />
              </TabsContent>
            </Tabs>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
