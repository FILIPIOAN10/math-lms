import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { type Role } from '@/lib/api'

/**
 * Gate for screens meant for exactly one role (e.g. the student's quiz pages). Same checks as
 * {@link AdminRoute}: unauthenticated -> /login, non-active -> /pending, wrong role -> home.
 * The backend enforces the same rule (@PreAuthorize); this only keeps people off pages that would
 * answer them with 403s.
 */
export function RoleRoute({ role, children }: { role: Role; children: ReactNode }) {
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

  if (user.role !== role) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
