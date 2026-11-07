import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { ServerUnreachable } from '@/components/ServerUnreachable'
import {
  ApiError,
  getCurrentUser,
  login as apiLogin,
  logout as apiLogout,
  verifyTwoFactor as apiVerifyTwoFactor,
  type User,
} from '@/lib/api'
import { clearAllAnswerQueues } from '@/lib/answerQueue'
import { forgetReturnOnSignOut } from '@/lib/returnTo'

interface AuthContextValue {
  user: User | null
  loading: boolean
  /** The user, or null when the authenticator code is still needed (then call `verifyTwoFactor`). */
  login: (email: string, password: string) => Promise<User | null>
  verifyTwoFactor: (code: string) => Promise<User>
  /** Re-reads the signed-in user (e.g. after turning two-step sign-in on). */
  refreshUser: () => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

/** How often the check runs again while the server does not answer. */
const RETRY_MS = 5000

/** No answer about the session at all (no network, a server error, rate limited), as opposed to "you are signed out". */
function isUnreachable(error: unknown): boolean {
  return !(error instanceof ApiError) || error.status >= 500 || error.status === 429
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [unreachable, setUnreachable] = useState(false)

  const checkSession = useCallback(async () => {
    try {
      setUser(await getCurrentUser())
      setUnreachable(false)
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) {
        console.error('Auth check failed', error)
      }
      setUnreachable(isUnreachable(error))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void checkSession()
  }, [checkSession])

  useEffect(() => {
    if (!unreachable) return
    const retry = () => void checkSession()
    const timer = window.setInterval(retry, RETRY_MS)
    window.addEventListener('online', retry)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('online', retry)
    }
  }, [unreachable, checkSession])

  async function login(email: string, password: string) {
    const loggedIn = await apiLogin(email, password)
    if (loggedIn) setUser(loggedIn)
    return loggedIn
  }

  async function verifyTwoFactor(code: string) {
    const loggedIn = await apiVerifyTwoFactor(code)
    setUser(loggedIn)
    return loggedIn
  }

  async function refreshUser() {
    setUser(await getCurrentUser())
  }

  async function logout() {
    // Answers waiting for a connection belong to this person: the next one on this computer must not send them.
    clearAllAnswerQueues()
    // The page they leave is theirs: the next person signing in here must not be sent to it.
    forgetReturnOnSignOut()
    await apiLogout()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, verifyTwoFactor, refreshUser, logout }}>
      {unreachable ? <ServerUnreachable onRetry={checkSession} /> : children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
