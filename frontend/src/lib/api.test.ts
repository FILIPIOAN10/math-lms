import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createQuiz, listClasses, login } from '@/lib/api'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** The URLs fetch was called with, in order, as "METHOD /path". */
function calls(fetchMock: ReturnType<typeof vi.fn>): string[] {
  return fetchMock.mock.calls.map(([url, init]) => `${(init?.method ?? 'GET').toUpperCase()} ${url}`)
}

describe('API client', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the parsed body and sends the session cookies', async () => {
    fetchMock.mockResolvedValueOnce(json([{ id: 1, name: 'Clasa a 9-a', description: null }]))

    const classes = await listClasses()

    expect(classes).toHaveLength(1)
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials: 'include' })
  })

  it('turns a failed response into an ApiError carrying the status and the body', async () => {
    fetchMock.mockResolvedValueOnce(new Response('Nu ai voie', { status: 403 }))

    await expect(listClasses()).rejects.toMatchObject({ status: 403, body: 'Nu ai voie' })
    await expect(Promise.reject(new ApiError(403, 'x'))).rejects.toBeInstanceOf(ApiError)
  })

  describe('CSRF double-submit', () => {
    it('echoes the XSRF-TOKEN cookie on writes', async () => {
      document.cookie = 'XSRF-TOKEN=token-123'
      fetchMock.mockResolvedValueOnce(json({ id: 1, title: 't', description: null, status: 'DRAFT' }))

      await createQuiz('Test', null, null, null)

      const headers = fetchMock.mock.calls[0][1].headers as Headers
      expect(headers.get('X-XSRF-TOKEN')).toBe('token-123')
    })

    it('does not send it on reads', async () => {
      document.cookie = 'XSRF-TOKEN=token-123'
      fetchMock.mockResolvedValueOnce(json([]))

      await listClasses()

      const headers = fetchMock.mock.calls[0][1].headers as Headers
      expect(headers.has('X-XSRF-TOKEN')).toBe(false)
    })
  })

  describe('silent session refresh', () => {
    it('refreshes once on a 401 and replays the request', async () => {
      fetchMock
        .mockResolvedValueOnce(new Response('', { status: 401 })) // stale access cookie
        .mockResolvedValueOnce(new Response(null, { status: 204 })) // POST /auth/refresh
        .mockResolvedValueOnce(json([])) // the replay

      await expect(listClasses()).resolves.toEqual([])

      expect(calls(fetchMock)).toEqual(['GET /api/classes', 'POST /api/auth/refresh', 'GET /api/classes'])
    })

    it('gives up with the 401 when the refresh itself fails (the session is really over)', async () => {
      fetchMock
        .mockResolvedValueOnce(new Response('', { status: 401 }))
        .mockResolvedValueOnce(new Response('', { status: 401 })) // refresh refused

      await expect(listClasses()).rejects.toMatchObject({ status: 401 })
      expect(calls(fetchMock)).toEqual(['GET /api/classes', 'POST /api/auth/refresh'])
    })

    it('never refreshes for a wrong password: a 401 on login is the real answer', async () => {
      fetchMock.mockResolvedValueOnce(new Response('Invalid email or password', { status: 401 }))

      await expect(login('x@y.ro', 'gresita')).rejects.toMatchObject({ status: 401 })
      expect(calls(fetchMock)).toEqual(['POST /api/auth/login'])
    })

    it('shares ONE refresh between parallel requests (the server rotates the token on use)', async () => {
      let refreshCalls = 0
      let refreshed = false
      fetchMock.mockImplementation(async (url: string) => {
        if (url === '/api/auth/refresh') {
          refreshCalls++
          refreshed = true
          return new Response(null, { status: 204 })
        }
        // every data call is rejected with 401 until the session has been refreshed
        return refreshed ? json([]) : new Response('', { status: 401 })
      })

      await Promise.all([listClasses(), listClasses(), listClasses()])

      expect(refreshCalls).toBe(1)
      // 3 rejected + 1 refresh + 3 replays
      expect(fetchMock).toHaveBeenCalledTimes(7)
    })
  })
})
