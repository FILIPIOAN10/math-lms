import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from '@/components/AppShell'
import type { User } from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false, logout: vi.fn() } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  listPendingUsers: vi.fn(),
  listAttemptsForGrading: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const user = (over: Partial<User>): User => ({
  email: 'x@scoala.ro', fullName: 'Ana Student', role: 'STUDENT', status: 'ACTIVE', ...over,
})

function renderShell(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<p>pagina acasă</p>} />
          <Route path="/quizzes" element={<p>pagina teste</p>} />
          <Route path="/admin/grading" element={<p>pagina corectură</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

const nav = () => screen.getByRole('navigation', { name: 'Navigare principală' })
const navLabels = () => within(nav()).getAllByRole('link').map((l) => l.textContent)

describe('AppShell', () => {
  beforeEach(() => {
    auth.value = { user: null, loading: false, logout: vi.fn() }
    api.listPendingUsers.mockResolvedValue([])
    api.listAttemptsForGrading.mockResolvedValue([])
  })

  it('renders only the page when nobody is signed in (the route guard decides where to go)', () => {
    renderShell()
    expect(screen.getByText('pagina acasă')).toBeInTheDocument()
    expect(screen.queryByRole('banner')).not.toBeInTheDocument()
  })

  it('gives a student their own links and marks the page they are on', () => {
    auth.value.user = user({})
    renderShell('/quizzes')

    expect(navLabels()).toEqual(['Acasă', 'Testele mele', 'Progresul meu', 'Conținut'])
    expect(within(nav()).getByRole('link', { name: 'Testele mele' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav()).getByRole('link', { name: 'Acasă' })).not.toHaveAttribute('aria-current')
    expect(screen.getByText('pagina teste')).toBeInTheDocument()
  })

  it('gives a parent the link to their children', () => {
    auth.value.user = user({ role: 'PARENT', fullName: 'Maria Parinte' })
    renderShell()
    expect(navLabels()).toEqual(['Acasă', 'Copiii mei', 'Conținut'])
  })

  it('names the role in Romanian, never the enum', () => {
    auth.value.user = user({ role: 'ADMIN', fullName: 'Prof Admin' })
    renderShell()
    const banner = screen.getByRole('banner')
    expect(within(banner).getByText('Profesor')).toBeInTheDocument()
    expect(within(banner).queryByText('ADMIN')).not.toBeInTheDocument()
  })

  it('shows the teacher how much work is waiting next to the links', async () => {
    auth.value.user = user({ role: 'ADMIN', fullName: 'Prof Admin' })
    api.listAttemptsForGrading.mockResolvedValue([{}, {}, {}])
    api.listPendingUsers.mockResolvedValue([{}])
    renderShell()

    expect(navLabels()[0]).toBe('Acasă')
    expect(await within(nav()).findByRole('link', { name: 'Corectură, 3 lucrări de corectat' })).toBeInTheDocument()
    expect(within(nav()).getByRole('link', { name: 'Conturi, 1 cont de aprobat' })).toBeInTheDocument()
    expect(api.listAttemptsForGrading).toHaveBeenCalledWith('SUBMITTED')
  })

  it('shows no counts when there is nothing waiting', async () => {
    auth.value.user = user({ role: 'ADMIN' })
    renderShell()
    await waitFor(() => expect(api.listPendingUsers).toHaveBeenCalled())
    expect(within(nav()).getByRole('link', { name: 'Corectură' })).toBeInTheDocument()
  })

  it('does not ask a student for the teacher counts', () => {
    auth.value.user = user({})
    renderShell()
    expect(api.listPendingUsers).not.toHaveBeenCalled()
    expect(api.listAttemptsForGrading).not.toHaveBeenCalled()
  })

  it('logs out from any page', async () => {
    auth.value.user = user({})
    renderShell('/quizzes')
    await userEvent.click(screen.getByRole('button', { name: 'Ieșire' }))
    expect(auth.value.logout).toHaveBeenCalled()
  })
})
