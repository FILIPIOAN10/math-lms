import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from '@/context/AuthContext'
import { ApiError, type User } from '@/lib/api'

const api = vi.hoisted(() => ({ getCurrentUser: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const student: User = { email: 'ana@x.ro', fullName: 'Ana', role: 'STUDENT', status: 'ACTIVE' }

function Who() {
  const { user, loading } = useAuth()
  if (loading) return <p>se verifică</p>
  return <p>{user ? `conectat: ${user.fullName}` : 'neconectat'}</p>
}

function renderApp() {
  return render(
    <AuthProvider>
      <Who />
    </AuthProvider>,
  )
}

describe('AuthProvider', () => {
  beforeEach(() => {
    api.getCurrentUser.mockReset()
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows the app with the signed-in user', async () => {
    api.getCurrentUser.mockResolvedValue(student)
    renderApp()
    expect(await screen.findByText('conectat: Ana')).toBeInTheDocument()
  })

  it('treats a 401 as signed out', async () => {
    api.getCurrentUser.mockRejectedValue(new ApiError(401, ''))
    renderApp()
    expect(await screen.findByText('neconectat')).toBeInTheDocument()
    expect(screen.queryByTestId('server-unreachable')).not.toBeInTheDocument()
  })

  it('treats another refusal as signed out, so nobody is locked behind the connection screen', async () => {
    api.getCurrentUser.mockRejectedValue(new ApiError(403, ''))
    renderApp()
    expect(await screen.findByText('neconectat')).toBeInTheDocument()
  })

  it.each([
    ['no network', new TypeError('Failed to fetch')],
    ['a bad gateway', new ApiError(502, '')],
    ['a server error', new ApiError(500, '')],
    ['too many requests', new ApiError(429, '')],
  ])('shows the connection screen, not the signed-out app, on %s', async (_, failure) => {
    api.getCurrentUser.mockRejectedValue(failure)
    renderApp()

    expect(await screen.findByTestId('server-unreachable')).toHaveTextContent('Nu ne putem conecta la server')
    expect(screen.queryByText('neconectat')).not.toBeInTheDocument()
  })

  it('carries on where the student was once "Reîncearcă" gets through', async () => {
    api.getCurrentUser.mockRejectedValueOnce(new ApiError(502, '')).mockResolvedValue(student)
    renderApp()

    await userEvent.click(await screen.findByRole('button', { name: 'Reîncearcă' }))

    expect(await screen.findByText('conectat: Ana')).toBeInTheDocument()
    expect(screen.queryByTestId('server-unreachable')).not.toBeInTheDocument()
  })

  it('tries again by itself every few seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    api.getCurrentUser.mockRejectedValue(new TypeError('Failed to fetch'))
    renderApp()
    await screen.findByTestId('server-unreachable')

    api.getCurrentUser.mockResolvedValue(student)
    await act(() => vi.advanceTimersByTimeAsync(5000))

    await waitFor(() => expect(screen.getByText('conectat: Ana')).toBeInTheDocument())
  })

  it('tries again as soon as the device is back online', async () => {
    api.getCurrentUser.mockRejectedValue(new TypeError('Failed to fetch'))
    renderApp()
    await screen.findByTestId('server-unreachable')

    api.getCurrentUser.mockResolvedValue(student)
    act(() => {
      window.dispatchEvent(new Event('online'))
    })

    expect(await screen.findByText('conectat: Ana')).toBeInTheDocument()
  })

  it('says a retry that fails again, without leaving the screen', async () => {
    api.getCurrentUser.mockRejectedValue(new ApiError(503, ''))
    renderApp()

    await userEvent.click(await screen.findByRole('button', { name: 'Reîncearcă' }))

    expect(await screen.findByRole('button', { name: 'Reîncearcă' })).toBeEnabled()
    expect(screen.getByTestId('server-unreachable')).toBeInTheDocument()
  })
})
