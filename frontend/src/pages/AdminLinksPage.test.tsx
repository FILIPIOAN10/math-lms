import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminLinksPage } from '@/pages/AdminLinksPage'
import { ApiError, type AdminUserSummary, type InviteLink } from '@/lib/api'

const api = vi.hoisted(() => ({ listActiveUsers: vi.fn(), linkParent: vi.fn(), createParentInvite: vi.fn() }))
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
      <AdminLinksPage />
    </MemoryRouter>,
  )
}

describe('AdminLinksPage — parent invites', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listActiveUsers.mockImplementation((role: string) =>
      Promise.resolve(role === 'STUDENT' ? [student(1, 'Ana Pop'), student(2, 'Radu Nou', 'Maria Parinte')] : []))
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
