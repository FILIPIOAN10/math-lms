import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AccountPage } from '@/pages/AccountPage'
import { ApiError, type Account, type DeviceSession } from '@/lib/api'

const auth = vi.hoisted(() => ({ logout: vi.fn() }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ user: null, loading: false, logout: auth.logout }) }))

const api = vi.hoisted(() => ({
  getAccount: vi.fn(),
  changePassword: vi.fn(),
  listSessions: vi.fn(),
  revokeSession: vi.fn(),
  logoutAll: vi.fn(),
  requestMyData: vi.fn(),
  requestAccountErasure: vi.fn(),
}))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const account = (over: Partial<Account> = {}): Account => ({
  email: 'ana@scoala.ro', fullName: 'Ana Student', role: 'STUDENT', hasPassword: true, googleLinked: false, ...over,
})
const session = (id: string, current: boolean, device = 'Chrome pe Windows'): DeviceSession => ({
  sessionId: id, deviceInfo: device, ipAddress: '10.0.0.1', createdAt: Date.now() - 86_400_000, lastUsedAt: Date.now(), current,
})

function renderPage() {
  return render(
    <MemoryRouter>
      <AccountPage />
    </MemoryRouter>,
  )
}

const section = (name: string) => screen.findByRole('region', { name })

describe('AccountPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    auth.logout.mockReset()
    auth.logout.mockResolvedValue(undefined)
    api.getAccount.mockResolvedValue(account())
    api.listSessions.mockResolvedValue([session('s1', true), session('s2', false, 'Safari pe iPhone')])
  })

  it('names the page and shows who is signed in, with the role in Romanian', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Contul meu' })).toBeInTheDocument()
    const profile = await section('Profil')
    expect(profile).toHaveTextContent('Ana Student')
    expect(profile).toHaveTextContent('ana@scoala.ro')
    expect(profile).toHaveTextContent('Elev')
    expect(profile).toHaveTextContent('Email și parolă')
    expect(document.title).toBe('Contul meu · Math LMS')
  })

  describe('password', () => {
    it('changes it and says the other devices were signed out', async () => {
      api.changePassword.mockResolvedValue(undefined)
      renderPage()
      const box = await section('Parola')

      await userEvent.type(within(box).getByLabelText('Parola actuală'), 'parola-veche')
      await userEvent.type(within(box).getByLabelText('Parola nouă'), 'parola-noua-123')
      await userEvent.type(within(box).getByLabelText('Repetă parola nouă'), 'parola-noua-123')
      await userEvent.click(within(box).getByRole('button', { name: 'Schimbă parola' }))

      await waitFor(() => expect(api.changePassword).toHaveBeenCalledWith('parola-veche', 'parola-noua-123'))
      expect(await within(box).findByRole('status')).toHaveTextContent('Parola a fost schimbată')
      expect(within(box).getByLabelText('Parola actuală')).toHaveValue('')
      expect(api.listSessions).toHaveBeenCalledTimes(2) // the session list is re-read: the other devices are gone
    })

    it('checks the two new passwords match before asking the server', async () => {
      renderPage()
      const box = await section('Parola')

      await userEvent.type(within(box).getByLabelText('Parola actuală'), 'parola-veche')
      await userEvent.type(within(box).getByLabelText('Parola nouă'), 'parola-noua-123')
      await userEvent.type(within(box).getByLabelText('Repetă parola nouă'), 'alta-parola-123')
      await userEvent.click(within(box).getByRole('button', { name: 'Schimbă parola' }))

      expect(await within(box).findByRole('alert')).toHaveTextContent('Parolele noi nu coincid')
      expect(api.changePassword).not.toHaveBeenCalled()
    })

    it("shows the server's reason when the current password is wrong", async () => {
      api.changePassword.mockRejectedValue(new ApiError(400, 'Parola actuală nu e corectă.'))
      renderPage()
      const box = await section('Parola')

      await userEvent.type(within(box).getByLabelText('Parola actuală'), 'gresita')
      await userEvent.type(within(box).getByLabelText('Parola nouă'), 'parola-noua-123')
      await userEvent.type(within(box).getByLabelText('Repetă parola nouă'), 'parola-noua-123')
      await userEvent.click(within(box).getByRole('button', { name: 'Schimbă parola' }))

      expect(await within(box).findByRole('alert')).toHaveTextContent('Parola actuală nu e corectă.')
    })

    it('sends a Google-only account to the reset flow instead of a form', async () => {
      api.getAccount.mockResolvedValue(account({ hasPassword: false, googleLinked: true }))
      renderPage()
      const box = await section('Parola')

      expect(within(box).queryByLabelText('Parola actuală')).not.toBeInTheDocument()
      expect(box).toHaveTextContent('Google')
      expect(within(box).getByRole('link', { name: 'Setează o parolă' })).toHaveAttribute('href', '/forgot-password')
    })
  })

  describe('sessions', () => {
    it('lists the devices, marks this one, and signs another one out', async () => {
      api.revokeSession.mockResolvedValue(undefined)
      renderPage()
      const box = await section('Dispozitive conectate')

      const rows = await within(box).findAllByTestId('session-row')
      expect(rows).toHaveLength(2)
      expect(rows[0]).toHaveTextContent('Acest dispozitiv')
      expect(within(rows[0]).queryByRole('button', { name: /Deconectează/ })).not.toBeInTheDocument()

      await userEvent.click(within(rows[1]).getByRole('button', { name: 'Deconectează Safari pe iPhone' }))

      await waitFor(() => expect(api.revokeSession).toHaveBeenCalledWith('s2'))
    })

    it('signs out everywhere after confirming, and then this browser too', async () => {
      api.logoutAll.mockResolvedValue(undefined)
      renderPage()
      const box = await section('Dispozitive conectate')

      await userEvent.click(within(box).getByRole('button', { name: 'Deconectează-te de peste tot' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Deconectează-te' }))

      await waitFor(() => expect(api.logoutAll).toHaveBeenCalled())
      expect(auth.logout).toHaveBeenCalled()
    })
  })

  describe('your data', () => {
    it('asks for the archive and says the link comes by email', async () => {
      api.requestMyData.mockResolvedValue('QUEUED')
      renderPage()
      const box = await section('Datele tale')

      await userEvent.click(within(box).getByRole('button', { name: 'Cere datele mele' }))

      expect(await within(box).findByRole('status')).toHaveTextContent('primești pe email un link')
      expect(api.requestMyData).toHaveBeenCalledTimes(1)
    })

    it('says so when the archive is already being built', async () => {
      api.requestMyData.mockResolvedValue('ALREADY_BUILDING')
      renderPage()
      const box = await section('Datele tale')

      await userEvent.click(within(box).getByRole('button', { name: 'Cere datele mele' }))

      expect(await within(box).findByRole('status')).toHaveTextContent('se pregătește deja')
    })
  })

  describe('deleting the account', () => {
    it('asks for the password, confirms, and says to check the email', async () => {
      api.requestAccountErasure.mockResolvedValue(undefined)
      renderPage()
      const box = await section('Șterge contul')

      await userEvent.type(within(box).getByLabelText('Parola (pentru confirmare)'), 'parola-veche')
      await userEvent.click(within(box).getByRole('button', { name: 'Șterge contul' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Trimite emailul' }))

      await waitFor(() => expect(api.requestAccountErasure).toHaveBeenCalledWith('parola-veche'))
      expect(await within(box).findByRole('status')).toHaveTextContent('Ți-am trimis un email')
    })

    it('says plainly when the password is wrong (the server answers 401 there)', async () => {
      api.requestAccountErasure.mockRejectedValue(new ApiError(401, 'Invalid email or password'))
      renderPage()
      const box = await section('Șterge contul')

      await userEvent.type(within(box).getByLabelText('Parola (pentru confirmare)'), 'gresita')
      await userEvent.click(within(box).getByRole('button', { name: 'Șterge contul' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Trimite emailul' }))

      expect(await within(box).findByRole('alert')).toHaveTextContent('Parola nu e corectă.')
    })

    it('needs no password for a Google-only account', async () => {
      api.getAccount.mockResolvedValue(account({ hasPassword: false, googleLinked: true }))
      api.requestAccountErasure.mockResolvedValue(undefined)
      renderPage()
      const box = await section('Șterge contul')

      expect(within(box).queryByLabelText('Parola (pentru confirmare)')).not.toBeInTheDocument()
      await userEvent.click(within(box).getByRole('button', { name: 'Șterge contul' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Trimite emailul' }))

      await waitFor(() => expect(api.requestAccountErasure).toHaveBeenCalledWith(null))
    })

    it("lets a teacher try, and shows the server's reason when they still own classes", async () => {
      api.getAccount.mockResolvedValue(account({ role: 'TEACHER', fullName: 'Prof Ana', hasPassword: false }))
      api.requestAccountErasure.mockRejectedValue(
        new ApiError(409, 'Mai ai clase sau teste: cere unui administrator să le dea altui profesor, apoi poți șterge contul.'))
      renderPage()
      const box = await section('Șterge contul')

      expect(within(box).getByTestId('staff-erase-note')).toBeInTheDocument()
      await userEvent.click(within(box).getByRole('button', { name: 'Șterge contul' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Trimite emailul' }))

      expect(await within(box).findByRole('alert')).toHaveTextContent('Mai ai clase sau teste')
    })
  })
})
