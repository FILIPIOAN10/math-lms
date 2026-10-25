import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { TwoFactorRequired } from '@/components/TwoFactorRequired'
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
