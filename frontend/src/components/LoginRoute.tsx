import { useEffect } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { LoginPage } from '@/pages/LoginPage'
import { clearReturnTo, readReturnTo } from '@/lib/returnTo'

/**
 * /login: the sign-in for a visitor, and for someone who just signed in (password, the 2FA code, or back from Google)
 * a jump to the page they had asked for, or to Acasă. The page's own guard still decides whether this role may see it.
 */
export function LoginRoute() {
  const { user, loading } = useAuth()

  // Forgotten only once the person is on their way: reading it twice in one render pass must give the same page.
  useEffect(() => {
    if (user) clearReturnTo()
  }, [user])

  if (loading) {
    return null
  }
  if (user) {
    return <Navigate to={readReturnTo() ?? '/'} replace />
  }
  return <LoginPage />
}
