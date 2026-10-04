import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RoleRoute } from '@/components/RoleRoute'
import type { User } from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const user = (over: Partial<User>): User => ({
  email: 'x@scoala.ro', fullName: 'X', role: 'STUDENT', status: 'ACTIVE', ...over,
})

function renderGuarded() {
  return render(
    <MemoryRouter initialEntries={['/parent']}>
      <Routes>
        <Route path="/parent" element={<RoleRoute role="PARENT"><p>zona părintelui</p></RoleRoute>} />
        <Route path="/login" element={<p>pagina de login</p>} />
        <Route path="/pending" element={<p>cont în așteptare</p>} />
        <Route path="/" element={<p>acasă</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RoleRoute', () => {
  beforeEach(() => {
    auth.value = { user: null, loading: false }
  })

  it('waits while the session is being restored', () => {
    auth.value = { user: null, loading: true }
    renderGuarded()
    expect(screen.getByText('Se încarcă...')).toBeInTheDocument()
  })

  it('sends an anonymous visitor to the login page', () => {
    renderGuarded()
    expect(screen.getByText('pagina de login')).toBeInTheDocument()
  })

  it('sends an account that is not approved yet to the waiting screen', () => {
    auth.value = { user: user({ role: 'PARENT', status: 'PENDING_APPROVAL' }), loading: false }
    renderGuarded()
    expect(screen.getByText('cont în așteptare')).toBeInTheDocument()
  })

  it('sends another role home instead of showing a page that would only answer 403', () => {
    auth.value = { user: user({ role: 'STUDENT' }), loading: false }
    renderGuarded()
    expect(screen.getByText('acasă')).toBeInTheDocument()
  })

  it('lets the right role in', () => {
    auth.value = { user: user({ role: 'PARENT' }), loading: false }
    renderGuarded()
    expect(screen.getByText('zona părintelui')).toBeInTheDocument()
  })
})
