import { render, screen, waitFor, within } from '@testing-library/react'
import { useState } from 'react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from '@/components/AppShell'
import { isNewPage } from '@/lib/shell'
import { ErrorState } from '@/components/ErrorState'
import type { User } from '@/lib/api'
import { NAV_COUNTS_CHANGED } from '@/lib/roles'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false, logout: vi.fn() } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  listPendingUsers: vi.fn(),
  listAttemptsForGrading: vi.fn(),
  getMyAttempts: vi.fn(),
  getStudentTutor: vi.fn(() => Promise.resolve({ enabled: false, dailyLimit: 0, remainingToday: 0, openTest: null })),
  listQuizzes: vi.fn(() => Promise.resolve([])),
  listActiveUsers: vi.fn(() => Promise.resolve([])),
  listEnChapters: vi.fn(() => Promise.resolve([])),
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
          <Route path="/capitole" element={<p>pagina capitole</p>} />
          <Route path="/admin/en-chapters" element={<p>pagina capitole admin</p>} />
          <Route path="/content" element={<p>pagina conținut</p>} />
          <Route path="/reincarca" element={<FailsOnce />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

const nav = () => screen.getByRole('navigation', { name: 'Navigare principală' })
const navLabels = () => within(nav()).getAllByRole('link').map((l) => l.textContent)
const tabBar = () => screen.getByRole('navigation', { name: 'Meniu principal' })
const tabLabels = () => within(tabBar()).getAllByRole('link').map((l) => l.textContent)

function FailsOnce() {
  const [failed] = useState(() => api.getMyAttempts.mock.calls.length < 2)
  return failed ? <ErrorState message="Nu am putut încărca pagina." /> : <p>pagina încărcată</p>
}

describe('AppShell', () => {
  beforeEach(() => {
    auth.value = { user: null, loading: false, logout: vi.fn() }
    api.listPendingUsers.mockResolvedValue([])
    api.listAttemptsForGrading.mockResolvedValue([])
    api.getMyAttempts.mockResolvedValue([])
  })

  it('loads a page again from "Reîncearcă", without leaving it', async () => {
    auth.value = { ...auth.value, user: user({}) }
    renderShell('/reincarca')
    await waitFor(() => expect(api.getMyAttempts).toHaveBeenCalled())

    api.getMyAttempts.mock.calls.push([], []) // from here on the page finds what it needs
    await userEvent.click(await screen.findByRole('button', { name: 'Reîncearcă' }))

    expect(await screen.findByText('pagina încărcată')).toBeInTheDocument()
    expect(screen.getByRole('banner')).toBeInTheDocument() // the app stayed: only the page was loaded again
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

  it('gives a parent no menu: Acasă (the logo) is their only page', () => {
    auth.value.user = user({ role: 'PARENT', fullName: 'Maria Parinte' })
    renderShell()
    expect(screen.queryByRole('navigation', { name: 'Navigare principală' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Math LMS' })).toHaveAttribute('href', '/')
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

  it('F8: does not ask for the staff counts before two-step sign-in is set up (they would all be 403)', async () => {
    auth.value.user = user({ role: 'ADMIN', twoFactorSetupRequired: true })
    renderShell()
    expect(within(nav()).getByRole('link', { name: 'Corectură' })).toBeInTheDocument()
    expect(api.listAttemptsForGrading).not.toHaveBeenCalled()
    expect(api.listPendingUsers).not.toHaveBeenCalled()
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

  describe('on a phone: the tab bar at the bottom', () => {
    it("keeps the student's four main pages one tap away and the rest under \"Mai mult\"", async () => {
      auth.value.user = user({})
      renderShell()

      expect(tabLabels()).toEqual(['Acasă', 'Teste', 'Recapitulare', 'Progres'])
      const more = within(tabBar()).getByRole('button', { name: 'Mai mult' })
      expect(more).toHaveAttribute('aria-expanded', 'false')
      expect(within(tabBar()).queryByRole('link', { name: 'Capitole EN' })).not.toBeInTheDocument()

      await userEvent.click(more)
      expect(more).toHaveAttribute('aria-expanded', 'true')
      const menu = screen.getByRole('list', { name: 'Mai mult' })
      expect(within(menu).getAllByRole('link').map((l) => l.textContent)).toEqual(['Capitole EN', 'Conținut'])
    })

    it('closes "Mai mult" on Escape and after a page is picked from it', async () => {
      auth.value.user = user({})
      renderShell()
      const more = within(tabBar()).getByRole('button', { name: 'Mai mult' })

      await userEvent.click(more)
      await userEvent.keyboard('{Escape}')
      expect(more).toHaveAttribute('aria-expanded', 'false')
      expect(screen.queryByRole('list', { name: 'Mai mult' })).not.toBeInTheDocument()

      await userEvent.click(more)
      await userEvent.click(within(screen.getByRole('list', { name: 'Mai mult' })).getByRole('link', { name: 'Conținut' }))
      expect(screen.getByText('pagina conținut')).toBeInTheDocument()
      expect(more).toHaveAttribute('aria-expanded', 'false')
    })

    it('marks the current page, and "Mai mult" when that page is inside it', async () => {
      auth.value.user = user({})
      renderShell('/capitole')

      const more = within(tabBar()).getByRole('button', { name: 'Mai mult' })
      expect(more).toHaveAttribute('data-current', 'true')
      await userEvent.click(more)
      const menu = screen.getByRole('list', { name: 'Mai mult' })
      expect(within(menu).getByRole('link', { name: 'Capitole EN' })).toHaveAttribute('aria-current', 'page')
    })

    it('marks the tab of the page the student is on', () => {
      auth.value.user = user({})
      renderShell('/quizzes')
      expect(within(tabBar()).getByRole('link', { name: 'Teste' })).toHaveAttribute('aria-current', 'page')
      expect(within(tabBar()).getByRole('button', { name: 'Mai mult' })).not.toHaveAttribute('data-current')
    })

    it('fits all five teacher links, so there is no "Mai mult"', () => {
      auth.value.user = user({ role: 'ADMIN' })
      renderShell()
      expect(tabLabels()).toEqual(['Acasă', 'Clase', 'Teste', 'Corectură', 'Elevi'])
      expect(within(tabBar()).queryByRole('button', { name: 'Mai mult' })).not.toBeInTheDocument()
    })

    it('shows a parent no tab bar', () => {
      auth.value.user = user({ role: 'PARENT' })
      renderShell()
      expect(screen.queryByRole('navigation', { name: 'Meniu principal' })).not.toBeInTheDocument()
    })

    it('carries the same counts as the desktop links', async () => {
      auth.value.user = user({})
      api.getMyAttempts.mockResolvedValue([{ newResult: true }, { newResult: true }])
      renderShell()
      expect(await within(tabBar()).findByRole('link', { name: 'Teste, 2 note noi' })).toBeInTheDocument()
    })
  })

  describe('side menu (computer)', () => {
    it("puts an admin's rarer pages under \"Mai mult\", and marks their own link, not the one that groups them", () => {
      auth.value = { ...auth.value, user: user({ role: 'ADMIN', fullName: 'Ion Profesor' }) }
      renderShell('/admin/en-chapters')
      const more = screen.getByRole('navigation', { name: 'Mai mult' })
      expect(within(more).getAllByRole('link').map((l) => l.textContent)).toEqual(['Capitole EN', 'Import din poze', 'Jurnal'])
      expect(within(more).getByRole('link', { name: 'Capitole EN' })).toHaveAttribute('aria-current', 'page')
      expect(within(nav()).getByRole('link', { name: 'Clase' })).not.toHaveAttribute('aria-current')
    })

    it('has no "Mai mult" for a student: all their pages are main links', () => {
      auth.value = { ...auth.value, user: user({}) }
      renderShell()
      expect(screen.queryByRole('navigation', { name: 'Mai mult' })).not.toBeInTheDocument()
    })

    it("shows the user's initials next to their name", () => {
      auth.value = { ...auth.value, user: user({ fullName: 'Ana Maria Pop' }) }
      renderShell()
      expect(within(screen.getByRole('banner')).getByText('AM')).toBeInTheDocument()
    })

    it('switches between light and dark in one press and remembers it', async () => {
      document.documentElement.classList.remove('dark')
      auth.value = { ...auth.value, user: user({}) }
      renderShell()
      const toggle = screen.getByRole('button', { name: 'Temă întunecată' })
      expect(toggle).toHaveAttribute('aria-pressed', 'false')
      await userEvent.click(toggle)
      expect(document.documentElement).toHaveClass('dark')
      expect(localStorage.getItem('mathlms-theme')).toBe('dark')
      expect(toggle).toHaveAttribute('aria-pressed', 'true')
      await userEvent.click(toggle)
      expect(document.documentElement).not.toHaveClass('dark')
      localStorage.removeItem('mathlms-theme')
    })
  })

  describe('search (Ctrl+K)', () => {
    it('opens from the keyboard anywhere in the app', async () => {
      auth.value = { ...auth.value, user: user({ role: 'TEACHER', fullName: 'Ion Profesor' }) }
      renderShell()
      await userEvent.keyboard('{Control>}k{/Control}')
      expect(await screen.findByRole('combobox', { name: 'Caută o pagină, un test, un elev' })).toBeInTheDocument()
    })

    it('opens from the search button, and takes the user to the page picked', async () => {
      auth.value = { ...auth.value, user: user({}) }
      renderShell()
      await userEvent.click(screen.getByRole('button', { name: 'Caută' }))
      await userEvent.type(await screen.findByRole('combobox'), 'teste{Enter}')
      expect(await screen.findByText('pagina teste')).toBeInTheDocument()
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    })
  })

  it('fades a page in only when the user goes to another page, not when one opens a dialog of its own', () => {
    expect(isNewPage('/', '/quizzes')).toBe(true)
    expect(isNewPage('/admin/quizzes', '/admin/quizzes/5')).toBe(false)
    expect(isNewPage('/admin/quizzes/5', '/admin/quizzes')).toBe(false)
    expect(isNewPage('/admin/quizzes', '/admin/quizzesX')).toBe(true)
    expect(isNewPage('/review', '/review')).toBe(false)
  })
})
