import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyArt } from '@/components/EmptyArt'
import { useAuth } from '@/context/AuthContext'
import type { AccountStatus } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { cn } from '@/lib/utils'

type Waiting = Exclude<AccountStatus, 'ACTIVE'>

const MESSAGES: Record<Waiting, { title: string; description: string; symbol: string }> = {
  PENDING_VERIFICATION: {
    title: 'Confirmă-ți emailul',
    description:
      'Ți-am trimis un link de confirmare pe email. Deschide-l ca să-ți activezi contul.',
    symbol: '@',
  },
  PENDING_APPROVAL: {
    title: 'Contul tău așteaptă aprobarea profesorului',
    description:
      'Emailul tău e confirmat. Un profesor trebuie să-ți aprobe contul înainte să poți intra. Vei primi acces imediat ce ești aprobat.',
    symbol: '…',
  },
  REJECTED: {
    title: 'Cont respins',
    description:
      'Cererea ta de acces a fost respinsă. Contactează profesorul pentru mai multe detalii.',
    symbol: '×',
  },
}

/** The way from signing up to the first page, with the step the user is on marked. */
const STEPS: { label: string; hint: string }[] = [
  { label: 'Confirmi emailul', hint: 'Din linkul primit pe email.' },
  { label: 'Profesorul îți aprobă contul', hint: 'Când e gata, apasă „Verifică din nou” sau intră mai târziu.' },
  { label: 'Intri în aplicație', hint: 'Cu același email și aceeași parolă.' },
]

const CURRENT_STEP: Record<Waiting, number | null> = { PENDING_VERIFICATION: 0, PENDING_APPROVAL: 1, REJECTED: null }

export function PendingPage() {
  usePageTitle('Cont în așteptare')
  const { user, loading, logout, refreshUser } = useAuth()
  const [checking, setChecking] = useState(false)
  const [stillWaiting, setStillWaiting] = useState(false)

  if (loading) {
    return null
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (user.status === 'ACTIVE') {
    return <Navigate to="/" replace />
  }

  const { title, description, symbol } = MESSAGES[user.status]
  const current = CURRENT_STEP[user.status]

  // Asks the server again: an account approved meanwhile goes straight in (the redirect above), no new login needed.
  async function checkAgain() {
    setChecking(true)
    try {
      await refreshUser()
      setStillWaiting(true)
    } finally {
      setChecking(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="justify-items-center text-center">
          <EmptyArt symbol={symbol} className="mb-2" />
          <CardTitle className="text-2xl">{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col items-center gap-4">
          {current !== null && (
            <ol data-testid="pending-steps" className="w-full space-y-3 rounded-lg bg-muted/60 p-3 text-sm">
              {STEPS.map((step, i) => (
                <li key={step.label} aria-current={i === current ? 'step' : undefined} className="flex gap-3">
                  <span
                    aria-hidden="true"
                    className={cn(
                      'flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
                      i < current ? 'bg-success/15 text-success-foreground'
                        : i === current ? 'bg-primary text-primary-foreground' : 'bg-background text-muted-foreground',
                    )}
                  >
                    {i < current ? '✓' : i + 1}
                  </span>
                  <span className="min-w-0">
                    <span className={cn('block font-medium', i > current && 'text-muted-foreground')}>
                      {step.label}
                      {i < current && <span className="sr-only"> (făcut)</span>}
                    </span>
                    {i === current && <span className="block text-muted-foreground">{step.hint}</span>}
                  </span>
                </li>
              ))}
            </ol>
          )}
          <p className="text-sm text-muted-foreground">
            Ești conectat ca <strong>{user.email}</strong>.
          </p>
          {stillWaiting && (
            <p role="status" className="text-sm">Încă nu e gata. Încearcă din nou mai târziu.</p>
          )}
          <div className="flex flex-wrap justify-center gap-2">
            {user.status !== 'REJECTED' && (
              <Button onClick={() => void checkAgain()} disabled={checking}>
                {checking ? 'Se verifică...' : 'Verifică din nou'}
              </Button>
            )}
            <Button variant="outline" onClick={logout}>
              Deconectează-te
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
