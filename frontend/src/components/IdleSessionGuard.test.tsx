import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IdleSessionGuard } from '@/components/IdleSessionGuard'
import { ApiError, type User } from '@/lib/api'
import { readSignOutNotice } from '@/lib/idleSession'

const student: User = { email: 'ana@scoala.ro', fullName: 'Ana', role: 'STUDENT', status: 'ACTIVE' }

const auth = vi.hoisted(() => ({
  value: {
    user: null as User | null,
    logout: vi.fn(),
    expireSession: vi.fn(),
  },
}))
vi.mock('@/context/AuthContext', () => ({ useAuth: () => auth.value }))

const api = vi.hoisted(() => ({ keepSessionAlive: vi.fn(), getCurrentUser: vi.fn() }))
vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), ...api }))

const MINUTE = 60_000

async function wait(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms))
}

const warning = () => screen.queryByRole('dialog', { name: 'Mai ești aici?' })

describe('IdleSessionGuard: a session nobody uses for an hour closes itself', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date('2026-11-28T08:00:00Z'))
    localStorage.clear()
    sessionStorage.clear()
    auth.value = {
      user: student,
      logout: vi.fn().mockResolvedValue(undefined),
      expireSession: vi.fn().mockResolvedValue(undefined),
    }
    api.keepSessionAlive.mockReset().mockResolvedValue({ idleTimeoutMinutes: 60 })
    api.getCurrentUser.mockReset()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('does nothing without a signed-in user', async () => {
    auth.value.user = null
    render(<IdleSessionGuard />)

    await wait(61 * MINUTE)

    expect(api.keepSessionAlive).not.toHaveBeenCalled()
    expect(auth.value.expireSession).not.toHaveBeenCalled()
  })

  it('counts the sign-in itself as activity, so an old timestamp does not sign out at once', async () => {
    localStorage.setItem('mathlms-last-activity', String(Date.now() - 3 * 60 * MINUTE))
    render(<IdleSessionGuard />)

    await wait(MINUTE)

    expect(auth.value.expireSession).not.toHaveBeenCalled()
    expect(warning()).not.toBeInTheDocument()
  })

  it('warns five minutes before, with the time left', async () => {
    render(<IdleSessionGuard />)

    await wait(54 * MINUTE)
    expect(warning()).not.toBeInTheDocument()

    await wait(MINUTE + 1000)
    expect(warning()).toBeInTheDocument()
    expect(warning()).toHaveTextContent(/4:5\d/)
  })

  it('takes any key press as activity and closes the warning', async () => {
    render(<IdleSessionGuard />)
    await wait(56 * MINUTE)
    expect(warning()).toBeInTheDocument()

    fireEvent.keyDown(window, { key: 'a' })

    await waitFor(() => expect(warning()).not.toBeInTheDocument())
    await wait(10 * MINUTE)
    expect(auth.value.expireSession).not.toHaveBeenCalled()
  })

  it('signs out after an hour without activity and leaves a note for the sign-in page', async () => {
    render(<IdleSessionGuard />)

    await wait(60 * MINUTE + 15_000)

    expect(auth.value.expireSession).toHaveBeenCalledTimes(1)
    expect(readSignOutNotice()).toBe('idle')
  })

  it('keeps the session on „Rămân conectat” and tells the server at once', async () => {
    render(<IdleSessionGuard />)
    await wait(56 * MINUTE)
    api.keepSessionAlive.mockClear()

    fireEvent.click(screen.getByRole('button', { name: 'Rămân conectat' }))

    await waitFor(() => expect(api.keepSessionAlive).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(warning()).not.toBeInTheDocument())
    await wait(10 * MINUTE)
    expect(auth.value.expireSession).not.toHaveBeenCalled()
  })

  it('signs out on „Ieși acum” like the account menu does', async () => {
    render(<IdleSessionGuard />)
    await wait(56 * MINUTE)

    fireEvent.click(screen.getByRole('button', { name: 'Ieși acum' }))

    await waitFor(() => expect(auth.value.logout).toHaveBeenCalledTimes(1))
    expect(auth.value.expireSession).not.toHaveBeenCalled()
  })

  it('tells the server about activity at most once a minute, and not at all while nobody works', async () => {
    render(<IdleSessionGuard />)
    await waitFor(() => expect(api.keepSessionAlive).toHaveBeenCalledTimes(1)) // the sign-in itself
    api.keepSessionAlive.mockClear()

    for (let i = 0; i < 10; i++) {
      fireEvent.keyDown(window, { key: 'a' })
      await wait(5000)
    }
    expect(api.keepSessionAlive).not.toHaveBeenCalled()

    await wait(15_000)
    expect(api.keepSessionAlive).toHaveBeenCalledTimes(1)

    await wait(5 * MINUTE)
    expect(api.keepSessionAlive).toHaveBeenCalledTimes(1)
  })

  it('signs out, with its own note, when the server has already closed the session', async () => {
    api.keepSessionAlive.mockRejectedValue(new ApiError(401, ''))
    api.getCurrentUser.mockRejectedValue(new ApiError(401, ''))

    render(<IdleSessionGuard />)

    await waitFor(() => expect(auth.value.expireSession).toHaveBeenCalledTimes(1))
    expect(readSignOutNotice()).toBe('ended')
  })

  it('stays signed in when a refused ping turns out to be a session another tab just renewed', async () => {
    api.keepSessionAlive.mockRejectedValue(new ApiError(401, ''))
    api.getCurrentUser.mockResolvedValue(student)

    render(<IdleSessionGuard />)

    await waitFor(() => expect(api.getCurrentUser).toHaveBeenCalled())
    await wait(MINUTE)
    expect(auth.value.expireSession).not.toHaveBeenCalled()
  })

  it('does not take a ping lost to the network for a closed session', async () => {
    api.keepSessionAlive.mockRejectedValue(new TypeError('Failed to fetch'))

    render(<IdleSessionGuard />)

    await waitFor(() => expect(api.keepSessionAlive).toHaveBeenCalled())
    await wait(MINUTE)
    expect(api.getCurrentUser).not.toHaveBeenCalled()
    expect(auth.value.expireSession).not.toHaveBeenCalled()
  })

  it('counts work done in another tab', async () => {
    render(<IdleSessionGuard />)
    await wait(56 * MINUTE)
    expect(warning()).toBeInTheDocument()

    act(() => {
      localStorage.setItem('mathlms-last-activity', String(Date.now()))
      window.dispatchEvent(new StorageEvent('storage', { key: 'mathlms-last-activity' }))
    })

    await waitFor(() => expect(warning()).not.toBeInTheDocument())
    await wait(10 * MINUTE)
    expect(auth.value.expireSession).not.toHaveBeenCalled()
  })

  it('signs out at once on a computer that wakes up two hours later', async () => {
    render(<IdleSessionGuard />)
    await wait(1000)

    // Asleep: no timer ran, only the clock moved on.
    vi.setSystemTime(Date.now() + 2 * 60 * MINUTE)
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'))
    })

    await waitFor(() => expect(auth.value.expireSession).toHaveBeenCalledTimes(1))
    expect(readSignOutNotice()).toBe('idle')
  })

  it('follows the idle limit the server names', async () => {
    api.keepSessionAlive.mockResolvedValue({ idleTimeoutMinutes: 30 })
    render(<IdleSessionGuard />)
    await waitFor(() => expect(api.keepSessionAlive).toHaveBeenCalled())

    await wait(25 * MINUTE + 1000)

    expect(warning()).toBeInTheDocument()
  })
})
