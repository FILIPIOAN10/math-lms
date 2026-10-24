import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminStudentsPage } from '@/pages/AdminStudentsPage'
import { ApiError, type AdminUserSummary, type InviteLink, type User } from '@/lib/api'

const auth = vi.hoisted(() => ({ value: { user: null as User | null, loading: false, logout: vi.fn() } }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({
  listActiveUsers: vi.fn(),
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
