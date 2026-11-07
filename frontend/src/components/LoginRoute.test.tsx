import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginRoute } from '@/components/LoginRoute'
import { ReturnAfterSignIn } from '@/components/ReturnAfterSignIn'
import { RoleRoute } from '@/components/RoleRoute'
import type { User } from '@/lib/api'
import { clearReturnTo, forgetReturnOnSignOut, readReturnTo } from '@/lib/returnTo'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))
vi.mock('@/pages/LoginPage', () => ({ LoginPage: () => <p>formular conectare</p> }))

const ana: User = { email: 'ana@scoala.ro', fullName: 'Ana', role: 'STUDENT', status: 'ACTIVE' }

function app(at: string) {
  return (
    <MemoryRouter initialEntries={[at]}>
      <Routes>
        <Route path="/" element={<p>acasă</p>} />
        <Route path="/login" element={<LoginRoute />} />
        <Route path="/quizzes/attempts/:id" element={<RoleRoute role="STUDENT"><p>rezultatul</p></RoleRoute>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('LoginRoute', () => {
  beforeEach(() => {
    sessionStorage.clear()
    clearReturnTo()
    auth.value.user = null
  })

  it('brings a student back to the result link they opened while signed out', () => {
    const { rerender } = render(app('/quizzes/attempts/7?from=email'))
    expect(screen.getByText('formular conectare')).toBeInTheDocument()
    expect(readReturnTo()).toBe('/quizzes/attempts/7?from=email')

    auth.value.user = ana // the sign-in succeeded
    rerender(app('/login'))

    expect(screen.getByText('rezultatul')).toBeInTheDocument()
    expect(readReturnTo()).toBeNull() // used once
  })

  it('goes to Acasă when nothing was asked for', () => {
    auth.value.user = ana
    render(app('/login'))
    expect(screen.getByText('acasă')).toBeInTheDocument()
  })

  it('sends someone back from Google, who lands on Acasă, on to the page they had asked for', () => {
    render(app('/quizzes/attempts/7'))
    expect(readReturnTo()).toBe('/quizzes/attempts/7')

    auth.value.user = ana // Google sent the browser back to /
    render(
      <MemoryRouter initialEntries={['/']}>
        <ReturnAfterSignIn />
        <Routes>
          <Route path="/" element={<p>acasă</p>} />
          <Route path="/quizzes/attempts/:id" element={<p>rezultatul de la Google</p>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('rezultatul de la Google')).toBeInTheDocument()
    expect(readReturnTo()).toBeNull()
  })

  it('after a sign-out, does not send the next person to the page that was open', () => {
    auth.value.user = ana
    forgetReturnOnSignOut()
    auth.value.user = null
    render(app('/quizzes/attempts/7'))
    expect(screen.getByText('formular conectare')).toBeInTheDocument()
    expect(readReturnTo()).toBeNull()
  })
})
