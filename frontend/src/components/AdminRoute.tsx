import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'

/**
 * Gate for admin-only screens. Layers on top of {@link ProtectedRoute}'s checks:
 * unauthenticated -> /login, non-active -> /pending, and anyone who is not an ADMIN
 * is sent home rather than shown the page.
 */
export function AdminRoute({ children }: { children: ReactNode }) {
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

  if (user.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
