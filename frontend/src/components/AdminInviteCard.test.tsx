import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminInviteCard } from '@/components/AdminInviteCard'
import { ApiError, type InviteLink } from '@/lib/api'

const api = vi.hoisted(() => ({
  listAdminInvites: vi.fn(),
  createAdminInvite: vi.fn(),
  revokeInviteLink: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const link: InviteLink = {
  id: 7, kind: 'ADMIN', url: 'http://localhost:5173/register?token=codProfesor', uses: 0, maxUses: 1,
  expiresAt: '2026-10-25T10:00:00Z', createdAt: '2026-10-24T10:00:00Z', className: null, studentName: null,
}

describe('AdminInviteCard', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.listAdminInvites.mockResolvedValue([link])
  })

  it('labels each link field so a screen reader can name it', async () => {
    render(<AdminInviteCard />)

    expect(await screen.findByLabelText(/Link pentru profesor/)).toHaveValue(link.url)
  })

  it('revokes only after the admin confirms', async () => {
    const user = userEvent.setup()
    api.revokeInviteLink.mockResolvedValue(undefined)
    render(<AdminInviteCard />)

    await user.click(await screen.findByRole('button', { name: 'Revocă' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Anulează' }))
    expect(api.revokeInviteLink).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Revocă' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Revocă' }))
    expect(api.revokeInviteLink).toHaveBeenCalledWith(7)
  })

  it("shows the server's reason when a link cannot be made", async () => {
    const user = userEvent.setup()
    api.createAdminInvite.mockRejectedValue(new ApiError(429, 'Prea multe cereri. Încearcă peste un minut.'))
    render(<AdminInviteCard />)

    await user.click(await screen.findByRole('button', { name: 'Generează link sigur' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Prea multe cereri')
  })
})
