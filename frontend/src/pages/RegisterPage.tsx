import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthLayout } from '@/components/AuthLayout'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, previewInvite, register, type InvitePreview } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

/** Turns a failed registration into a message safe to show the user. */
function registerErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return 'Există deja un cont cu acest email.'
    }
    if (error.status === 400) {
      return 'Linkul de invitație este invalid sau a expirat. Cere altul administratorului.'
    }
    // A class/parent link that was revoked, expired or filled up: the server says which.
    if (error.status === 410 && error.body) {
      return error.body
    }
  }
  return 'A apărut o eroare. Încearcă din nou.'
}

/** "Te alături clasei Clasa a 8-a" — what the newcomer is signing up for. */
function inviteHeadline(preview: InvitePreview): string {
  if (preview.className) return `Te alături clasei ${preview.className}`
  if (preview.role === 'TEACHER') return 'Cont de profesor'
  return preview.role === 'PARENT' ? 'Cont de părinte' : 'Cont de elev'
}

/** Shown instead of the form when the link can no longer be used (revoked, expired, full, unknown). */
function DeadInvite({ reason }: { reason: string }) {
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Link indisponibil</CardTitle>
        <CardDescription role="alert">{reason}</CardDescription>
      </CardHeader>
      <CardContent className="text-center">
        <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
          Înapoi la autentificare
        </Link>
      </CardContent>
    </Card>
  )
}

/** Shown when the register page is opened without an invite token in the URL. */
function MissingInvite() {
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Invitație necesară</CardTitle>
        <CardDescription>
          Înregistrarea se face doar printr-un link de invitație primit de la administrator.
        </CardDescription>
      </CardHeader>
      <CardContent className="text-center">
        <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
          Înapoi la autentificare
        </Link>
      </CardContent>
    </Card>
  )
}

/** Success screen after the account is created and the verification email is sent. */
function VerifyEmailNotice({ email, direct }: { email: string; direct: boolean }) {
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Verifică-ți emailul</CardTitle>
        <CardDescription>
          Ți-am trimis un link de confirmare la <strong>{email}</strong>.{' '}
          {direct
            ? 'Deschide-l și contul tău e gata — te poți conecta imediat.'
            : 'Deschide-l ca să îți activezi contul, apoi așteaptă aprobarea profesorului.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="text-center">
        <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
          Înapoi la autentificare
        </Link>
      </CardContent>
    </Card>
  )
}

function RegisterForm({ inviteToken, preview }: { inviteToken: string; preview: InvitePreview | null }) {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (password.length < 8) {
      setError('Parola trebuie să aibă cel puțin 8 caractere.')
      return
    }
    if (password !== confirm) {
      setError('Parolele nu coincid.')
      return
    }

    setSubmitting(true)
    try {
      await register({ email, fullName, password, inviteToken })
      setDone(true)
    } catch (err) {
      setError(registerErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return <VerifyEmailNotice email={email} direct={preview?.direct ?? false} />
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Creează-ți contul</CardTitle>
        {preview && (
          <p data-testid="invite-headline" className="font-medium">
            {inviteHeadline(preview)}
          </p>
        )}
        <CardDescription>Completează datele ca să continui</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <div className="flex flex-col gap-2">
            <Label htmlFor="fullName">Nume complet</Label>
            <Input
              id="fullName"
              autoComplete="name"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          </div>
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
            <Label htmlFor="password">Parolă</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="confirm">Confirmă parola</Label>
            <Input
              id="confirm"
              type="password"
              autoComplete="new-password"
              required
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? 'Se creează…' : 'Creează contul'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

/**
 * Asks the server what the link is for before showing the form, so a dead link is refused up front (not after the
 * newcomer typed everything) and a class link can say which class. If the preview fails for another reason (network,
 * server), the form is still offered — registering gives the definitive answer.
 */
function InvitedRegistration({ inviteToken }: { inviteToken: string }) {
  const [preview, setPreview] = useState<InvitePreview | null>(null)
  const [deadReason, setDeadReason] = useState<string | null>(null)
  const [checked, setChecked] = useState(false)

  useEffect(() => {
    let cancelled = false
    previewInvite(inviteToken)
      .then((p) => {
        if (!cancelled) setPreview(p)
      })
      .catch((e) => {
        if (!cancelled && e instanceof ApiError && e.status === 410) {
          setDeadReason(e.body || 'Linkul de invitație nu mai e valid. Cere profesorului unul nou.')
        }
      })
      .finally(() => {
        if (!cancelled) setChecked(true)
      })
    return () => {
      cancelled = true
    }
  }, [inviteToken])

  if (!checked) {
    return <p className="text-muted-foreground">Se verifică linkul…</p>
  }
  if (deadReason) {
    return <DeadInvite reason={deadReason} />
  }
  return <RegisterForm inviteToken={inviteToken} preview={preview} />
}

export function RegisterPage() {
  usePageTitle('Creează-ți contul')
  const [searchParams] = useSearchParams()
  const inviteToken = searchParams.get('token')

  return (
    <AuthLayout>
      {inviteToken ? <InvitedRegistration inviteToken={inviteToken} /> : <MissingInvite />}
    </AuthLayout>
  )
}
