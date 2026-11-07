import { Navigate, useLocation } from 'react-router-dom'
import { rememberReturnTo } from '@/lib/returnTo'

/**
 * What the route guards render for a visitor with no session: the sign-in page, remembering the page they asked for
 * (path, query and hash) so the sign-in brings them back to it. Remembering is idempotent, so a second render is harmless.
 */
export function LoginRedirect() {
  const { pathname, search, hash } = useLocation()
  rememberReturnTo(pathname + search + hash)
  return <Navigate to="/login" replace />
}
