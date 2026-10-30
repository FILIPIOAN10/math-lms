import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminStudentsPage } from '@/pages/AdminStudentsPage'
import { ApiError, type AdminUserSummary, type InviteLink, type User } from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false, logout: vi.fn() } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  listActiveUsers: vi.fn(),
  listStudentRows: vi.fn(),
  linkParent: vi.fn(),
  createParentInvite: vi.fn(),
  listPendingUsers: vi.fn(),
  approveUser: vi.fn(),
  rejectUser: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const student = (id: number, fullName: string, parentName: string | null = null): AdminUserSummary => ({
  id, email: `${id}@scoala.ro`, fullName, role: 'STUDENT', parentId: parentName ? 99 : null, parentName,
})

const parentLink: InviteLink = {
  id: 5, kind: 'PARENT', url: 'http://localhost:5173/register?token=codParinte', uses: 0, maxUses: 1,
  expiresAt: '2026-10-18T10:00:00Z', createdAt: '2026-10-11T10:00:00Z', className: null, studentName: 'Ana Pop',
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminStudentsPage />
    </MemoryRouter>,
  )
}

describe('AdminStudentsPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    auth.value.user = { email: 'prof@scoala.ro', fullName: 'Prof', role: 'ADMIN', status: 'ACTIVE' }
    api.listActiveUsers.mockImplementation((role: string) =>
      Promise.resolve(role === 'STUDENT' ? [student(1, 'Ana Pop'), student(2, 'Radu Nou', 'Maria Parinte')] : []))
    api.listPendingUsers.mockResolvedValue([])
    api.listStudentRows.mockResolvedValue([])
  })

  it('shows a teacher their students and the parent invite, but nothing only admins do', async () => {
    auth.value.user = { email: 'ana@scoala.ro', fullName: 'Prof Ana', role: 'TEACHER', status: 'ACTIVE' }
    renderPage()

    expect(await screen.findByRole('link', { name: 'Ana Pop' })).toBeInTheDocument()
    expect(screen.getByTestId('parent-invite-1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Leagă' })).not.toBeInTheDocument()
    expect(screen.queryByTestId('pending-accounts')).not.toBeInTheDocument()
    expect(api.listPendingUsers).not.toHaveBeenCalled()
    expect(api.listActiveUsers).not.toHaveBeenCalledWith('PARENT')
  })

  it('shows each student with their class and latest grade, a dash before the first one', async () => {
    api.listStudentRows.mockResolvedValue([
      { studentId: 1, classes: ['7A', '8B'], lastScore: 8, lastMaxScore: 11 },
      { studentId: 2, classes: [], lastScore: null, lastMaxScore: null },
    ])
    renderPage()

    await screen.findByRole('link', { name: 'Ana Pop' })
    expect(await screen.findByText('7A')).toBeInTheDocument()
    expect(screen.getByTestId('student-classes-1')).toHaveTextContent('8B')
    expect(screen.getByTestId('student-grade-1')).toHaveTextContent('7,55')
    expect(screen.getByTestId('student-classes-2')).toHaveTextContent('—')
    expect(screen.getByTestId('student-grade-2')).toHaveTextContent('—')
  })

  it('keeps the long explanation behind a "?"', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('link', { name: 'Ana Pop' })
    expect(screen.queryByText(/valabil 7 zile: părintele/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Despre lista de elevi' }))
    expect(await screen.findByText(/valabil 7 zile: părintele/)).toBeInTheDocument()
  })

  describe('search and filters', () => {
    const five = (role: string) => Promise.resolve(role === 'STUDENT'
      ? [student(1, 'Ana Pop'), student(2, 'Radu Nou'), student(3, 'Ștefan Ene'), student(4, 'Maria Ionescu'), student(5, 'Dan Vasile')]
      : [])
    const inClass = (studentId: number, ...classes: string[]) => ({ studentId, classes, lastScore: null, lastMaxScore: null })

    it('offers none for a short list', async () => {
      renderPage()

      await screen.findByRole('link', { name: 'Ana Pop' })
      expect(screen.queryByTestId('filter-bar')).not.toBeInTheDocument()
    })

    it('finds a student by name, ignoring diacritics, and says how many are left', async () => {
      const user = userEvent.setup()
      api.listActiveUsers.mockImplementation(five)
      renderPage()

      await screen.findByRole('link', { name: 'Ana Pop' })
      await user.type(screen.getByRole('searchbox', { name: /Caută elev/ }), 'stefan')

      expect(screen.getByRole('link', { name: 'Ștefan Ene' })).toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'Ana Pop' })).not.toBeInTheDocument()
      expect(within(screen.getByTestId('filter-bar')).getByRole('status')).toHaveTextContent('1 din 5')
    })

    it('filters by class, and says so when nothing matches, with a way back', async () => {
      const user = userEvent.setup()
      api.listActiveUsers.mockImplementation(five)
      api.listStudentRows.mockResolvedValue([inClass(1, '7A'), inClass(2, '8B'), inClass(3, '7A')])
      renderPage()

      await user.selectOptions(await screen.findByRole('combobox', { name: 'Clasa' }), '7A')
      expect(screen.getByRole('link', { name: 'Ana Pop' })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Ștefan Ene' })).toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'Radu Nou' })).not.toBeInTheDocument()

      await user.type(screen.getByRole('searchbox', { name: /Caută elev/ }), 'zzz')
      expect(screen.getByTestId('no-matches')).toBeInTheDocument()

      await user.click(screen.getAllByRole('button', { name: 'Șterge filtrele' })[0])
      expect(screen.getByRole('link', { name: 'Radu Nou' })).toBeInTheDocument()
    })
  })

  it("links each student's name to their page", async () => {
    renderPage()

    expect(await screen.findByRole('link', { name: 'Ana Pop' })).toHaveAttribute('href', '/admin/students/1')
    expect(screen.getByRole('heading', { name: 'Elevi și părinți' })).toBeInTheDocument()
  })

  it('shows waiting accounts only while someone waits, and lists an approved student right away', async () => {
    const user = userEvent.setup()
    api.listPendingUsers.mockResolvedValue([
      { id: 9, email: 'nou@scoala.ro', fullName: 'Elev Nou', requestedRole: 'STUDENT', emailVerified: true },
    ])
    api.approveUser.mockResolvedValue({})
    renderPage()

    const card = await screen.findByTestId('pending-accounts')
    expect(card).toHaveTextContent('Conturi de aprobat (1)')
    expect(card).toHaveTextContent('Elev') // the requested role in Romanian, not STUDENT
    api.listActiveUsers.mockImplementation((role: string) =>
      Promise.resolve(role === 'STUDENT' ? [student(1, 'Ana Pop'), student(9, 'Elev Nou')] : []))
    await user.click(screen.getByRole('button', { name: 'Aprobă' }))

    expect(api.approveUser).toHaveBeenCalledWith(9, 'STUDENT')
    expect(await screen.findByRole('link', { name: 'Elev Nou' })).toBeInTheDocument()
    expect(screen.queryByTestId('pending-accounts')).not.toBeInTheDocument()
  })

  it('shows no approval card when nobody waits', async () => {
    renderPage()

    await screen.findByRole('link', { name: 'Ana Pop' })
    expect(screen.queryByTestId('pending-accounts')).not.toBeInTheDocument()
  })

  it('offers "Invită părintele" only on students who have no parent', async () => {
    renderPage()

    expect(await screen.findByTestId('parent-invite-1')).toBeInTheDocument()
    expect(screen.queryByTestId('parent-invite-2')).not.toBeInTheDocument()
  })

  it("shows the student's single-use link to copy", async () => {
    const user = userEvent.setup()
    api.createParentInvite.mockResolvedValue(parentLink)
    renderPage()

    await user.click(await screen.findByTestId('parent-invite-1'))

    expect(api.createParentInvite).toHaveBeenCalledWith(1)
    expect(screen.getByLabelText('Link pentru părintele lui Ana Pop')).toHaveValue(parentLink.url)
    expect(screen.getByText(/O singură folosință/)).toBeInTheDocument()
  })

  it("shows the server's reason when the link is refused", async () => {
    const user = userEvent.setup()
    api.createParentInvite.mockRejectedValue(new ApiError(409, 'Ana Pop are deja un părinte legat (Maria).'))
    renderPage()

    await user.click(await screen.findByTestId('parent-invite-1'))

    expect(await screen.findByText(/are deja un părinte legat/)).toBeInTheDocument()
  })
})
