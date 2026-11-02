import { render, screen, waitFor } from '@testing-library/react'
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

describe('LoginPage: how you signed in last', () => {
  beforeEach(() => {
    login.mockReset()
    localStorage.clear()
  })

  const renderLogin = () => render(<MemoryRouter><LoginPage /></MemoryRouter>)

  it('opens on Google the first time', () => {
    renderLogin()

    expect(screen.getByRole('tab', { name: 'Google' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.queryByLabelText('Parolă')).not.toBeInTheDocument()
  })

  it('opens with the password fields on screen after a password sign-in', async () => {
    login.mockResolvedValue({ email: 'ana@scoala.ro' })
    const { unmount } = renderLogin()
    await userEvent.click(screen.getByRole('tab', { name: 'Email și parolă' }))
    await userEvent.type(screen.getByLabelText('Email'), 'ana@scoala.ro')
    await userEvent.type(screen.getByLabelText('Parolă'), 'parola-buna')
    await userEvent.click(screen.getByRole('button', { name: 'Conectează-te' }))
    await waitFor(() => expect(login).toHaveBeenCalled())
    await waitFor(() => expect(localStorage.getItem('mathlms-login-method')).toBe('password'))
    unmount()

    renderLogin()

    expect(screen.getByLabelText('Parolă')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
  })

  it('remembers a password sign-in that goes on to the authenticator code', async () => {
    login.mockResolvedValue(null)
    await submitWithPassword()

    expect(await screen.findByTestId('two-factor-step')).toBeInTheDocument()
    expect(localStorage.getItem('mathlms-login-method')).toBe('password')
  })

  it('does not remember a password that was refused', async () => {
    login.mockRejectedValue(new ApiError(401, ''))
    await submitWithPassword()

    await screen.findByText('Email sau parolă greșite.')
    expect(localStorage.getItem('mathlms-login-method')).toBeNull()
  })

  it('still opens when the browser blocks storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    renderLogin()

    expect(screen.getByRole('tab', { name: 'Google' })).toBeInTheDocument()
    vi.restoreAllMocks()
  })

  it('tells someone without an account where to get one', () => {
    renderLogin()
    expect(screen.getByTestId('no-account')).toHaveTextContent('Nu ai cont? Cere-i profesorului linkul de invitație.')
  })

  it('leaves that line out of the authenticator step', async () => {
    login.mockResolvedValue(null)
    await submitWithPassword()

    await screen.findByTestId('two-factor-step')
    expect(screen.queryByTestId('no-account')).not.toBeInTheDocument()
  })
})

describe('LoginPage error messages', () => {
  beforeEach(() => {
    login.mockReset()
    localStorage.clear()
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

describe('LoginPage after Google refused a sign-in', () => {
  beforeEach(() => localStorage.clear())

  const renderAt = (url: string) => render(<MemoryRouter initialEntries={[url]}><LoginPage /></MemoryRouter>)

  it('says there is no account, and still says where to get one', () => {
    renderAt('/login?error=no_account')

    expect(screen.getByRole('alert')).toHaveTextContent('Nu există un cont pentru acest email Google.')
    expect(screen.getByTestId('no-account')).toHaveTextContent('Cere-i profesorului linkul de invitație')
  })

  it('says the invitation is no longer valid', () => {
    renderAt('/login?error=invite_unavailable')

    expect(screen.getByRole('alert')).toHaveTextContent('Linkul de invitație nu mai e valabil.')
  })

  it('says Google has not confirmed the email', () => {
    renderAt('/login?error=email_not_verified')

    expect(screen.getByRole('alert')).toHaveTextContent('Google nu a confirmat încă acest email.')
  })

  it('says the visitor cancelled on Google', () => {
    renderAt('/login?error=access_denied')

    expect(screen.getByRole('alert')).toHaveTextContent('Ai anulat conectarea cu Google.')
  })

  it('never repeats what it does not know from the address', () => {
    renderAt('/login?error=altceva-din-adres%C4%83')

    expect(screen.getByRole('alert')).toHaveTextContent('Conectarea cu Google nu a reușit.')
    expect(screen.queryByText(/altceva/)).not.toBeInTheDocument()
  })

  it('shows the reason on the Google tab even after a password sign-in', () => {
    localStorage.setItem('mathlms-login-method', 'password')
    renderAt('/login?error=no_account')

    expect(screen.getByRole('tab', { name: 'Google' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('has nothing to say on a plain visit', () => {
    renderAt('/login')

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
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
