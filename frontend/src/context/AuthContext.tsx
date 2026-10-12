import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import {
  ApiError,
  getCurrentUser,
  login as apiLogin,
  logout as apiLogout,
  type User,
} from '@/lib/api'
import { clearAllAnswerQueues } from '@/lib/answerQueue'

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getCurrentUser()
      .then(setUser)
      .catch((error) => {
        if (error instanceof ApiError && error.status === 401) {
          setUser(null)
        } else {
          console.error('Auth check failed', error)
        }
      })
      .finally(() => setLoading(false))
  }, [])

  async function login(email: string, password: string) {
    const loggedIn = await apiLogin(email, password)
    setUser(loggedIn)
    return loggedIn
  }

  async function logout() {
    // Answers waiting for a connection belong to this person: the next one on this computer must not send them.
    clearAllAnswerQueues()
    await apiLogout()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
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
