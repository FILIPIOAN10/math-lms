import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from '@/components/AppShell'
import type { User } from '@/lib/api'
import { NAV_COUNTS_CHANGED } from '@/lib/roles'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false, logout: vi.fn() } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  listPendingUsers: vi.fn(),
  listAttemptsForGrading: vi.fn(),
  getMyAttempts: vi.fn(),
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
          <Route path="/admin/assignments" element={<p>pagina teme</p>} />
          <Route path="/admin/students/:id" element={<p>pagina elev</p>} />
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
    api.getMyAttempts.mockResolvedValue([])
  })

  it('renders only the page when nobody is signed in (the route guard decides where to go)', () => {
    renderShell()
    expect(screen.getByText('pagina acasă')).toBeInTheDocument()
    expect(screen.queryByRole('banner')).not.toBeInTheDocument()
  })

  it('gives a student their own links and marks the page they are on', () => {
    auth.value.user = user({})
    renderShell('/quizzes')

    expect(navLabels()).toEqual(['Acasă', 'Testele mele', 'Recapitulare', 'Progresul meu', 'Capitole EN', 'Conținut'])
    expect(within(nav()).getByRole('link', { name: 'Testele mele' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav()).getByRole('link', { name: 'Acasă' })).not.toHaveAttribute('aria-current')
    expect(screen.getByText('pagina teste')).toBeInTheDocument()
  })

  it('gives a parent the link to their children', () => {
    auth.value.user = user({ role: 'PARENT', fullName: 'Maria Parinte' })
    renderShell()
    expect(navLabels()).toEqual(['Acasă', 'Copiii mei', 'Capitole EN', 'Conținut'])
  })

  it('names the role in Romanian, never the enum', () => {
    auth.value.user = user({ role: 'ADMIN', fullName: 'Prof Admin' })
    renderShell()
    const banner = screen.getByRole('banner')
    expect(within(banner).getByText('Administrator')).toBeInTheDocument()
    expect(within(banner).queryByText('ADMIN')).not.toBeInTheDocument()
  })

  it('gives a teacher the staff links but never asks for the accounts to approve', async () => {
    api.listAttemptsForGrading.mockResolvedValue([])
    auth.value.user = user({ role: 'TEACHER', fullName: 'Prof Ana' })
    renderShell()
    const banner = screen.getByRole('banner')
    expect(within(banner).getByText('Profesor')).toBeInTheDocument()
    expect(within(banner).getByRole('link', { name: /Corectură/ })).toBeInTheDocument()
    await waitFor(() => expect(api.listAttemptsForGrading).toHaveBeenCalled())
    expect(api.listPendingUsers).not.toHaveBeenCalled()
  })

  it('shows the teacher how much work is waiting next to the links', async () => {
    auth.value.user = user({ role: 'ADMIN', fullName: 'Prof Admin' })
    api.listAttemptsForGrading.mockResolvedValue([{}, {}, {}])
    api.listPendingUsers.mockResolvedValue([{}])
    renderShell()

    expect(navLabels()[0]).toBe('Acasă')
    expect(await within(nav()).findByRole('link', { name: 'Corectură, 3 lucrări de corectat' })).toBeInTheDocument()
    expect(within(nav()).getByRole('link', { name: 'Elevi, 1 cont de aprobat' })).toBeInTheDocument()
    expect(api.listAttemptsForGrading).toHaveBeenCalledWith('SUBMITTED')
  })

  it('gives the teacher five links', () => {
    auth.value.user = user({ role: 'ADMIN' })
    renderShell()
    expect(navLabels()).toEqual(['Acasă', 'Clase', 'Teste', 'Corectură', 'Elevi'])
  })

  it("keeps the teacher's area lit on the pages it groups", () => {
    auth.value.user = user({ role: 'ADMIN' })
    renderShell('/admin/assignments')
    expect(within(nav()).getByRole('link', { name: 'Teste' })).toHaveAttribute('aria-current', 'page')
  })

  it("marks Elevi on a student's page", () => {
    auth.value.user = user({ role: 'ADMIN' })
    renderShell('/admin/students/4')
    expect(within(nav()).getByRole('link', { name: 'Elevi' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav()).getByRole('link', { name: 'Acasă' })).not.toHaveAttribute('aria-current')
  })

  it('shows no counts when there is nothing waiting', async () => {
    auth.value.user = user({ role: 'ADMIN' })
    renderShell()
    await waitFor(() => expect(api.listPendingUsers).toHaveBeenCalled())
    expect(within(nav()).getByRole('link', { name: 'Corectură' })).toBeInTheDocument()
  })

  it('S3: shows the student how many new grades wait, and re-reads it when a result is opened', async () => {
    auth.value.user = user({})
    api.getMyAttempts.mockResolvedValue([{ newResult: true }, { newResult: true }, { newResult: false }])
    renderShell()

    expect(await within(nav()).findByRole('link', { name: 'Testele mele, 2 note noi' })).toBeInTheDocument()

    api.getMyAttempts.mockResolvedValue([{ newResult: true }])
    window.dispatchEvent(new Event(NAV_COUNTS_CHANGED))
    expect(await within(nav()).findByRole('link', { name: 'Testele mele, 1 notă nouă' })).toBeInTheDocument()
  })

  it('does not ask a student for the teacher counts', () => {
    auth.value.user = user({})
    renderShell()
    expect(api.listPendingUsers).not.toHaveBeenCalled()
    expect(api.listAttemptsForGrading).not.toHaveBeenCalled()
  })

  it('leads to "Contul meu" from the account corner', () => {
    auth.value.user = user({})
    renderShell()
    expect(within(screen.getByRole('banner')).getByRole('link', { name: /Contul meu/ })).toHaveAttribute('href', '/account')
  })

  it('logs out from any page', async () => {
    auth.value.user = user({})
    renderShell('/quizzes')
    await userEvent.click(screen.getByRole('button', { name: 'Ieșire' }))
    expect(auth.value.logout).toHaveBeenCalled()
  })
})
