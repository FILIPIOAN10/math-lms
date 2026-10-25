import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TwoFactorSection } from '@/components/TwoFactorSection'
import { ApiError } from '@/lib/api'

const auth = vi.hoisted(() => ({ refreshUser: vi.fn() }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn(() => Promise.resolve('data:image/png;base64,QR')) } }))

const api = vi.hoisted(() => ({
  getTwoFactorStatus: vi.fn(),
  startTwoFactorSetup: vi.fn(),
  enableTwoFactor: vi.fn(),
  regenerateRecoveryCodes: vi.fn(),
  disableTwoFactor: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const codes = Array.from({ length: 10 }, (_, i) => `abcd-efgh-jk${i}m`)

describe('TwoFactorSection', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    auth.refreshUser.mockReset().mockResolvedValue(undefined)
  })

  it('sets it up: QR code and key, the first code, then the recovery codes once', async () => {
    api.getTwoFactorStatus
      .mockResolvedValueOnce({ enabled: false, required: true, recoveryCodesLeft: 0 })
      .mockResolvedValue({ enabled: true, required: true, recoveryCodesLeft: 10 })
    api.startTwoFactorSetup.mockResolvedValue({ secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/x' })
    api.enableTwoFactor.mockResolvedValue(codes)
    render(<TwoFactorSection />)

    expect(await screen.findByText(/Pentru administratori e obligatorie/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Activează autentificarea în doi pași' }))
    expect(await screen.findByAltText('Cod QR pentru aplicația de autentificare')).toHaveAttribute('src', 'data:image/png;base64,QR')
    expect(screen.getByTestId('two-factor-secret')).toHaveTextContent('JBSWY3DPEHPK3PXP')

    await userEvent.type(screen.getByLabelText('Codul din aplicație'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Activează' }))

    expect(api.enableTwoFactor).toHaveBeenCalledWith('123456')
    expect(await screen.findByTestId('recovery-codes')).toHaveTextContent(codes[0])
    expect(auth.refreshUser).toHaveBeenCalled() // an admin's staff pages open now
    await userEvent.click(screen.getByRole('button', { name: 'Le-am salvat' }))
    expect(await screen.findByText(/Activă\. Coduri de recuperare rămase: 10/)).toBeInTheDocument()
  })

  it('shows why a wrong first code is refused', async () => {
    api.getTwoFactorStatus.mockResolvedValue({ enabled: false, required: false, recoveryCodesLeft: 0 })
    api.startTwoFactorSetup.mockResolvedValue({ secret: 'ABC', otpauthUri: 'otpauth://totp/x' })
    api.enableTwoFactor.mockRejectedValue(new ApiError(400, 'Codul nu se potrivește. Verifică ora telefonului și încearcă din nou.'))
    render(<TwoFactorSection />)

    await userEvent.click(await screen.findByRole('button', { name: 'Activează autentificarea în doi pași' }))
    await userEvent.type(await screen.findByLabelText('Codul din aplicație'), '000000')
    await userEvent.click(screen.getByRole('button', { name: 'Activează' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Verifică ora telefonului')
  })

  it('lets a teacher turn it off with a current code, but never an admin', async () => {
    api.getTwoFactorStatus.mockResolvedValue({ enabled: true, required: false, recoveryCodesLeft: 9 })
    api.disableTwoFactor.mockResolvedValue(undefined)
    const { unmount } = render(<TwoFactorSection />)

    await userEvent.type(await screen.findByLabelText('Codul din aplicație'), '654321')
    await userEvent.click(screen.getByRole('button', { name: 'Oprește' }))
    expect(api.disableTwoFactor).toHaveBeenCalledWith('654321')
    unmount()

    api.getTwoFactorStatus.mockResolvedValue({ enabled: true, required: true, recoveryCodesLeft: 9 })
    render(<TwoFactorSection />)
    expect(await screen.findByRole('button', { name: 'Coduri de recuperare noi' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Oprește' })).not.toBeInTheDocument()
  })
})
