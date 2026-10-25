import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AdminRoute } from '@/components/AdminRoute'
import type { User } from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const staff = (over: Partial<User>): User => ({
  email: 'prof@scoala.ro', fullName: 'Prof', role: 'ADMIN', status: 'ACTIVE', ...over,
})

function renderAt(adminOnly = false) {
  return render(
    <MemoryRouter initialEntries={['/admin/content']}>
      <Routes>
        <Route path="/" element={<p>acasă</p>} />
        <Route path="/admin/content" element={<AdminRoute adminOnly={adminOnly}><p>pagina clase</p></AdminRoute>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminRoute', () => {
  it('opens the staff page for an admin and a teacher', () => {
    auth.value.user = staff({ role: 'ADMIN' })
    renderAt()
    expect(screen.getByText('pagina clase')).toBeInTheDocument()

    auth.value.user = staff({ role: 'TEACHER' })
    renderAt()
    expect(screen.getAllByText('pagina clase')).toHaveLength(2)
  })

  it('keeps a teacher off an admin-only page', () => {
    auth.value.user = staff({ role: 'TEACHER' })
    renderAt(true)
    expect(screen.getByText('acasă')).toBeInTheDocument()
  })

  it('sends an admin who must set up two-step sign-in to Contul meu instead of the page', () => {
    auth.value.user = staff({ role: 'ADMIN', twoFactorSetupRequired: true })
    renderAt()

    expect(screen.getByTestId('two-factor-required')).toBeInTheDocument()
    expect(screen.queryByText('pagina clase')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mergi la Contul meu' })).toHaveAttribute('href', '/account')
  })
})
