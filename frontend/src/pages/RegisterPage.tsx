import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, register } from '@/lib/api'

/** Turns a failed registration into a message safe to show the user. */
function registerErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return 'Există deja un cont cu acest email.'
    }
    if (error.status === 400) {
      return 'Linkul de invitație este invalid sau a expirat. Cere altul administratorului.'
    }
  }
  return 'A apărut o eroare. Încearcă din nou.'
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
function VerifyEmailNotice({ email }: { email: string }) {
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Verifică-ți emailul</CardTitle>
        <CardDescription>
          Ți-am trimis un link de confirmare la <strong>{email}</strong>. Deschide-l ca să
          îți activezi contul, apoi așteaptă aprobarea administratorului.
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

function RegisterForm({ inviteToken }: { inviteToken: string }) {
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
    return <VerifyEmailNotice email={email} />
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Creează-ți contul</CardTitle>
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

export function RegisterPage() {
  const [searchParams] = useSearchParams()
  const inviteToken = searchParams.get('token')

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      {inviteToken ? <RegisterForm inviteToken={inviteToken} /> : <MissingInvite />}
    </div>
  )
}
