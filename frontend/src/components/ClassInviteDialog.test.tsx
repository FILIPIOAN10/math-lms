import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ClassInviteDialog } from '@/components/ClassInviteDialog'
import { ApiError, type InviteLink, type SchoolClass } from '@/lib/api'

const clasa: SchoolClass = { id: 3, name: 'Clasa a 8-a', description: null, teacherId: 1 }

const link = (id: number, over: Partial<InviteLink> = {}): InviteLink => ({
  id, kind: 'CLASS', url: `http://localhost:5173/register?token=cod${id}`, uses: 2, maxUses: 30,
  expiresAt: '2026-10-18T10:00:00Z', createdAt: '2026-10-11T10:00:00Z', className: 'Clasa a 8-a', studentName: null,
  ...over,
})

function renderDialog(api: {
  list?: (classId: number) => Promise<InviteLink[]>
  create?: (classId: number, maxUses?: number) => Promise<InviteLink>
  revoke?: (id: number) => Promise<void>
} = {}) {
  const list = vi.fn(api.list ?? (() => Promise.resolve([])))
  const create = vi.fn(api.create ?? ((_: number, max?: number) => Promise.resolve(link(9, { uses: 0, maxUses: max ?? 30 }))))
  const revoke = vi.fn(api.revoke ?? (() => Promise.resolve()))
  render(
    <ClassInviteDialog open onClose={() => {}} schoolClass={clasa}
      listClassInvites={list} createClassInvite={create} revokeInviteLink={revoke} />,
  )
  return { list, create, revoke }
}

describe('ClassInviteDialog', () => {
  it("lists the class's working links with how much of each is used", async () => {
    renderDialog({ list: () => Promise.resolve([link(1), link(2, { uses: 30 })]) })

    const items = within(await screen.findByTestId('class-invites')).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Folosit 2 / 30')
    expect(within(items[0]).getByRole('textbox')).toHaveValue('http://localhost:5173/register?token=cod1')
    expect(items[1]).toHaveTextContent('plin — generează altul')
  })

  it('says so when the class has no link yet', async () => {
    renderDialog()

    expect(await screen.findByText(/Niciun link activ/)).toBeInTheDocument()
  })

  it('generates a link with the chosen cap and shows it first', async () => {
    const user = userEvent.setup()
    const { create } = renderDialog({ list: () => Promise.resolve([link(1)]) })
    await screen.findByTestId('class-invites')

    const cap = screen.getByLabelText('Câți elevi îl pot folosi')
    await user.clear(cap)
    await user.type(cap, '25')
    await user.click(screen.getByRole('button', { name: 'Generează link nou' }))

    expect(create).toHaveBeenCalledWith(3, 25)
    const items = within(screen.getByTestId('class-invites')).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Folosit 0 / 25')
  })

  it('refuses a cap outside 1–200 without calling the server', async () => {
    const user = userEvent.setup()
    const { create } = renderDialog()
    await screen.findByText(/Niciun link activ/)

    const cap = screen.getByLabelText('Câți elevi îl pot folosi')
    await user.clear(cap)
    await user.type(cap, '0')

    expect(screen.getByRole('button', { name: 'Generează link nou' })).toBeDisabled()
    expect(screen.getByText('Alege un număr între 1 și 200.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('stops a link', async () => {
    const user = userEvent.setup()
    const { revoke } = renderDialog({ list: () => Promise.resolve([link(1), link(2)]) })
    await screen.findByTestId('class-invites')

    await user.click(screen.getAllByRole('button', { name: 'Oprește linkul' })[0])

    expect(revoke).toHaveBeenCalledWith(1)
    expect(within(screen.getByTestId('class-invites')).getAllByRole('listitem')).toHaveLength(1)
  })

  it("shows the server's reason when generating fails", async () => {
    const user = userEvent.setup()
    renderDialog({ create: () => Promise.reject(new ApiError(404, 'Clasa nu există.')) })
    await screen.findByText(/Niciun link activ/)

    await user.click(screen.getByRole('button', { name: 'Generează link nou' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Clasa nu există.')
  })
})
