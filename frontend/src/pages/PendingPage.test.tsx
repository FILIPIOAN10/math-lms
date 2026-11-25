import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PendingPage } from '@/pages/PendingPage'
import type { User } from '@/lib/api'

const auth = vi.hoisted(() => ({
  value: { user: null as User | null, loading: false, logout: vi.fn(), refreshUser: vi.fn() },
}))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const waiting = (status: User['status']): User =>
  ({ email: 'ana@scoala.ro', fullName: 'Ana', role: 'STUDENT', status }) as User

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/pending']}>
      <Routes>
        <Route path="/pending" element={<PendingPage />} />
        <Route path="/" element={<p>acasă</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PendingPage', () => {
  beforeEach(() => {
    auth.value = { user: null, loading: false, logout: vi.fn(), refreshUser: vi.fn() }
  })

  it('shows where the account is on the way in: email done, the approval now', () => {
    auth.value.user = waiting('PENDING_APPROVAL')
    renderPage()
    const steps = screen.getByTestId('pending-steps')
    expect(steps).toHaveTextContent('Confirmi emailul (făcut)')
    expect(screen.getByText('Profesorul îți aprobă contul').closest('li')).toHaveAttribute('aria-current', 'step')
  })

  it('asks the server again, and says so when the account is still waiting', async () => {
    auth.value.user = waiting('PENDING_APPROVAL')
    auth.value.refreshUser.mockResolvedValue(undefined)
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Verifică din nou' }))
    expect(auth.value.refreshUser).toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent('Încă nu e gata.')
  })

  it('offers no steps and no "check again" to a rejected account', () => {
    auth.value.user = waiting('REJECTED')
    renderPage()
    expect(screen.queryByTestId('pending-steps')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Verifică din nou' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Deconectează-te' })).toBeInTheDocument()
  })
})
