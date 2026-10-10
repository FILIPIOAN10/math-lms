import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { EraseAccountPage } from '@/pages/EraseAccountPage'
import { ApiError } from '@/lib/api'

const auth = vi.hoisted(() => ({ logout: vi.fn() }))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ user: null, loading: false, logout: auth.logout }) }))

const api = vi.hoisted(() => ({ confirmAccountErasure: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <EraseAccountPage />
    </MemoryRouter>,
  )
}

describe('EraseAccountPage (the link from the confirmation email)', () => {
  beforeEach(() => {
    api.confirmAccountErasure.mockReset()
    auth.logout.mockReset().mockResolvedValue(undefined)
  })

  it('deletes nothing on its own: the user has to press the button', async () => {
    renderAt('/erase-account?token=abc')

    expect(screen.getByRole('heading', { name: 'Ștergi contul definitiv?' })).toBeInTheDocument()
    expect(api.confirmAccountErasure).not.toHaveBeenCalled()
    expect(document.title).toBe('Ștergerea contului · Math LMS')
  })

  it('erases the account, signs this browser out and says it is done', async () => {
    api.confirmAccountErasure.mockResolvedValue(undefined)
    renderAt('/erase-account?token=abc')

    await userEvent.click(screen.getByRole('button', { name: 'Șterge definitiv contul' }))

    await waitFor(() => expect(api.confirmAccountErasure).toHaveBeenCalledWith('abc'))
    expect(await screen.findByRole('heading', { name: 'Contul a fost șters' })).toBeInTheDocument()
    expect(auth.logout).toHaveBeenCalled()
  })

  it('says the link is no good when the token is expired or already used', async () => {
    api.confirmAccountErasure.mockRejectedValue(new ApiError(400, 'Invalid or expired token'))
    renderAt('/erase-account?token=vechi')

    await userEvent.click(screen.getByRole('button', { name: 'Șterge definitiv contul' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Linkul a expirat sau a fost deja folosit')
  })

  it('shows an invalid-link screen without a token', () => {
    renderAt('/erase-account')

    expect(screen.getByRole('heading', { name: 'Link invalid' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Șterge definitiv contul' })).not.toBeInTheDocument()
  })
})
