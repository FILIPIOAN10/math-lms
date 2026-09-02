import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { verifyEmail } from '@/lib/api'

type VerifyState = 'verifying' | 'success' | 'error'

/** Shown when the page is opened without a token in the URL. */
function InvalidLink() {
  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Link invalid</CardTitle>
        <CardDescription>Linkul de confirmare lipsește sau este incomplet.</CardDescription>
      </CardHeader>
      <CardContent className="text-center">
        <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
          Înapoi la autentificare
        </Link>
      </CardContent>
    </Card>
  )
}

function VerifyResult({ token }: { token: string }) {
  const [state, setState] = useState<VerifyState>('verifying')
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    verifyEmail(token)
      .then(() => setState('success'))
      .catch(() => setState('error'))
  }, [token])

  if (state === 'verifying') {
    return (
      <Card className="w-full max-w-sm">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">Se confirmă emailul…</CardTitle>
          <CardDescription>Un moment, verificăm linkul tău.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  if (state === 'success') {
    return (
      <Card className="w-full max-w-sm">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">Email confirmat</CardTitle>
          <CardDescription>
            Emailul tău a fost confirmat. Contul așteaptă acum aprobarea profesorului.
          </CardDescription>
        </CardHeader>
        <CardContent className="text-center">
          <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
            Mergi la autentificare
          </Link>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">Confirmare eșuată</CardTitle>
        <CardDescription>
          Linkul de confirmare este invalid sau a expirat. Autentifică-te ca să ceri unul nou.
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

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      {token ? <VerifyResult token={token} /> : <InvalidLink />}
    </div>
  )
}
