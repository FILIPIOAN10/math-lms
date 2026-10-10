import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/context/AuthContext'
import { ApiError, confirmAccountErasure } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { usePageTitle } from '@/lib/usePageTitle'

type State = 'ask' | 'busy' | 'done'

/**
 * Where the erasure confirmation email lands (`/erase-account?token=…`, see EmailService). Opening the link deletes
 * nothing - mail scanners open links too - the user has to press the button. No session is needed: the single-use
 * token proves the mailbox. Afterwards this browser is signed out, since the account no longer exists.
 */
export function EraseAccountPage() {
  usePageTitle('Ștergerea contului')
  const [params] = useSearchParams()
  const token = params.get('token')
  const { logout } = useAuth()
  const [state, setState] = useState<State>('ask')
  const [error, setError] = useState<string | null>(null)

  async function erase() {
    if (!token) return
    setState('busy')
    setError(null)
    try {
      await confirmAccountErasure(token)
      await logout().catch(() => undefined) // the sessions were revoked by the erasure; this clears the app's state
      setState('done')
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 400
          ? 'Linkul a expirat sau a fost deja folosit. Cere unul nou din „Contul meu”.'
          : errorMessage(e),
      )
      setState('ask')
    }
  }

  const [title, description] = !token
    ? ['Link invalid', 'Linkul nu conține un cod de confirmare. Deschide-l exact cum a venit în email.']
    : state === 'done'
      ? ['Contul a fost șters', 'Datele tale personale au fost șterse. Îți mulțumim că ai folosit MathLMS.']
      : ['Ștergi contul definitiv?', 'Numele, emailul, parola, înscrierile, pozele și comentariile se șterg. Nu se poate anula.']

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl">
            <h1>{title}</h1>
          </CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          {token && state !== 'done' && (
            <div className="flex flex-wrap gap-2">
              <Button variant="destructive" onClick={erase} disabled={state === 'busy'}>
                {state === 'busy' ? 'Se șterge...' : 'Șterge definitiv contul'}
              </Button>
              <Link to="/" className={buttonVariants({ variant: 'outline' })}>Renunță</Link>
            </div>
          )}
          {(state === 'done' || !token) && (
            <Link to="/login" className={buttonVariants({ variant: 'outline' })}>Mergi la autentificare</Link>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
