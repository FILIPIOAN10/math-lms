import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, resetPassword } from '@/lib/api'

/** Turns a failed reset into a message safe to show the user. */
function resetErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) {
    return 'Linkul de resetare este invalid sau a expirat. Cere unul nou.'
  }
  return 'A apărut o eroare. Încearcă din nou.'
}

/** Shown when the page is opened without a reset token in the URL. */
function InvalidLink() {
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Link invalid</CardTitle>
        <CardDescription>
          Linkul de resetare lipsește sau este incomplet.
        </CardDescription>
      </CardHeader>
      <CardContent className="text-center">
        <Link
          to="/forgot-password"
          className="text-sm text-primary underline-offset-4 hover:underline"
        >
          Cere alt link
        </Link>
      </CardContent>
    </Card>
  )
}

/** Success screen after the password has been changed. */
function ResetDone() {
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Parolă schimbată</CardTitle>
        <CardDescription>
          Parola ta a fost actualizată. Te poți conecta cu noua parolă.
        </CardDescription>
      </CardHeader>
      <CardContent className="text-center">
        <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
          Conectează-te
        </Link>
      </CardContent>
    </Card>
  )
}

function ResetForm({ token }: { token: string }) {
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (newPassword.length < 8) {
      setError('Parola trebuie să aibă cel puțin 8 caractere.')
      return
    }
    if (newPassword !== confirm) {
      setError('Parolele nu coincid.')
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(token, newPassword)
      setDone(true)
    } catch (err) {
      setError(resetErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return <ResetDone />
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Setează o parolă nouă</CardTitle>
        <CardDescription>Alege o parolă de cel puțin 8 caractere.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <div className="flex flex-col gap-2">
            <Label htmlFor="newPassword">Parolă nouă</Label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
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
            {submitting ? 'Se salvează…' : 'Schimbă parola'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      {token ? <ResetForm token={token} /> : <InvalidLink />}
    </div>
  )
}
