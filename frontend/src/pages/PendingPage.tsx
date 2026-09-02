import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/context/AuthContext'
import type { AccountStatus } from '@/lib/api'

const MESSAGES: Record<Exclude<AccountStatus, 'ACTIVE'>, { title: string; description: string }> = {
  PENDING_VERIFICATION: {
    title: 'Confirmă-ți emailul',
    description:
      'Ți-am trimis un link de confirmare pe email. Deschide-l ca să-ți activezi contul.',
  },
  PENDING_APPROVAL: {
    title: 'Contul tău așteaptă aprobarea profesorului',
    description:
      'Emailul tău e confirmat. Un profesor trebuie să-ți aprobe contul înainte să poți intra. Vei primi acces imediat ce ești aprobat.',
  },
  REJECTED: {
    title: 'Cont respins',
    description:
      'Cererea ta de acces a fost respinsă. Contactează profesorul pentru mai multe detalii.',
  },
}

export function PendingPage() {
  const { user, loading, logout } = useAuth()

  if (loading) {
    return null
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (user.status === 'ACTIVE') {
    return <Navigate to="/" replace />
  }

  const { title, description } = MESSAGES[user.status]

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col items-center gap-4">
          <p className="text-sm text-muted-foreground">
            Ești conectat ca <strong>{user.email}</strong>.
          </p>
          <Button variant="outline" onClick={logout}>
            Deconectează-te
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
