import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from '@/pages/LoginPage'
import { ApiError } from '@/lib/api'

const login = vi.hoisted(() => vi.fn())
const verifyTwoFactor = vi.hoisted(() => vi.fn())
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ user: null, loading: false, login, verifyTwoFactor }) }))

async function submitWithPassword() {
  render(<MemoryRouter><LoginPage /></MemoryRouter>)
  await userEvent.click(screen.getByRole('tab', { name: 'Email și parolă' }))
  await userEvent.type(screen.getByLabelText('Email'), 'ana@scoala.ro')
  await userEvent.type(screen.getByLabelText('Parolă'), 'parola-gresita')
  await userEvent.click(screen.getByRole('button', { name: 'Conectează-te' }))
}

describe('LoginPage error messages', () => {
  beforeEach(() => {
    login.mockReset()
  })

  it('says the credentials are wrong on 401', async () => {
    login.mockRejectedValue(new ApiError(401, 'Invalid email or password'))
    await submitWithPassword()
    expect(await screen.findByText('Email sau parolă greșite.')).toBeInTheDocument()
  })

  it('asks the user to wait on 429 (rate limit)', async () => {
    login.mockRejectedValue(new ApiError(429, 'Prea multe cereri. Încearcă din nou peste 42 secunde.'))
    await submitWithPassword()
    expect(await screen.findByText(/Prea multe încercări\. Așteaptă un minut/)).toBeInTheDocument()
  })

  it.each([
    ['EMAIL_NOT_VERIFIED', /Confirmă-ți adresa de email/],
    ['PENDING_APPROVAL', /așteaptă aprobarea/],
    ['REJECTED', /a fost respins/],
  ])('explains a 403 with reason %s', async (reason, expected) => {
    login.mockRejectedValue(new ApiError(403, reason))
    await submitWithPassword()
    expect(await screen.findByText(expected)).toBeInTheDocument()
  })

  it('falls back to a generic message for anything unexpected', async () => {
    login.mockRejectedValue(new ApiError(500, ''))
    await submitWithPassword()
    expect(await screen.findByText('A apărut o eroare. Încearcă din nou.')).toBeInTheDocument()
  })
})

describe('LoginPage two-step sign-in', () => {
  beforeEach(() => {
    login.mockReset()
    verifyTwoFactor.mockReset()
  })

  it('asks for the authenticator code after a right password, and sends it', async () => {
    login.mockResolvedValue(null) // 202: the code is next
    verifyTwoFactor.mockResolvedValue({ email: 'ana@scoala.ro', fullName: 'Ana', role: 'ADMIN', status: 'ACTIVE' })
    await submitWithPassword()

    await userEvent.type(await screen.findByLabelText('Codul din aplicație'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Continuă' }))

    expect(verifyTwoFactor).toHaveBeenCalledWith('123456')
  })

  it('says a wrong code is wrong and keeps the step open', async () => {
    login.mockResolvedValue(null)
    verifyTwoFactor.mockRejectedValue(new ApiError(400, 'Codul nu se potrivește.'))
    await submitWithPassword()

    await userEvent.type(await screen.findByLabelText('Codul din aplicație'), '000000')
    await userEvent.click(screen.getByRole('button', { name: 'Continuă' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Codul nu se potrivește')
    expect(screen.getByTestId('two-factor-step')).toBeInTheDocument()
  })

  it('opens straight on the code step when Google sends the user back with ?step=2fa', () => {
    render(<MemoryRouter initialEntries={['/login?step=2fa']}><LoginPage /></MemoryRouter>)

    expect(screen.getByLabelText('Codul din aplicație')).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: 'Google' })).not.toBeInTheDocument()
  })
})
