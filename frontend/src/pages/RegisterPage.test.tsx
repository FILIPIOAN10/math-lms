import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RegisterPage } from '@/pages/RegisterPage'
import { ApiError } from '@/lib/api'

const api = vi.hoisted(() => ({ previewInvite: vi.fn(), register: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <RegisterPage />
    </MemoryRouter>,
  )
}

async function fillAndSubmit() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Nume complet'), 'Ana Pop')
  await user.type(screen.getByLabelText('Email'), 'ana@scoala.ro')
  await user.type(screen.getByLabelText('Parolă'), 'parola123')
  await user.type(screen.getByLabelText('Confirmă parola'), 'parola123')
  await user.click(screen.getByRole('button', { name: 'Creează contul' }))
}

describe('RegisterPage', () => {
  beforeEach(() => {
    api.previewInvite.mockReset()
    api.register.mockReset().mockResolvedValue(undefined)
  })

  it('needs an invite', () => {
    renderAt('/register')

    expect(screen.getByText('Invitație necesară')).toBeInTheDocument()
    expect(api.previewInvite).not.toHaveBeenCalled()
  })

  it('names the class of a class link and promises no waiting', async () => {
    api.previewInvite.mockResolvedValue({ role: 'STUDENT', className: 'Clasa a 8-a', direct: true })
    renderAt('/register?token=codClasa')

    expect(await screen.findByTestId('invite-headline')).toHaveTextContent('Te alături clasei Clasa a 8-a')
    await fillAndSubmit()

    expect(api.register).toHaveBeenCalledWith({
      email: 'ana@scoala.ro', fullName: 'Ana Pop', password: 'parola123', inviteToken: 'codClasa',
    })
    expect(await screen.findByText(/contul tău e gata/)).toBeInTheDocument()
  })

  it('tells a newcomer through a role link that the teacher still approves', async () => {
    api.previewInvite.mockResolvedValue({ role: 'PARENT', className: null, direct: false })
    renderAt('/register?token=a.b.c')

    expect(await screen.findByTestId('invite-headline')).toHaveTextContent('Cont de părinte')
    await fillAndSubmit()

    expect(await screen.findByText(/așteaptă aprobarea profesorului/)).toBeInTheDocument()
  })

  it('refuses a dead link before the form is filled in, with the reason', async () => {
    api.previewInvite.mockRejectedValue(new ApiError(410, 'Linkul de invitație a fost oprit de profesor. Cere-i unul nou.'))
    renderAt('/register?token=codOprit')

    expect(await screen.findByRole('alert')).toHaveTextContent('oprit de profesor')
    expect(screen.queryByRole('button', { name: 'Creează contul' })).not.toBeInTheDocument()
  })

  it('still offers the form when the preview cannot be loaded', async () => {
    api.previewInvite.mockRejectedValue(new TypeError('Failed to fetch'))
    renderAt('/register?token=cod')

    expect(await screen.findByRole('button', { name: 'Creează contul' })).toBeInTheDocument()
    expect(screen.queryByTestId('invite-headline')).not.toBeInTheDocument()
  })

  it('shows why the link stopped working if it fills up while the form is open', async () => {
    api.previewInvite.mockResolvedValue({ role: 'STUDENT', className: 'Clasa a 8-a', direct: true })
    api.register.mockRejectedValue(new ApiError(410, 'Linkul de invitație a fost folosit deja de numărul maxim de persoane.'))
    renderAt('/register?token=codPlin')
    await screen.findByTestId('invite-headline')

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('numărul maxim')
  })
})
