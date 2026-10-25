import { type ReactNode } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/context/AuthContext'
import { isStaff } from '@/lib/roles'

/**
 * Gate for the staff screens. Layers on top of {@link ProtectedRoute}'s checks:
 * unauthenticated -> /login, non-active -> /pending, and anyone who is not staff (ADMIN or TEACHER) is sent home
 * rather than shown the page. {@code adminOnly} keeps a TEACHER out too (journal, shared EN pages); the backend
 * refuses those requests anyway, this only spares the teacher a page full of errors.
 */
export function AdminRoute({ children, adminOnly = false }: { children: ReactNode; adminOnly?: boolean }) {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center text-muted-foreground">
        Se încarcă...
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (user.status !== 'ACTIVE') {
    return <Navigate to="/pending" replace />
  }

  if (adminOnly ? user.role !== 'ADMIN' : !isStaff(user.role)) {
    return <Navigate to="/" replace />
  }

  if (user.twoFactorSetupRequired) {
    return <TwoFactorRequired />
  }

  return <>{children}</>
}

/**
 * F8: an admin without two-step sign-in yet. The server answers every staff request with 403 until it is set up, so
 * instead of a page full of errors: why, and the one place to fix it.
 */
function TwoFactorRequired() {
  return (
    <div className="flex justify-center p-4">
      <Card className="w-full max-w-md" data-testid="two-factor-required">
        <CardHeader>
          <CardTitle className="text-xl">Activează autentificarea în doi pași</CardTitle>
          <CardDescription>
            Contul de administrator aprobă conturi și vede toate clasele, așa că la conectare cere și un cod din
            aplicația de pe telefon. Paginile de profesor se deschid după ce o activezi (durează un minut).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Link to="/account" className={buttonVariants()}>Mergi la Contul meu</Link>
        </CardContent>
      </Card>
    </div>
  )
}
