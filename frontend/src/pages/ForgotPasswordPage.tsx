import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { forgotPassword } from '@/lib/api'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await forgotPassword(email)
    } catch {
      // Deliberately swallowed: we show the same screen whether or not an account
      // exists for this email, so callers cannot probe which emails are registered.
    } finally {
      setSubmitting(false)
      setSent(true)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      <Card className="w-full max-w-sm">
        {sent ? (
          <>
            <CardHeader className="text-center">
              <CardTitle className="text-2xl">Verifică-ți emailul</CardTitle>
              <CardDescription>
                Dacă există un cont cu acest email, ți-am trimis un link de resetare.
                Linkul expiră într-o oră.
              </CardDescription>
            </CardHeader>
            <CardContent className="text-center">
              <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
                Înapoi la autentificare
              </Link>
            </CardContent>
          </>
        ) : (
          <>
            <CardHeader className="text-center">
              <CardTitle className="text-2xl">Ai uitat parola?</CardTitle>
              <CardDescription>
                Scrie-ți adresa de email și îți trimitem un link de resetare.
              </CardDescription>
            </CardHeader>
            <CardContent>
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
                <Button type="submit" className="w-full" disabled={submitting}>
                  {submitting ? 'Se trimite…' : 'Trimite linkul'}
                </Button>
                <Link
                  to="/login"
                  className="text-center text-xs text-muted-foreground underline-offset-4 hover:underline"
                >
                  Înapoi la autentificare
                </Link>
              </form>
            </CardContent>
          </>
        )}
      </Card>
    </div>
  )
}
