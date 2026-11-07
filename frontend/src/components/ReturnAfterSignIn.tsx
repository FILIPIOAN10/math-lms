import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { clearReturnTo, readReturnTo } from '@/lib/returnTo'

/**
 * The Google sign-in comes back to `/`, not to /login: once someone is signed in, this sends them on to the page they
 * had asked for (kept by LoginRedirect), then forgets it. The password and 2FA sign-ins get there through LoginRoute.
 */
export function ReturnAfterSignIn() {
  const { user } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!user) return
    const to = readReturnTo()
    clearReturnTo()
    if (to) navigate(to, { replace: true })
  }, [user, navigate])

  return null
}
